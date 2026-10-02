package com.anitec.platform.veterinary.interfaces.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.livestock.application.ObserveAnimalsUseCase
import com.anitec.platform.livestock.application.ObserveHerdsUseCase
import com.anitec.platform.livestock.application.RefreshLivestockUseCase
import com.anitec.platform.sanitary.application.ObserveHealthEventsUseCase
import com.anitec.platform.sanitary.application.RefreshSanitaryUseCase
import com.anitec.platform.veterinary.application.AddClientUseCase
import com.anitec.platform.veterinary.application.ListAvailableRanchersUseCase
import com.anitec.platform.veterinary.application.ObserveClientsUseCase
import com.anitec.platform.veterinary.application.RefreshClientsUseCase
import com.anitec.platform.veterinary.application.RemoveClientUseCase
import com.anitec.platform.veterinary.application.search
import com.anitec.platform.veterinary.domain.AvailableRancher
import com.anitec.platform.veterinary.domain.Client
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A client with figures computed from the animals and records the veterinarian can see. */
data class ClientSummary(
    val client: Client,
    val farmNames: List<String>,
    val animalCount: Int,
    val speciesCount: Int,
    val attentionCount: Int,
    val recordCount: Int,
    val followUpCount: Int,
)

data class ClientListUiState(
    val items: List<ClientSummary> = emptyList(),
    val pendingRemove: ClientSummary? = null,
    val refreshing: Boolean = false,
    @StringRes val messageRes: Int? = null,
)

private data class ClientListLocal(
    val pendingRemove: ClientSummary? = null,
    val refreshing: Boolean = false,
    val messageRes: Int? = null,
)

/** Builds the per-client figures; shared by the clients list and the veterinarian dashboard. */
fun summarize(
    clients: List<Client>,
    herds: List<com.anitec.platform.livestock.domain.Herd>,
    animals: List<com.anitec.platform.livestock.domain.Animal>,
    events: List<com.anitec.platform.sanitary.domain.HealthEvent>,
): List<ClientSummary> = clients.map { client ->
    val clientHerds = herds.filter { it.ownerId == client.rancherId }
    val herdIds = clientHerds.map { it.id }.toSet()
    val clientAnimals = animals.filter { it.herdId in herdIds }
    val animalIds = clientAnimals.map { it.id }.toSet()
    val clientEvents = events.filter { it.animalId in animalIds }
    ClientSummary(
        client = client,
        farmNames = clientHerds.map { it.name },
        animalCount = clientAnimals.size,
        speciesCount = clientAnimals.map { it.species.lowercase() }.distinct().size,
        attentionCount = clientAnimals.count { it.healthStatus.needsAttention },
        recordCount = clientEvents.size,
        followUpCount = clientEvents.count { it.hasFollowUp },
    )
}

@HiltViewModel
class ClientListViewModel @Inject constructor(
    observeClients: ObserveClientsUseCase,
    observeHerds: ObserveHerdsUseCase,
    observeAnimals: ObserveAnimalsUseCase,
    observeEvents: ObserveHealthEventsUseCase,
    private val refreshClients: RefreshClientsUseCase,
    private val refreshLivestock: RefreshLivestockUseCase,
    private val refreshSanitary: RefreshSanitaryUseCase,
    private val removeClient: RemoveClientUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(ClientListLocal())

    val state: StateFlow<ClientListUiState> = combine(
        observeClients(), observeHerds(), observeAnimals(), observeEvents(), local,
    ) { clients, herds, animals, events, local ->
        ClientListUiState(
            items = summarize(clients, herds, animals, events),
            pendingRemove = local.pendingRemove,
            refreshing = local.refreshing,
            messageRes = local.messageRes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientListUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            val result = refreshAll()
            local.update { it.copy(refreshing = false, messageRes = (result as? AppResult.Failure)?.error?.messageRes()) }
        }
    }

    private suspend fun refreshAll(): AppResult<Unit> {
        val clients = refreshClients()
        if (clients is AppResult.Failure) return clients
        // Herds, animals and records are scoped by the linked clients, so they are refreshed in this order.
        val livestock = refreshLivestock()
        if (livestock is AppResult.Failure) return livestock
        return refreshSanitary()
    }

    fun requestRemove(item: ClientSummary) = local.update { it.copy(pendingRemove = item) }
    fun dismissRemove() = local.update { it.copy(pendingRemove = null) }
    fun onMessageShown() = local.update { it.copy(messageRes = null) }

    fun confirmRemove() {
        val item = local.value.pendingRemove ?: return
        local.update { it.copy(pendingRemove = null) }
        viewModelScope.launch {
            when (val result = removeClient(item.client.rancherId)) {
                is AppResult.Failure -> local.update { it.copy(messageRes = result.error.messageRes()) }
                // The client's herds, animals and records leave the veterinarian's view.
                is AppResult.Success -> refreshAll()
            }
        }
    }
}

data class AddClientUiState(
    val ranchers: List<AvailableRancher> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val addingId: Int? = null,
    @StringRes val messageRes: Int? = null,
) {
    val visible: List<AvailableRancher> get() = ranchers.search(query)
}

@HiltViewModel
class AddClientViewModel @Inject constructor(
    private val listAvailable: ListAvailableRanchersUseCase,
    private val addClient: AddClientUseCase,
    private val refreshLivestock: RefreshLivestockUseCase,
    private val refreshSanitary: RefreshSanitaryUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AddClientUiState())
    val state: StateFlow<AddClientUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val result = listAvailable()) {
                is AppResult.Success -> _state.update { it.copy(ranchers = result.value, loading = false) }
                is AppResult.Failure -> _state.update { it.copy(loading = false, messageRes = result.error.messageRes()) }
            }
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }
    fun onMessageShown() = _state.update { it.copy(messageRes = null) }

    fun add(rancher: AvailableRancher) {
        if (_state.value.addingId != null) return
        _state.update { it.copy(addingId = rancher.id) }
        viewModelScope.launch {
            when (val result = addClient(rancher.id)) {
                is AppResult.Failure -> _state.update { it.copy(addingId = null, messageRes = result.error.messageRes()) }
                is AppResult.Success -> {
                    // Bring in the new client's herds, animals and records, then drop them from the "available" list.
                    refreshLivestock()
                    refreshSanitary()
                    _state.update { it.copy(addingId = null, ranchers = it.ranchers.filterNot { r -> r.id == rancher.id }) }
                }
            }
        }
    }
}
