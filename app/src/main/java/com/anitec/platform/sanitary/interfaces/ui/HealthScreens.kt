package com.anitec.platform.sanitary.interfaces.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.anitec.platform.core.designsystem.component.AniTecTextField
import com.anitec.platform.core.designsystem.component.ConfirmDialog
import com.anitec.platform.core.designsystem.component.DangerTextButton
import com.anitec.platform.core.designsystem.component.DateField
import com.anitec.platform.core.designsystem.component.DropdownField
import com.anitec.platform.core.designsystem.component.EmptyState
import com.anitec.platform.core.designsystem.component.FormHero
import com.anitec.platform.core.designsystem.component.FormScreenScaffold
import com.anitec.platform.core.designsystem.component.LoadingState
import com.anitec.platform.core.designsystem.component.MessageEffect
import com.anitec.platform.core.designsystem.component.PrimaryButton
import com.anitec.platform.core.designsystem.component.RecordCard
import com.anitec.platform.core.designsystem.component.SecondaryButton
import com.anitec.platform.core.designsystem.component.StatusTag
import com.anitec.platform.sanitary.domain.HealthTypes
import com.anitec.platform.sanitary.interfaces.viewmodel.HealthFormViewModel
import com.anitec.platform.sanitary.interfaces.viewmodel.HealthItem
import com.anitec.platform.sanitary.interfaces.viewmodel.HealthListViewModel

@StringRes
fun healthTypeLabelRes(type: String): Int? = when (type) {
    HealthTypes.INCIDENT -> R.string.health_type_incident
    HealthTypes.VACCINE -> R.string.health_type_vaccine
    HealthTypes.TREATMENT -> R.string.health_type_treatment
    HealthTypes.DIAGNOSIS -> R.string.health_type_diagnosis
    HealthTypes.CHECKUP -> R.string.health_type_checkup
    else -> null
}

@Composable
fun healthTypeLabel(type: String): String = healthTypeLabelRes(type)?.let { stringResource(it) } ?: type

@Composable
fun FollowUpTag(hasFollowUp: Boolean) {
    if (hasFollowUp) StatusTag(stringResource(R.string.health_tag_follow_up), Severity.Warn)
    else StatusTag(stringResource(R.string.health_tag_closed), Severity.Success)
}

/** Card of one health record, shared by the list and the dashboard. */
@Composable
fun HealthRecordCard(item: HealthItem, modifier: Modifier = Modifier, footer: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null) {
    val event = item.event
    val details = buildList {
        add(stringResource(R.string.health_type) to healthTypeLabel(event.type))
        add(stringResource(R.string.health_description) to event.description)
        if (event.veterinarian.isNotBlank()) add(stringResource(R.string.health_veterinarian) to event.veterinarian)
        if (event.diagnosis.isNotBlank()) add(stringResource(R.string.health_diagnosis) to event.diagnosis)
        if (event.treatment.isNotBlank()) add(stringResource(R.string.health_treatment) to event.treatment)
        if (event.prescription.isNotBlank()) add(stringResource(R.string.health_prescription) to event.prescription)
        if (event.followUp.isNotBlank()) add(stringResource(R.string.health_follow_up) to event.followUp)
        event.nextDueDate?.let { add(stringResource(R.string.health_next_due).substringBefore(" (") to it) }
    }
    RecordCard(
        title = item.animalName.ifBlank { event.animalId.toString() },
        kicker = event.date,
        trailing = { FollowUpTag(event.hasFollowUp) },
        details = details,
        footer = footer,
        modifier = modifier,
    )
}

