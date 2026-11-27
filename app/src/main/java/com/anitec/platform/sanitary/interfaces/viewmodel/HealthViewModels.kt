package com.anitec.platform.sanitary.interfaces.viewmodel

import com.anitec.platform.core.common.asConflict
import com.anitec.platform.core.common.SaveConflict
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import android.content.Context
import com.anitec.platform.R
import com.anitec.platform.app.HealthFormRoute
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.core.common.serverHints
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.livestock.application.ObserveAnimalsUseCase
import com.anitec.platform.livestock.application.ObserveHerdsUseCase
import com.anitec.platform.livestock.application.RefreshLivestockUseCase
import com.anitec.platform.livestock.domain.Animal
import com.anitec.platform.sanitary.application.DeleteHealthEventUseCase
import com.anitec.platform.sanitary.application.ObserveHealthEventsUseCase
import com.anitec.platform.sanitary.application.RefreshSanitaryUseCase
import com.anitec.platform.sanitary.application.SaveHealthEventUseCase
import com.anitec.platform.sanitary.application.ScheduleFollowUpUseCase
import com.anitec.platform.sanitary.domain.FollowUpRules
import com.anitec.platform.sanitary.domain.HealthEvent
import com.anitec.platform.sanitary.domain.HealthEventDraft
import com.anitec.platform.sanitary.domain.HealthTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A health event with the name of its animal. */
data class HealthItem(val event: HealthEvent, val animalName: String)

/** Health records list state. */
data class HealthListUiState(
    val items: List<HealthItem> = emptyList(),
    val pendingDelete: HealthItem? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    @StringRes val messageRes: Int? = null,
)

private data class HealthListLocal(
    val pendingDelete: HealthItem? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val messageRes: Int? = null,
)

/** Lists the cached health events and handles refresh and delete with confirmation. */
@HiltViewModel
class HealthListViewModel @Inject constructor(
    observeEvents: ObserveHealthEventsUseCase,
    observeAnimals: ObserveAnimalsUseCase,
    private val refreshLivestock: RefreshLivestockUseCase,
    private val refreshSanitary: RefreshSanitaryUseCase,
    private val deleteEvent: DeleteHealthEventUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(HealthListLocal())

    val state: StateFlow<HealthListUiState> = combine(observeEvents(), observeAnimals(), local) { events, animals, local ->
        val names = animals.associate { it.id to it.name }
        HealthListUiState(
            items = events.map { HealthItem(it, names[it.animalId].orEmpty()) },
            pendingDelete = local.pendingDelete,
            loading = local.loading && events.isEmpty(),
            refreshing = local.refreshing,
            messageRes = local.messageRes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthListUiState())

    init {
        refresh()
    }

    /** Refreshes livestock first, because health events are scoped by the visible animals. */
    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            // Records are scoped by the animals the user can see, so livestock is refreshed first.
            val result = when (val livestock = refreshLivestock()) {
                is AppResult.Failure -> livestock
                is AppResult.Success -> refreshSanitary()
            }
            local.update {
                it.copy(
                    refreshing = false,
                    loading = false,
                    messageRes = (result as? AppResult.Failure)?.error?.messageRes(),
                )
            }
        }
    }

    fun requestDelete(item: HealthItem) = local.update { it.copy(pendingDelete = item) }
    fun dismissDelete() = local.update { it.copy(pendingDelete = null) }
    fun onMessageShown() = local.update { it.copy(messageRes = null) }

    fun confirmDelete() {
        val item = local.value.pendingDelete ?: return
        local.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val result = deleteEvent(item.event.id)
            if (result is AppResult.Failure) local.update { it.copy(messageRes = result.error.messageRes()) }
        }
    }
}

/** Create or edit health event form state. */
data class HealthFormUiState(
    val isEdit: Boolean = false,
    val animalId: Int? = null,
    val animals: List<Animal> = emptyList(),
    val type: String = HealthTypes.INCIDENT,
    val date: String = "",
    val nextDueDate: String = "",
    val veterinarian: String = "",
    val description: String = "",
    val diagnosis: String = "",
    val treatment: String = "",
    val prescription: String = "",
    val followUp: String = "",
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    @StringRes val messageRes: Int? = null,
    val serverMessages: List<String> = emptyList(),
    /** Set when the server refused the save because the record changed after the form was opened. */
    val conflict: SaveConflict? = null,
) {
    /** The next check is not a day after the date of the record (US-019 scenario 2). */
    val nextDueInvalid get() = !FollowUpRules.isValidNextCheck(date, nextDueDate)
    val animalMissing get() = showErrors && animalId == null
    val dateMissing get() = showErrors && date.isBlank()
    val veterinarianMissing get() = showErrors && veterinarian.isBlank()
    val descriptionMissing get() = showErrors && description.isBlank()
    val isValid get() = animalId != null && date.isNotBlank() && veterinarian.isNotBlank() && description.isNotBlank() && !nextDueInvalid
}

