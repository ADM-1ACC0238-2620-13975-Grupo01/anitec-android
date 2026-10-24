package com.anitec.platform.core.devtools

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Debug builds reach a backend on the developer's computer (10.0.2.2 from the emulator, or a LAN address).
 * Android 17 treats those addresses as the local network: an app targeting it is blocked from them, with
 * connections that just hang, until the user allows it. This asks for that permission when the app opens, so
 * nobody has to grant it with adb after each install. Release builds use the hosted backend and do not need it.
 */
object LocalNetworkAccess {
    private const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val FIRST_SDK_WITH_PERMISSION = 37
    private const val REQUEST_CODE = 4301

    /** The permission only exists from Android 17; before that, or once granted, there is nothing to ask. */
    internal fun needsRequest(sdkInt: Int, granted: Boolean): Boolean = sdkInt >= FIRST_SDK_WITH_PERMISSION && !granted

    fun requestIfNeeded(activity: Activity) {
        val granted = ContextCompat.checkSelfPermission(activity, PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (needsRequest(Build.VERSION.SDK_INT, granted)) {
            ActivityCompat.requestPermissions(activity, arrayOf(PERMISSION), REQUEST_CODE)
        }
    }
}
