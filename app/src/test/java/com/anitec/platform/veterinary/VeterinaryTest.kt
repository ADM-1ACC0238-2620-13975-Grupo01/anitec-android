package com.anitec.platform.veterinary

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.livestock.application.ObserveAnimalsUseCase
import com.anitec.platform.livestock.application.ObserveHerdsUseCase
import com.anitec.platform.livestock.application.RefreshLivestockUseCase
import com.anitec.platform.livestock.domain.Animal
import com.anitec.platform.livestock.domain.Herd
import com.anitec.platform.livestock.domain.LivestockRepository
import com.anitec.platform.sanitary.application.ObserveHealthEventsUseCase
import com.anitec.platform.sanitary.application.RefreshSanitaryUseCase
import com.anitec.platform.sanitary.domain.HealthEvent
import com.anitec.platform.sanitary.domain.SanitaryRepository
import com.anitec.platform.veterinary.application.ObserveClientsUseCase
import com.anitec.platform.veterinary.application.RefreshClientsUseCase
import com.anitec.platform.veterinary.application.search
import com.anitec.platform.veterinary.domain.AvailableRancher
import com.anitec.platform.veterinary.domain.Client
import com.anitec.platform.veterinary.domain.VeterinaryRepository
import com.anitec.platform.veterinary.domain.initialsOf
import com.anitec.platform.veterinary.infrastructure.VeterinaryRepositoryImpl
import com.anitec.platform.veterinary.infrastructure.local.ClientEntity
import com.anitec.platform.veterinary.infrastructure.local.VeterinaryDao
import com.anitec.platform.veterinary.infrastructure.remote.AvailableRancherDto
import com.anitec.platform.veterinary.infrastructure.remote.ClientLinkDto
import com.anitec.platform.veterinary.infrastructure.remote.VeterinarianClientDto
import com.anitec.platform.veterinary.infrastructure.remote.VeterinaryApi
import com.anitec.platform.veterinary.interfaces.viewmodel.VetHomeViewModel
import com.anitec.platform.veterinary.interfaces.viewmodel.summarize
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

private fun herd(id: Int, ownerId: Int, name: String = "Farm $id") = Herd(id, name, "Cajamarca", "Owner", ownerId, 50, "Mixto")

private fun animal(id: Int, herdId: Int, species: String = "Bovino", status: String = "Saludable") =
    Animal(id, "T$id", "A$id", species, "B", "Hembra", null, 1.0, status, herdId, null, null, null, null)

private fun event(id: Int, animalId: Int, next: String? = null) =
    HealthEvent(id, animalId, "Vacuna", "2026-09-0$id", "d", "Dr. Ana", "", "", "", "", next)

class VeterinaryHelpersTest {

    @Test
    fun `initials use up to two words`() {
        assertEquals("CM", initialsOf("Carlos Mendoza"))
        assertEquals("CM", initialsOf("  carlos   mendoza quispe "))
        assertEquals("M", initialsOf("maria"))
        assertEquals("?", initialsOf("   "))
    }

    @Test
    fun `available ranchers are searched by name or username`() {
        val ranchers = listOf(
            AvailableRancher(1, "ganadero", "Carlos Mendoza", 2, 10),
            AvailableRancher(2, "maria", "Maria Gonzales", 1, 4),
            AvailableRancher(3, "jose", "", 0, 0),
        )
        assertEquals(listOf(1, 2, 3), ranchers.search("  ").map { it.id })
        assertEquals(listOf(2), ranchers.search("GONZ").map { it.id })
        assertEquals(listOf(1), ranchers.search("ganad").map { it.id })
        assertEquals("jose", ranchers[2].displayName)
    }

    @Test
    fun `a summary counts only the animals and records of that client`() {
        val clients = listOf(Client(10, "Carlos", "Accepted", 2, 9), Client(11, "Maria", "Accepted", 1, 9))
        val herds = listOf(herd(1, 10, "Los Alamos"), herd(2, 10, "El Molino"), herd(3, 11))
        val animals = listOf(
            animal(1, 1, "Bovino", "Saludable"), animal(2, 1, "Bovino", "En tratamiento"),
            animal(3, 2, "Pollo", "Observacion"), animal(4, 3, "Ovino"),
        )
        val events = listOf(event(1, 1), event(2, 2, next = "2026-10-01"), event(3, 4))

        val carlos = summarize(clients, herds, animals, events).first { it.client.rancherId == 10 }

        assertEquals(listOf("Los Alamos", "El Molino"), carlos.farmNames)
        assertEquals(3, carlos.animalCount)
        assertEquals(2, carlos.speciesCount)
        assertEquals(2, carlos.attentionCount)
        assertEquals(2, carlos.recordCount)
        assertEquals(1, carlos.followUpCount)
    }

    @Test
    fun `a client without farms has empty figures`() {
        val summary = summarize(listOf(Client(12, "Rosa", "Accepted", 0, 0)), emptyList(), emptyList(), emptyList()).single()
        assertEquals(0, summary.animalCount)
        assertEquals(emptyList<String>(), summary.farmNames)
    }
}

class VeterinaryRepositoryTest {
    private val api = mockk<VeterinaryApi>()
    private val dao = mockk<VeterinaryDao>(relaxed = true)

