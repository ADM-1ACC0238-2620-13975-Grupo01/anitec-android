package com.anitec.platform.activities.interfaces.viewmodel

import com.anitec.platform.core.common.asConflict
import com.anitec.platform.core.common.SaveConflict
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anitec.platform.activities.application.DeleteActivityUseCase
import com.anitec.platform.activities.application.ObserveActivitiesUseCase
import com.anitec.platform.activities.application.RefreshActivitiesUseCase
import com.anitec.platform.activities.application.SaveActivityUseCase
import com.anitec.platform.activities.domain.Activity
import com.anitec.platform.activities.domain.ActivityDraft
import com.anitec.platform.activities.domain.ActivityPriorities
import com.anitec.platform.activities.domain.ActivityRules
import com.anitec.platform.activities.domain.ActivityScope
import com.anitec.platform.activities.domain.ActivityStatuses
import com.anitec.platform.activities.domain.ActivityTypes
import com.anitec.platform.R
import com.anitec.platform.app.ActivityFormRoute
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.core.common.serverHints
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.veterinary.application.ObserveClientsUseCase
import com.anitec.platform.veterinary.application.RefreshClientsUseCase
import com.anitec.platform.veterinary.domain.Client
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Upcoming activities first (soonest first), then finished or past ones (most recent first). */
fun List<Activity>.sortedForDisplay(today: LocalDate): List<Activity> {
    val (upcoming, others) = partition { it.isUpcoming(today) }
    return upcoming.sortedBy { it.date } + others.sortedByDescending { it.date }
}
/**
 * Immutable UI state data class representing the state of the activity list screen.
 */
data class ActivityListUiState(
    val items: List<Activity> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val pendingDelete: Activity? = null,
    /** Activity the user is about to cancel, waiting for confirmation. */
    val pendingCancel: Activity? = null,
    /** Activity the user is choosing a new day for. */
    val pendingReschedule: Activity? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    @StringRes val messageRes: Int? = null,
)
/**
 * Internal UI state holder representing local UI flags and transient data for the activity list.
 */
private data class ActivityListLocal(
    val pendingDelete: Activity? = null,
    val pendingCancel: Activity? = null,
    val pendingReschedule: Activity? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val messageRes: Int? = null,
)
/**
 * ViewModel responsible for managing the state and business logic of the activity list screen.
 *
 * Injects required use cases and session management dependencies using Hilt.
 */
