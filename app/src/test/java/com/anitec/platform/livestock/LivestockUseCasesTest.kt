package com.anitec.platform.livestock

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.livestock.application.ChangeAnimalsStatusUseCase
import com.anitec.platform.livestock.application.DeleteAnimalsUseCase
import com.anitec.platform.livestock.application.LivestockValidation
import com.anitec.platform.livestock.application.RegisterAnimalBatchUseCase
import com.anitec.platform.livestock.application.SaveAnimalUseCase
import com.anitec.platform.livestock.domain.AnimalBatchDraft
import com.anitec.platform.livestock.domain.AnimalDraft
import com.anitec.platform.livestock.domain.LivestockRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LivestockUseCasesTest {

    private val repository = mockk<LivestockRepository>()
    private val corrals = listOf(corral(1, herdId = 1), corral(2, herdId = 2))

    private fun draft(herdId: Int = 1, corralId: Int? = 1) = AnimalDraft(
        tag = "BOV-001", name = "Luna", species = "Bovino", breed = "Brown Swiss", gender = "Hembra", birthDate = null,
        weight = 410.0, status = "Saludable", herdId = herdId, corralId = corralId, source = null, ageRange = null, imageUrl = null,
    )

    private fun batch(quantity: Int = 10, herdId: Int = 1, corralId: Int = 1) = AnimalBatchDraft(
        species = "Pollo", breed = "Criollo", gender = "Mixto", birthDate = null, weight = 1.5, status = "Saludable",
        herdId = herdId, corralId = corralId, quantity = quantity, source = null, ageRange = null, imageUrl = null,
    )

    private fun invalid(code: String) = AppResult.Failure(AppError.Validation(listOf(code)))

    @Test
    fun `saving an animal in a corral of another farm is refused without a request`() = runTest {
        val result = SaveAnimalUseCase(repository)(null, draft(herdId = 1, corralId = 2), corrals)

        assertEquals(invalid(LivestockValidation.INVALID_PLACEMENT), result)
        coVerify(exactly = 0) { repository.createAnimal(any()) }
    }

    @Test
    fun `an animal without a corral is refused`() = runTest {
        val result = SaveAnimalUseCase(repository)(null, draft(corralId = null), corrals)
        assertEquals(invalid(LivestockValidation.INVALID_PLACEMENT), result)
    }

    @Test
    fun `a new animal is created and an existing one is updated`() = runTest {
        val saved = animal(7, 1, 1)
        coEvery { repository.createAnimal(any()) } returns AppResult.Success(saved)
        coEvery { repository.updateAnimal(7, any()) } returns AppResult.Success(saved)
        val useCase = SaveAnimalUseCase(repository)

        useCase(null, draft(), corrals)
        useCase(7, draft(), corrals)

        coVerify(exactly = 1) { repository.createAnimal(any()) }
        coVerify(exactly = 1) { repository.updateAnimal(7, any()) }
    }

    @Test
    fun `batch size outside 1 to 500 is refused`() = runTest {
        val useCase = RegisterAnimalBatchUseCase(repository)

        assertEquals(invalid(LivestockValidation.INVALID_BATCH_SIZE), useCase(batch(quantity = 0), corrals))
        assertEquals(invalid(LivestockValidation.INVALID_BATCH_SIZE), useCase(batch(quantity = 501), corrals))
        coVerify(exactly = 0) { repository.createAnimalBatch(any()) }
    }

    @Test
    fun `a batch in a corral of another farm is refused`() = runTest {
        val result = RegisterAnimalBatchUseCase(repository)(batch(herdId = 1, corralId = 2), corrals)
        assertEquals(invalid(LivestockValidation.INVALID_PLACEMENT), result)
    }

    @Test
    fun `a valid batch reaches the repository`() = runTest {
        coEvery { repository.createAnimalBatch(any()) } returns AppResult.Success(listOf(animal(1, 1, 1)))

        RegisterAnimalBatchUseCase(repository)(batch(quantity = 25), corrals)

        coVerify(exactly = 1) { repository.createAnimalBatch(match { it.quantity == 25 }) }
    }

    @Test
    fun `bulk actions need a selection`() = runTest {
        assertEquals(invalid(LivestockValidation.NOTHING_SELECTED), ChangeAnimalsStatusUseCase(repository)(emptySet(), "Vendido"))
        assertEquals(invalid(LivestockValidation.NOTHING_SELECTED), DeleteAnimalsUseCase(repository)(emptySet()))
        coVerify(exactly = 0) { repository.updateAnimalsStatus(any(), any()) }
        coVerify(exactly = 0) { repository.deleteAnimals(any()) }
    }

    @Test
    fun `bulk actions forward the selected ids`() = runTest {
        coEvery { repository.updateAnimalsStatus(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { repository.deleteAnimals(any()) } returns AppResult.Success(Unit)

        ChangeAnimalsStatusUseCase(repository)(setOf(1, 2), "Vendido")
        DeleteAnimalsUseCase(repository)(setOf(3, 4))

        coVerify { repository.updateAnimalsStatus(listOf(1, 2), "Vendido") }
        coVerify { repository.deleteAnimals(listOf(3, 4)) }
    }
}
