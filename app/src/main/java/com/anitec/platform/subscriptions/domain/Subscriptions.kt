package com.anitec.platform.subscriptions.domain

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.UserRole
import java.net.URI

/** A monthly plan. [maxAnimals] is how many animals the plan allows. */
data class Plan(
    val id: Int,
    val name: String,
    val price: Double,
    val maxAnimals: Int,
    val isActive: Boolean,
)

/** The plan a user pays for. Dates are ISO (yyyy-MM-dd); [endsAt] is null while it has no end. */
data class Subscription(
    val id: Int,
    val userId: Int,
    val planId: Int,
    val status: String,
    val startedAt: String,
    val endsAt: String?,
) {
    val isActive get() = status.equals(SubscriptionStatuses.ACTIVE, ignoreCase = true)
}

object SubscriptionStatuses {
    const val ACTIVE = "Active"
}

/** One charge. [paidAt] is the API date-time (`2026-10-20T15:30:00`). */
data class Payment(
    val id: Int,
    val amount: Double,
    val currency: String,
    val provider: String,
    val status: String,
    val paidAt: String,
) {
    val isPaid get() = status.equals("Paid", ignoreCase = true)
}

/** A Stripe checkout the user has to complete in the browser. */
data class CheckoutSession(val sessionId: String, val checkoutUrl: String)

/** What the server created once Stripe confirmed the payment. */
data class CheckoutResult(val subscription: Subscription, val payment: Payment)

object PlanAudience {
    private const val RANCHER_PREFIX = "Ganadero"
    private const val VETERINARIAN_PREFIX = "Veterinario"

    /**
     * The plans a user can choose. The API does not say which role a plan is for, so the plan name does
     * (the landing page lists them the same way): "Ganadero ..." for ranchers and "Veterinario ..." for
     * veterinarians. A plan with any other name is offered to both, and inactive plans are never offered.
     */
    fun forRole(plans: List<Plan>, role: UserRole): List<Plan> = plans.filter { plan ->
        plan.isActive && when {
            plan.name.startsWith(RANCHER_PREFIX, ignoreCase = true) -> role == UserRole.Rancher
            plan.name.startsWith(VETERINARIAN_PREFIX, ignoreCase = true) -> role == UserRole.Veterinarian
            else -> true
        }
    }.sortedBy { it.price }
}

/** How the browser sends the user back to the app after a Stripe checkout. */
sealed interface CheckoutReturn {
    /** The user paid. [sessionId] is the checkout to confirm with the server. */
    data class Success(val sessionId: String?) : CheckoutReturn

    /** The user left the checkout without paying. */
    data object Cancelled : CheckoutReturn
}

object CheckoutReturnParser {
    private const val SCHEME = "anitec"
    private const val HOST = "subscriptions"
    private val sessionIdPattern = Regex("^[A-Za-z0-9_]{1,200}$")

    /**
     * Reads `anitec://subscriptions/success?session_id=cs_test_...` and `anitec://subscriptions/cancel`.
     * Any other link, and a session id with unexpected characters, is not trusted and gives null or no id.
     */
    fun parse(link: String?): CheckoutReturn? {
        val uri = runCatching { URI(link ?: return null) }.getOrNull() ?: return null
        if (!SCHEME.equals(uri.scheme, ignoreCase = true) || !HOST.equals(uri.host, ignoreCase = true)) return null
        return when (uri.path?.trim('/')) {
            "success" -> CheckoutReturn.Success(sessionId(uri.rawQuery))
            "cancel" -> CheckoutReturn.Cancelled
            else -> null
        }
    }

    private fun sessionId(query: String?): String? = query?.split('&')
        ?.map { it.split('=', limit = 2) }
        ?.firstOrNull { it.size == 2 && it[0] == "session_id" }
        ?.get(1)
        ?.takeIf { sessionIdPattern.matches(it) }
}

interface SubscriptionsRepository {
    suspend fun plans(): AppResult<List<Plan>>

    /** The user's active subscription, or null when there is none. */
    suspend fun activeSubscription(userId: Int): AppResult<Subscription?>

    suspend fun payments(userId: Int): AppResult<List<Payment>>

    /** Opens a Stripe checkout for [planId]; the user pays in the browser. */
    suspend fun startCheckout(planId: Int): AppResult<CheckoutSession>

    /** Asks the server to check the checkout with Stripe and create the subscription. */
    suspend fun confirmCheckout(sessionId: String): AppResult<CheckoutResult>
}
