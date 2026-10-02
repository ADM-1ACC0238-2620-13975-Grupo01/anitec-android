package com.anitec.platform.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic severity of a status tag, equivalent to PrimeVue's Tag severities. */
enum class Severity { Success, Warn, Info, Danger, Neutral }

data class StatusPalette(val container: Color, val content: Color)

/** Material 3 has no success/warn/info roles, so the web tag colors live here. */
data class StatusColors(
    val success: StatusPalette = StatusPalette(Color(0xFFE4F2DE), Color(0xFF386B2C)),
    val warn: StatusPalette = StatusPalette(Color(0xFFFBEBD0), Color(0xFF92600B)),
    val info: StatusPalette = StatusPalette(Color(0xFFE1EEF7), Color(0xFF1F5C86)),
    val danger: StatusPalette = StatusPalette(Color(0xFFF8E1DE), Color(0xFFA23B2E)),
    val neutral: StatusPalette = StatusPalette(AniTecSand, AniTecBrownDark),
) {
    fun of(severity: Severity): StatusPalette = when (severity) {
        Severity.Success -> success
        Severity.Warn -> warn
        Severity.Info -> info
        Severity.Danger -> danger
        Severity.Neutral -> neutral
    }
}

val LocalStatusColors = staticCompositionLocalOf { StatusColors() }

val MaterialTheme.statusColors: StatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current
