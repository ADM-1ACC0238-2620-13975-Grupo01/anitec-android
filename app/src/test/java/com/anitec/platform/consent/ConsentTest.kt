package com.anitec.platform.consent

// Test coverage marker: veterinarian consent scenarios (US-020, US-021, US-022).

import com.anitec.platform.R
import com.anitec.platform.consent.application.AcceptVeterinarianRequestUseCase
import com.anitec.platform.consent.application.ListVeterinarianLinksUseCase
import com.anitec.platform.consent.application.RejectVeterinarianRequestUseCase
import com.anitec.platform.consent.application.RevokeVeterinarianAccessUseCase
import com.anitec.platform.consent.domain.ConsentRepository
import com.anitec.platform.consent.domain.LinkStatus
import com.anitec.platform.consent.domain.VeterinarianLink
import com.anitec.platform.consent.domain.closed
import com.anitec.platform.consent.domain.pending
import com.anitec.platform.consent.domain.withAccess
import com.anitec.platform.consent.infrastructure.ConsentRepositoryImpl
import com.anitec.platform.consent.infrastructure.remote.ConsentApi
import com.anitec.platform.consent.infrastructure.remote.VeterinarianLinkDto
import com.anitec.platform.consent.interfaces.viewmodel.PendingRequestsViewModel
import com.anitec.platform.consent.interfaces.viewmodel.VeterinarianRequestsViewModel
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private fun link(id: Int, status: LinkStatus, name: String = "Vet $id", at: String = "2026-11-0${id}T10:00:00Z") =
    VeterinarianLink(id, 100 + id, name, "vet$id", status, at)

private fun dto(id: Int, status: String) = VeterinarianLinkDto(id, 100 + id, "Vet $id", "vet$id", status, "2026-11-0${id}T10:00:00Z")

private fun httpError(code: Int) = retrofit2.HttpException(
    retrofit2.Response.error<Any>(code, okhttp3.ResponseBody.create(null, "")),
)

class ConsentDomainTest {

    @Test
    fun `a status is read ignoring case, and an unknown one never counts as access`() {
        assertEquals(LinkStatus.Pending, LinkStatus.parse("Pending"))
        assertEquals(LinkStatus.Accepted, LinkStatus.parse("accepted"))
        assertEquals(LinkStatus.Revoked, LinkStatus.parse("REVOKED"))
        assertEquals(LinkStatus.Unknown, LinkStatus.parse("Suspended"))
        assertEquals(LinkStatus.Unknown, LinkStatus.parse("Unknown"))
        assertEquals(LinkStatus.Unknown, LinkStatus.parse(""))
    }

    @Test
    fun `links are grouped into requests, access and closed ones`() {
        val links = listOf(
            link(1, LinkStatus.Pending), link(2, LinkStatus.Accepted, name = "Zoe"), link(3, LinkStatus.Rejected),
            link(4, LinkStatus.Revoked), link(5, LinkStatus.Pending), link(6, LinkStatus.Accepted, name = "Ana"),
            link(7, LinkStatus.Unknown),
        )

        assertEquals(listOf(5, 1), links.pending().map { it.id })
        assertEquals(listOf(6, 2), links.withAccess().map { it.id })
        assertEquals(listOf(7, 4, 3), links.closed().map { it.id })
    }

    @Test
    fun `the name falls back to the username and the date is the day of the request`() {
        val unnamed = VeterinarianLink(1, 101, "", "dra.ana", LinkStatus.Pending, "2026-11-23T21:40:29.123Z")

        assertEquals("dra.ana", unnamed.displayName)
        assertEquals("2026-11-23", unnamed.requestedDay)
    }
}

class ConsentRepositoryTest {
    private val api = mockk<ConsentApi>()

    private fun repository(userId: Int? = 20): ConsentRepositoryImpl {
        val sessionStore = mockk<SessionStore>()
        val state: SessionState = if (userId == null) SessionState.SignedOut
        else SessionState.SignedIn(UserSession(userId, "ranch", "Rancher", UserRole.Rancher, "t"))
        every { sessionStore.state } returns MutableStateFlow(state)
        return ConsentRepositoryImpl(api, sessionStore)
    }

