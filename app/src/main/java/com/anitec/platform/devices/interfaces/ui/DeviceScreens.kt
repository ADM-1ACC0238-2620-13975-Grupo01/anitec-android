package com.anitec.platform.devices.interfaces.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.Severity
import com.anitec.platform.core.designsystem.component.AniTecTextField
import com.anitec.platform.core.designsystem.component.ConfirmDialog
import com.anitec.platform.core.designsystem.component.DangerTextButton
import com.anitec.platform.core.designsystem.component.DropdownField
import com.anitec.platform.core.designsystem.component.EmptyState
import com.anitec.platform.core.designsystem.component.FormHero
import com.anitec.platform.core.designsystem.component.FormScreenScaffold
import com.anitec.platform.core.designsystem.component.LoadingState
import com.anitec.platform.core.designsystem.component.MessageEffect
import com.anitec.platform.core.designsystem.component.PanelHeader
import com.anitec.platform.core.designsystem.component.PrimaryButton
import com.anitec.platform.core.designsystem.component.RecordCard
import com.anitec.platform.core.designsystem.component.SecondaryButton
import com.anitec.platform.core.designsystem.component.StatusTag
import com.anitec.platform.devices.domain.DeviceReading
import com.anitec.platform.devices.domain.DeviceStatuses
import com.anitec.platform.devices.domain.DeviceTypes
import com.anitec.platform.devices.interfaces.viewmodel.DeviceFormViewModel
import com.anitec.platform.devices.interfaces.viewmodel.DeviceListViewModel
import com.anitec.platform.devices.interfaces.viewmodel.DeviceTarget

@StringRes
fun deviceTypeRes(type: String): Int? = when (DeviceTypes.normalize(type)) {
    DeviceTypes.SCALE -> R.string.iot_type_scale
    DeviceTypes.SMART_COLLAR -> R.string.iot_type_collar
    DeviceTypes.RFID_TAG -> R.string.iot_type_ear_tag
    DeviceTypes.THERMAL_CAMERA -> R.string.iot_type_thermal_camera
    DeviceTypes.WEATHER_STATION -> R.string.iot_type_weather_station
    DeviceTypes.ENVIRONMENTAL_SENSOR -> R.string.iot_type_environmental
    else -> null
}

@StringRes
fun deviceStatusRes(status: String): Int? = when (DeviceStatuses.normalize(status)) {
    DeviceStatuses.ONLINE -> R.string.iot_status_online
    DeviceStatuses.MAINTENANCE -> R.string.iot_status_maintenance
    DeviceStatuses.OFFLINE -> R.string.iot_status_offline
    else -> null
}

@StringRes
fun metricTypeRes(type: String): Int? = when (type.lowercase()) {
    "weight" -> R.string.iot_metric_weight
    "temperature" -> R.string.iot_metric_temperature
    "humidity" -> R.string.iot_metric_humidity
    "heartrate" -> R.string.iot_metric_heart_rate
    else -> null
}

@Composable
fun deviceTypeLabel(type: String): String = deviceTypeRes(type)?.let { stringResource(it) } ?: type

@Composable
fun deviceStatusLabel(status: String): String = deviceStatusRes(status)?.let { stringResource(it) } ?: status

fun deviceStatusSeverity(status: String): Severity = when (DeviceStatuses.normalize(status)) {
    DeviceStatuses.ONLINE -> Severity.Success
    DeviceStatuses.MAINTENANCE -> Severity.Warn
    else -> Severity.Neutral
}

/** Value without trailing zeros, e.g. `455 kg` or `38.5 °C`. */
fun formatReadingValue(reading: DeviceReading): String =
    listOf(reading.value.toBigDecimal().stripTrailingZeros().toPlainString(), reading.unit).filter { it.isNotBlank() }.joinToString(" ")

