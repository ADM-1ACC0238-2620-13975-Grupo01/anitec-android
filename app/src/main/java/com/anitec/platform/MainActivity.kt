package com.anitec.platform

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.anitec.platform.app.AniTecApp
import com.anitec.platform.core.designsystem.AniTecTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity host for the Compose UI.
 *
 * Extends [AppCompatActivity] (not ComponentActivity) so the in-app language switch
 * also works below Android 13 via AppCompat's locale APIs. Marked with [AndroidEntryPoint]
 * so Hilt can inject into this Activity and into Compose destinations under it.
 *
 * [onCreate] enables edge-to-edge drawing and mounts [AniTecApp] inside [AniTecTheme].
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AniTecTheme {
                AniTecApp()
            }
        }
    }
}
