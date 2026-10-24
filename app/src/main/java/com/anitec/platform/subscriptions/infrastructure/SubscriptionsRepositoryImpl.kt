package com.anitec.platform.subscriptions.infrastructure

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.map
import com.anitec.platform.core.network.safeApiCall
import com.anitec.platform.subscriptions.domain.CheckoutResult
import com.anitec.platform.subscriptions.domain.CheckoutSession
import com.anitec.platform.subscriptions.domain.Payment
import com.anitec.platform.subscriptions.domain.Plan
import com.anitec.platform.subscriptions.domain.Subscription
import com.anitec.platform.subscriptions.domain.SubscriptionsRepository
import com.anitec.platform.subscriptions.infrastructure.remote.CheckoutResultDto
import com.anitec.platform.subscriptions.infrastructure.remote.CheckoutSessionDto
import com.anitec.platform.subscriptions.infrastructure.remote.PaymentDto
import com.anitec.platform.subscriptions.infrastructure.remote.PlanDto
import com.anitec.platform.subscriptions.infrastructure.remote.StripeCheckoutRequestDto
import com.anitec.platform.subscriptions.infrastructure.remote.SubscriptionDto
import com.anitec.platform.subscriptions.infrastructure.remote.SubscriptionsApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

fun PlanDto.toDomain() = Plan(id, name, price, maxAnimals, isActive)
fun SubscriptionDto.toDomain() = Subscription(id, userId, planId, status, startedAt, endsAt)
fun PaymentDto.toDomain() = Payment(id, amount, currency, provider, status, paidAt)
fun CheckoutSessionDto.toDomain() = CheckoutSession(sessionId, checkoutUrl)
fun CheckoutResultDto.toDomain() = CheckoutResult(subscription.toDomain(), payment.toDomain())

/** Nothing here is cached: plans, the subscription and the payments are read from the server each time they are shown. */
@Singleton
class SubscriptionsRepositoryImpl @Inject constructor(
    private val api: SubscriptionsApi,
) : SubscriptionsRepository {

    override suspend fun plans(): AppResult<List<Plan>> = safeApiCall { api.getPlans().map { it.toDomain() } }

    /** The API answers 404 when the user has no active subscription, which is a normal state, not an error. */
    override suspend fun activeSubscription(userId: Int): AppResult<Subscription?> =
        when (val result = safeApiCall { api.getActive(userId).toDomain() }) {
            is AppResult.Success -> AppResult.Success(result.value)
            is AppResult.Failure -> if (result.error == AppError.NotFound) AppResult.Success(null) else result
        }

    override suspend fun payments(userId: Int): AppResult<List<Payment>> =
        safeApiCall { api.getPayments(userId).map { it.toDomain() } }.map { list -> list.sortedByDescending { it.paidAt } }

    override suspend fun startCheckout(planId: Int): AppResult<CheckoutSession> =
        safeApiCall { api.startCheckout(StripeCheckoutRequestDto(planId)).toDomain() }

    override suspend fun confirmCheckout(sessionId: String): AppResult<CheckoutResult> =
        safeApiCall { api.confirm(sessionId).toDomain() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SubscriptionsBindingsModule {
    @Binds
    abstract fun bindSubscriptionsRepository(impl: SubscriptionsRepositoryImpl): SubscriptionsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object SubscriptionsApiModule {
    @Provides
    @Singleton
    fun provideSubscriptionsApi(retrofit: Retrofit): SubscriptionsApi = retrofit.create(SubscriptionsApi::class.java)
}