@HiltViewModel
class ActivityListViewModel @Inject constructor(
    observeActivities: ObserveActivitiesUseCase,
    private val refreshActivities: RefreshActivitiesUseCase,
    private val refreshClients: RefreshClientsUseCase,
    private val deleteActivity: DeleteActivityUseCase,
    private val saveActivity: SaveActivityUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val local = MutableStateFlow(ActivityListLocal())

    val state: StateFlow<ActivityListUiState> = combine(observeActivities(), local) { activities, local ->
        val today = LocalDate.now()
        ActivityListUiState(
            items = activities.sortedForDisplay(today),
            today = today,
            pendingDelete = local.pendingDelete,
            pendingCancel = local.pendingCancel,
            pendingReschedule = local.pendingReschedule,
            loading = local.loading && activities.isEmpty(),
            refreshing = local.refreshing,
            messageRes = local.messageRes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityListUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            val role = (sessionStore.state.value as? SessionState.SignedIn)?.session?.role
            // A veterinarian's view includes their clients' activities, so the clients must be known first.
            val result = if (role == UserRole.Veterinarian) {
                when (val clients = refreshClients()) {
                    is AppResult.Failure -> clients
                    is AppResult.Success -> refreshActivities()
                }
            } else {
                refreshActivities()
            }
            local.update { it.copy(refreshing = false, loading = false, messageRes = (result as? AppResult.Failure)?.error?.messageRes()) }
        }
    }

    fun requestDelete(activity: Activity) = local.update { it.copy(pendingDelete = activity) }
    fun dismissDelete() = local.update { it.copy(pendingDelete = null) }
    fun onMessageShown() = local.update { it.copy(messageRes = null) }

    /** The activity was attended: it is completed and keeps its date (US-028 scenario 1). */
    fun markAttended(activity: Activity) = change(activity.toDraft(status = ActivityStatuses.COMPLETED), activity, R.string.activity_attended_message)

    fun requestReschedule(activity: Activity) = local.update { it.copy(pendingReschedule = activity) }
    fun dismissReschedule() = local.update { it.copy(pendingReschedule = null) }

    /** Moves the activity to [date]. Its reminder is replaced by the one for the new day (US-028 scenario 2). */
    fun reschedule(date: String) {
        val activity = local.value.pendingReschedule ?: return
        local.update { it.copy(pendingReschedule = null) }
        if (!ActivityRules.canScheduleOn(date, LocalDate.now())) {
            local.update { it.copy(messageRes = R.string.activity_date_past) }
            return
        }
        if (date == activity.date) return
        change(activity.toDraft(date = date), activity, R.string.activity_rescheduled_message)
    }

    fun requestCancel(activity: Activity) = local.update { it.copy(pendingCancel = activity) }
    fun dismissCancel() = local.update { it.copy(pendingCancel = null) }

    /** Cancels the activity: it stays in the list as cancelled and gets no more reminders (US-026 scenario 2). */
    fun confirmCancel() {
        val activity = local.value.pendingCancel ?: return
        local.update { it.copy(pendingCancel = null) }
        change(activity.toDraft(status = ActivityStatuses.CANCELLED), activity, R.string.activity_cancelled_message)
    }

    /** Saves a change of an existing activity and reports the outcome; a refused one leaves the list as it was. */
    private fun change(draft: ActivityDraft, activity: Activity, @StringRes success: Int) {
        viewModelScope.launch {
            val result = saveActivity(activity.id, draft)
            local.update { it.copy(messageRes = (result as? AppResult.Failure)?.error?.messageRes() ?: success) }
        }
    }

    fun confirmDelete() {
        val activity = local.value.pendingDelete ?: return
        local.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val result = deleteActivity(activity.id)
            if (result is AppResult.Failure) local.update { it.copy(messageRes = result.error.messageRes()) }
        }
    }
}
/**
 * Immutable UI state data class representing the state of the activity form screen.
 */
data class ActivityFormUiState(
    val isEdit: Boolean = false,
    val isVeterinarian: Boolean = false,
    val title: String = "",
    val type: String = ActivityTypes.HEALTH,
    val date: String = LocalDate.now().toString(),
    val priority: String = ActivityPriorities.MEDIUM,
    val status: String = ActivityStatuses.PENDING,
    val clients: List<Client> = emptyList(),
    val clientId: Int? = null,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    @StringRes val messageRes: Int? = null,
    val serverMessages: List<String> = emptyList(),
    /** Set when the server refused the save because the record changed after the form was opened. */
    val conflict: SaveConflict? = null,
    /** Day the activity had when the form opened; changing an open activity to an earlier day than today is refused. */
    val originalDate: String? = null,
    val today: LocalDate = LocalDate.now(),
) {
    /** The date was moved to a day that has passed. An unchanged past date is allowed, so an overdue activity can still be edited. */
    val dateInPast get() = isOpenStatus && date.isNotBlank() && date != originalDate && !ActivityRules.canScheduleOn(date, today)
    private val isOpenStatus get() = !status.equals(ActivityStatuses.COMPLETED, ignoreCase = true) && !status.equals(ActivityStatuses.CANCELLED, ignoreCase = true)
    val titleMissing get() = showErrors && title.isBlank()
    val dateMissing get() = showErrors && date.isBlank()
    // A veterinarian must say which client the activity is for, otherwise the rancher could not see it.
    val clientMissing get() = showErrors && isVeterinarian && !isEdit && clientId == null
    val isValid get() = title.isNotBlank() && date.isNotBlank() && !dateInPast && !(isVeterinarian && !isEdit && clientId == null)
}
/**
 * ViewModel responsible for managing the state and business logic of the activity form screen.
 *
 * Handles navigation arguments, observation of activities and clients, saving logic, and session context via Hilt.
 */