    @Test
    fun `the links of the signed-in rancher are read and mapped`() = runTest {
        coEvery { api.getLinks(20) } returns listOf(dto(1, "Pending"), dto(2, "Accepted"), dto(3, "Whatever"))

        val result = repository().links() as AppResult.Success

        assertEquals(listOf(LinkStatus.Pending, LinkStatus.Accepted, LinkStatus.Unknown), result.value.map { it.status })
        assertEquals("Vet 1", result.value.first().displayName)
    }

    @Test
    fun `accepting, rejecting and revoking call the server with the rancher and link ids`() = runTest {
        coEvery { api.accept(20, 5) } returns dto(5, "Accepted")
        coEvery { api.reject(20, 6) } returns dto(6, "Rejected")
        coEvery { api.revoke(20, 7) } returns dto(7, "Revoked")

        assertEquals(AppResult.Success(Unit), repository().accept(5))
        assertEquals(AppResult.Success(Unit), repository().reject(6))
        assertEquals(AppResult.Success(Unit), repository().revoke(7))

        coVerify { api.accept(20, 5) }
        coVerify { api.reject(20, 6) }
        coVerify { api.revoke(20, 7) }
    }

    @Test
    fun `server refusals become typed errors`() = runTest {
        coEvery { api.accept(20, 1) } throws httpError(409)
        coEvery { api.revoke(20, 2) } throws httpError(404)
        coEvery { api.getLinks(20) } throws IOException("offline")

        assertTrue((repository().accept(1) as AppResult.Failure).error is AppError.Conflict)
        assertEquals(AppResult.Failure(AppError.NotFound), repository().revoke(2))
        assertEquals(AppResult.Failure(AppError.Network), repository().links())
    }

