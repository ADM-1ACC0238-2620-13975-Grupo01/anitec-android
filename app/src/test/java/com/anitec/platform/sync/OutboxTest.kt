package com.anitec.platform.sync

import com.anitec.platform.activities.infrastructure.ActivitiesRepositoryImpl
import com.anitec.platform.activities.infrastructure.local.ActivitiesDao
import com.anitec.platform.activities.infrastructure.remote.ActivitiesApi
import com.anitec.platform.activities.infrastructure.remote.FarmActivityDto
import com.anitec.platform.activities.domain.ActivityDraft
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.outbox.OutboxDao
import com.anitec.platform.core.outbox.OutboxFailures
import com.anitec.platform.core.outbox.OutboxKinds
import com.anitec.platform.core.outbox.OutboxQueue
import com.anitec.platform.core.outbox.OutboxScheduler
import com.anitec.platform.core.outbox.PendingOperationEntity
import com.anitec.platform.core.outbox.isPendingSync
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.livestock.infrastructure.local.LivestockDao
import com.anitec.platform.livestock.infrastructure.remote.AnimalDto
import com.anitec.platform.livestock.infrastructure.remote.LivestockApi
import com.anitec.platform.sanitary.infrastructure.local.SanitaryDao
import com.anitec.platform.sanitary.infrastructure.remote.HealthEventDto
import com.anitec.platform.sanitary.infrastructure.remote.SanitaryApi
import com.anitec.platform.veterinary.infrastructure.local.VeterinaryDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

private fun animalDto(id: Int = 0, tag: String = "BOV-900") =
    AnimalDto(id = id, tag = tag, name = "Nueva", species = "Bovino", breed = "B", gender = "Hembra", status = "Saludable", herdId = 1)

private fun healthDto(animalId: Int, id: Int = 0) =
    HealthEventDto(id = id, animalId = animalId, type = "Vacuna", date = "2026-10-02", description = "d")

/** In-memory stand-in for the queue table, so the processor can be exercised without Room. */
private class FakeOutbox {
    val rows = mutableListOf<PendingOperationEntity>()
    val dao = mockk<OutboxDao>().also { dao ->
        coEvery { dao.pending() } answers { rows.filter { !it.failed }.sortedBy { it.seq } }
        coEvery { dao.failed() } answers { rows.filter { it.failed } }
        coEvery { dao.all() } answers { rows.toList() }
        coEvery { dao.delete(any()) } answers { rows.removeAll { it.seq == firstArg<Long>() } }
        coEvery { dao.recordAttempt(any()) } answers { update(firstArg()) { it.copy(attempts = it.attempts + 1) } }
        coEvery { dao.markFailed(any(), any()) } answers { update(firstArg()) { it.copy(failed = true, lastError = secondArg()) } }
        coEvery { dao.updatePayload(any(), any()) } answers { update(firstArg()) { it.copy(payload = secondArg()) } }
        coEvery { dao.resetFailed() } answers { rows.replaceAll { if (it.failed) it.copy(failed = false, attempts = 0, lastError = null) else it } }
    }

    private fun update(seq: Long, change: (PendingOperationEntity) -> PendingOperationEntity) {
        val index = rows.indexOfFirst { it.seq == seq }
        if (index >= 0) rows[index] = change(rows[index])
    }

    fun add(kind: String, localId: Int, payload: String, attempts: Int = 0) {
        rows += PendingOperationEntity(seq = rows.size + 1L, kind = kind, localId = localId, payload = payload, createdAt = 0, attempts = attempts)
    }
}

class OutboxProcessorTest {
    private val fake = FakeOutbox()
    private val livestockApi = mockk<LivestockApi>()
    private val livestockDao = mockk<LivestockDao>(relaxed = true)
    private val sanitaryApi = mockk<SanitaryApi>()
    private val sanitaryDao = mockk<SanitaryDao>(relaxed = true)
    private val activitiesApi = mockk<ActivitiesApi>()
    private val activitiesDao = mockk<ActivitiesDao>(relaxed = true)
    private val processor = OutboxProcessor(fake.dao, livestockApi, livestockDao, sanitaryApi, sanitaryDao, activitiesApi, activitiesDao, json)

