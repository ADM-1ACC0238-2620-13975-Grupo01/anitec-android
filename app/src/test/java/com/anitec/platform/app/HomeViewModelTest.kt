package com.anitec.platform.app

import com.anitec.platform.core.common.AppResult
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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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


@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val herds = listOf(
        Herd(1, "Hato Los Alamos", "Cajamarca", "Carlos", 10, null, "Mixto"),
        Herd(2, "Granja El Molino", "Cajamarca", "Carlos", 10, null, "Aves"),
    )

    private fun animal(id: Int, herdId: Int, status: String) =
        Animal(id, "T$id", "A$id", "Bovino", "B", "Hembra", null, 1.0, status, herdId, null, null, null, null)

    private fun event(id: Int, animalId: Int, next: String? = null) = HealthEvent(
        id, animalId, "Vacuna", "2026-09-0$id", "d", "Dr. Ana", "", "", "", "", next,
    )

    private val animals = listOf(
        animal(1, 1, "Saludable"),
        animal(2, 1, "En tratamiento"),
        animal(3, 2, "Observacion"),
        animal(4, 2, "Vendido"),
    )
    private val events = listOf(event(1, 1), event(2, 2, next = "2026-10-20"), event(3, 3, next = "2026-10-25"), event(4, 4))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): HomeViewModel {
        val livestock = mockk<LivestockRepository>()
        every { livestock.observeHerds() } returns flowOf(herds)
        every { livestock.observeAnimals() } returns flowOf(animals)
        coEvery { livestock.refresh() } returns AppResult.Success(Unit)
        val sanitary = mockk<SanitaryRepository>()
        every { sanitary.observeEvents() } returns flowOf(events)
        coEvery { sanitary.refresh() } returns AppResult.Success(Unit)
        return HomeViewModel(
            ObserveHerdsUseCase(livestock), ObserveAnimalsUseCase(livestock), ObserveHealthEventsUseCase(sanitary),
            RefreshLivestockUseCase(livestock), RefreshSanitaryUseCase(sanitary),
        )
    }

    @Test
    fun `all farms are counted by default`() = runTest(UnconfinedTestDispatcher()) {
        val state = viewModel().state.first { it.animalCount > 0 }

        assertEquals(4, state.animalCount)
        // Only observation and treatment need attention; sold animals do not.
        assertEquals(2, state.attentionCount)
        assertEquals(4, state.recordCount)
        assertEquals(2, state.followUpCount)
    }

    @Test
    fun `selecting a farm narrows every counter to that farm`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel()
        viewModel.onHerdSelected(1)

        val state = viewModel.state.first { it.selectedHerdId == 1 }

        assertEquals(2, state.animalCount)
        assertEquals(1, state.attentionCount)
        assertEquals(2, state.recordCount)
        assertEquals(1, state.followUpCount)
    }

    @Test
    fun `the dashboard lists at most four recent records`() = runTest(UnconfinedTestDispatcher()) {
        val state = viewModel().state.first { it.recent.isNotEmpty() }
        assertEquals(4, state.recent.size)
        assertEquals("A1", state.recent.first().animalName)
    }

    
    @Test
    fun `a selected farm that no longer exists falls back to all farms`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel()
        viewModel.onHerdSelected(99)

        val state = viewModel.state.first { it.animalCount > 0 }

        assertEquals(null, state.selectedHerdId)
        assertEquals(4, state.animalCount)
    }
}

