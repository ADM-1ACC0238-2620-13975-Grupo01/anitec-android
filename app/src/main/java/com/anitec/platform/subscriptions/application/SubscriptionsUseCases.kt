package com.anitec.platform.subscriptions.application

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.subscriptions.domain.CheckoutResult
import com.anitec.platform.subscriptions.domain.CheckoutReturn
import com.anitec.platform.subscriptions.domain.CheckoutReturnParser
import com.anitec.platform.subscriptions.domain.CheckoutSession
import com.anitec.platform.subscriptions.domain.Payment
import com.anitec.platform.subscriptions.domain.Plan
import com.anitec.platform.subscriptions.domain.Subscription
import com.anitec.platform.subscriptions.domain.SubscriptionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

class GetPlansUseCase @Inject constructor(private val repository: SubscriptionsRepository) {
    suspend operator fun invoke(): AppResult<List<Plan>> = repository.plans()
}

class GetActiveSubscriptionUseCase @Inject constructor(private val repository: SubscriptionsRepository) {
    suspend operator fun invoke(userId: Int): AppResult<Subscription?> = repository.activeSubscription(userId)
}

class GetPaymentsUseCase @Inject constructor(private val repository: SubscriptionsRepository) {
    suspend operator fun invoke(userId: Int): AppResult<List<Payment>> = repository.payments(userId)
}

class StartCheckoutUseCase @Inject constructor(private val repository: SubscriptionsRepository) {
    suspend operator fun invoke(planId: Int): AppResult<CheckoutSession> = repository.startCheckout(planId)
}

class ConfirmCheckoutUseCase @Inject constructor(private val repository: SubscriptionsRepository) {
    suspend operator fun invoke(sessionId: String): AppResult<CheckoutResult> = repository.confirmCheckout(sessionId)
}

/**
 * Holds the link that brought the user back from the Stripe checkout until the navigation consumes it.
 * The Activity feeds it, whether the app was running or had to start for the link.
 */
@Singleton
class CheckoutReturns @Inject constructor() {
    private val _pending = MutableStateFlow<CheckoutReturn?>(null)

    /** The return that has not been shown yet, or null. */
    val pending: StateFlow<CheckoutReturn?> = _pending.asStateFlow()

    /** Keeps the return described by [link]; links that are not a checkout return are ignored. */
    fun onLink(link: String?) {
        CheckoutReturnParser.parse(link)?.let { _pending.value = it }
    }

    fun consume() {
        _pending.value = null
    }
}
