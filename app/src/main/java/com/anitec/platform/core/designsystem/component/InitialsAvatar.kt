package com.anitec.platform.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anitec.platform.core.designsystem.AniTecBrown
import com.anitec.platform.core.designsystem.AniTecGreenDark

/** Round avatar with the person's initials on the web's green-to-brown gradient. Decorative: the name is always shown next to it. */
@Composable
fun InitialsAvatar(initials: String, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(AniTecGreenDark, AniTecBrown)))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = MaterialTheme.typography.titleSmall, color = Color.White)
    }
}
