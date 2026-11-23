package com.anitec.platform.core.designsystem

// Test coverage marker: colour contrast scenarios (TS-012, US-042).

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.anitec.platform.iam.interfaces.ui.HeroOverlayTop
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 AA contrast of the colours the app really uses, read from the theme and not copied, so changing a colour
 * that breaks a pair fails here. Text needs 4.5:1; controls and graphics need 3:1 (success criteria 1.4.3 and 1.4.11).
 * Disabled controls are exempt, as the guidelines allow.
 */
class ContrastTest {

    private val scheme = AniTecColorScheme

    private fun ratio(foreground: Color, background: Color): Double {
        val light = maxOf(foreground.luminance(), background.luminance()).toDouble()
        val dark = minOf(foreground.luminance(), background.luminance()).toDouble()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun assertText(name: String, foreground: Color, background: Color, minimum: Double = TEXT) {
        val actual = ratio(foreground, background)
        assertTrue("$name has $actual:1 and needs $minimum:1", actual >= minimum)
    }

    /** Every surface a screen is drawn on, including the translucent tints that the cards and chips use. */
    private val surfaces: Map<String, Color> = mapOf(
        "background" to scheme.background,
        "surface" to scheme.surface,
        "surfaceVariant" to scheme.surfaceVariant,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "white" to Color.White,
        "primary 10% over surface" to scheme.primary.copy(alpha = 0.10f).compositeOver(scheme.surface),
        "primary 12% over surface" to scheme.primary.copy(alpha = 0.12f).compositeOver(scheme.surface),
        "primaryContainer 50% over surface" to scheme.primaryContainer.copy(alpha = 0.5f).compositeOver(scheme.surface),
        "primaryContainer 60% over surface" to scheme.primaryContainer.copy(alpha = 0.6f).compositeOver(scheme.surface),
        "secondaryContainer 40% over surface" to scheme.secondaryContainer.copy(alpha = 0.4f).compositeOver(scheme.surface),
        "secondaryContainer 50% over surface" to scheme.secondaryContainer.copy(alpha = 0.5f).compositeOver(scheme.surface),
    )

    @Test
    fun `every pair of container and content colour of the theme is readable`() {
        assertText("onPrimary on primary", scheme.onPrimary, scheme.primary)
        assertText("onSecondary on secondary", scheme.onSecondary, scheme.secondary)
        assertText("onTertiary on tertiary", scheme.onTertiary, scheme.tertiary)
        assertText("onError on error", scheme.onError, scheme.error)
        assertText("onPrimaryContainer on primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer)
        assertText("onSecondaryContainer on secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer)
        assertText("onTertiaryContainer on tertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer)
        assertText("onErrorContainer on errorContainer", scheme.onErrorContainer, scheme.errorContainer)
        assertText("onBackground on background", scheme.onBackground, scheme.background)
        assertText("onSurface on surface", scheme.onSurface, scheme.surface)
        assertText("onSurfaceVariant on surfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant)
    }

    @Test
    fun `the main text and the secondary text are readable on every surface`() {
        surfaces.forEach { (name, surface) ->
            assertText("onSurface on $name", scheme.onSurface, surface)
            assertText("onSurfaceVariant on $name", scheme.onSurfaceVariant, surface)
        }
        assertText("onSurfaceVariant on surfaceContainerHighest", scheme.onSurfaceVariant, scheme.surfaceContainerHighest)
    }

    @Test
    fun `green text, which is used for links, buttons and kickers, is readable on the surfaces it appears on`() {
        listOf("background", "surface", "white", "primary 10% over surface", "primary 12% over surface").forEach { name ->
            assertText("primary on $name", scheme.primary, surfaces.getValue(name))
        }
    }

    @Test
    fun `error and brown text are readable on the background and the panels`() {
        listOf("background", "surface", "white").forEach { name ->
            assertText("error on $name", scheme.error, surfaces.getValue(name))
            assertText("secondary on $name", scheme.secondary, surfaces.getValue(name))
            assertText("onErrorContainer on $name", scheme.onErrorContainer, surfaces.getValue(name))
        }
    }

    @Test
    fun `the main button keeps white text readable along its whole gradient`() {
        assertText("white on the start of the button gradient", Color.White, AniTecGreenDark)
        assertText("white on the end of the button gradient", Color.White, AniTecGreenButtonEnd)
    }

    @Test
    fun `the white initials of the avatar are readable on both ends of its gradient`() {
        assertText("white on the green end", Color.White, AniTecGreenDark)
        assertText("white on the brown end", Color.White, AniTecBrown)
    }

    @Test
    fun `every status tag is readable`() {
        val colors = StatusColors()
        Severity.entries.forEach { severity ->
            val palette = colors.of(severity)
            assertText("$severity tag", palette.content, palette.container)
        }
    }

    @Test
    fun `white text over the hero photo is readable even where the photo is lightest`() {
        val overPureWhitePhoto = HeroOverlayTop.compositeOver(Color.White)
        assertText("white on the top of the hero veil over a white photo", Color.White, overPureWhitePhoto)
    }

    @Test
    fun `controls and graphics stand out from the background`() {
        assertText("field border", scheme.outline, scheme.background, GRAPHIC)
        assertText("field border on the panel", scheme.outline, scheme.surface, GRAPHIC)
        assertText("secondary button border", scheme.primary, scheme.background, GRAPHIC)
        AniTecChartPalette.forEachIndexed { index, color ->
            assertText("chart colour $index on the panel", color, scheme.surface, GRAPHIC)
        }
    }

    @Test
    fun `the chart palette does not repeat a colour`() {
        assertTrue(AniTecChartPalette.distinct().size == AniTecChartPalette.size)
    }

    private companion object {
        const val TEXT = 4.5
        const val GRAPHIC = 3.0
    }
}
