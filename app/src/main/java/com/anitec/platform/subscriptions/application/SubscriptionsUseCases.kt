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

/** Plans offered by the server. */
class GetPlansUseCase @Inject constructor(private val repository: SubscriptionsRepository) {
    suspend operator fun invoke(): AppResult<List<Plan>> = repository.plans()
}

/** Current subscription of the user, or null when they have none. */
class GetActiveSubscriptionUseCase @Inject constructor(private val repository: SubscriptionsRepository) {
    suspend operator fun invoke(userId: Int): AppResult<Subscription?> = repository.activeSubscription(userId)
}

/** Payments of the user, newest first. */
class GetPaymentsUseCase @Inject constructor(private val repository: SubscriptionsRepository) {
    suspend operator fun invoke(userId: Int): AppResult<List<Payment>> = repository.payments(userId)
}

/** Opens a Stripe checkout session for [planId] and returns the URL to show. */
class StartCheckoutUseCase @Inject constructor(private val repository: SubscriptionsRepository) {
    suspend operator fun invoke(planId: Int): AppResult<CheckoutSession> = repository.startCheckout(planId)
}

/** Confirms a finished checkout and returns the new subscription and its payment. */
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

    /** Marks the pending return as shown. */
    fun consume() {
        _pending.value = null
    }
}