    private fun queueAnimal(localId: Int, dto: AnimalDto = animalDto(), attempts: Int = 0) =
        fake.add(OutboxKinds.CREATE_ANIMAL, localId, json.encodeToString(AnimalDto.serializer(), dto), attempts)

    private fun queueHealth(localId: Int, animalId: Int) =
        fake.add(OutboxKinds.CREATE_HEALTH_EVENT, localId, json.encodeToString(HealthEventDto.serializer(), healthDto(animalId)))

    @Test
    fun `a sent animal replaces its local copy and leaves the queue`() = runTest {
        queueAnimal(localId = -1)
        coEvery { livestockApi.createAnimal(any()) } returns animalDto(id = 50)

        assertEquals(SyncOutcome.Done, processor.run())

        coVerify { livestockDao.deleteAnimal(-1) }
        coVerify { livestockDao.upsertAnimal(match { it.id == 50 }) }
        assertTrue(fake.rows.isEmpty())
    }

    @Test
    fun `records queued for an offline animal follow it to the id the server gave`() = runTest {
        queueAnimal(localId = -1)
        queueHealth(localId = -2, animalId = -1)
        coEvery { livestockApi.createAnimal(any()) } returns animalDto(id = 50)
        val sent = slot<HealthEventDto>()
        coEvery { sanitaryApi.createEvent(capture(sent)) } returns healthDto(animalId = 50, id = 700)

        assertEquals(SyncOutcome.Done, processor.run())

        assertEquals(50, sent.captured.animalId)
        coVerify { sanitaryDao.reassignAnimal(-1, 50) }
        coVerify { sanitaryDao.delete(-2) }
        coVerify { sanitaryDao.upsert(match { it.id == 700 }) }
        assertTrue(fake.rows.isEmpty())
    }

    @Test
    fun `no connection keeps the change and asks for a retry`() = runTest {
        queueAnimal(localId = -1)
        coEvery { livestockApi.createAnimal(any()) } throws IOException("offline")

        assertEquals(SyncOutcome.Retry, processor.run())

        assertEquals(1, fake.rows.size)
        assertEquals(1, fake.rows.single().attempts)
        assertFalse(fake.rows.single().failed)
    }

    @Test
    fun `after a lost answer the animal is adopted from the server instead of created twice`() = runTest {
        queueAnimal(localId = -1, attempts = 1)
        coEvery { livestockApi.getAnimals() } returns listOf(animalDto(id = 77, tag = "BOV-900"))

        assertEquals(SyncOutcome.Done, processor.run())

        coVerify(exactly = 0) { livestockApi.createAnimal(any()) }
        coVerify { livestockDao.upsertAnimal(match { it.id == 77 }) }
    }

    @Test
    fun `a refused change is marked failed and the next one is still sent`() = runTest {
        fake.add(OutboxKinds.CREATE_ACTIVITY, -1, json.encodeToString(FarmActivityDto.serializer(), FarmActivityDto(title = "A", date = "2026-10-05")))
        fake.add(OutboxKinds.CREATE_ACTIVITY, -2, json.encodeToString(FarmActivityDto.serializer(), FarmActivityDto(title = "B", date = "2026-10-06")))
        coEvery { activitiesApi.create(match { it.title == "A" }) } throws httpError(400)
        coEvery { activitiesApi.create(match { it.title == "B" }) } returns FarmActivityDto(id = 9, title = "B", date = "2026-10-06")

        assertEquals(SyncOutcome.Done, processor.run())

        assertEquals(1, fake.rows.size)
        assertTrue(fake.rows.single().failed)
        assertEquals(OutboxFailures.REJECTED, fake.rows.single().lastError)
        coVerify { activitiesDao.upsert(match { it.id == 9 }) }
    }

