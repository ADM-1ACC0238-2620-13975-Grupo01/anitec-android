package com.anitec.platform.subscriptions

import com.anitec.platform.R
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.subscriptions.application.CheckoutReturns
import com.anitec.platform.subscriptions.application.GetActiveSubscriptionUseCase
import com.anitec.platform.subscriptions.application.GetPaymentsUseCase
import com.anitec.platform.subscriptions.application.GetPlansUseCase
import com.anitec.platform.subscriptions.application.StartCheckoutUseCase
import com.anitec.platform.subscriptions.domain.CheckoutReturn
import com.anitec.platform.subscriptions.domain.CheckoutReturnParser
import com.anitec.platform.subscriptions.domain.CheckoutSession
import com.anitec.platform.subscriptions.domain.Payment
import com.anitec.platform.subscriptions.domain.Plan
import com.anitec.platform.subscriptions.domain.PlanAudience
import com.anitec.platform.subscriptions.domain.Subscription
import com.anitec.platform.subscriptions.domain.SubscriptionsRepository
import com.anitec.platform.subscriptions.infrastructure.SubscriptionsRepositoryImpl
import com.anitec.platform.subscriptions.infrastructure.remote.CheckoutResultDto
import com.anitec.platform.subscriptions.infrastructure.remote.CheckoutSessionDto
import com.anitec.platform.subscriptions.infrastructure.remote.PaymentDto
import com.anitec.platform.subscriptions.infrastructure.remote.PlanDto
import com.anitec.platform.subscriptions.infrastructure.remote.StripeCheckoutRequestDto
import com.anitec.platform.subscriptions.infrastructure.remote.SubscriptionDto
import com.anitec.platform.subscriptions.infrastructure.remote.SubscriptionsApi
import com.anitec.platform.subscriptions.interfaces.viewmodel.SubscriptionsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

private fun plan(id: Int, name: String, price: Double, active: Boolean = true) = Plan(id, name, price, maxAnimals = 80, isActive = active)

private val basicRancher = plan(1, "Ganadero Basico", 49.0)
private val proRancher = plan(2, "Ganadero Pro", 99.0)
private val vetPro = plan(3, "Veterinario Profesional", 129.0)

class PlanAudienceTest {

    private val all = listOf(vetPro, proRancher, basicRancher)

    @Test
    fun `a rancher is offered the rancher plans, cheapest first`() {
        assertEquals(listOf(1, 2), PlanAudience.forRole(all, UserRole.Rancher).map { it.id })
    }

    @Test
    fun `a veterinarian is offered the veterinarian plan only`() {
        assertEquals(listOf(3), PlanAudience.forRole(all, UserRole.Veterinarian).map { it.id })
    }

    @Test
    fun `a plan with another name is offered to both roles`() {
        val other = plan(4, "Plan Institucional", 10.0)
        assertTrue(other in PlanAudience.forRole(listOf(other), UserRole.Rancher))
        assertTrue(other in PlanAudience.forRole(listOf(other), UserRole.Veterinarian))
    }

    @Test
    fun `inactive plans are never offered`() {
        assertTrue(PlanAudience.forRole(listOf(plan(5, "Ganadero Viejo", 20.0, active = false)), UserRole.Rancher).isEmpty())
    }
}

class CheckoutReturnParserTest {

    @Test
    fun `the success link carries the checkout session`() {
        assertEquals(
            CheckoutReturn.Success("cs_test_a1B2c3"),
            CheckoutReturnParser.parse("anitec://subscriptions/success?session_id=cs_test_a1B2c3"),
        )
    }

    @Test
    fun `the session is read among other parameters and with a trailing slash`() {
        assertEquals(
            CheckoutReturn.Success("cs_live_9"),
            CheckoutReturnParser.parse("anitec://subscriptions/success/?utm=x&session_id=cs_live_9"),
        )
    }

    @Test
    fun `a success link without a usable session still shows the result`() {
        assertEquals(CheckoutReturn.Success(null), CheckoutReturnParser.parse("anitec://subscriptions/success"))
        assertEquals(CheckoutReturn.Success(null), CheckoutReturnParser.parse("anitec://subscriptions/success?session_id="))
    }

    @Test
    fun `a session with unexpected characters is dropped`() {
        assertEquals(CheckoutReturn.Success(null), CheckoutReturnParser.parse("anitec://subscriptions/success?session_id=cs_1%2F..%2Fadmin"))
        assertEquals(CheckoutReturn.Success(null), CheckoutReturnParser.parse("anitec://subscriptions/success?session_id=" + "a".repeat(201)))
    }