@HiltViewModel
class ActivityFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeActivities: ObserveActivitiesUseCase,
    observeClients: ObserveClientsUseCase,
    private val saveActivity: SaveActivityUseCase,
    sessionStore: SessionStore,
) : ViewModel() {

    private val activityId: Int? = savedStateHandle.toRoute<ActivityFormRoute>().activityId
    private val session = (sessionStore.state.value as? SessionState.SignedIn)?.session
    private val activities = observeActivities()

    // Editing keeps who the activity belongs to and who created it.
    private var existing: Activity? = null

    // Version of the record when the form opened; an edit is sent against it.
    private var baseVersion: Int? = null

    private val _state = MutableStateFlow(
        ActivityFormUiState(isEdit = activityId != null, isVeterinarian = session?.role == UserRole.Veterinarian),
    )
    val state: StateFlow<ActivityFormUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeClients().collect { clients ->
                _state.update { it.copy(clients = clients, clientId = it.clientId ?: clients.firstOrNull()?.rancherId) }
            }
        }
        if (activityId != null) {
            viewModelScope.launch {
                val activity = activities.first().firstOrNull { it.id == activityId }
                if (activity == null) {
                    _state.update { it.copy(done = true) }
                } else {
                    existing = activity
                    baseVersion = activity.version
                    _state.update {
                        it.copy(
                            title = activity.title, type = activity.type, date = activity.date, priority = activity.priority,
                            status = activity.status, originalDate = activity.date,
                        )
                    }
                }
            }
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value) }
    fun onTypeChange(value: String) = _state.update { it.copy(type = value) }
    fun onDateChange(value: String) = _state.update { it.copy(date = value) }
    fun onPriorityChange(value: String) = _state.update { it.copy(priority = value) }
    fun onStatusChange(value: String) = _state.update { it.copy(status = value) }
    fun onClientChange(value: Int) = _state.update { it.copy(clientId = value) }
    fun onMessageShown() = _state.update { it.copy(messageRes = null) }

    /** The user keeps their change over the version that appeared meanwhile: it is sent again against that version. */
    fun keepMine() {
        val conflict = _state.value.conflict ?: return
        baseVersion = conflict.serverVersion
        _state.update { it.copy(conflict = null) }
        save()
    }

    /** The user takes the version on the server: the cache already holds it, so the form just closes. */
    fun keepServer() = _state.update { it.copy(conflict = null, done = true) }

    /** The user goes back to the form with their change and the conflict forgotten. */
    fun dismissConflict() = _state.update { it.copy(conflict = null) }

    fun save() {
        val current = _state.value
        val user = session ?: return
        if (current.saving) return
        if (!current.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true, messageRes = null, serverMessages = emptyList()) }
        viewModelScope.launch {
            val (ownerId, veterinarianId) = existing?.let { it.ownerId to it.veterinarianId }
                ?: ActivityScope.ownership(user.role, user.userId, current.clientId)
            val draft = ActivityDraft(
                ownerId = ownerId, veterinarianId = veterinarianId, title = current.title.trim(),
                type = current.type, date = current.date, priority = current.priority, status = current.status,
                baseVersion = baseVersion,
            )
            _state.update {
                when (val result = saveActivity(activityId, draft)) {
                    is AppResult.Success -> it.copy(saving = false, done = true)
                    is AppResult.Failure -> {
                        val conflict = result.error.asConflict()
                        it.copy(
                            saving = false, conflict = conflict,
                            messageRes = if (conflict == null) result.error.messageRes() else null,
                            serverMessages = result.error.serverHints(),
                        )
                    }
                }
            }
        }
    }
}
