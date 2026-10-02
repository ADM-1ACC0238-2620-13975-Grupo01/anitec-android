package com.anitec.platform.sanitary

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.livestock.infrastructure.local.LivestockDao
import com.anitec.platform.sanitary.domain.HealthEvent
import com.anitec.platform.sanitary.domain.SanitaryScope
import com.anitec.platform.sanitary.infrastructure.SanitaryRepositoryImpl
import com.anitec.platform.sanitary.infrastructure.local.HealthEventEntity
import com.anitec.platform.sanitary.infrastructure.local.SanitaryDao
import com.anitec.platform.sanitary.infrastructure.remote.HealthEventDto
import com.anitec.platform.sanitary.infrastructure.remote.SanitaryApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

internal fun healthEvent(id: Int, animalId: Int, nextDueDate: String? = null, date: String = "2026-09-01") = HealthEvent(
    id = id, animalId = animalId, type = "Vacuna", date = date, description = "d", veterinarian = "Dr. Ana",
    diagnosis = "", treatment = "", prescription = "", followUp = "", nextDueDate = nextDueDate,
)

class SanitaryScopeTest {
    @Test
    fun `only records of visible animals are kept`() {
        val events = listOf(healthEvent(1, animalId = 10), healthEvent(2, animalId = 20), healthEvent(3, animalId = 10))
        assertEquals(listOf(1, 3), SanitaryScope.visibleEvents(events, setOf(10)).map { it.id })
    }

    @Test
    fun `no visible animals means no records`() {
        assertTrue(SanitaryScope.visibleEvents(listOf(healthEvent(1, 10)), emptySet()).isEmpty())
    }

    @Test
    fun `a record with a next due date is a pending follow-up`() {
        assertTrue(healthEvent(1, 10, nextDueDate = "2026-10-20").hasFollowUp)
        assertFalse(healthEvent(2, 10).hasFollowUp)
    }
}

class SanitaryRepositoryTest {
    private val api = mockk<SanitaryApi>()
    private val dao = mockk<SanitaryDao>(relaxed = true)
    private val livestockDao = mockk<LivestockDao>()
    private val repository = SanitaryRepositoryImpl(api, dao, livestockDao)

    private fun dto(id: Int, animalId: Int) = HealthEventDto(id = id, animalId = animalId, type = "Incidencia", date = "2026-09-01")

    @Test
    fun `refresh caches only records of the animals the user can see`() = runTest {
        coEvery { api.getEvents() } returns listOf(dto(1, 10), dto(2, 99), dto(3, 11))
        coEvery { livestockDao.animalIds() } returns listOf(10, 11)
        val cached = slot<List<HealthEventEntity>>()
        coEvery { dao.replaceAll(capture(cached)) } returns Unit

        val result = repository.refresh()

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(listOf(1, 3), cached.captured.map { it.id })
    }

    @Test
    fun `a failed refresh leaves the cache untouched`() = runTest {
        coEvery { api.getEvents() } throws IOException("offline")

        val result = repository.refresh()

        assertEquals(AppResult.Failure(AppError.Network), result)
        coVerify(exactly = 0) { dao.replaceAll(any()) }
    }

    @Test
    fun `deleting a record removes it from the cache only after the server accepts`() = runTest {
        coEvery { api.deleteEvent(4) } returns Unit

        repository.delete(4)

        coVerify { dao.delete(4) }
    }
}