    @Test
    fun `the cancel link is recognised`() {
        assertEquals(CheckoutReturn.Cancelled, CheckoutReturnParser.parse("anitec://subscriptions/cancel"))
    }

    @Test
    fun `any other link is ignored`() {
        assertNull(CheckoutReturnParser.parse(null))
        assertNull(CheckoutReturnParser.parse("not a link"))
        assertNull(CheckoutReturnParser.parse("https://subscriptions/success"))
        assertNull(CheckoutReturnParser.parse("anitec://animals/success"))
        assertNull(CheckoutReturnParser.parse("anitec://subscriptions/refund"))
    }
}

class CheckoutReturnsTest {

    @Test
    fun `a return link waits until it is consumed`() {
        val returns = CheckoutReturns()
        assertNull(returns.pending.value)

        returns.onLink("anitec://subscriptions/success?session_id=cs_1")
        assertEquals(CheckoutReturn.Success("cs_1"), returns.pending.value)

        returns.consume()
        assertNull(returns.pending.value)
    }

    @Test
    fun `a link that is not a checkout return changes nothing`() {
        val returns = CheckoutReturns()
        returns.onLink("anitec://subscriptions/cancel")

        returns.onLink("https://example.com")
        returns.onLink(null)

        assertEquals(CheckoutReturn.Cancelled, returns.pending.value)
    }
}

class SubscriptionsRepositoryTest {

    private val api = mockk<SubscriptionsApi>()
    private val repository = SubscriptionsRepositoryImpl(api)

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "{}".toResponseBody()))

    @Test
    fun `plans are mapped from the server`() = runTest {
        coEvery { api.getPlans() } returns listOf(PlanDto(1, "Ganadero Basico", 49.0, 80, true))

        val result = repository.plans() as AppResult.Success

        assertEquals(listOf(basicRancher), result.value)
    }

    @Test
    fun `a user with no active subscription is not an error`() = runTest {
        coEvery { api.getActive(7) } throws http(404)

        assertEquals(AppResult.Success(null), repository.activeSubscription(7))
    }

    @Test
    fun `other failures of the active subscription are reported`() = runTest {
        coEvery { api.getActive(7) } throws http(403)
        assertEquals(AppResult.Failure(AppError.Forbidden), repository.activeSubscription(7))

        coEvery { api.getActive(7) } throws IOException("offline")
        assertEquals(AppResult.Failure(AppError.Network), repository.activeSubscription(7))
    }

    @Test
    fun `the active subscription is mapped`() = runTest {
        coEvery { api.getActive(7) } returns SubscriptionDto(3, 7, 2, "Active", "2026-10-20", null)

        val result = repository.activeSubscription(7) as AppResult.Success

        assertEquals(Subscription(3, 7, 2, "Active", "2026-10-20", null), result.value)
        assertTrue(result.value!!.isActive)
    }

    @Test
    fun `payments come newest first`() = runTest {
        coEvery { api.getPayments(7) } returns listOf(
            PaymentDto(1, 49.0, "PEN", "StripeTest", "Paid", "2026-09-20T10:00:00"),
            PaymentDto(2, 99.0, "PEN", "StripeTest", "Paid", "2026-10-20T10:00:00"),
        )

        val result = repository.payments(7) as AppResult.Success

        assertEquals(listOf(2, 1), result.value.map { it.id })
        assertTrue(result.value.all { it.isPaid })
    }

    @Test
    fun `the checkout is requested for the android app`() = runTest {
        val body = slot<StripeCheckoutRequestDto>()
        coEvery { api.startCheckout(capture(body)) } returns CheckoutSessionDto("cs_test_1", "https://checkout.stripe.com/c/pay/cs_test_1")

        val result = repository.startCheckout(2) as AppResult.Success

        assertEquals(2, body.captured.planId)
        assertEquals("android", body.captured.platform)
        assertEquals(CheckoutSession("cs_test_1", "https://checkout.stripe.com/c/pay/cs_test_1"), result.value)
    }

    @Test
    fun `confirming a checkout returns the subscription and the payment`() = runTest {
        coEvery { api.confirm("cs_test_1") } returns CheckoutResultDto(
            SubscriptionDto(3, 7, 2, "Active", "2026-10-20", null),
            PaymentDto(9, 99.0, "PEN", "StripeTest", "Paid", "2026-10-20T10:00:00"),
        )

        val result = repository.confirmCheckout("cs_test_1") as AppResult.Success

        assertEquals(2, result.value.subscription.planId)
        assertEquals(99.0, result.value.payment.amount, 0.0)
    }

    @Test
    fun `a checkout that Stripe did not complete is a validation failure`() = runTest {
        coEvery { api.confirm("cs_test_2") } throws HttpException(Response.error<Any>(400, """{"message":"not completed"}""".toResponseBody()))

        val result = repository.confirmCheckout("cs_test_2") as AppResult.Failure

        assertEquals(AppError.Validation(listOf("not completed")), result.error)
    }
}

