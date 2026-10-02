package com.anitec.platform.core.session

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "anitec_session")

/**
 * Persists the signed-in user. The JWT is encrypted with an AES-256-GCM key kept in the
 * Android Keystore (Tink), so it is never written to disk in clear text.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Emits when the server rejects the stored token (HTTP 401). */
    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    init {
        scope.launch { _state.value = readStored() }
    }

    /** Token used by the HTTP layer; null when signed out. */
    fun currentToken(): String? = (_state.value as? SessionState.SignedIn)?.session?.token

    suspend fun save(session: UserSession) {
        context.sessionDataStore.edit { prefs ->
            prefs[USER_ID] = session.userId
            prefs[USERNAME] = session.username
            prefs[FULL_NAME] = session.fullName
            if (session.email != null) prefs[EMAIL] = session.email else prefs.remove(EMAIL)
            prefs[ROLE] = session.role.apiValue
            prefs[TOKEN] = encrypt(session.token)
        }
        _state.value = SessionState.SignedIn(session)
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
        _state.value = SessionState.SignedOut
    }

    /** Called from the HTTP layer when the server answers 401 to an authenticated request. */
    fun onUnauthorized() {
        if (_state.value !is SessionState.SignedIn) return
        _state.value = SessionState.SignedOut
        scope.launch {
            context.sessionDataStore.edit { it.clear() }
            _expired.emit(Unit)
        }
    }

    private suspend fun readStored(): SessionState = try {
        val prefs = context.sessionDataStore.data.first()
        val encrypted = prefs[TOKEN]
        val role = UserRole.fromApi(prefs[ROLE])
        val userId = prefs[USER_ID]
        if (encrypted == null || role == null || userId == null) {
            SessionState.SignedOut
        } else {
            SessionState.SignedIn(
                UserSession(
                    userId = userId,
                    username = prefs[USERNAME].orEmpty(),
                    fullName = prefs[FULL_NAME].orEmpty(),
                    role = role,
                    token = decrypt(encrypted),
                    email = prefs[EMAIL],
                ),
            )
        }
    } catch (e: Exception) {
        // A key that cannot be used (e.g. after a device restore) means the user must sign in again.
        context.sessionDataStore.edit { it.clear() }
        SessionState.SignedOut
    }

    private fun encrypt(plain: String): String =
        Base64.encodeToString(aead.encrypt(plain.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA), Base64.NO_WRAP)

    private fun decrypt(encoded: String): String =
        String(aead.decrypt(Base64.decode(encoded, Base64.NO_WRAP), ASSOCIATED_DATA), Charsets.UTF_8)

    private companion object {
        const val KEYSET_NAME = "anitec_session_keyset"
        const val KEYSET_PREFS = "anitec_session_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://anitec_session_master_key"
        val ASSOCIATED_DATA = "anitec-session-token".toByteArray(Charsets.UTF_8)

        val USER_ID = intPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val FULL_NAME = stringPreferencesKey("full_name")
        val EMAIL = stringPreferencesKey("email")
        val ROLE = stringPreferencesKey("role")
        val TOKEN = stringPreferencesKey("token")
    }
}
