package com.anitec.platform

import android.app.Application
import com.anitec.platform.core.i18n.LanguageManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AniTecApplication : Application() {

    @Inject
    lateinit var languageManager: LanguageManager

    override fun onCreate() {
        super.onCreate()
        languageManager.applyDefaultIfNeeded()
    }
}
