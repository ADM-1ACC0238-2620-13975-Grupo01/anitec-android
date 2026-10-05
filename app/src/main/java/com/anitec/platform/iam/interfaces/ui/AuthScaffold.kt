package com.anitec.platform.iam.interfaces.ui

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.component.AniTecPanel
import com.anitec.platform.core.designsystem.component.SectionChip
import com.anitec.platform.core.i18n.LanguageSwitcher
import com.anitec.platform.core.session.UserRole

@StringRes
fun UserRole.labelRes(): Int = when (this) {
    UserRole.Rancher -> R.string.role_rancher
    UserRole.Veterinarian -> R.string.role_veterinarian
}

/** Shared layout of the sign-in and sign-up screens: farm hero on top, form panel below. */
@Composable
fun AuthScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AuthHero()
            AniTecPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.anitec_logo),
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    SectionChip(stringResource(R.string.auth_hero_chip))
                    Text(title, style = MaterialTheme.typography.headlineMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    content()
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(72.dp))
        }
        LanguageSwitcher(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AuthHero() {
    Box(modifier = Modifier.fillMaxWidth().height(230.dp)) {
        Image(
            painter = painterResource(R.drawable.bg_auth_hero),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x99163A12), Color(0xE62A4A22)))),
        )
        LightStatusBarIcons()
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).clip(MaterialTheme.shapes.medium),
            color = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    stringResource(R.string.auth_hero_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.auth_hero_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }
    }
}

/** The hero is a dark photo under the status bar, so the clock and icons must be light while it is on screen. */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

/** Small green pill used for the "Rancher account / Veterinarian account" hints. */
@Composable
fun RoleBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