/**
 * Loads the health event to edit, if any, and saves the form through [SaveHealthEventUseCase].
 *
 * A veterinarian's name is filled in for them; a rancher types the professional's name.
 */
@HiltViewModel
class HealthFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAnimals: ObserveAnimalsUseCase,
    private val observeHerds: ObserveHerdsUseCase,
    private val observeEvents: ObserveHealthEventsUseCase,
    private val saveEvent: SaveHealthEventUseCase,
    private val scheduleFollowUp: ScheduleFollowUpUseCase,
    @ApplicationContext private val context: Context,
    sessionStore: SessionStore,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<HealthFormRoute>()
    private val eventId: Int? = route.eventId

    // Version of the record when the form opened; an edit is sent against it.
    private var baseVersion: Int? = null

    // Next check the record had when the form opened; a follow-up is only scheduled when it changes.
    private var originalNextDue: String? = null

    private val session = (sessionStore.state.value as? SessionState.SignedIn)?.session

    private val _state = MutableStateFlow(
        HealthFormUiState(
            isEdit = eventId != null,
            animalId = route.animalId,
            // A veterinarian signs their own records; a rancher types the name of the professional.
            veterinarian = if (session?.role == UserRole.Veterinarian) session.fullName else "",
            date = java.time.LocalDate.now().toString(),
        ),
    )
    val state: StateFlow<HealthFormUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeAnimals().collect { animals ->
                _state.update { it.copy(animals = animals, animalId = it.animalId ?: animals.firstOrNull()?.id) }
            }
        }
        if (eventId != null) {
            viewModelScope.launch {
                val event = observeEvents().first().firstOrNull { it.id == eventId }
                if (event == null) {
                    _state.update { it.copy(done = true) }
                } else {
                    baseVersion = event.version
                    originalNextDue = event.nextDueDate
                    _state.update {
                        it.copy(
                            animalId = event.animalId, type = event.type, date = event.date,
                            nextDueDate = event.nextDueDate.orEmpty(), veterinarian = event.veterinarian,
                            description = event.description, diagnosis = event.diagnosis, treatment = event.treatment,
                            prescription = event.prescription, followUp = event.followUp,
                        )
                    }
                }
            }
        }
    }

    fun onAnimalChange(value: Int) = _state.update { it.copy(animalId = value) }
    fun onTypeChange(value: String) = _state.update { it.copy(type = value) }
    fun onDateChange(value: String) = _state.update { it.copy(date = value) }
    fun onNextDueDateChange(value: String) = _state.update { it.copy(nextDueDate = value) }
    fun onVeterinarianChange(value: String) = _state.update { it.copy(veterinarian = value) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }
    fun onDiagnosisChange(value: String) = _state.update { it.copy(diagnosis = value) }
    fun onTreatmentChange(value: String) = _state.update { it.copy(treatment = value) }
    fun onPrescriptionChange(value: String) = _state.update { it.copy(prescription = value) }
    fun onFollowUpChange(value: String) = _state.update { it.copy(followUp = value) }
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

    /**
     * A veterinarian who sets a new date for the next check gets an activity for it, for the rancher the patient
     * belongs to (US-019). The record is already saved at this point, so if the activity cannot be created the
     * record is not lost; offline the activity waits in the queue like any other.
     */
    private suspend fun scheduleNextCheckIfNeeded(draft: HealthEventDraft) {
        val user = session?.takeIf { it.role == UserRole.Veterinarian } ?: return
        val date = draft.nextDueDate?.takeIf { it != originalNextDue } ?: return
        val animal = _state.value.animals.firstOrNull { it.id == draft.animalId } ?: return
        val owner = observeHerds().first().firstOrNull { it.id == animal.herdId }?.ownerId ?: return
        val title = context.getString(R.string.health_follow_up_activity_title, animal.name.ifBlank { animal.tag })
        scheduleFollowUp(owner, user.userId, title, date)
    }

    fun save() {
        val current = _state.value
        if (current.saving) return
        if (!current.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true, messageRes = null, serverMessages = emptyList()) }
        viewModelScope.launch {
            val draft = HealthEventDraft(
                animalId = current.animalId!!,
                type = current.type,
                date = current.date,
                description = current.description.trim(),
                veterinarian = current.veterinarian.trim(),
                diagnosis = current.diagnosis.trim(),
                treatment = current.treatment.trim(),
                prescription = current.prescription.trim(),
                followUp = current.followUp.trim(),
                nextDueDate = current.nextDueDate.ifBlank { null },
                baseVersion = baseVersion,
            )
            val result = saveEvent(eventId, draft)
            if (result is AppResult.Success) scheduleNextCheckIfNeeded(draft)
            _state.update {
                when (result) {
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
