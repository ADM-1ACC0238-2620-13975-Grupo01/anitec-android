package com.anitec.platform

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.anitec.platform.app.AniTecApp
import com.anitec.platform.core.devtools.LocalNetworkAccess
import com.anitec.platform.core.designsystem.AniTecTheme
import com.anitec.platform.subscriptions.application.CheckoutReturns
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single Activity host for the Compose UI.
 *
 * Extends [AppCompatActivity] (not ComponentActivity) so the in-app language switch
 * also works below Android 13 via AppCompat's locale APIs. Marked with [AndroidEntryPoint]
 * so Hilt can inject into this Activity and into Compose destinations under it.
 *
 * [onCreate] enables edge-to-edge drawing and mounts [AniTecApp] inside [AniTecTheme].
 *
 * The activity is single-top: when the Stripe payment page opens `anitec://subscriptions/...` to bring
 * the user back, that link reaches [onNewIntent] of the running instance (or [onCreate] if the app
 * had to start) and is handed to [CheckoutReturns].
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var checkoutReturns: CheckoutReturns

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug builds ask to reach the backend on this computer; release builds do nothing here.
        LocalNetworkAccess.requestIfNeeded(this)
        // After a rotation the same intent is delivered again; the link was already handled the first time.
        if (savedInstanceState == null) checkoutReturns.onLink(intent?.dataString)
        setContent {
            AniTecTheme {
                AniTecApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkoutReturns.onLink(intent.dataString)
    }
}
