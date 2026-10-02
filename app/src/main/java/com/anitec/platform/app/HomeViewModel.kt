package com.anitec.platform.app

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.livestock.application.ObserveAnimalsUseCase
import com.anitec.platform.livestock.application.ObserveHerdsUseCase
import com.anitec.platform.livestock.application.RefreshLivestockUseCase
import com.anitec.platform.livestock.domain.Herd
import com.anitec.platform.sanitary.application.ObserveHealthEventsUseCase
import com.anitec.platform.sanitary.application.RefreshSanitaryUseCase
import com.anitec.platform.sanitary.interfaces.viewmodel.HealthItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val herds: List<Herd> = emptyList(),
    val selectedHerdId: Int? = null,
    val animalCount: Int = 0,
    val attentionCount: Int = 0,
    val recordCount: Int = 0,
    val followUpCount: Int = 0,
    val recent: List<HealthItem> = emptyList(),
    val refreshing: Boolean = false,
    @StringRes val messageRes: Int? = null,
)

private data class HomeLocal(
    val selectedHerdId: Int? = null,
    val refreshing: Boolean = false,
    val messageRes: Int? = null,
)

/** Dashboard of the rancher: counters and recent records for all farms or one selected farm. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    observeHerds: ObserveHerdsUseCase,
    observeAnimals: ObserveAnimalsUseCase,
    observeEvents: ObserveHealthEventsUseCase,
    private val refreshLivestock: RefreshLivestockUseCase,
    private val refreshSanitary: RefreshSanitaryUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(HomeLocal())

    val state: StateFlow<HomeUiState> = combine(observeHerds(), observeAnimals(), observeEvents(), local) { herds, animals, events, local ->
        val selected = local.selectedHerdId?.takeIf { id -> herds.any { it.id == id } }
        val scopedAnimals = animals.filter { selected == null || it.herdId == selected }
        val animalIds = scopedAnimals.map { it.id }.toSet()
        val names = animals.associate { it.id to it.name }
        val scopedEvents = events.filter { it.animalId in animalIds }
        HomeUiState(
            herds = herds,
            selectedHerdId = selected,
            animalCount = scopedAnimals.size,
            attentionCount = scopedAnimals.count { it.healthStatus.needsAttention },
            recordCount = scopedEvents.size,
            followUpCount = scopedEvents.count { it.hasFollowUp },
            recent = scopedEvents.take(4).map { HealthItem(it, names[it.animalId].orEmpty()) },
            refreshing = local.refreshing,
            messageRes = local.messageRes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            val result = when (val livestock = refreshLivestock()) {
                is AppResult.Failure -> livestock
                is AppResult.Success -> refreshSanitary()
            }
            local.update { it.copy(refreshing = false, messageRes = (result as? AppResult.Failure)?.error?.messageRes()) }
        }
    }

    fun onHerdSelected(herdId: Int?) = local.update { it.copy(selectedHerdId = herdId) }
    fun onMessageShown() = local.update { it.copy(messageRes = null) }
}
