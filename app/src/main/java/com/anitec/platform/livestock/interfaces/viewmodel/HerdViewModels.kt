package com.anitec.platform.livestock.interfaces.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anitec.platform.R
import com.anitec.platform.app.HerdFormRoute
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.livestock.application.DeleteHerdUseCase
import com.anitec.platform.livestock.application.ObserveAnimalsUseCase
import com.anitec.platform.livestock.application.ObserveCorralsUseCase
import com.anitec.platform.livestock.application.ObserveHerdsUseCase
import com.anitec.platform.livestock.application.RefreshLivestockUseCase
import com.anitec.platform.livestock.application.SaveHerdUseCase
import com.anitec.platform.livestock.domain.Herd
import com.anitec.platform.livestock.domain.HerdDraft
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

data class HerdItem(val herd: Herd, val corralCount: Int, val animalCount: Int)

data class HerdListUiState(
    val items: List<HerdItem> = emptyList(),
    val pendingDelete: Herd? = null,
    val refreshing: Boolean = false,
    @StringRes val messageRes: Int? = null,
)

private data class HerdListLocal(
    val pendingDelete: Herd? = null,
    val refreshing: Boolean = false,
    val messageRes: Int? = null,
)

@HiltViewModel
class HerdListViewModel @Inject constructor(
    observeHerds: ObserveHerdsUseCase,
    observeCorrals: ObserveCorralsUseCase,
    observeAnimals: ObserveAnimalsUseCase,
    private val refreshLivestock: RefreshLivestockUseCase,
    private val deleteHerd: DeleteHerdUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(HerdListLocal())

    val state: StateFlow<HerdListUiState> = combine(observeHerds(), observeCorrals(), observeAnimals(), local) { herds, corrals, animals, local ->
        HerdListUiState(
            items = herds.map { herd ->
                HerdItem(herd, corrals.count { it.herdId == herd.id }, animals.count { it.herdId == herd.id })
            },
            pendingDelete = local.pendingDelete,
            refreshing = local.refreshing,
            messageRes = local.messageRes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HerdListUiState())

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

    /** The API refuses to delete a farm that still has corrals (and would fail with a server error), so it is checked first. */
    fun requestDelete(item: HerdItem) = local.update {
        if (item.corralCount > 0 || item.animalCount > 0) {
            it.copy(messageRes = R.string.herd_delete_blocked)
        } else {
            it.copy(pendingDelete = item.herd)
        }
    }

    fun dismissDelete() = local.update { it.copy(pendingDelete = null) }

    fun onMessageShown() = local.update { it.copy(messageRes = null) }

    fun confirmDelete() {
        val herd = local.value.pendingDelete ?: return
        local.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val result = deleteHerd(herd.id)
            if (result is AppResult.Failure) local.update { it.copy(messageRes = result.error.livestockMessageRes()) }
        }
    }
}

data class HerdFormUiState(
    val isEdit: Boolean = false,
    val name: String = "",
    val location: String = "",
    val mainType: String = "Mixto",
    val owner: String = "",
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    @StringRes val messageRes: Int? = null,
) {
    val nameMissing get() = showErrors && name.isBlank()
    val locationMissing get() = showErrors && location.isBlank()
    val ownerMissing get() = showErrors && owner.isBlank()
    val isValid get() = name.isNotBlank() && location.isNotBlank() && owner.isNotBlank()
}

@HiltViewModel
class HerdFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeHerds: ObserveHerdsUseCase,
    private val saveHerd: SaveHerdUseCase,
    sessionStore: SessionStore,
) : ViewModel() {

    private val herdId: Int? = savedStateHandle.toRoute<HerdFormRoute>().herdId
    private val session: UserSession? = (sessionStore.state.value as? SessionState.SignedIn)?.session

    // Editing keeps the veterinarian already linked to the farm; the app has no way to choose one.
    private var existing: Herd? = null

    private val _state = MutableStateFlow(HerdFormUiState(isEdit = herdId != null, owner = session?.fullName.orEmpty()))
    val state: StateFlow<HerdFormUiState> = _state.asStateFlow()

    init {
        if (herdId != null) {
            viewModelScope.launch {
                val herd = observeHerds().first().firstOrNull { it.id == herdId }
                if (herd == null) {
                    _state.update { it.copy(done = true) }
                } else {
                    existing = herd
                    _state.update { it.copy(name = herd.name, location = herd.location, mainType = herd.mainType, owner = herd.owner) }
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onLocationChange(value: String) = _state.update { it.copy(location = value) }
    fun onMainTypeChange(value: String) = _state.update { it.copy(mainType = value) }
    fun onOwnerChange(value: String) = _state.update { it.copy(owner = value) }
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
            val draft = HerdDraft(
                name = current.name.trim(),
                location = current.location.trim(),
                owner = current.owner.trim(),
                ownerId = existing?.ownerId ?: user.userId,
                veterinarianId = existing?.veterinarianId,
                mainType = current.mainType,
            )
            _state.update {
                when (val result = saveHerd(herdId, draft)) {
                    is AppResult.Success -> it.copy(saving = false, done = true)
                    is AppResult.Failure -> it.copy(saving = false, messageRes = result.error.livestockMessageRes())
                }
            }
        }
    }
}