    @Test
    fun `a record whose animal never reached the server is not sent`() = runTest {
        queueHealth(localId = -2, animalId = -1)

        assertEquals(SyncOutcome.Done, processor.run())

        coVerify(exactly = 0) { sanitaryApi.createEvent(any()) }
        assertEquals(OutboxFailures.DEPENDENCY, fake.rows.single().lastError)
    }

    @Test
    fun `an expired session stops the run without touching the queue`() = runTest {
        queueAnimal(localId = -1)
        coEvery { livestockApi.createAnimal(any()) } throws httpError(401)

        assertEquals(SyncOutcome.Stopped, processor.run())

        assertEquals(1, fake.rows.size)
    }

    @Test
    fun `retrying puts refused changes back and discarding removes them with their local records`() = runTest {
        queueAnimal(localId = -1)
        fake.rows[0] = fake.rows[0].copy(failed = true, lastError = OutboxFailures.REJECTED)

        processor.retryFailed()
        assertFalse(fake.rows.single().failed)

        fake.rows[0] = fake.rows[0].copy(failed = true)
        processor.discardFailed()

        assertTrue(fake.rows.isEmpty())
        coVerify { livestockDao.deleteAnimal(-1) }
    }

    private fun httpError(code: Int) = retrofit2.HttpException(
        retrofit2.Response.error<Any>(code, okhttp3.ResponseBody.create(null, "")),
    )
}

class OfflineCreationTest {
    private val api = mockk<ActivitiesApi>()
    private val dao = mockk<ActivitiesDao>(relaxed = true)
    private val queue = mockk<OutboxQueue>()
    private val draft = ActivityDraft(10, null, "Dip cattle", "Sanitario", "2026-10-05", "Alta", "Pendiente")

    private fun repository(): ActivitiesRepositoryImpl {
        val sessionStore = mockk<SessionStore>()
        every { sessionStore.state } returns MutableStateFlow<SessionState>(SessionState.SignedIn(UserSession(10, "u", "U", UserRole.Rancher, "t")))
        return ActivitiesRepositoryImpl(api, dao, mockk<VeterinaryDao>(), sessionStore, queue)
    }

    @Test
    fun `creating without a connection queues the activity and keeps it locally under a negative id`() = runTest {
        coEvery { api.create(any()) } throws IOException("offline")
        coEvery { queue.enqueue(OutboxKinds.CREATE_ACTIVITY, any<kotlinx.serialization.KSerializer<FarmActivityDto>>(), any()) } returns -4

        val result = repository().create(draft)

        val activity = (result as AppResult.Success).value
        assertEquals(-4, activity.id)
        assertTrue(activity.id.isPendingSync)
        assertEquals("Dip cattle", activity.title)
        coVerify { dao.upsert(match { it.id == -4 }) }
    }

    @Test
    fun `a refusal from the server is reported and nothing is queued`() = runTest {
        coEvery { api.create(any()) } throws retrofit2.HttpException(retrofit2.Response.error<Any>(400, okhttp3.ResponseBody.create(null, "")))

        val result = repository().create(draft)

        assertTrue(result is AppResult.Failure)
        coVerify(exactly = 0) { queue.enqueue(any(), any<kotlinx.serialization.KSerializer<FarmActivityDto>>(), any()) }
    }

    @Test
    fun `queueing stores the change and schedules the send`() = runTest {
        val dao = mockk<OutboxDao>()
        val scheduler = mockk<OutboxScheduler>(relaxed = true)
        coEvery { dao.enqueue(OutboxKinds.CREATE_ACTIVITY, any(), any()) } returns -1

        val localId = OutboxQueue(dao, json, scheduler)
            .enqueue(OutboxKinds.CREATE_ACTIVITY, FarmActivityDto.serializer(), FarmActivityDto(title = "T", date = "2026-10-05"))

        assertEquals(-1, localId)
        verify { scheduler.schedule() }
    }

    @Test
    fun `only negative ids are pending`() {
        assertTrue((-1).isPendingSync)
        assertFalse(0.isPendingSync)
        assertFalse(12.isPendingSync)
    }
}
