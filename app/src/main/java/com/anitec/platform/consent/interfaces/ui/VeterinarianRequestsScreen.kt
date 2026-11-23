package com.anitec.platform.consent.interfaces.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anitec.platform.R
import com.anitec.platform.consent.domain.LinkStatus
import com.anitec.platform.consent.domain.VeterinarianLink
import com.anitec.platform.consent.interfaces.viewmodel.VeterinarianRequestsViewModel
import com.anitec.platform.core.designsystem.Severity
import com.anitec.platform.core.designsystem.component.AniTecPanel
import com.anitec.platform.core.designsystem.component.ConfirmDialog
import com.anitec.platform.core.designsystem.component.DangerTextButton
import com.anitec.platform.core.designsystem.component.EmptyState
import com.anitec.platform.core.designsystem.component.InitialsAvatar
import com.anitec.platform.core.designsystem.component.LoadingState
import com.anitec.platform.core.designsystem.component.MessageEffect
import com.anitec.platform.core.designsystem.component.PanelHeader
import com.anitec.platform.core.designsystem.component.PrimaryButton
import com.anitec.platform.core.designsystem.component.SecondaryButton
import com.anitec.platform.core.designsystem.component.StatusTag
import com.anitec.platform.core.designsystem.component.sectionHeading
import com.anitec.platform.veterinary.domain.initialsOf

/** Label and colour of the state of a request. */
@Composable
fun LinkStatus.tagText(): String = stringResource(
    when (this) {
        LinkStatus.Pending -> R.string.consent_status_pending
        LinkStatus.Accepted -> R.string.consent_status_accepted
        LinkStatus.Rejected -> R.string.consent_status_rejected
        LinkStatus.Revoked -> R.string.consent_status_revoked
        LinkStatus.Unknown -> R.string.consent_status_closed
    },
)

fun LinkStatus.severity(): Severity = when (this) {
    LinkStatus.Pending -> Severity.Warn
    LinkStatus.Accepted -> Severity.Success
    LinkStatus.Rejected, LinkStatus.Revoked, LinkStatus.Unknown -> Severity.Neutral
}

/**
 * The rancher decides who may see and record data about their animals: requests to answer, veterinarians with
 * access (which can be revoked) and the closed ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeterinarianRequestsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VeterinarianRequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_veterinarians)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing && !state.loading, onRefresh = viewModel::load, modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                state.links.isEmpty() -> EmptyState(stringResource(R.string.consent_empty))
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        PanelHeader(
                            title = stringResource(R.string.consent_title),
                            subtitle = stringResource(R.string.consent_subtitle),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    section(R.string.consent_section_pending, state.pending) { link ->
                        LinkCard(link) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton(
                                    text = stringResource(R.string.consent_accept),
                                    onClick = { viewModel.accept(link) },
                                    icon = Icons.Filled.Check,
                                    loading = state.actingId == link.id,
                                    enabled = state.actingId == null,
                                    modifier = Modifier.weight(1f),
                                )
                                SecondaryButton(
                                    text = stringResource(R.string.consent_reject),
                                    onClick = { viewModel.reject(link) },
                                    icon = Icons.Filled.Close,
                                    enabled = state.actingId == null,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    section(R.string.consent_section_active, state.withAccess) { link ->
                        LinkCard(link) {
                            DangerTextButton(
                                text = stringResource(R.string.consent_revoke),
                                onClick = { viewModel.requestRevoke(link) },
                                icon = Icons.Filled.PersonRemove,
                            )
                        }
                    }
                    section(R.string.consent_section_closed, state.closed) { link -> LinkCard(link) }
                }
            }
        }
    }

    state.pendingRevoke?.let { link ->
        ConfirmDialog(
            title = stringResource(R.string.consent_revoke),
            message = stringResource(R.string.consent_revoke_confirm, link.displayName),
            confirmLabel = stringResource(R.string.consent_revoke),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = viewModel::confirmRevoke,
            onDismiss = viewModel::dismissRevoke,
        )
    }
}

/** A titled group of cards; nothing is added when the group is empty. */
private fun androidx.compose.foundation.lazy.LazyListScope.section(
    titleRes: Int,
    links: List<VeterinarianLink>,
    card: @Composable (VeterinarianLink) -> Unit,
) {
    if (links.isEmpty()) return
    item(key = "title-$titleRes") {
        Text(
            stringResource(titleRes),
            modifier = Modifier.sectionHeading().padding(top = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    items(links, key = { it.id }) { card(it) }
}

/** Card of one veterinarian: avatar, name, username, date of the request and state, with optional actions. */
@Composable
private fun LinkCard(link: VeterinarianLink, actions: (@Composable () -> Unit)? = null) {
    AniTecPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(initialsOf(link.displayName))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(link.displayName, style = MaterialTheme.typography.titleMedium)
                    if (link.veterinarianUsername.isNotBlank() && link.veterinarianName.isNotBlank()) {
                        Text(
                            "@${link.veterinarianUsername}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(R.string.consent_requested_on, link.requestedDay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusTag(link.status.tagText(), link.status.severity())
            }
            actions?.invoke()
        }
    }
}
