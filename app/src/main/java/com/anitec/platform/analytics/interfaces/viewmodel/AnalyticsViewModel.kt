package com.anitec.platform.analytics.interfaces.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitec.platform.analytics.domain.AnalyticsCalculator
import com.anitec.platform.analytics.domain.AnalyticsSnapshot
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.financial.application.ObserveFinancialRecordsUseCase
import com.anitec.platform.financial.application.RefreshFinancialUseCase
import com.anitec.platform.financial.domain.FinancialScope
import com.anitec.platform.livestock.application.ObserveAnimalsUseCase
import com.anitec.platform.livestock.application.ObserveHerdsUseCase
import com.anitec.platform.livestock.application.RefreshLivestockUseCase
import com.anitec.platform.sanitary.application.ObserveHealthEventsUseCase
import com.anitec.platform.sanitary.application.RefreshSanitaryUseCase
import com.anitec.platform.veterinary.application.ObserveClientsUseCase
import com.anitec.platform.veterinary.application.RefreshClientsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyticsUiState(
    val role: UserRole = UserRole.Rancher,
    val snapshot: AnalyticsSnapshot = AnalyticsSnapshot(),
    val clientCount: Int = 0,
    /** Income minus expenses; only a rancher has finances. */
    val balance: Double = 0.0,
    val refreshing: Boolean = false,
    @StringRes val messageRes: Int? = null,
)

private data class AnalyticsLocal(val refreshing: Boolean = false, val messageRes: Int? = null)

private data class AnalyticsInputs(
    val herds: List<com.anitec.platform.livestock.domain.Herd>,
    val animals: List<com.anitec.platform.livestock.domain.Animal>,
    val events: List<com.anitec.platform.sanitary.domain.HealthEvent>,
    val clients: Int,
    val balance: Double,
)

/**
 * Indicators and charts of the signed-in user. The server also offers dashboard endpoints, but they are not
 * scoped to the user, so everything is computed here from the user's own cache.
 */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    observeHerds: ObserveHerdsUseCase,
    observeAnimals: ObserveAnimalsUseCase,
    observeEvents: ObserveHealthEventsUseCase,
    observeClients: ObserveClientsUseCase,
    observeFinancial: ObserveFinancialRecordsUseCase,
    private val refreshLivestock: RefreshLivestockUseCase,
    private val refreshSanitary: RefreshSanitaryUseCase,
    private val refreshClients: RefreshClientsUseCase,
    private val refreshFinancial: RefreshFinancialUseCase,
    sessionStore: SessionStore,
) : ViewModel() {

    private val role = (sessionStore.state.value as? SessionState.SignedIn)?.session?.role ?: UserRole.Rancher
    private val local = MutableStateFlow(AnalyticsLocal())

    val state: StateFlow<AnalyticsUiState> = combine(
        combine(observeHerds(), observeAnimals(), observeEvents(), observeClients(), observeFinancial()) { herds, animals, events, clients, records ->
            AnalyticsInputs(herds, animals, events, clients.size, FinancialScope.summarize(records).balance)
        },
        local,
    ) { inputs, local ->
        AnalyticsUiState(
            role = role,
            snapshot = AnalyticsCalculator.compute(inputs.herds, inputs.animals, inputs.events),
            clientCount = inputs.clients,
            balance = inputs.balance,
            refreshing = local.refreshing,
            messageRes = local.messageRes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState(role = role))

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            var result: AppResult<Unit> = AppResult.Success(Unit)
            // Clients first for a veterinarian, then livestock and records, which are scoped by them.
            if (role == UserRole.Veterinarian) result = refreshClients()
            if (result is AppResult.Success) result = refreshLivestock()
            if (result is AppResult.Success) result = refreshSanitary()
            // Finance is rancher-only; asking as a veterinarian would be refused.
            if (result is AppResult.Success && role == UserRole.Rancher) result = refreshFinancial()
            local.update { it.copy(refreshing = false, messageRes = (result as? AppResult.Failure)?.error?.messageRes()) }
        }
    }

    fun onMessageShown() = local.update { it.copy(messageRes = null) }
}
