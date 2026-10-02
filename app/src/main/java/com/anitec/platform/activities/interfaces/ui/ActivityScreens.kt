package com.anitec.platform.activities.interfaces.ui

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
import androidx.compose.material.icons.filled.Event
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
import com.anitec.platform.activities.domain.Activity
import com.anitec.platform.activities.domain.ActivityPriorities
import com.anitec.platform.activities.domain.ActivityStatuses
import com.anitec.platform.activities.domain.ActivityTypes
import com.anitec.platform.activities.interfaces.viewmodel.ActivityFormViewModel
import com.anitec.platform.activities.interfaces.viewmodel.ActivityListViewModel
import com.anitec.platform.core.designsystem.Severity
import com.anitec.platform.core.outbox.isPendingSync
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
import java.time.LocalDate

@StringRes
fun activityTypeRes(type: String): Int? = when (type) {
    ActivityTypes.HEALTH -> R.string.activity_type_health
    ActivityTypes.VET_VISIT -> R.string.activity_type_vet_visit
    ActivityTypes.PRODUCTION -> R.string.activity_type_production
    ActivityTypes.FINANCIAL -> R.string.activity_type_financial
    ActivityTypes.REPRODUCTIVE -> R.string.activity_type_reproductive
    // English values found in the backend seed data.
    "VeterinaryVisit" -> R.string.activity_type_vet_visit
    "Vaccination" -> R.string.activity_type_vaccination
    "TreatmentFollowUp" -> R.string.activity_type_treatment_followup
    "FinancialTask" -> R.string.activity_type_financial
    else -> null
}

@StringRes
fun activityPriorityRes(priority: String): Int? = when (priority) {
    ActivityPriorities.HIGH -> R.string.activity_priority_high
    ActivityPriorities.MEDIUM -> R.string.activity_priority_medium
    ActivityPriorities.LOW -> R.string.activity_priority_low
    else -> null
}

@StringRes
fun activityStatusRes(status: String): Int? = when (status) {
    ActivityStatuses.PENDING -> R.string.activity_status_pending
    ActivityStatuses.SCHEDULED -> R.string.activity_status_scheduled
    ActivityStatuses.COMPLETED -> R.string.activity_status_completed
    else -> null
}

@Composable
fun activityTypeLabel(type: String): String = activityTypeRes(type)?.let { stringResource(it) } ?: type

@Composable
fun activityPriorityLabel(priority: String): String = activityPriorityRes(priority)?.let { stringResource(it) } ?: priority

@Composable
fun activityStatusLabel(status: String): String = activityStatusRes(status)?.let { stringResource(it) } ?: status

/** High priority is red, medium amber, anything else blue, as in the web. */
fun activityPrioritySeverity(priority: String): Severity = when (priority) {
    ActivityPriorities.HIGH -> Severity.Danger
    ActivityPriorities.MEDIUM -> Severity.Warn
    else -> Severity.Info
}

/** Card of one activity, shared by the list and the dashboards. */
@Composable
fun ActivityCard(
    activity: Activity,
    today: LocalDate,
    modifier: Modifier = Modifier,
    footer: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
) {
    val overdue = activity.isUpcoming(today).not() && !activity.status.equals(ActivityStatuses.COMPLETED, ignoreCase = true)
    RecordCard(
        title = activity.title,
        kicker = if (overdue) "${activity.date} · ${stringResource(R.string.activity_overdue)}" else activity.date,
        trailing = { StatusTag(activityPriorityLabel(activity.priority), activityPrioritySeverity(activity.priority)) },
        details = listOf(
            stringResource(R.string.activity_type) to activityTypeLabel(activity.type),
            stringResource(R.string.activity_status) to activityStatusLabel(activity.status),
        ),
        footer = if (activity.id.isPendingSync) null else footer,
        pendingSync = activity.id.isPendingSync,
        modifier = modifier,
    )
}

@Composable
fun ActivityListScreen(
    onNew: () -> Unit,
    onEdit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                state.items.isEmpty() -> EmptyState(stringResource(R.string.activities_empty))
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.id }) { activity ->
                        ActivityCard(
                            activity = activity,
                            today = state.today,
                            footer = {
                                SecondaryButton(
                                    text = stringResource(R.string.common_edit),
                                    onClick = { onEdit(activity.id) },
                                    icon = Icons.Filled.Edit,
                                )
                                DangerTextButton(
                                    text = stringResource(R.string.common_delete),
                                    onClick = { viewModel.requestDelete(activity) },
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
            text = { Text(stringResource(R.string.activities_new)) },
        )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    state.pendingDelete?.let { activity ->
        ConfirmDialog(
            title = stringResource(R.string.common_confirm_delete_title),
            message = stringResource(R.string.activities_confirm_delete, activity.title),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
fun ActivityFormScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)
    LaunchedEffect(state.done) { if (state.done) onBack() }
    val required = stringResource(R.string.error_field_required)
    val title = stringResource(if (state.isEdit) R.string.activity_form_edit else R.string.activity_form_new)

    FormScreenScaffold(title = title, onBack = onBack, snackbarHostState = snackbarHostState, modifier = modifier) {
        FormHero(
            icon = Icons.Filled.Event,
            title = title,
            chip = stringResource(R.string.nav_activities),
            subtitle = stringResource(R.string.activity_form_subtitle),
        )
        if (state.isVeterinarian && !state.isEdit) {
            DropdownField(
                label = stringResource(R.string.activity_for_client),
                options = state.clients.map { it.rancherId },
                selected = state.clientId,
                onSelected = viewModel::onClientChange,
                optionLabel = { id -> state.clients.firstOrNull { it.rancherId == id }?.rancherName.orEmpty() },
                error = if (state.clientMissing) required else null,
                placeholder = stringResource(R.string.animal_select),
            )
        }
        AniTecTextField(
            value = state.title, onValueChange = viewModel::onTitleChange, label = stringResource(R.string.activity_title),
            modifier = Modifier.fillMaxWidth(), error = if (state.titleMissing) required else null,
        )
        DropdownField(
            label = stringResource(R.string.activity_type),
            options = ActivityTypes.all.let { if (state.type in it) it else it + state.type },
            selected = state.type,
            onSelected = viewModel::onTypeChange,
            optionLabel = { activityTypeLabel(it) },
        )
        DateField(
            value = state.date, onChange = viewModel::onDateChange, label = stringResource(R.string.activity_date),
            error = if (state.dateMissing) required else null,
        )
        DropdownField(
            label = stringResource(R.string.activity_priority),
            options = ActivityPriorities.all.let { if (state.priority in it) it else it + state.priority },
            selected = state.priority,
            onSelected = viewModel::onPriorityChange,
            optionLabel = { activityPriorityLabel(it) },
        )
        DropdownField(
            label = stringResource(R.string.activity_status),
            options = ActivityStatuses.all.let { if (state.status in it) it else it + state.status },
            selected = state.status,
            onSelected = viewModel::onStatusChange,
            optionLabel = { activityStatusLabel(it) },
        )
        PrimaryButton(text = stringResource(R.string.common_save), onClick = viewModel::save, modifier = Modifier.fillMaxWidth(), loading = state.saving)
        SecondaryButton(text = stringResource(R.string.common_cancel), onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
