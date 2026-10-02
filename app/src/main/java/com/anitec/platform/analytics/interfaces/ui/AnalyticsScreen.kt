package com.anitec.platform.analytics.interfaces.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anitec.platform.R
import com.anitec.platform.analytics.domain.AnalyticsSnapshot
import com.anitec.platform.analytics.domain.RecordKinds
import com.anitec.platform.analytics.interfaces.viewmodel.AnalyticsViewModel
import com.anitec.platform.core.designsystem.AniTecChartPalette
import com.anitec.platform.core.designsystem.component.AniTecPanel
import com.anitec.platform.core.designsystem.component.BarChart
import com.anitec.platform.core.designsystem.component.ChartPoint
import com.anitec.platform.core.designsystem.component.DonutChart
import com.anitec.platform.core.designsystem.component.LineChart
import com.anitec.platform.core.designsystem.component.MessageEffect
import com.anitec.platform.core.designsystem.component.MetricCard
import com.anitec.platform.core.designsystem.component.PanelHeader
import com.anitec.platform.core.designsystem.component.SectionChip
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.financial.interfaces.ui.formatSoles
import com.anitec.platform.sanitary.interfaces.ui.healthTypeLabel

@Composable
private fun recordKindLabel(kind: String): String =
    if (kind == RecordKinds.OTHER) stringResource(R.string.analytics_kind_other) else healthTypeLabel(kind)

@Composable
private fun ChartPanel(chip: String, title: String, content: @Composable () -> Unit) {
    AniTecPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionChip(chip)
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun StatusDonut(snapshot: AnalyticsSnapshot) {
    val points = listOf(
        ChartPoint(stringResource(R.string.status_healthy), snapshot.healthyCount, AniTecChartPalette[0]),
        ChartPoint(stringResource(R.string.status_observation), snapshot.observationCount, AniTecChartPalette[3]),
        ChartPoint(stringResource(R.string.status_in_treatment), snapshot.treatmentCount, AniTecChartPalette[1]),
    )
    DonutChart(points, centerText = snapshot.animalCount.toString())
}

@Composable
private fun RecordsByKindBars(snapshot: AnalyticsSnapshot) {
    BarChart(snapshot.recordsByKind.map { (kind, count) -> ChartPoint(recordKindLabel(kind), count) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_analytics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PanelHeader(
                    title = stringResource(R.string.analytics_title),
                    subtitle = stringResource(R.string.analytics_subtitle),
                )
                if (state.role == UserRole.Veterinarian) {
                    MetricCard(
                        label = stringResource(R.string.analytics_clients),
                        value = state.clientCount.toString(),
                        icon = Icons.Filled.People,
                        caption = stringResource(R.string.analytics_farms_followed, snapshot.herdCount),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetricCard(
                        label = stringResource(R.string.analytics_patients),
                        value = snapshot.animalCount.toString(),
                        icon = Icons.Filled.Pets,
                        caption = stringResource(R.string.analytics_need_attention, snapshot.attentionCount),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetricCard(
                        label = stringResource(R.string.analytics_records),
                        value = snapshot.recordCount.toString(),
                        icon = Icons.Filled.Description,
                        caption = stringResource(R.string.analytics_pending_follow_ups, snapshot.followUpCount),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    MetricCard(
                        label = stringResource(R.string.analytics_animals),
                        value = snapshot.animalCount.toString(),
                        icon = Icons.Filled.Pets,
                        caption = stringResource(R.string.analytics_active_farms, snapshot.herdCount),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetricCard(
                        label = stringResource(R.string.analytics_alerts),
                        value = snapshot.attentionCount.toString(),
                        icon = Icons.Filled.Warning,
                        caption = stringResource(R.string.analytics_healthy_count, snapshot.healthyCount),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetricCard(
                        label = stringResource(R.string.analytics_records),
                        value = snapshot.recordCount.toString(),
                        icon = Icons.Filled.Favorite,
                        caption = stringResource(R.string.analytics_balance, formatSoles(state.balance)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.role == UserRole.Rancher) {
                    ChartPanel(stringResource(R.string.analytics_chip_production), stringResource(R.string.analytics_herd_status)) {
                        StatusDonut(snapshot)
                    }
                    ChartPanel(stringResource(R.string.analytics_chip_health), stringResource(R.string.analytics_records_by_type)) {
                        RecordsByKindBars(snapshot)
                    }
                } else {
                    ChartPanel(stringResource(R.string.analytics_chip_health), stringResource(R.string.analytics_records_by_type)) {
                        RecordsByKindBars(snapshot)
                    }
                    ChartPanel(stringResource(R.string.analytics_chip_clients), stringResource(R.string.analytics_visits_by_herd)) {
                        if (snapshot.recordsByHerd.isEmpty()) {
                            Text(stringResource(R.string.analytics_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LineChart(snapshot.recordsByHerd.map { ChartPoint(it.herdName, it.recordCount) })
                        }
                    }
                }
            }
        }
    }
}
