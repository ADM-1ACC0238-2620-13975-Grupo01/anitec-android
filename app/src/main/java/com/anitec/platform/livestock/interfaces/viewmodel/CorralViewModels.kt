package com.anitec.platform.livestock.interfaces.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anitec.platform.app.CorralFormRoute
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.livestock.application.DeleteCorralUseCase
import com.anitec.platform.livestock.application.ObserveAnimalsUseCase
import com.anitec.platform.livestock.application.ObserveCorralsUseCase
import com.anitec.platform.livestock.application.ObserveHerdsUseCase
import com.anitec.platform.livestock.application.RefreshLivestockUseCase
import com.anitec.platform.livestock.application.SaveCorralUseCase
import com.anitec.platform.livestock.domain.Corral
import com.anitec.platform.livestock.domain.CorralDraft
import com.anitec.platform.livestock.domain.Herd
import com.anitec.platform.livestock.interfaces.ui.livestockMessageRes
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
import javax.inject.Inject

data class CorralItem(val corral: Corral, val herdName: String, val animalCount: Int)

data class CorralListUiState(
    val items: List<CorralItem> = emptyList(),
    val pendingDelete: CorralItem? = null,
    val refreshing: Boolean = false,
    val canEdit: Boolean = false,
    @StringRes val messageRes: Int? = null,
)

private data class CorralListLocal(
    val pendingDelete: CorralItem? = null,
    val refreshing: Boolean = false,
    val messageRes: Int? = null,
)

@HiltViewModel
class CorralListViewModel @Inject constructor(
    observeHerds: ObserveHerdsUseCase,
    observeCorrals: ObserveCorralsUseCase,
    observeAnimals: ObserveAnimalsUseCase,
    private val refreshLivestock: RefreshLivestockUseCase,
    private val deleteCorral: DeleteCorralUseCase,
    sessionStore: SessionStore,
) : ViewModel() {

    private val canEdit = (sessionStore.state.value as? SessionState.SignedIn)?.session?.role == UserRole.Rancher
    private val local = MutableStateFlow(CorralListLocal())

    val state: StateFlow<CorralListUiState> = combine(observeHerds(), observeCorrals(), observeAnimals(), local) { herds, corrals, animals, local ->
        val herdNames = herds.associate { it.id to it.name }
        CorralListUiState(
            items = corrals.map { CorralItem(it, herdNames[it.herdId].orEmpty(), animals.count { animal -> animal.corralId == it.id }) },
            pendingDelete = local.pendingDelete,
            refreshing = local.refreshing,
            canEdit = canEdit,
            messageRes = local.messageRes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CorralListUiState(canEdit = canEdit))

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            val result = refreshLivestock()
            local.update {
                it.copy(refreshing = false, messageRes = (result as? AppResult.Failure)?.error?.livestockMessageRes())
            }
        }
    }

    fun requestDelete(item: CorralItem) = local.update { it.copy(pendingDelete = item) }
    fun dismissDelete() = local.update { it.copy(pendingDelete = null) }
    fun onMessageShown() = local.update { it.copy(messageRes = null) }

    fun confirmDelete() {
        val item = local.value.pendingDelete ?: return
        local.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val result = deleteCorral(item.corral.id)
            if (result is AppResult.Failure) local.update { it.copy(messageRes = result.error.livestockMessageRes()) }
        }
    }
}

data class CorralFormUiState(
    val isEdit: Boolean = false,
    val name: String = "",
    val herdId: Int? = null,
    val herds: List<Herd> = emptyList(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    @StringRes val messageRes: Int? = null,
) {
    val nameMissing get() = showErrors && name.isBlank()
    val herdMissing get() = showErrors && herdId == null
    val isValid get() = name.isNotBlank() && herdId != null
}

@HiltViewModel
class CorralFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeHerds: ObserveHerdsUseCase,
    private val observeCorrals: ObserveCorralsUseCase,
    private val saveCorral: SaveCorralUseCase,
) : ViewModel() {

    private val corralId: Int? = savedStateHandle.toRoute<CorralFormRoute>().corralId

    private val _state = MutableStateFlow(CorralFormUiState(isEdit = corralId != null))
    val state: StateFlow<CorralFormUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeHerds().collect { herds ->
                _state.update { it.copy(herds = herds, herdId = it.herdId ?: herds.firstOrNull()?.id) }
            }
        }
        if (corralId != null) {
            viewModelScope.launch {
                val corral = observeCorrals().first().firstOrNull { it.id == corralId }
                if (corral == null) _state.update { it.copy(done = true) } else _state.update { it.copy(name = corral.name, herdId = corral.herdId) }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onHerdChange(value: Int) = _state.update { it.copy(herdId = value) }
    fun onMessageShown() = _state.update { it.copy(messageRes = null) }

    fun save() {
        val current = _state.value
        if (current.saving) return
        if (!current.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true, messageRes = null) }
        viewModelScope.launch {
            _state.update {
                when (val result = saveCorral(corralId, CorralDraft(current.name.trim(), current.herdId!!))) {
                    is AppResult.Success -> it.copy(saving = false, done = true)
                    is AppResult.Failure -> it.copy(saving = false, messageRes = result.error.livestockMessageRes())
                }
            }
        }
    }
}
