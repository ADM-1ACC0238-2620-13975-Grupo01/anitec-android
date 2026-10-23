package com.anitec.platform.veterinary.interfaces.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anitec.platform.app.ClinicalHistoryRoute
import com.anitec.platform.app.PatientsRoute
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.livestock.application.ObserveAnimalsUseCase
import com.anitec.platform.livestock.application.ObserveCorralsUseCase
import com.anitec.platform.livestock.application.ObserveHerdsUseCase
import com.anitec.platform.livestock.application.RefreshLivestockUseCase
import com.anitec.platform.livestock.domain.Animal
import com.anitec.platform.livestock.domain.AnimalView
import com.anitec.platform.livestock.domain.Herd
import com.anitec.platform.sanitary.application.ObserveHealthEventsUseCase
import com.anitec.platform.sanitary.application.RefreshSanitaryUseCase
import com.anitec.platform.sanitary.interfaces.viewmodel.HealthItem
import com.anitec.platform.veterinary.application.ObserveClientsUseCase
import com.anitec.platform.veterinary.application.RefreshClientsUseCase
import com.anitec.platform.veterinary.domain.Client
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientsUiState(
    val clients: List<Client> = emptyList(),
    val selectedClientId: Int? = null,
    val herds: List<Herd> = emptyList(),
    val selectedHerdId: Int? = null,
    val patients: List<AnimalView> = emptyList(),
    val refreshing: Boolean = false,
    @StringRes val messageRes: Int? = null,
)

private data class PatientsLocal(
    val clientId: Int? = null,
    val herdId: Int? = null,
    val refreshing: Boolean = false,
    val messageRes: Int? = null,
)

/** Patients of one client (their animals), optionally narrowed to one of the client's farms. */
@HiltViewModel
class PatientsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeClients: ObserveClientsUseCase,
    observeHerds: ObserveHerdsUseCase,
    observeCorrals: ObserveCorralsUseCase,
    observeAnimals: ObserveAnimalsUseCase,
    private val refreshClients: RefreshClientsUseCase,
    private val refreshLivestock: RefreshLivestockUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(PatientsLocal(clientId = savedStateHandle.toRoute<PatientsRoute>().clientId))

    val state: StateFlow<PatientsUiState> = combine(
        observeClients(), observeHerds(), observeCorrals(), observeAnimals(), local,
    ) { clients, herds, corrals, animals, local ->
        // The requested client wins; otherwise the first one, like the web.
        val selected = clients.firstOrNull { it.rancherId == local.clientId }?.rancherId ?: clients.firstOrNull()?.rancherId
        val clientHerds = herds.filter { it.ownerId == selected }
        val herd = local.herdId?.takeIf { id -> clientHerds.any { it.id == id } }
        val herdNames = herds.associate { it.id to it.name }
        val corralNames = corrals.associate { it.id to it.name }
        val shownHerds = clientHerds.filter { herd == null || it.id == herd }.map { it.id }.toSet()
        PatientsUiState(
            clients = clients,
            selectedClientId = selected,
            herds = clientHerds,
            selectedHerdId = herd,
            patients = animals.filter { it.herdId in shownHerds }
                .map { AnimalView(it, herdNames[it.herdId].orEmpty(), it.corralId?.let(corralNames::get)) },
            refreshing = local.refreshing,
            messageRes = local.messageRes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientsUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            val result = when (val clients = refreshClients()) {
                is AppResult.Failure -> clients
                is AppResult.Success -> refreshLivestock()
            }
            local.update { it.copy(refreshing = false, messageRes = (result as? AppResult.Failure)?.error?.messageRes()) }
        }
    }

    // Changing the client resets the farm filter to "all farms".
    fun onClientSelected(rancherId: Int) = local.update { it.copy(clientId = rancherId, herdId = null) }
    fun onHerdSelected(herdId: Int?) = local.update { it.copy(herdId = herdId) }
    fun onMessageShown() = local.update { it.copy(messageRes = null) }
}

