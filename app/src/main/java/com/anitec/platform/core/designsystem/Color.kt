package com.anitec.platform.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Brand tokens ported from anitec-frontend/src/style.css, with the greens and the muted gray darkened so that text and
 * controls reach the WCAG 2.1 AA contrast (4.5:1 for text, 3:1 for controls and graphics). The web values were
 * #4F8F45 for the green, which gave 3.9:1 with white text and 3.5:1 as text on the cream background, and #657069 for
 * the muted gray, which gave 4.0:1 on the sand placeholders. [ContrastTest] keeps every pair above its minimum.
 */

/** The light brand green. Decorative only: it is too light for text, or for a background with white text. */
val AniTecGreen = Color(0xFF79B267)

/** The main green: buttons, links, selected states and text. 5.7:1 with white and 5.0:1 on the cream background. */
val AniTecGreenDark = Color(0xFF3B7334)

/** The lighter end of the main button's gradient. White text on it still has 4.8:1. */
val AniTecGreenButtonEnd = Color(0xFF45803D)
val AniTecBrown = Color(0xFF925930)
val AniTecBrownDark = Color(0xFF6F3F22)
val AniTecCream = Color(0xFFF5F0E6)
val AniTecPanel = Color(0xFFFFFAF2)
val AniTecSand = Color(0xFFD1BFA5)
val AniTecInk = Color(0xFF1E2520)
val AniTecMuted = Color(0xFF58625C)
val AniTecLine = Color(0xFFE7DFD1)
val AniTecError = Color(0xFFB42318)

/**
 * Chart series palette. The web used light tones (the sand one had 1.7:1 against the panel); these keep the order and
 * the meaning of the first ones (healthy green, treatment brown, observation amber) and every colour has at least
 * 3:1 against the panel, so a slice or a bar can be told from the background. Charts also show their values and a
 * legend, so colour is never the only way to read them.
 */
val AniTecChartPalette = listOf(
    Color(0xFF3B7334),
    Color(0xFF925930),
    Color(0xFF2F6F8F),
    Color(0xFFA66A00),
    Color(0xFF7A4B8A),
    Color(0xFF1E7A74),
    Color(0xFF7A6A1F),
    Color(0xFFA23B2E),
)
