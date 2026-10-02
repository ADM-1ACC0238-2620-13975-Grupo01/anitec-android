package com.anitec.platform.activities.interfaces.viewmodel

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
import com.anitec.platform.activities.domain.ActivityScope
import com.anitec.platform.activities.domain.ActivityStatuses
import com.anitec.platform.activities.domain.ActivityTypes
import com.anitec.platform.app.ActivityFormRoute
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
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

data class ActivityListUiState(
    val items: List<Activity> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val pendingDelete: Activity? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    @StringRes val messageRes: Int? = null,
)

private data class ActivityListLocal(
    val pendingDelete: Activity? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val messageRes: Int? = null,
)

@HiltViewModel
class ActivityListViewModel @Inject constructor(
    observeActivities: ObserveActivitiesUseCase,
    private val refreshActivities: RefreshActivitiesUseCase,
    private val refreshClients: RefreshClientsUseCase,
    private val deleteActivity: DeleteActivityUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val local = MutableStateFlow(ActivityListLocal())

    val state: StateFlow<ActivityListUiState> = combine(observeActivities(), local) { activities, local ->
        val today = LocalDate.now()
        ActivityListUiState(
            items = activities.sortedForDisplay(today),
            today = today,
            pendingDelete = local.pendingDelete,
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

    fun confirmDelete() {
        val activity = local.value.pendingDelete ?: return
        local.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val result = deleteActivity(activity.id)
            if (result is AppResult.Failure) local.update { it.copy(messageRes = result.error.messageRes()) }
        }
    }
}

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
) {
    val titleMissing get() = showErrors && title.isBlank()
    val dateMissing get() = showErrors && date.isBlank()
    // A veterinarian must say which client the activity is for, otherwise the rancher could not see it.
    val clientMissing get() = showErrors && isVeterinarian && !isEdit && clientId == null
    val isValid get() = title.isNotBlank() && date.isNotBlank() && !(isVeterinarian && !isEdit && clientId == null)
}

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
                    _state.update {
                        it.copy(title = activity.title, type = activity.type, date = activity.date, priority = activity.priority, status = activity.status)
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

    fun save() {
        val current = _state.value
        val user = session ?: return
        if (current.saving) return
        if (!current.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true, messageRes = null) }
        viewModelScope.launch {
            val (ownerId, veterinarianId) = existing?.let { it.ownerId to it.veterinarianId }
                ?: ActivityScope.ownership(user.role, user.userId, current.clientId)
            val draft = ActivityDraft(
                ownerId = ownerId, veterinarianId = veterinarianId, title = current.title.trim(),
                type = current.type, date = current.date, priority = current.priority, status = current.status,
            )
            _state.update {
                when (val result = saveActivity(activityId, draft)) {
                    is AppResult.Success -> it.copy(saving = false, done = true)
                    is AppResult.Failure -> it.copy(saving = false, messageRes = result.error.messageRes())
                }
            }
        }
    }
}