data class ClinicalHistoryUiState(
    val animal: AnimalView? = null,
    val events: List<HealthItem> = emptyList(),
    val loaded: Boolean = false,
)

/** Everything recorded about one animal, newest first. */
@HiltViewModel
class ClinicalHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAnimals: ObserveAnimalsUseCase,
    observeHerds: ObserveHerdsUseCase,
    observeCorrals: ObserveCorralsUseCase,
    observeEvents: ObserveHealthEventsUseCase,
) : ViewModel() {

    val animalId: Int = savedStateHandle.toRoute<ClinicalHistoryRoute>().animalId

    val state: StateFlow<ClinicalHistoryUiState> = combine(
        observeAnimals(), observeHerds(), observeCorrals(), observeEvents(),
    ) { animals, herds, corrals, events ->
        val animal: Animal? = animals.firstOrNull { it.id == animalId }
        ClinicalHistoryUiState(
            animal = animal?.let { a ->
                AnimalView(a, herds.firstOrNull { it.id == a.herdId }?.name.orEmpty(), corrals.firstOrNull { it.id == a.corralId }?.name)
            },
            events = events.filter { it.animalId == animalId }.map { HealthItem(it, animal?.name.orEmpty()) },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClinicalHistoryUiState())
}

data class VetHomeUiState(
    val clients: List<ClientSummary> = emptyList(),
    val selectedClientId: Int? = null,
    val clientCount: Int = 0,
    val activePatients: Int = 0,
    val recordCount: Int = 0,
    val followUpCount: Int = 0,
    val recent: List<HealthItem> = emptyList(),
    val refreshing: Boolean = false,
    @StringRes val messageRes: Int? = null,
)

private data class VetHomeLocal(val clientId: Int? = null, val refreshing: Boolean = false, val messageRes: Int? = null)

/** Dashboard of the veterinarian: totals over all clients, or over the selected one. */
@HiltViewModel
class VetHomeViewModel @Inject constructor(
    observeClients: ObserveClientsUseCase,
    observeHerds: ObserveHerdsUseCase,
    observeAnimals: ObserveAnimalsUseCase,
    observeEvents: ObserveHealthEventsUseCase,
    private val refreshClients: RefreshClientsUseCase,
    private val refreshLivestock: RefreshLivestockUseCase,
    private val refreshSanitary: RefreshSanitaryUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(VetHomeLocal())

    val state: StateFlow<VetHomeUiState> = combine(
        observeClients(), observeHerds(), observeAnimals(), observeEvents(), local,
    ) { clients, herds, animals, events, local ->
        val selected = local.clientId?.takeIf { id -> clients.any { it.rancherId == id } }
        val scopedHerds = herds.filter { selected == null || it.ownerId == selected }.map { it.id }.toSet()
        val scopedAnimals = animals.filter { it.herdId in scopedHerds }
        val animalIds = scopedAnimals.map { it.id }.toSet()
        val scopedEvents = events.filter { it.animalId in animalIds }
        val names = animals.associate { it.id to it.name }
        VetHomeUiState(
            clients = summarize(clients, herds, animals, events),
            selectedClientId = selected,
            clientCount = if (selected == null) clients.size else 1,
            activePatients = scopedAnimals.count { it.healthStatus.needsAttention },
            recordCount = scopedEvents.size,
            followUpCount = scopedEvents.count { it.hasFollowUp },
            recent = scopedEvents.take(5).map { HealthItem(it, names[it.animalId].orEmpty()) },
            refreshing = local.refreshing,
            messageRes = local.messageRes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VetHomeUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            var result: AppResult<Unit> = refreshClients()
            if (result is AppResult.Success) result = refreshLivestock()
            if (result is AppResult.Success) result = refreshSanitary()
            local.update { it.copy(refreshing = false, messageRes = (result as? AppResult.Failure)?.error?.messageRes()) }
        }
    }

    fun onClientSelected(rancherId: Int?) = local.update { it.copy(clientId = rancherId) }
    fun onMessageShown() = local.update { it.copy(messageRes = null) }
}
