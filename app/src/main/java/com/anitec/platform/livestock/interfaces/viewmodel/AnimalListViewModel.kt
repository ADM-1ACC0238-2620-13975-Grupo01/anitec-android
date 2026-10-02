package com.anitec.platform.livestock.interfaces.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anitec.platform.R
import com.anitec.platform.app.AnimalsRoute
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.livestock.application.ChangeAnimalsStatusUseCase
import com.anitec.platform.livestock.application.DeleteAnimalUseCase
import com.anitec.platform.livestock.application.DeleteAnimalsUseCase
import com.anitec.platform.livestock.application.ObserveAnimalsUseCase
import com.anitec.platform.livestock.application.ObserveCorralsUseCase
import com.anitec.platform.livestock.application.ObserveHerdsUseCase
import com.anitec.platform.livestock.application.RefreshLivestockUseCase
import com.anitec.platform.livestock.domain.AnimalStatus
import com.anitec.platform.livestock.domain.AnimalView
import com.anitec.platform.livestock.domain.Corral
import com.anitec.platform.livestock.domain.filterBy
import com.anitec.platform.livestock.interfaces.ui.livestockMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A confirmation the user still has to answer. */
sealed interface PendingAction {
    data class DeleteOne(val animalId: Int, val name: String) : PendingAction
    data object DeleteSelected : PendingAction
    data object PickStatus : PendingAction
    data class ChangeStatus(val status: AnimalStatus) : PendingAction
}

data class AnimalListUiState(
    val items: List<AnimalView> = emptyList(),
    val totalCount: Int = 0,
    val corrals: List<Corral> = emptyList(),
    val query: String = "",
    val corralFilter: Int? = null,
    val selectedIds: Set<Int> = emptySet(),
    val detail: AnimalView? = null,
    val pending: PendingAction? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val canEdit: Boolean = false,
    @StringRes val messageRes: Int? = null,
) {
    val selectionMode: Boolean get() = selectedIds.isNotEmpty()
}

private data class LocalState(
    val query: String = "",
    val corralFilter: Int? = null,
    val selectedIds: Set<Int> = emptySet(),
    val detailId: Int? = null,
    val pending: PendingAction? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val messageRes: Int? = null,
)

@HiltViewModel
class AnimalListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAnimals: ObserveAnimalsUseCase,
    observeHerds: ObserveHerdsUseCase,
    observeCorrals: ObserveCorralsUseCase,
    private val refreshLivestock: RefreshLivestockUseCase,
    private val deleteAnimal: DeleteAnimalUseCase,
    private val changeStatus: ChangeAnimalsStatusUseCase,
    private val deleteAnimals: DeleteAnimalsUseCase,
    sessionStore: SessionStore,
) : ViewModel() {

    // Only ranchers edit livestock; veterinarians read it.
    private val canEdit = (sessionStore.state.value as? SessionState.SignedIn)?.session?.role == UserRole.Rancher

    // The scanner can open the list straight on one animal's record.
    private val local = MutableStateFlow(LocalState(detailId = savedStateHandle.toRoute<AnimalsRoute>().openAnimalId))

    val state: StateFlow<AnimalListUiState> = combine(
        observeAnimals(), observeHerds(), observeCorrals(), local,
    ) { animals, herds, corrals, local ->
        val herdNames = herds.associate { it.id to it.name }
        val corralNames = corrals.associate { it.id to it.name }
        val all = animals.map { AnimalView(it, herdNames[it.herdId].orEmpty(), it.corralId?.let(corralNames::get)) }
        val existingIds = all.map { it.animal.id }.toSet()
        AnimalListUiState(
            items = all.filterBy(local.query, local.corralFilter),
            totalCount = all.size,
            corrals = corrals,
            query = local.query,
            corralFilter = local.corralFilter,
            selectedIds = local.selectedIds.intersect(existingIds),
            detail = local.detailId?.let { id -> all.firstOrNull { it.animal.id == id } },
            pending = local.pending,
            // Cached data is shown at once; the spinner only covers the very first load.
            loading = local.loading && all.isEmpty(),
            refreshing = local.refreshing,
            canEdit = canEdit,
            messageRes = local.messageRes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnimalListUiState(canEdit = canEdit))

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            val result = refreshLivestock()
            local.update {
                it.copy(
                    refreshing = false,
                    loading = false,
                    messageRes = (result as? AppResult.Failure)?.error?.livestockMessageRes() ?: it.messageRes,
                )
            }
        }
    }

    fun onQueryChange(value: String) = local.update { it.copy(query = value) }

    fun onCorralFilterChange(corralId: Int?) = local.update { it.copy(corralFilter = corralId) }

    fun toggleSelection(animalId: Int) = local.update {
        val next = if (animalId in it.selectedIds) it.selectedIds - animalId else it.selectedIds + animalId
        it.copy(selectedIds = next)
    }

    fun selectAllVisible() = local.update { it.copy(selectedIds = state.value.items.map { view -> view.animal.id }.toSet()) }

    fun clearSelection() = local.update { it.copy(selectedIds = emptySet()) }

    fun openDetail(animalId: Int) = local.update { it.copy(detailId = animalId) }

    fun closeDetail() = local.update { it.copy(detailId = null) }

    fun requestDelete(view: AnimalView) = local.update {
        it.copy(detailId = null, pending = PendingAction.DeleteOne(view.animal.id, view.animal.name))
    }

    fun requestDeleteSelected() = local.update { it.copy(pending = PendingAction.DeleteSelected) }

    fun requestStatusChange() = local.update { it.copy(pending = PendingAction.PickStatus) }

    fun onStatusPicked(status: AnimalStatus) = local.update { it.copy(pending = PendingAction.ChangeStatus(status)) }

    fun dismissPending() = local.update { it.copy(pending = null) }

    fun onMessageShown() = local.update { it.copy(messageRes = null) }

    fun confirmPending() {
        val action = local.value.pending ?: return
        val selected = local.value.selectedIds
        local.update { it.copy(pending = null) }
        viewModelScope.launch {
            val result: AppResult<*> = when (action) {
                is PendingAction.DeleteOne -> deleteAnimal(action.animalId)
                PendingAction.DeleteSelected -> deleteAnimals(selected)
                is PendingAction.ChangeStatus -> changeStatus(selected, action.status.apiValue)
                PendingAction.PickStatus -> return@launch
            }
            local.update {
                when (result) {
                    is AppResult.Success<*> -> it.copy(
                        selectedIds = if (action is PendingAction.DeleteOne) it.selectedIds else emptySet(),
                        messageRes = R.string.animals_saved,
                    )
                    is AppResult.Failure -> it.copy(messageRes = result.error.livestockMessageRes())
                }
            }
        }
    }
}
