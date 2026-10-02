package com.anitec.platform.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.iam.application.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = sessionStore.state

    private val _sessionExpired = MutableStateFlow(false)

    /** True after the server rejected the token, until the next successful sign-in. */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    init {
        viewModelScope.launch { sessionStore.expired.collect { _sessionExpired.value = true } }
        viewModelScope.launch {
            sessionStore.state.collect { if (it is SessionState.SignedIn) _sessionExpired.value = false }
        }
    }

    fun onSignOut() {
        viewModelScope.launch { signOut() }
    }
}
