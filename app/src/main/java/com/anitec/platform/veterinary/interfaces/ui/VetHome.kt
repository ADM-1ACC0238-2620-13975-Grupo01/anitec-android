package com.anitec.platform.veterinary.interfaces.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.anitec.platform.core.designsystem.Severity
import com.anitec.platform.core.designsystem.component.AniTecPanel
import com.anitec.platform.core.designsystem.component.DropdownField
import com.anitec.platform.core.designsystem.component.EmptyState
import com.anitec.platform.core.designsystem.component.MessageEffect
import com.anitec.platform.core.designsystem.component.MetricCard
import com.anitec.platform.core.designsystem.component.PrimaryButton
import com.anitec.platform.core.designsystem.component.SecondaryButton
import com.anitec.platform.core.designsystem.component.SectionChip
import com.anitec.platform.core.designsystem.component.StatusTag
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.iam.interfaces.ui.labelRes
import com.anitec.platform.sanitary.interfaces.ui.HealthRecordCard
import com.anitec.platform.veterinary.interfaces.viewmodel.VetHomeViewModel

/** Dashboard of the veterinarian: totals, a client selector, a card per client and the latest records. */
@Composable
fun VetHome(
    session: UserSession,
    onAddClient: () -> Unit,
    onReviewRecords: () -> Unit,
    onViewPatients: (Int) -> Unit,
    onOpenClients: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VetHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AniTecPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionChip(stringResource(R.string.app_name))
                        Text(stringResource(R.string.home_greeting, session.fullName), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.vet_home_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusTag(stringResource(session.role.labelRes()), Severity.Success)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimaryButton(
                        text = stringResource(R.string.vet_add_client),
                        onClick = onAddClient,
                        icon = Icons.Filled.PersonAdd,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.vet_review_records),
                        onClick = onReviewRecords,
                        icon = Icons.Filled.Favorite,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (state.clients.isNotEmpty()) {
                    val options: List<Int?> = listOf<Int?>(null) + state.clients.map { it.client.rancherId }
                    DropdownField(
                        label = stringResource(R.string.vet_select_client),
                        options = options,
                        selected = state.selectedClientId,
                        onSelected = viewModel::onClientSelected,
                        optionLabel = { id -> if (id == null) stringResource(R.string.vet_all_clients) else state.clients.first { it.client.rancherId == id }.client.rancherName },
                        emptyLabel = stringResource(R.string.vet_all_clients),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(stringResource(R.string.vet_metric_clients), state.clientCount.toString(), Icons.Filled.Group, Modifier.weight(1f))
                    MetricCard(stringResource(R.string.vet_metric_patients), state.activePatients.toString(), Icons.Filled.Warning, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(stringResource(R.string.vet_metric_records), state.recordCount.toString(), Icons.Filled.Favorite, Modifier.weight(1f))
                    MetricCard(stringResource(R.string.vet_metric_followups), state.followUpCount.toString(), Icons.Filled.EventRepeat, Modifier.weight(1f))
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.vet_overview_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenClients) { Text(stringResource(R.string.home_view_all), color = MaterialTheme.colorScheme.primary) }
                }
                if (state.clients.isEmpty()) {
                    EmptyState(stringResource(R.string.vet_clients_empty))
                } else {
                    state.clients.forEach { summary ->
                        ClientCard(summary = summary, onViewPatients = { onViewPatients(summary.client.rancherId) })
                    }
                }

                Text(stringResource(R.string.home_recent_title), style = MaterialTheme.typography.titleMedium)
                if (state.recent.isEmpty()) {
                    EmptyState(stringResource(R.string.home_recent_empty))
                } else {
                    state.recent.forEach { HealthRecordCard(it) }
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
