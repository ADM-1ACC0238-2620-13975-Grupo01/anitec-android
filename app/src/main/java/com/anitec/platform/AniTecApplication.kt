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

@HiltAndroidApp
class AniTecApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var languageManager: LanguageManager

    @Inject
    lateinit var sessionCleanup: SessionCleanup

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var outboxDao: OutboxDao

    @Inject
    lateinit var outboxScheduler: OutboxScheduler

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
