package com.anitec.platform

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.anitec.platform.core.i18n.LanguageManager
import com.anitec.platform.core.outbox.OutboxDao
import com.anitec.platform.core.outbox.OutboxScheduler
import com.anitec.platform.core.session.SessionCleanup
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
class AniTecApplication : Application(), Configuration.Provider {

    /** Resolves and persists the in-app language preference. */
    @Inject
    lateinit var languageManager: LanguageManager

    /** Observes session loss and clears local livestock / sanitary caches. */
    @Inject
    lateinit var sessionCleanup: SessionCleanup

    /** Builds the WorkManager workers with Hilt, so they can receive injected dependencies. */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** Queue of changes made offline that still have to reach the server. */
    @Inject
    lateinit var outboxDao: OutboxDao

    /** Schedules the background job that sends the queued changes. */
    @Inject
    lateinit var outboxScheduler: OutboxScheduler

    /**
     * Long-lived scope tied to the process, not to a screen.
     * Default dispatcher keeps locale and cleanup work off the main thread.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        languageManager.applyDefaultIfNeeded()
        sessionCleanup.start(applicationScope)
        // Changes made offline in a previous run are sent as soon as there is a connection.
        applicationScope.launch { if (outboxDao.count() > 0) outboxScheduler.schedule() }
    }
}