class SubscriptionsViewModelTest {

    private val repository = mockk<SubscriptionsRepository>()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(role: UserRole = UserRole.Rancher): SubscriptionsViewModel {
        val sessionStore = mockk<SessionStore>()
        every { sessionStore.state } returns MutableStateFlow<SessionState>(
            SessionState.SignedIn(UserSession(7, "carlos", "Carlos", role, "token")),
        )
        return SubscriptionsViewModel(
            sessionStore,
            GetPlansUseCase(repository),
            GetActiveSubscriptionUseCase(repository),
            GetPaymentsUseCase(repository),
            StartCheckoutUseCase(repository),
        )
    }

    private fun stubServer(active: Subscription? = null) {
        coEvery { repository.plans() } returns AppResult.Success(listOf(vetPro, proRancher, basicRancher))
        coEvery { repository.activeSubscription(7) } returns AppResult.Success(active)
        coEvery { repository.payments(7) } returns AppResult.Success(emptyList())
    }

    @Test
    fun `refreshing shows only the plans of the user's role`() = runTest {
        stubServer()
        val viewModel = viewModel(UserRole.Rancher)

        viewModel.refresh()

        val state = viewModel.state.value
        assertEquals(listOf(1, 2), state.plans.map { it.id })
        assertFalse(state.loading)
        assertNull(state.active)
    }

    @Test
    fun `the active plan is found even when it is not offered to the user`() = runTest {
        stubServer(active = Subscription(3, 7, planId = 3, status = "Active", startedAt = "2026-10-20", endsAt = null))
        val viewModel = viewModel(UserRole.Rancher)

        viewModel.refresh()

        assertEquals(vetPro, viewModel.state.value.activePlan)
    }

    @Test
    fun `subscribing opens the checkout page once`() = runTest {
        stubServer()
        coEvery { repository.startCheckout(1) } returns AppResult.Success(CheckoutSession("cs_1", "https://checkout.stripe.com/pay/cs_1"))
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.subscribe(basicRancher)

        assertEquals("https://checkout.stripe.com/pay/cs_1", viewModel.state.value.checkoutUrl)
        assertNull(viewModel.state.value.startingPlanId)
        viewModel.onCheckoutOpened()
        assertNull(viewModel.state.value.checkoutUrl)
    }

    @Test
    fun `a checkout address that is not https is never opened`() = runTest {
        stubServer()
        coEvery { repository.startCheckout(1) } returns AppResult.Success(CheckoutSession("cs_1", "http://example.com/pay"))
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.subscribe(basicRancher)

        assertNull(viewModel.state.value.checkoutUrl)
        assertEquals(R.string.error_unknown, viewModel.state.value.messageRes)
    }

    @Test
    fun `a checkout that cannot start shows a message and opens nothing`() = runTest {
        stubServer()
        coEvery { repository.startCheckout(1) } returns AppResult.Failure(AppError.Server(500))
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.subscribe(basicRancher)

        assertEquals(R.string.error_server, viewModel.state.value.messageRes)
        assertNull(viewModel.state.value.checkoutUrl)
        assertNull(viewModel.state.value.startingPlanId)
    }

    @Test
    fun `a missing browser shows its own message`() = runTest {
        stubServer()
        val viewModel = viewModel()

        viewModel.onBrowserMissing()

        assertEquals(R.string.subscriptions_no_browser, viewModel.state.value.messageRes)
    }

    @Test
    fun `a failed refresh keeps what was already shown and reports the error`() = runTest {
        stubServer()
        val viewModel = viewModel()
        viewModel.refresh()
        coEvery { repository.plans() } returns AppResult.Failure(AppError.Network)

        viewModel.refresh()

        assertEquals(listOf(1, 2), viewModel.state.value.plans.map { it.id })
        assertEquals(R.string.error_network, viewModel.state.value.messageRes)
        coVerify(atLeast = 2) { repository.plans() }
    }

    @Test
    fun `payments are shown as the server returns them`() = runTest {
        stubServer()
        val payment = Payment(1, 99.0, "PEN", "StripeTest", "Paid", "2026-10-20T10:00:00")
        coEvery { repository.payments(7) } returns AppResult.Success(listOf(payment))
        val viewModel = viewModel()

        viewModel.refresh()

        assertEquals(listOf(payment), viewModel.state.value.payments)
    }
}
