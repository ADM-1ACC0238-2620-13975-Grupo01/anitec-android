package com.anitec.platform.core.devtools

import android.app.Activity

/** Release builds only talk to the hosted backend, a public address, so they never ask for local network access. */
object LocalNetworkAccess {
    @Suppress("UNUSED_PARAMETER")
    fun requestIfNeeded(activity: Activity) = Unit
}
