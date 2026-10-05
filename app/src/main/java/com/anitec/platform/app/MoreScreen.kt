package com.anitec.platform.app

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.component.AniTecPanel
import com.anitec.platform.core.designsystem.component.DangerTextButton
import com.anitec.platform.core.i18n.LanguageSwitcher
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.iam.interfaces.ui.labelRes

private data class MoreItem(@StringRes val label: Int, val icon: ImageVector, val route: Any)

private fun moreItems(role: UserRole): List<MoreItem> = when (role) {
    UserRole.Rancher -> listOf(
        MoreItem(R.string.nav_herds, Icons.Filled.Place, HerdsRoute),
        MoreItem(R.string.nav_corrals, Icons.Filled.GridView, CorralsRoute),
        MoreItem(R.string.nav_finance, Icons.Filled.AccountBalanceWallet, PlaceholderRoute(R.string.nav_finance)),
        MoreItem(R.string.nav_analytics, Icons.AutoMirrored.Filled.ShowChart, PlaceholderRoute(R.string.nav_analytics)),
        MoreItem(R.string.nav_iot, Icons.Filled.Sensors, PlaceholderRoute(R.string.nav_iot)),
        MoreItem(R.string.nav_subscriptions, Icons.Filled.CreditCard, PlaceholderRoute(R.string.nav_subscriptions)),
        MoreItem(R.string.nav_terms, Icons.Filled.Description, TermsRoute),
    )
    UserRole.Veterinarian -> listOf(
        MoreItem(R.string.nav_clients, Icons.Filled.People, PlaceholderRoute(R.string.nav_clients)),
        MoreItem(R.string.nav_analytics, Icons.AutoMirrored.Filled.ShowChart, PlaceholderRoute(R.string.nav_analytics)),
        MoreItem(R.string.nav_iot, Icons.Filled.Sensors, PlaceholderRoute(R.string.nav_iot)),
        MoreItem(R.string.nav_subscriptions, Icons.Filled.CreditCard, PlaceholderRoute(R.string.nav_subscriptions)),
        MoreItem(R.string.nav_terms, Icons.Filled.Description, TermsRoute),
    )
}
/**
 * Displays the secondary/profile menu screen.
 */
@Composable
fun MoreScreen(
    session: UserSession,
    onOpen: (Any) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = moreItems(session.role)
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AniTecPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(session.fullName, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.shell_signed_in_as, session.username, stringResource(session.role.labelRes())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        /**
         * Renders a list of menu items inside a panel with divider separators.
         */

        AniTecPanel(modifier = Modifier.fillMaxWidth()) {
            Column {
                items.forEachIndexed { index, item ->
                    MoreRow(item.label, item.icon) { onOpen(item.route) }
                    if (index < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        AniTecPanel(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.language_label), style = MaterialTheme.typography.bodyLarge)
                }
                LanguageSwitcher()
            }
        }

        DangerTextButton(
            text = stringResource(R.string.nav_sign_out),
            onClick = onSignOut,
            icon = Icons.AutoMirrored.Filled.Logout,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MoreRow(@StringRes label: Int, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
