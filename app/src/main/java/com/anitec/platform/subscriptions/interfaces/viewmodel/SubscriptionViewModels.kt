package com.anitec.platform.subscriptions.interfaces.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anitec.platform.R
import com.anitec.platform.app.SubscriptionResultRoute
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.subscriptions.application.CheckoutReturns
import com.anitec.platform.subscriptions.application.ConfirmCheckoutUseCase
import com.anitec.platform.subscriptions.application.GetActiveSubscriptionUseCase
import com.anitec.platform.subscriptions.application.GetPaymentsUseCase
import com.anitec.platform.subscriptions.application.GetPlansUseCase
import com.anitec.platform.subscriptions.application.StartCheckoutUseCase
import com.anitec.platform.subscriptions.domain.CheckoutResult
import com.anitec.platform.subscriptions.domain.CheckoutReturn
import com.anitec.platform.subscriptions.domain.Payment
import com.anitec.platform.subscriptions.domain.Plan
import com.anitec.platform.subscriptions.domain.PlanAudience
import com.anitec.platform.subscriptions.domain.Subscription
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionsUiState(
    /** The plans this user can choose, cheapest first. */
    val plans: List<Plan> = emptyList(),
    val active: Subscription? = null,
    /** The plan of [active], even when it is no longer offered. */
    val activePlan: Plan? = null,
    val payments: List<Payment> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** The plan whose checkout is being created. */
    val startingPlanId: Int? = null,
    /** A checkout page to open in the browser once; the screen reports back with [SubscriptionsViewModel.onCheckoutOpened]. */
    val checkoutUrl: String? = null,
    @StringRes val messageRes: Int? = null,
)

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val getPlans: GetPlansUseCase,
    private val getActive: GetActiveSubscriptionUseCase,
    private val getPayments: GetPaymentsUseCase,
    private val startCheckout: StartCheckoutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SubscriptionsUiState())
    val state: StateFlow<SubscriptionsUiState> = _state.asStateFlow()

    /** Reads plans, subscription and payments again. The screen calls it each time it is shown, also when the browser returns. */
    fun refresh() {
        val session = (sessionStore.state.value as? SessionState.SignedIn)?.session ?: return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            val plans = getPlans()
            val active = getActive(session.userId)
            val payments = getPayments(session.userId)
            _state.update { current ->
                val allPlans = (plans as? AppResult.Success)?.value ?: current.plans
                val subscription = if (active is AppResult.Success) active.value else current.active
                current.copy(
                    plans = PlanAudience.forRole(allPlans, session.role),
                    active = subscription,
                    activePlan = allPlans.firstOrNull { it.id == subscription?.planId },
                    payments = (payments as? AppResult.Success)?.value ?: current.payments,
                    loading = false,
                    refreshing = false,
                    messageRes = listOf(plans, active, payments).filterIsInstance<AppResult.Failure>().firstOrNull()?.error?.messageRes(),
                )
            }
        }
    }

    fun subscribe(plan: Plan) {
        if (_state.value.startingPlanId != null) return
        _state.update { it.copy(startingPlanId = plan.id) }
        viewModelScope.launch {
            when (val result = startCheckout(plan.id)) {
                is AppResult.Success -> _state.update {
                    // The payment page must be a secure web address; anything else is not opened.
                    if (result.value.checkoutUrl.startsWith("https://")) {
                        it.copy(startingPlanId = null, checkoutUrl = result.value.checkoutUrl)
                    } else {
                        it.copy(startingPlanId = null, messageRes = R.string.error_unknown)
                    }
                }
                is AppResult.Failure -> _state.update { it.copy(startingPlanId = null, messageRes = result.error.messageRes()) }
            }
        }
    }

    fun onCheckoutOpened() = _state.update { it.copy(checkoutUrl = null) }

    /** The device has no browser that can show the checkout. */
    fun onBrowserMissing() = _state.update { it.copy(checkoutUrl = null, messageRes = R.string.subscriptions_no_browser) }

    fun onMessageShown() = _state.update { it.copy(messageRes = null) }
}

/** What the screen after the checkout shows. */
sealed interface CheckoutPhase {
    data object Confirming : CheckoutPhase
    data class Confirmed(val result: CheckoutResult, val planName: String?) : CheckoutPhase
    data class Failed(@StringRes val messageRes: Int) : CheckoutPhase
    data object Cancelled : CheckoutPhase
}

@HiltViewModel
class SubscriptionResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val confirmCheckout: ConfirmCheckoutUseCase,
    private val getPlans: GetPlansUseCase,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<SubscriptionResultRoute>()
    private val _phase = MutableStateFlow<CheckoutPhase>(CheckoutPhase.Confirming)
    val phase: StateFlow<CheckoutPhase> = _phase.asStateFlow()

    init {
        confirm()
    }

    fun confirm() {
        val sessionId = route.sessionId
        when {
            route.cancelled -> _phase.value = CheckoutPhase.Cancelled
            sessionId == null -> _phase.value = CheckoutPhase.Failed(R.string.subscriptions_result_missing)
            else -> {
                _phase.value = CheckoutPhase.Confirming
                viewModelScope.launch {
                    _phase.value = when (val result = confirmCheckout(sessionId)) {
                        is AppResult.Success -> {
                            val planName = (getPlans() as? AppResult.Success)?.value
                                ?.firstOrNull { it.id == result.value.subscription.planId }?.name
                            CheckoutPhase.Confirmed(result.value, planName)
                        }
                        // The server answers 400 when Stripe has not completed that checkout.
                        is AppResult.Failure -> CheckoutPhase.Failed(
                            if (result.error is AppError.Validation) R.string.subscriptions_result_not_completed else result.error.messageRes(),
                        )
                    }
                }
            }
        }
    }
}

/** Lets the navigation react to the link that brought the user back from the browser. */
@HiltViewModel
class CheckoutReturnViewModel @Inject constructor(
    private val returns: CheckoutReturns,
) : ViewModel() {
    val pending: StateFlow<CheckoutReturn?> = returns.pending

    fun consume() = returns.consume()
}
