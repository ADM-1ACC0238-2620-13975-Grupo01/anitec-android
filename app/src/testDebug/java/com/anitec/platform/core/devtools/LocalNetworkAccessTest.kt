package com.anitec.platform.core.devtools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAccessTest {

    @Test
    fun `android 17 without the permission asks for it`() {
        assertTrue(LocalNetworkAccess.needsRequest(sdkInt = 37, granted = false))
        assertTrue(LocalNetworkAccess.needsRequest(sdkInt = 38, granted = false))
    }

    @Test
    fun `nothing is asked once it is granted`() {
        assertFalse(LocalNetworkAccess.needsRequest(sdkInt = 37, granted = true))
    }

    @Test
    fun `before android 17 the permission does not exist so nothing is asked`() {
        assertFalse(LocalNetworkAccess.needsRequest(sdkInt = 36, granted = false))
        assertFalse(LocalNetworkAccess.needsRequest(sdkInt = 26, granted = false))
    }
}
