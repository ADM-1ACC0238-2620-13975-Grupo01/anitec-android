package com.anitec.platform

import android.app.Application
import com.anitec.platform.core.i18n.LanguageManager
import com.anitec.platform.core.session.SessionCleanup
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * Process-wide entry point for AniTec.
 *
 * Annotated with [HiltAndroidApp] so Hilt can build the application-level component and
 * inject collaborators before [onCreate] runs. Two side effects are started here (and nowhere
 * else) because they must outlive any single Activity:
 *
 * 1. [LanguageManager] — applies the default UI locale on a fresh install.
 * 2. [SessionCleanup] — wipes the Room cache whenever the session ends (sign-out or 401).
 *
 * The [applicationScope] uses a [SupervisorJob] so a failure in one cleanup coroutine does not
 * cancel the others for the rest of the process lifetime.
 */
@HiltAndroidApp
class AniTecApplication : Application() {

    /** Resolves and persists the in-app language preference. */
    @Inject
    lateinit var languageManager: LanguageManager

    /** Observes session loss and clears local livestock / sanitary caches. */
    @Inject
    lateinit var sessionCleanup: SessionCleanup

    /**
     * Long-lived scope tied to the process, not to a screen.
     * Default dispatcher keeps locale and cleanup work off the main thread.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        languageManager.applyDefaultIfNeeded()
        sessionCleanup.start(applicationScope)
    }
}
