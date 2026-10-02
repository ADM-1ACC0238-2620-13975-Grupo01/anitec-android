package com.anitec.platform

import android.app.Application
import com.anitec.platform.core.i18n.LanguageManager
import com.anitec.platform.core.session.SessionCleanup
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class AniTecApplication : Application() {

    @Inject
    lateinit var languageManager: LanguageManager

    @Inject
    lateinit var sessionCleanup: SessionCleanup

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        languageManager.applyDefaultIfNeeded()
        sessionCleanup.start(applicationScope)
    }
}
