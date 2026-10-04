package com.anitec.platform.core.session

import com.anitec.platform.core.common.UserDataCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes [SessionStore] and wipes the local cache whenever the session ends.
 *
 * Triggered by explicit sign-out or an expired/rejected token (HTTP 401 via [AuthInterceptor]),
 * so one user's animals, farms and health records are never shown to the next person who signs
 * in on the same device. Started once from [com.anitec.platform.AniTecApplication.onCreate].
 */
@Singleton
class SessionCleanup @Inject constructor(
    private val sessionStore: SessionStore,
    private val cleaner: UserDataCleaner,
) {
    /**
     * Collects [SessionStore.state] on [scope] for the process lifetime.
     * Calls [UserDataCleaner.clear] on every transition to [SessionState.SignedOut].
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            sessionStore.state.collect { state ->
                if (state is SessionState.SignedOut) cleaner.clear()
            }
        }
    }
}
