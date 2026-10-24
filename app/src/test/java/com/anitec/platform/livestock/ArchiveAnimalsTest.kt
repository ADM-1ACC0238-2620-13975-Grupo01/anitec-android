package com.anitec.platform.livestock

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.livestock.application.ArchiveAnimalUseCase
import com.anitec.platform.livestock.application.ArchiveAnimalsUseCase
import com.anitec.platform.livestock.application.LivestockValidation
import com.anitec.platform.livestock.application.ObserveAllAnimalsUseCase
import com.anitec.platform.livestock.application.ObserveAnimalsUseCase
import com.anitec.platform.livestock.application.RestoreAnimalUseCase
import com.anitec.platform.livestock.domain.Animal
import com.anitec.platform.livestock.domain.AnimalStatus
import com.anitec.platform.livestock.domain.LivestockRepository
import com.anitec.platform.livestock.infrastructure.LivestockRepositoryImpl
import com.anitec.platform.livestock.infrastructure.local.LivestockDao
import com.anitec.platform.livestock.infrastructure.remote.AnimalDto
import com.anitec.platform.livestock.infrastructure.remote.AnimalStatusDto
import com.anitec.platform.livestock.infrastructure.remote.LivestockApi
import com.anitec.platform.veterinary.infrastructure.remote.VeterinaryApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun animalWith(id: Int, status: String) = Animal(
    id = id, tag = "T$id", name = "A$id", species = "Bovino", breed = "B", gender = "Hembra", birthDate = null, weight = 1.0,
    status = status, herdId = 1, corralId = 1, source = null, ageRange = null, imageUrl = null,
)

class ArchivedStatusTest {

    @Test
    fun `the archived status is read from the server in both spellings`() {
        assertEquals(AnimalStatus.Archived, AnimalStatus.fromApi("Archivado"))
        assertEquals(AnimalStatus.Archived, AnimalStatus.fromApi(" archived "))
        assertEquals("Archivado", AnimalStatus.Archived.apiValue)
    }

    @Test
    fun `archiving is not offered in the status picker and does not need attention`() {
        assertFalse(AnimalStatus.Archived in AnimalStatus.selectable)
        assertFalse(AnimalStatus.Archived.needsAttention)
    }

    @Test
    fun `an animal knows whether it is archived`() {
        assertTrue(animalWith(1, "Archivado").isArchived)
        assertFalse(animalWith(2, "Saludable").isArchived)
        assertFalse(animalWith(3, "Vendido").isArchived)
    }
}

class ArchiveAnimalsUseCasesTest {

    private val repository = mockk<LivestockRepository>()
    private val animals = listOf(animalWith(1, "Saludable"), animalWith(2, "Archivado"), animalWith(3, "Vendido"))

    @Test
    fun `the herd lists leave archived animals out`() = runTest {
        every { repository.observeAnimals() } returns flowOf(animals)

        assertEquals(listOf(1, 3), ObserveAnimalsUseCase(repository)().first().map { it.id })
    }

    @Test
    fun `the livestock list can still see archived animals`() = runTest {
        every { repository.observeAnimals() } returns flowOf(animals)

        assertEquals(listOf(1, 2, 3), ObserveAllAnimalsUseCase(repository)().first().map { it.id })
    }

    @Test
    fun `archiving one animal sends the archived status`() = runTest {
        coEvery { repository.updateAnimalStatus(1, "Archivado") } returns AppResult.Success(animalWith(1, "Archivado"))

        ArchiveAnimalUseCase(repository)(1)

        coVerify(exactly = 1) { repository.updateAnimalStatus(1, "Archivado") }
    }

    @Test
    fun `restoring an animal returns it to the herd as healthy`() = runTest {
        coEvery { repository.updateAnimalStatus(2, "Saludable") } returns AppResult.Success(animalWith(2, "Saludable"))

        RestoreAnimalUseCase(repository)(2)

        coVerify(exactly = 1) { repository.updateAnimalStatus(2, "Saludable") }
    }

    @Test
    fun `archiving several animals uses the bulk status`() = runTest {
        coEvery { repository.updateAnimalsStatus(listOf(1, 3), "Archivado") } returns AppResult.Success(emptyList())

        ArchiveAnimalsUseCase(repository)(listOf(1, 3))

        coVerify(exactly = 1) { repository.updateAnimalsStatus(listOf(1, 3), "Archivado") }
    }

    @Test
    fun `archiving nothing is refused without a request`() = runTest {
        val result = ArchiveAnimalsUseCase(repository)(emptyList())

        assertEquals(AppResult.Failure(AppError.Validation(listOf(LivestockValidation.NOTHING_SELECTED))), result)
        coVerify(exactly = 0) { repository.updateAnimalsStatus(any(), any()) }
    }
}

class ArchiveAnimalRepositoryTest {

    private val api = mockk<LivestockApi>()
    private val dao = mockk<LivestockDao>(relaxed = true)

    private fun repository(): LivestockRepositoryImpl {
        val sessionStore = mockk<SessionStore>()
        every { sessionStore.state } returns MutableStateFlow<SessionState>(
            SessionState.SignedIn(UserSession(10, "user", "User", UserRole.Rancher, "token")),
        )
        return LivestockRepositoryImpl(api, mockk<VeterinaryApi>(), dao, sessionStore, mockk(relaxed = true))
    }

    private fun dto(status: String) = AnimalDto(
        id = 5, tag = "T5", name = "A5", species = "Bovino", breed = "B", gender = "Hembra", weight = 1.0,
        status = status, herdId = 1, corralId = 1,
    )

    @Test
    fun `changing the status of one animal is saved on the server and in the cache`() = runTest {
        val body = slot<AnimalStatusDto>()
        coEvery { api.updateAnimalStatus(5, capture(body)) } returns dto("Archivado")

        val result = repository().updateAnimalStatus(5, "Archivado") as AppResult.Success

        assertEquals("Archivado", body.captured.status)
        assertTrue(result.value.isArchived)
        coVerify(exactly = 1) { dao.upsertAnimals(match { it.single().id == 5 && it.single().status == "Archivado" }) }
    }

    @Test
    fun `a refused change does not touch the cache`() = runTest {
        coEvery { api.updateAnimalStatus(5, any()) } throws HttpException(Response.error<Any>(403, "{}".toResponseBody()))

        val result = repository().updateAnimalStatus(5, "Archivado")

        assertEquals(AppResult.Failure(AppError.Forbidden), result)
        coVerify(exactly = 0) { dao.upsertAnimals(any()) }
    }
}
