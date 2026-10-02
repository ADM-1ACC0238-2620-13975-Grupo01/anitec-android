package com.anitec.platform.activities

import com.anitec.platform.activities.domain.Activity
import com.anitec.platform.activities.domain.ActivityDraft
import com.anitec.platform.activities.domain.ActivityScope
import com.anitec.platform.activities.infrastructure.ActivitiesRepositoryImpl
import com.anitec.platform.activities.infrastructure.local.ActivitiesDao
import com.anitec.platform.activities.infrastructure.local.ActivityEntity
import com.anitec.platform.activities.infrastructure.remote.ActivitiesApi
import com.anitec.platform.activities.infrastructure.remote.FarmActivityDto
import com.anitec.platform.activities.interfaces.viewmodel.sortedForDisplay
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.veterinary.infrastructure.local.VeterinaryDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 10, 2)

private fun activity(
    id: Int,
    date: String,
    ownerId: Int? = 10,
    veterinarianId: Int? = null,
    status: String = "Pendiente",
) = Activity(id, ownerId, veterinarianId, "Task $id", "Sanitario", date, "Media", status)

class ActivityRulesTest {

    @Test
    fun `an activity is upcoming from today on unless it is completed`() {
        assertTrue(activity(1, "2026-10-02").isUpcoming(TODAY))
        assertTrue(activity(2, "2026-12-01", status = "Programado").isUpcoming(TODAY))
        assertFalse(activity(3, "2026-10-01").isUpcoming(TODAY))
        assertFalse(activity(4, "2026-12-01", status = "Completado").isUpcoming(TODAY))
        assertFalse(activity(5, "not a date").isUpcoming(TODAY))
    }

    @Test
    fun `upcoming activities come first soonest first then the past most recent first`() {
        val sorted = listOf(
            activity(1, "2026-09-01"), activity(2, "2026-11-01"), activity(3, "2026-10-05"),
            activity(4, "2026-09-20"), activity(5, "2026-10-10", status = "Completado"),
        ).sortedForDisplay(TODAY)

        assertEquals(listOf(3, 2, 5, 4, 1), sorted.map { it.id })
    }

    @Test
    fun `a rancher sees only activities about them`() {
        val all = listOf(activity(1, "2026-10-05", ownerId = 10), activity(2, "2026-10-05", ownerId = 11))
        assertEquals(listOf(1), ActivityScope.visible(all, UserRole.Rancher, 10, emptySet()).map { it.id })
    }

    @Test
    fun `a veterinarian sees what they created and what concerns their clients`() {
        val all = listOf(
            activity(1, "2026-10-05", ownerId = null, veterinarianId = 50),   // created by me, no client
            activity(2, "2026-10-05", ownerId = 11, veterinarianId = null),   // a client created it
            activity(3, "2026-10-05", ownerId = 12, veterinarianId = null),   // unrelated rancher
            activity(4, "2026-10-05", ownerId = 11, veterinarianId = 99),     // client's, set by another vet
        )
        assertEquals(setOf(1, 2, 4), ActivityScope.visible(all, UserRole.Veterinarian, 50, setOf(11)).map { it.id }.toSet())
    }

    @Test
    fun `a rancher owns what they create and a veterinarian assigns it to a client`() {
        assertEquals(10 to null, ActivityScope.ownership(UserRole.Rancher, 10, selectedClientId = 99))
        assertEquals(11 to 50, ActivityScope.ownership(UserRole.Veterinarian, 50, selectedClientId = 11))
    }
}

class ActivitiesRepositoryTest {
    private val api = mockk<ActivitiesApi>()
    private val dao = mockk<ActivitiesDao>(relaxed = true)
    private val veterinaryDao = mockk<VeterinaryDao>()
    private val outbox = mockk<com.anitec.platform.core.outbox.OutboxQueue>(relaxed = true)

    private fun repository(role: UserRole, userId: Int): ActivitiesRepositoryImpl {
        val sessionStore = mockk<SessionStore>()
        every { sessionStore.state } returns MutableStateFlow<SessionState>(SessionState.SignedIn(UserSession(userId, "u", "U", role, "t")))
        return ActivitiesRepositoryImpl(api, dao, veterinaryDao, sessionStore, outbox)
    }

    private fun dto(id: Int, ownerId: Int?, vetId: Int?) =
        FarmActivityDto(id = id, ownerId = ownerId, veterinarianId = vetId, title = "T$id", type = "Sanitario", date = "2026-10-05", priority = "Media", status = "Pendiente")

    @Test
    fun `refresh caches only the activities of a veterinarian and their clients`() = runTest {
        coEvery { api.getActivities() } returns listOf(dto(1, null, 50), dto(2, 11, null), dto(3, 12, null))
        coEvery { veterinaryDao.rancherIds() } returns listOf(11)
        val cached = slot<List<ActivityEntity>>()
        coEvery { dao.replaceAll(capture(cached)) } returns Unit

        assertEquals(AppResult.Success(Unit), repository(UserRole.Veterinarian, 50).refresh())

        assertEquals(setOf(1, 2), cached.captured.map { it.id }.toSet())
    }

    @Test
    fun `a failed refresh keeps the cache`() = runTest {
        coEvery { api.getActivities() } throws IOException("offline")

        assertEquals(AppResult.Failure(AppError.Network), repository(UserRole.Rancher, 10).refresh())
        coVerify(exactly = 0) { dao.replaceAll(any()) }
    }

    @Test
    fun `creating an activity sends the owner and creator and caches the answer`() = runTest {
        val sent = slot<FarmActivityDto>()
        coEvery { api.create(capture(sent)) } returns dto(7, 11, 50)

        val result = repository(UserRole.Veterinarian, 50)
            .create(ActivityDraft(11, 50, "Visit", "Visita veterinaria", "2026-10-09", "Alta", "Programado"))

        assertTrue(result is AppResult.Success)
        assertEquals(11, sent.captured.ownerId)
        assertEquals(50, sent.captured.veterinarianId)
        coVerify { dao.upsert(match { it.id == 7 }) }
    }

    @Test
    fun `deleting removes the row only after the server agrees`() = runTest {
        coEvery { api.delete(4) } returns Unit
        repository(UserRole.Rancher, 10).delete(4)
        coVerify { dao.delete(4) }
    }
}
