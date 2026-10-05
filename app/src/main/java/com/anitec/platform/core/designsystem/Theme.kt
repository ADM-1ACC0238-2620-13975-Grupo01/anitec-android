package com.anitec.platform.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The web app has no dark mode, so the app ships a single light scheme.
private val AniTecColorScheme = lightColorScheme(
    primary = AniTecGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4F2DE),
    onPrimaryContainer = Color(0xFF386B2C),
    secondary = AniTecBrown,
    onSecondary = Color.White,
    secondaryContainer = AniTecSand,
    onSecondaryContainer = AniTecBrownDark,
    tertiary = AniTecBrownDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBEBD0),
    onTertiaryContainer = Color(0xFF92600B),
    background = AniTecCream,
    onBackground = AniTecInk,
    surface = AniTecPanel,
    onSurface = AniTecInk,
    surfaceVariant = Color(0xFFFBF6EC),
    onSurfaceVariant = AniTecMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFDF8),
    surfaceContainer = AniTecPanel,
    surfaceContainerHigh = Color(0xFFF7EFE2),
    surfaceContainerHighest = Color(0xFFF0E6D2),
    outline = AniTecMuted,
    outlineVariant = AniTecLine,
    error = AniTecError,
    onError = Color.White,
    errorContainer = Color(0xFFF8E1DE),
    onErrorContainer = Color(0xFFA23B2E),
    scrim = AniTecInk,
)

private val AniTecShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)
/**
 * Applies the application theme wrapping the MaterialTheme with custom color scheme,
 * typography, shapes, and localized status colors.
 */
@Composable
fun AniTecTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides StatusColors()) {
        MaterialTheme(
            colorScheme = AniTecColorScheme,
            typography = AniTecTypography,
            shapes = AniTecShapes,
            content = content,
        )
    }
}
