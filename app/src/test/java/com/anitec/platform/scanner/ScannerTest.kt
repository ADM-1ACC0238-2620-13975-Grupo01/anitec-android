package com.anitec.platform.scanner

import com.anitec.platform.livestock.application.ObserveAnimalsUseCase
import com.anitec.platform.livestock.application.RefreshLivestockUseCase
import com.anitec.platform.livestock.domain.Animal
import com.anitec.platform.livestock.domain.LivestockRepository
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.scanner.domain.TagResolver
import com.anitec.platform.scanner.interfaces.viewmodel.ScanResult
import com.anitec.platform.scanner.interfaces.viewmodel.ScannerViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun animal(id: Int, tag: String) =
    Animal(id, tag, "Animal $id", "Bovino", "Brown Swiss", "Hembra", null, 1.0, "Saludable", 1, null, null, null, null)

class TagResolverTest {
    private val animals = listOf(animal(1, "AN-001"), animal(2, "AN-002"))

    @Test
    fun `an exact tag finds the animal ignoring case and spaces`() {
        assertEquals(1, TagResolver.resolve("AN-001", animals)?.id)
        assertEquals(2, TagResolver.resolve("  an-002 \n", animals)?.id)
    }

    @Test
    fun `codes that wrap the tag in a link or prefix still find it`() {
        assertEquals(1, TagResolver.resolve("https://anitec.app/animals/AN-001", animals)?.id)
        assertEquals(2, TagResolver.resolve("anitec:animal:AN-002", animals)?.id)
        assertEquals(1, TagResolver.resolve("https://anitec.app/a?tag=AN-001&x=1", animals)?.id)
    }

    @Test
    fun `an unknown or blank code finds nothing`() {
        assertNull(TagResolver.resolve("AN-999", animals))
        assertNull(TagResolver.resolve("   ", animals))
        assertTrue(TagResolver.candidates("").isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): ScannerViewModel {
        val repository = mockk<LivestockRepository>()
        every { repository.observeAnimals() } returns flowOf(listOf(animal(1, "AN-001")))
        coEvery { repository.refresh() } returns AppResult.Success(Unit)
        return ScannerViewModel(ObserveAnimalsUseCase(repository), RefreshLivestockUseCase(repository))
    }

    @Test
    fun `a detected code that matches an animal shows it`() {
        val viewModel = viewModel()
        viewModel.onCodeDetected("AN-001")
        assertEquals(1, (viewModel.state.value.result as ScanResult.Found).animal.id)
    }

    @Test
    fun `a code that matches nothing is reported with the text read`() {
        val viewModel = viewModel()
        viewModel.onCodeDetected(" ZZ-9 ")
        assertEquals("ZZ-9", (viewModel.state.value.result as ScanResult.NotFound).code)
    }

    @Test
    fun `later detections are ignored while a result is shown`() {
        val viewModel = viewModel()
        viewModel.onCodeDetected("AN-001")
        viewModel.onCodeDetected("OTHER")
        assertTrue(viewModel.state.value.result is ScanResult.Found)
    }

    @Test
    fun `scanning again clears the result and the typed code`() {
        val viewModel = viewModel()
        viewModel.onManualCodeChange("AN-001")
        viewModel.lookUpManualCode()
        assertTrue(viewModel.state.value.result is ScanResult.Found)

        viewModel.scanAgain()

        assertNull(viewModel.state.value.result)
        assertEquals("", viewModel.state.value.manualCode)
    }

    @Test
    fun `an empty typed code is not looked up`() {
        val viewModel = viewModel()
        viewModel.lookUpManualCode()
        assertNull(viewModel.state.value.result)
    }
}