@Composable
private fun readingLabel(reading: DeviceReading): String {
    val type = metricTypeRes(reading.type)?.let { stringResource(it) } ?: reading.type
    return "$type: ${formatReadingValue(reading)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceListViewModel = hiltViewModel(),
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
                title = { Text(stringResource(R.string.nav_iot)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (state.canManage) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.iot_new)) },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                state.items.isEmpty() -> EmptyState(stringResource(R.string.iot_empty))
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        PanelHeader(
                            title = stringResource(R.string.iot_title),
                            subtitle = stringResource(R.string.iot_subtitle),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(state.items, key = { it.device.id }) { item ->
                        val device = item.device
                        val location = when {
                            item.animalName != null -> stringResource(R.string.iot_location_animal, item.animalName)
                            item.herdName != null -> stringResource(R.string.iot_location_farm, item.herdName)
                            else -> stringResource(R.string.iot_unassigned)
                        }
                        RecordCard(
                            title = device.name,
                            kicker = deviceTypeLabel(device.type),
                            trailing = { StatusTag(deviceStatusLabel(device.status), deviceStatusSeverity(device.status)) },
                            details = listOf(
                                stringResource(R.string.iot_series) to device.serialNumber,
                                stringResource(R.string.iot_location) to location,
                                stringResource(R.string.iot_readings) to
                                    stringResource(R.string.iot_readings_count, device.readingCount),
                                stringResource(R.string.iot_latest) to
                                    (device.latest?.let { readingLabel(it) } ?: stringResource(R.string.iot_no_readings)),
                            ),
                            footer = if (state.canManage) {
                                {
                                    SecondaryButton(
                                        text = stringResource(R.string.common_edit),
                                        onClick = { onEdit(device.id) },
                                        icon = Icons.Filled.Edit,
                                    )
                                    DangerTextButton(
                                        text = stringResource(R.string.common_delete),
                                        onClick = { viewModel.requestDelete(device) },
                                        icon = Icons.Filled.Delete,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    state.pendingDelete?.let { device ->
        ConfirmDialog(
            title = stringResource(R.string.common_confirm_delete_title),
            message = stringResource(R.string.iot_confirm_delete, device.name),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
fun DeviceFormScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)
    LaunchedEffect(state.done) { if (state.done) onBack() }
    val required = stringResource(R.string.error_field_required)
    val title = stringResource(if (state.isEdit) R.string.iot_form_edit else R.string.iot_form_new)

    FormScreenScaffold(title = title, onBack = onBack, snackbarHostState = snackbarHostState, modifier = modifier) {
        FormHero(
            icon = Icons.Filled.Sensors,
            title = title,
            chip = stringResource(R.string.nav_iot),
            subtitle = stringResource(R.string.iot_form_subtitle),
        )
        AniTecTextField(
            value = state.name, onValueChange = viewModel::onNameChange, label = stringResource(R.string.iot_name),
            modifier = Modifier.fillMaxWidth(), placeholder = stringResource(R.string.iot_name_placeholder),
            error = if (state.nameMissing) required else null,
        )
        DropdownField(
            label = stringResource(R.string.iot_type),
            options = DeviceTypes.all.let { if (state.type in it) it else it + state.type },
            selected = state.type,
            onSelected = viewModel::onTypeChange,
            optionLabel = { deviceTypeLabel(it) },
        )
        AniTecTextField(
            value = state.serialNumber, onValueChange = viewModel::onSerialChange, label = stringResource(R.string.iot_serial),
            modifier = Modifier.fillMaxWidth(), placeholder = stringResource(R.string.iot_serial_placeholder),
            error = if (state.serialMissing) required else null,
        )
        DropdownField(
            label = stringResource(R.string.iot_status),
            options = DeviceStatuses.all.let { if (state.status in it) it else it + state.status },
            selected = state.status,
            onSelected = viewModel::onStatusChange,
            optionLabel = { deviceStatusLabel(it) },
        )
        Text(stringResource(R.string.iot_assign_to), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.target == DeviceTarget.Herd,
                onClick = { viewModel.onTargetChange(DeviceTarget.Herd) },
                label = { Text(stringResource(R.string.iot_farm)) },
            )
            FilterChip(
                selected = state.target == DeviceTarget.Animal,
                onClick = { viewModel.onTargetChange(DeviceTarget.Animal) },
                label = { Text(stringResource(R.string.iot_animal)) },
            )
        }
        if (state.target == DeviceTarget.Herd) {
            DropdownField(
                label = stringResource(R.string.iot_farm),
                options = state.herds.map { it.id },
                selected = state.herdId,
                onSelected = viewModel::onHerdChange,
                optionLabel = { id -> state.herds.firstOrNull { it.id == id }?.name.orEmpty() },
                error = if (state.targetMissing) required else null,
                placeholder = stringResource(R.string.animal_select),
            )
        } else {
            DropdownField(
                label = stringResource(R.string.iot_animal),
                options = state.animals.map { it.id },
                selected = state.animalId,
                onSelected = viewModel::onAnimalChange,
                optionLabel = { id -> state.animals.firstOrNull { it.id == id }?.let { "${it.name} (${it.tag})" }.orEmpty() },
                error = if (state.targetMissing) required else null,
                placeholder = stringResource(R.string.animal_select),
            )
        }
        PrimaryButton(text = stringResource(R.string.common_save), onClick = viewModel::save, modifier = Modifier.fillMaxWidth(), loading = state.saving)
        SecondaryButton(text = stringResource(R.string.common_cancel), onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
