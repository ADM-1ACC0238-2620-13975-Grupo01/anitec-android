package com.anitec.platform.core.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AppLanguage(val tag: String) {
    English("en"),
    Spanish("es-419"),
}

/**
 * In-app language switch. The statement requires English as the default language of every
 * product, so on first launch English is applied explicitly instead of following the device.
 */
@Singleton
class LanguageManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy { context.getSharedPreferences("anitec_language", Context.MODE_PRIVATE) }

    fun applyDefaultIfNeeded() {
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return
        prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(AppLanguage.English.tag))
    }

    fun current(): AppLanguage {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return if (tags.startsWith("es")) AppLanguage.Spanish else AppLanguage.English
    }

    fun set(language: AppLanguage) {
        prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    private companion object {
        const val KEY_INITIALIZED = "initialized"
    }
}