@Composable
fun HealthListScreen(
    onNew: () -> Unit,
    onEdit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HealthListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                state.items.isEmpty() -> EmptyState(stringResource(R.string.health_empty))
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.event.id }) { item ->
                        HealthRecordCard(
                            item = item,
                            footer = {
                                SecondaryButton(
                                    text = stringResource(R.string.common_edit),
                                    onClick = { onEdit(item.event.id) },
                                    icon = Icons.Filled.Edit,
                                )
                                DangerTextButton(
                                    text = stringResource(R.string.common_delete),
                                    onClick = { viewModel.requestDelete(item) },
                                    icon = Icons.Filled.Delete,
                                )
                            },
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNew,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.health_new)) },
        )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    state.pendingDelete?.let {
        ConfirmDialog(
            title = stringResource(R.string.common_confirm_delete_title),
            message = stringResource(R.string.health_confirm_delete),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
fun HealthFormScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HealthFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)
    LaunchedEffect(state.done) { if (state.done) onBack() }
    val required = stringResource(R.string.error_field_required)
    val title = stringResource(if (state.isEdit) R.string.health_form_edit else R.string.health_form_new)

    FormScreenScaffold(title = title, onBack = onBack, snackbarHostState = snackbarHostState, modifier = modifier) {
        FormHero(
            icon = Icons.Filled.Favorite,
            title = title,
            chip = stringResource(R.string.nav_health),
            subtitle = stringResource(R.string.health_form_subtitle),
        )
        if (state.animals.isEmpty()) {
            Text(stringResource(R.string.health_no_animals), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownField(
            label = stringResource(R.string.nav_animals),
            options = state.animals.map { it.id },
            selected = state.animalId,
            onSelected = viewModel::onAnimalChange,
            optionLabel = { id -> state.animals.firstOrNull { it.id == id }?.let { "${it.name} · ${it.tag}" }.orEmpty() },
            error = if (state.animalMissing) required else null,
            placeholder = stringResource(R.string.animal_select),
        )
        DropdownField(
            label = stringResource(R.string.health_type),
            options = HealthTypes.all,
            selected = state.type,
            onSelected = viewModel::onTypeChange,
            optionLabel = { healthTypeLabel(it) },
        )
        DateField(
            value = state.date,
            onChange = viewModel::onDateChange,
            label = stringResource(R.string.health_date),
            error = if (state.dateMissing) required else null,
        )
        DateField(
            value = state.nextDueDate,
            onChange = viewModel::onNextDueDateChange,
            label = stringResource(R.string.health_next_due),
            clearable = true,
        )
        AniTecTextField(
            value = state.veterinarian, onValueChange = viewModel::onVeterinarianChange, label = stringResource(R.string.health_veterinarian),
            modifier = Modifier.fillMaxWidth(), error = if (state.veterinarianMissing) required else null,
        )
        AniTecTextField(
            value = state.description, onValueChange = viewModel::onDescriptionChange, label = stringResource(R.string.health_description),
            modifier = Modifier.fillMaxWidth(), singleLine = false, minLines = 3, error = if (state.descriptionMissing) required else null,
        )
        AniTecTextField(
            value = state.diagnosis, onValueChange = viewModel::onDiagnosisChange, label = stringResource(R.string.health_diagnosis),
            modifier = Modifier.fillMaxWidth(), singleLine = false, minLines = 2,
        )
        AniTecTextField(
            value = state.treatment, onValueChange = viewModel::onTreatmentChange, label = stringResource(R.string.health_treatment),
            modifier = Modifier.fillMaxWidth(), singleLine = false, minLines = 2,
        )
        AniTecTextField(
            value = state.prescription, onValueChange = viewModel::onPrescriptionChange, label = stringResource(R.string.health_prescription),
            modifier = Modifier.fillMaxWidth(), singleLine = false, minLines = 2,
        )
        AniTecTextField(
            value = state.followUp, onValueChange = viewModel::onFollowUpChange, label = stringResource(R.string.health_follow_up),
            modifier = Modifier.fillMaxWidth(), singleLine = false, minLines = 2,
        )
        PrimaryButton(text = stringResource(R.string.common_save), onClick = viewModel::save, modifier = Modifier.fillMaxWidth(), loading = state.saving)
        SecondaryButton(text = stringResource(R.string.common_cancel), onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
