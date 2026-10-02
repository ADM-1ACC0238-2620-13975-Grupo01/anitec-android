package com.anitec.platform.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// The web uses Inter with a system fallback. Inter is not bundled yet, so the system sans-serif is used.
private val AniTecFont = FontFamily.SansSerif

val AniTecTypography = Typography(
    displayMedium = TextStyle(fontFamily = AniTecFont, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = AniTecFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = AniTecFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = AniTecFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = AniTecFont, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp),
    titleSmall = TextStyle(fontFamily = AniTecFont, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = AniTecFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = AniTecFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = AniTecFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = AniTecFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = AniTecFont, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(
        fontFamily = AniTecFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.08.em,
    ),
)