    private fun repository(userId: Int = 50): VeterinaryRepositoryImpl {
        val sessionStore = mockk<SessionStore>()
        every { sessionStore.state } returns MutableStateFlow<SessionState>(
            SessionState.SignedIn(UserSession(userId, "vet", "Dra. Ana", UserRole.Veterinarian, "t")),
        )
        return VeterinaryRepositoryImpl(api, dao, sessionStore)
    }

    @Test
    fun `refresh stores the server clients`() = runTest {
        coEvery { api.getClients(50) } returns listOf(VeterinarianClientDto(rancherId = 10, rancherName = "Carlos", herds = 2, animals = 9))
        val cached = slot<List<ClientEntity>>()
        coEvery { dao.replaceAll(capture(cached)) } returns Unit

        assertEquals(AppResult.Success(Unit), repository().refreshClients())

        assertEquals(listOf(10), cached.captured.map { it.rancherId })
        assertEquals(9, cached.captured.single().animals)
    }

    @Test
    fun `a failed refresh keeps the cache`() = runTest {
        coEvery { api.getClients(50) } throws IOException("offline")

        assertEquals(AppResult.Failure(AppError.Network), repository().refreshClients())
        coVerify(exactly = 0) { dao.replaceAll(any()) }
    }

    @Test
    fun `adding a client links it and refreshes the list`() = runTest {
        coEvery { api.addClient(50, 12) } returns ClientLinkDto(rancherId = 12, veterinarianId = 50, status = "Accepted")
        coEvery { api.getClients(50) } returns listOf(VeterinarianClientDto(rancherId = 12, rancherName = "Rosa"))

        assertEquals(AppResult.Success(Unit), repository().addClient(12))

        coVerify { api.addClient(50, 12) }
        coVerify { dao.replaceAll(match { it.map { c -> c.rancherId } == listOf(12) }) }
    }

    @Test
    fun `a client that cannot be added does not touch the cache`() = runTest {
        coEvery { api.addClient(any(), any()) } throws IOException("offline")

        assertEquals(AppResult.Failure(AppError.Network), repository().addClient(12))
        coVerify(exactly = 0) { dao.replaceAll(any()) }
    }

    @Test
    fun `removing a client deletes it locally after the server agrees`() = runTest {
        coEvery { api.removeClient(50, 10) } returns Unit

        repository().removeClient(10)

        coVerify { dao.delete(10) }
    }

    @Test
    fun `available ranchers come straight from the server`() = runTest {
        coEvery { api.getAvailableRanchers(50) } returns listOf(AvailableRancherDto(id = 3, username = "jose", fullName = "Jose Quispe", herds = 1, animals = 5))

        val result = repository().availableRanchers() as AppResult.Success

        assertEquals("Jose Quispe", result.value.single().displayName)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VetHomeViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val clients = listOf(Client(10, "Carlos", "Accepted", 2, 9), Client(11, "Maria", "Accepted", 1, 9))
    private val herds = listOf(herd(1, 10), herd(2, 11))
    private val animals = listOf(animal(1, 1, status = "En tratamiento"), animal(2, 1), animal(3, 2, status = "Observacion"))
    private val events = listOf(event(1, 1, next = "2026-10-01"), event(2, 2), event(3, 3, next = "2026-10-05"))

    private fun viewModel(): VetHomeViewModel {
        val veterinary = mockk<VeterinaryRepository>()
        every { veterinary.observeClients() } returns flowOf(clients)
        coEvery { veterinary.refreshClients() } returns AppResult.Success(Unit)
        val livestock = mockk<LivestockRepository>()
        every { livestock.observeHerds() } returns flowOf(herds)
        every { livestock.observeAnimals() } returns flowOf(animals)
        coEvery { livestock.refresh() } returns AppResult.Success(Unit)
        val sanitary = mockk<SanitaryRepository>()
        every { sanitary.observeEvents() } returns flowOf(events)
        coEvery { sanitary.refresh() } returns AppResult.Success(Unit)
        return VetHomeViewModel(
            ObserveClientsUseCase(veterinary), ObserveHerdsUseCase(livestock), ObserveAnimalsUseCase(livestock),
            ObserveHealthEventsUseCase(sanitary), RefreshClientsUseCase(veterinary), RefreshLivestockUseCase(livestock),
            RefreshSanitaryUseCase(sanitary),
        )
    }

    @Test
    fun `totals cover every client by default`() = runTest(UnconfinedTestDispatcher()) {
        val state = viewModel().state.first { it.clients.isNotEmpty() }

        assertEquals(2, state.clientCount)
        assertEquals(2, state.activePatients)
        assertEquals(3, state.recordCount)
        assertEquals(2, state.followUpCount)
        assertEquals(3, state.recent.size)
    }

    @Test
    fun `selecting a client narrows the totals to it`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel()
        viewModel.onClientSelected(11)

        val state = viewModel.state.first { it.selectedClientId == 11 }

        assertEquals(1, state.clientCount)
        assertEquals(1, state.activePatients)
        assertEquals(1, state.recordCount)
        assertEquals(1, state.followUpCount)
    }
}
