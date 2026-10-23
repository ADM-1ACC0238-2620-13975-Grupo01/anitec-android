package com.anitec.platform.livestock

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.livestock.domain.CorralDraft
import com.anitec.platform.livestock.infrastructure.LivestockRepositoryImpl
import com.anitec.platform.livestock.infrastructure.local.AnimalEntity
import com.anitec.platform.livestock.infrastructure.local.CorralEntity
import com.anitec.platform.livestock.infrastructure.local.HerdEntity
import com.anitec.platform.livestock.infrastructure.local.LivestockDao
import com.anitec.platform.livestock.infrastructure.remote.AnimalDto
import com.anitec.platform.livestock.infrastructure.remote.AnimalIdsDto
import com.anitec.platform.livestock.infrastructure.remote.CorralDto
import com.anitec.platform.livestock.infrastructure.remote.HerdDto
import com.anitec.platform.livestock.infrastructure.remote.LivestockApi
import com.anitec.platform.veterinary.infrastructure.remote.VeterinaryApi
import com.anitec.platform.veterinary.infrastructure.remote.VeterinarianClientDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class LivestockRepositoryTest {

    private val api = mockk<LivestockApi>()
    private val veterinaryApi = mockk<VeterinaryApi>()
    private val dao = mockk<LivestockDao>(relaxed = true)
    private val outbox = mockk<com.anitec.platform.core.outbox.OutboxQueue>(relaxed = true)

    private fun repository(role: UserRole, userId: Int): LivestockRepositoryImpl {
        val sessionStore = mockk<SessionStore>()
        every { sessionStore.state } returns MutableStateFlow<SessionState>(
            SessionState.SignedIn(UserSession(userId, "user", "User", role, "token")),
        )
        return LivestockRepositoryImpl(api, veterinaryApi, dao, sessionStore, outbox)
    }

    private fun herdDto(id: Int, ownerId: Int, vetId: Int? = null) = HerdDto(id, "Farm $id", "Cajamarca", "Owner $ownerId", ownerId, vetId, "Mixto")
    private fun animalDto(id: Int, herdId: Int, corralId: Int? = null) = AnimalDto(
        id = id, tag = "T$id", name = "A$id", species = "Bovino", breed = "B", gender = "Hembra", weight = 1.0,
        status = "Saludable", herdId = herdId, corralId = corralId,
    )

    private fun stubServer() {
        coEvery { api.getHerds() } returns listOf(herdDto(1, ownerId = 10), herdDto(2, ownerId = 11, vetId = 50), herdDto(3, ownerId = 12))
        coEvery { api.getCorrals() } returns listOf(CorralDto(1, "C1", 1), CorralDto(2, "C2", 2), CorralDto(3, "C3", 3))
        coEvery { api.getAnimals() } returns listOf(animalDto(1, 1, 1), animalDto(2, 2, 2), animalDto(3, 3, 3))
    }

    @Test
    fun `refresh keeps only a ranchers own data in the cache`() = runTest {
        stubServer()
        val herds = slot<List<HerdEntity>>()
        val corrals = slot<List<CorralEntity>>()
        val animals = slot<List<AnimalEntity>>()
        coEvery { dao.replaceAll(capture(herds), capture(corrals), capture(animals)) } returns Unit

        val result = repository(UserRole.Rancher, userId = 10).refresh()

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(listOf(1), herds.captured.map { it.id })
        assertEquals(listOf(1), corrals.captured.map { it.id })
        assertEquals(listOf(1), animals.captured.map { it.id })
        coVerify(exactly = 0) { veterinaryApi.getClients(any()) }
    }

    @Test
    fun `refresh for a veterinarian uses the linked clients`() = runTest {
        stubServer()
        coEvery { veterinaryApi.getClients(50) } returns listOf(VeterinarianClientDto(rancherId = 12))
        val herds = slot<List<HerdEntity>>()
        val animals = slot<List<AnimalEntity>>()
        coEvery { dao.replaceAll(capture(herds), any(), capture(animals)) } returns Unit

        repository(UserRole.Veterinarian, userId = 50).refresh()

        // Herd 2 names the vet; herd 3 belongs to a linked rancher; herd 1 is unrelated.
        assertEquals(setOf(2, 3), herds.captured.map { it.id }.toSet())
        assertEquals(setOf(2, 3), animals.captured.map { it.id }.toSet())
    }

    @Test
    fun `a failed refresh leaves the cache untouched`() = runTest {
        coEvery { api.getHerds() } throws IOException("offline")
        coEvery { api.getCorrals() } returns emptyList()
        coEvery { api.getAnimals() } returns emptyList()

        val result = repository(UserRole.Rancher, userId = 10).refresh()

        assertEquals(AppResult.Failure(AppError.Network), result)
        coVerify(exactly = 0) { dao.replaceAll(any(), any(), any()) }
    }

    @Test
    fun `deleting a corral also detaches its animals locally`() = runTest {
        coEvery { api.deleteCorral(5) } returns Unit

        val result = repository(UserRole.Rancher, 10).deleteCorral(5)

        assertEquals(AppResult.Success(Unit), result)
        coVerify { dao.deleteCorral(5) }
        coVerify { dao.detachAnimalsFromCorral(5) }
    }

    @Test
    fun `a rejected request does not change the cache`() = runTest {
        val body = "{}".toResponseBody()
        coEvery { api.createCorral(any()) } throws HttpException(Response.error<CorralDto>(400, body))

        val result = repository(UserRole.Rancher, 10).createCorral(CorralDraft("New", 1))

        assertTrue(result is AppResult.Failure)
        coVerify(exactly = 0) { dao.upsertCorral(any()) }
    }

    @Test
    fun `bulk delete sends the ids and clears them from the cache`() = runTest {
        coEvery { api.deleteAnimals(AnimalIdsDto(listOf(1, 2))) } returns Unit

        repository(UserRole.Rancher, 10).deleteAnimals(listOf(1, 2))

        coVerify { dao.deleteAnimals(listOf(1, 2)) }
    }
}
