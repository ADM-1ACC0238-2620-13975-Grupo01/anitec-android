package com.anitec.platform.app

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLayoutTest {

    @Test
    fun `phones in portrait keep the bottom bar`() {
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(WindowWidthSizeClass.Compact))
    }

    @Test
    fun `wider windows move the destinations to a rail`() {
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(WindowWidthSizeClass.Medium))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(WindowWidthSizeClass.Expanded))
    }

    @Test
    fun `content is capped above the phone widths`() {
        // The widest compact window is 599 dp: capping at or above it leaves phones untouched.
        assertTrue(ContentMaxWidth >= 599.dp)
    }
}