    @Test
    fun `without a session nothing is sent`() = runTest {
        val repository = repository(userId = null)

        assertEquals(AppResult.Failure(AppError.Unauthorized), repository.links())
        assertEquals(AppResult.Failure(AppError.Unauthorized), repository.accept(1))
        assertEquals(AppResult.Failure(AppError.Unauthorized), repository.revoke(1))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VeterinarianRequestsViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val repository = mockk<ConsentRepository>()
    private var server = mutableListOf(link(1, LinkStatus.Pending), link(2, LinkStatus.Accepted))

    private fun viewModel(): VeterinarianRequestsViewModel {
        coEvery { repository.links() } answers { AppResult.Success(server.toList()) }
        return VeterinarianRequestsViewModel(
            ListVeterinarianLinksUseCase(repository), AcceptVeterinarianRequestUseCase(repository),
            RejectVeterinarianRequestUseCase(repository), RevokeVeterinarianAccessUseCase(repository),
        )
    }

    @Test
    fun `the screen loads the requests and splits them`() {
        val state = viewModel().state.value

        assertEquals(false, state.loading)
        assertEquals(listOf(1), state.pending.map { it.id })
        assertEquals(listOf(2), state.withAccess.map { it.id })
    }

    @Test
    fun `accepting a request moves it to the veterinarians with access and says so`() {
        coEvery { repository.accept(1) } answers {
            server[0] = link(1, LinkStatus.Accepted)
            AppResult.Success(Unit)
        }
        val viewModel = viewModel()

        viewModel.accept(viewModel.state.value.pending.single())

        val state = viewModel.state.value
        assertTrue(state.pending.isEmpty())
        assertEquals(listOf(1, 2), state.withAccess.map { it.id }.sorted())
        assertEquals(R.string.consent_accepted_message, state.messageRes)
        assertNull(state.actingId)
    }

    @Test
    fun `rejecting a request closes it without access`() {
        coEvery { repository.reject(1) } answers {
            server[0] = link(1, LinkStatus.Rejected)
            AppResult.Success(Unit)
        }
        val viewModel = viewModel()

        viewModel.reject(viewModel.state.value.pending.single())

        val state = viewModel.state.value
        assertEquals(listOf(2), state.withAccess.map { it.id })
        assertEquals(listOf(1), state.closed.map { it.id })
        assertEquals(R.string.consent_rejected_message, state.messageRes)
    }

    @Test
    fun `revoking asks for confirmation first and does nothing if it is dismissed`() {
        val viewModel = viewModel()
        val active = viewModel.state.value.withAccess.single()

        viewModel.requestRevoke(active)
        assertEquals(active, viewModel.state.value.pendingRevoke)
        viewModel.dismissRevoke()

        assertNull(viewModel.state.value.pendingRevoke)
        coVerify(exactly = 0) { repository.revoke(any()) }
    }

    @Test
    fun `confirming the revocation withdraws the access`() {
        coEvery { repository.revoke(2) } answers {
            server[1] = link(2, LinkStatus.Revoked)
            AppResult.Success(Unit)
        }
        val viewModel = viewModel()

        viewModel.requestRevoke(viewModel.state.value.withAccess.single())
        viewModel.confirmRevoke()

        val state = viewModel.state.value
        assertTrue(state.withAccess.isEmpty())
        assertEquals(listOf(2), state.closed.map { it.id })
        assertEquals(R.string.consent_revoked_message, state.messageRes)
        assertNull(state.pendingRevoke)
    }

    @Test
    fun `a refusal is reported and the list is read again so it shows the server state`() {
        // Someone answered the request from another device meanwhile.
        coEvery { repository.accept(1) } answers {
            server[0] = link(1, LinkStatus.Rejected)
            AppResult.Failure(AppError.Conflict())
        }
        val viewModel = viewModel()

        viewModel.accept(viewModel.state.value.pending.single())

        val state = viewModel.state.value
        assertEquals(AppError.Conflict().messageRes(), state.messageRes)
        assertTrue(state.pending.isEmpty())
        assertEquals(listOf(1), state.closed.map { it.id })
    }

    @Test
    fun `a failed load keeps the screen usable and reports the error`() {
        coEvery { repository.links() } returns AppResult.Failure(AppError.Network)
        val viewModel = VeterinarianRequestsViewModel(
            ListVeterinarianLinksUseCase(repository), AcceptVeterinarianRequestUseCase(repository),
            RejectVeterinarianRequestUseCase(repository), RevokeVeterinarianAccessUseCase(repository),
        )

        val state = viewModel.state.value

        assertEquals(false, state.loading)
        assertEquals(AppError.Network.messageRes(), state.messageRes)
        assertTrue(state.links.isEmpty())
    }

    @Test
    fun `the message is cleared once shown`() {
        val viewModel = viewModel()
        coEvery { repository.accept(1) } returns AppResult.Success(Unit)
        viewModel.accept(viewModel.state.value.pending.single())

        viewModel.onMessageShown()

        assertNull(viewModel.state.value.messageRes)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PendingRequestsViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the badge counts only the requests waiting for an answer`() {
        val repository = mockk<ConsentRepository>()
        coEvery { repository.links() } returns AppResult.Success(
            listOf(link(1, LinkStatus.Pending), link(2, LinkStatus.Accepted), link(3, LinkStatus.Pending), link(4, LinkStatus.Rejected)),
        )
        val viewModel = PendingRequestsViewModel(ListVeterinarianLinksUseCase(repository))

        viewModel.refresh()

        assertEquals(2, viewModel.count.value)
    }

    @Test
    fun `without a connection the last number stays`() {
        val repository = mockk<ConsentRepository>()
        coEvery { repository.links() } returns AppResult.Success(listOf(link(1, LinkStatus.Pending))) andThen AppResult.Failure(AppError.Network)
        val viewModel = PendingRequestsViewModel(ListVeterinarianLinksUseCase(repository))

        viewModel.refresh()
        viewModel.refresh()

        assertEquals(1, viewModel.count.value)
    }
}
