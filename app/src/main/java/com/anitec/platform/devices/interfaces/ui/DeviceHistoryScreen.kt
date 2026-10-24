package com.anitec.platform.devices.interfaces.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.anitec.platform.core.designsystem.component.AniTecPanel
import com.anitec.platform.core.designsystem.component.EmptyState
import com.anitec.platform.core.designsystem.component.LoadingState
import com.anitec.platform.core.designsystem.component.MessageEffect
import com.anitec.platform.core.designsystem.component.mergedSemantics
import com.anitec.platform.core.designsystem.component.sectionHeading
import com.anitec.platform.devices.domain.DeviceReading
import com.anitec.platform.devices.interfaces.viewmodel.DeviceHistoryViewModel

/** API date-time (`2026-06-11T10:34:52.518313`) as `2026-06-11 10:34`; text that is not a date-time is returned unchanged. */
fun formatRecordedAt(recordedAt: String): String =
    if (recordedAt.length >= 16 && recordedAt[10] == 'T') recordedAt.substring(0, 16).replace('T', ' ') else recordedAt

@Composable
private fun metricLabel(type: String): String = metricTypeRes(type)?.let { stringResource(it) } ?: type

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceHistoryViewModel = hiltViewModel(),
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
                title = { Text(state.device?.name ?: stringResource(R.string.iot_history_title)) },
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
            if (state.loading) {
                LoadingState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { LatestReadingCard(state.latest) }
                    item {
                        Text(
                            stringResource(R.string.iot_history_title),
                            modifier = Modifier.padding(top = 6.dp).sectionHeading(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (state.readings.isEmpty()) {
                        item { EmptyState(stringResource(R.string.iot_history_empty)) }
                    } else {
                        items(state.readings) { reading -> ReadingRow(reading) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LatestReadingCard(reading: DeviceReading?) {
    AniTecPanel(modifier = Modifier.fillMaxWidth().mergedSemantics()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.iot_latest_reading), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            if (reading == null) {
                Text(stringResource(R.string.iot_no_readings), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(formatReadingValue(reading), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.tertiary)
                Text(
                    "${metricLabel(reading.type)} · ${formatRecordedAt(reading.recordedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReadingRow(reading: DeviceReading) {
    AniTecPanel(modifier = Modifier.fillMaxWidth().mergedSemantics()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(metricLabel(reading.type), style = MaterialTheme.typography.bodyMedium)
                Text(formatRecordedAt(reading.recordedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatReadingValue(reading), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}
