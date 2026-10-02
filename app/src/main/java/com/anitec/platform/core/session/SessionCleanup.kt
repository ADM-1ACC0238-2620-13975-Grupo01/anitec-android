package com.anitec.platform.core.session

import com.anitec.platform.core.common.UserDataCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wipes the local cache whenever the session ends (sign-out or an expired token), so one user's
 * animals, farms and health records are never shown to the next person who signs in on the device.
 */
@Singleton
class SessionCleanup @Inject constructor(
    private val sessionStore: SessionStore,
    private val cleaner: UserDataCleaner,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            sessionStore.state.collect { state ->
                if (state is SessionState.SignedOut) cleaner.clear()
            }
        }
    }
}
