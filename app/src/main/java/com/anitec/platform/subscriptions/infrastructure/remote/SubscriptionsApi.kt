package com.anitec.platform.subscriptions.infrastructure.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class PlanDto(
    val id: Int = 0,
    val name: String = "",
    val price: Double = 0.0,
    val maxAnimals: Int = 0,
    val isActive: Boolean = false,
)

@Serializable
data class SubscriptionDto(
    val id: Int = 0,
    val userId: Int = 0,
    val planId: Int = 0,
    val status: String = "",
    val startedAt: String = "",
    val endsAt: String? = null,
)

@Serializable
data class PaymentDto(
    val id: Int = 0,
    val amount: Double = 0.0,
    val currency: String = "",
    val provider: String = "",
    val status: String = "",
    val paidAt: String = "",
)

/** `platform = "android"` makes Stripe return to the API page that reopens the app. */
@Serializable
data class StripeCheckoutRequestDto(val planId: Int, val platform: String = "android")

@Serializable
data class CheckoutSessionDto(val sessionId: String = "", val checkoutUrl: String = "")

@Serializable
data class CheckoutResultDto(val subscription: SubscriptionDto = SubscriptionDto(), val payment: PaymentDto = PaymentDto())

interface SubscriptionsApi {
    @GET("subscription-plans") suspend fun getPlans(): List<PlanDto>
    @GET("subscriptions/users/{userId}/active") suspend fun getActive(@Path("userId") userId: Int): SubscriptionDto
    @GET("subscriptions/users/{userId}/payments") suspend fun getPayments(@Path("userId") userId: Int): List<PaymentDto>
    @POST("subscriptions/stripe-checkout") suspend fun startCheckout(@Body body: StripeCheckoutRequestDto): CheckoutSessionDto
    @GET("subscriptions/stripe-checkout/{sessionId}/confirm") suspend fun confirm(@Path("sessionId") sessionId: String): CheckoutResultDto
}
