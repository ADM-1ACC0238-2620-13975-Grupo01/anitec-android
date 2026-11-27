package com.anitec.platform.sanitary

// Test coverage marker: schedule the next check as an activity (US-019).

import com.anitec.platform.activities.domain.ActivitiesRepository
import com.anitec.platform.activities.domain.Activity
import com.anitec.platform.activities.domain.ActivityDraft
import com.anitec.platform.activities.domain.ActivityPriorities
import com.anitec.platform.activities.domain.ActivityStatuses
import com.anitec.platform.activities.domain.ActivityTypes
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.sanitary.application.ScheduleFollowUpUseCase
import com.anitec.platform.sanitary.domain.FollowUpRules
import com.anitec.platform.sanitary.interfaces.viewmodel.HealthFormUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpRulesTest {

    @Test
    fun `a next check is optional`() {
        assertTrue(FollowUpRules.isValidNextCheck("2026-11-23", ""))
        assertTrue(FollowUpRules.isValidNextCheck("2026-11-23", "   "))
    }

    @Test
    fun `a next check must be a day after the record`() {
        assertTrue(FollowUpRules.isValidNextCheck("2026-11-23", "2026-11-24"))
        assertTrue(FollowUpRules.isValidNextCheck("2026-11-23", "2027-01-05"))
        assertFalse(FollowUpRules.isValidNextCheck("2026-11-23", "2026-11-23"))
        assertFalse(FollowUpRules.isValidNextCheck("2026-11-23", "2026-11-22"))
    }

    @Test
    fun `a next check that is not a date is refused`() {
        assertFalse(FollowUpRules.isValidNextCheck("2026-11-23", "next week"))
        assertFalse(FollowUpRules.isValidNextCheck("2026-11-23", "2026-13-40"))
    }

    @Test
    fun `the form is not valid with a next check that does not follow the record, and says so`() {
        val valid = HealthFormUiState(animalId = 1, date = "2026-11-23", veterinarian = "Dra. Ana", description = "Control", nextDueDate = "2026-12-01")
        val sameDay = valid.copy(nextDueDate = "2026-11-23")
        val before = valid.copy(nextDueDate = "2026-11-01")
        val none = valid.copy(nextDueDate = "")

        assertTrue(valid.isValid)
        assertFalse(valid.nextDueInvalid)
        assertTrue(sameDay.nextDueInvalid)
        assertFalse(sameDay.isValid)
        assertTrue(before.nextDueInvalid)
        assertTrue(none.isValid)
    }
}

class ScheduleFollowUpUseCaseTest {
    private val activities = mockk<ActivitiesRepository>()
    private val sent = slot<ActivityDraft>()

    @Test
    fun `the next check becomes a scheduled visit for the rancher, created by the veterinarian`() = runTest {
        coEvery { activities.create(capture(sent)) } returns AppResult.Success(
            Activity(7, 10, 50, "Follow-up check: Lola", ActivityTypes.VET_VISIT, "2026-12-01", ActivityPriorities.MEDIUM, ActivityStatuses.SCHEDULED),
        )

        val result = ScheduleFollowUpUseCase(activities)(ownerId = 10, veterinarianId = 50, title = "Follow-up check: Lola", date = "2026-12-01")

        assertTrue(result is AppResult.Success)
        with(sent.captured) {
            assertEquals(10, ownerId)
            assertEquals(50, veterinarianId)
            assertEquals("Follow-up check: Lola", title)
            assertEquals(ActivityTypes.VET_VISIT, type)
            assertEquals("2026-12-01", date)
            assertEquals(ActivityPriorities.MEDIUM, priority)
            assertEquals(ActivityStatuses.SCHEDULED, status)
        }
    }

    @Test
    fun `a failure is returned to the caller`() = runTest {
        coEvery { activities.create(any()) } returns AppResult.Failure(AppError.Forbidden)

        val result = ScheduleFollowUpUseCase(activities)(10, 50, "t", "2026-12-01")

        assertEquals(AppResult.Failure(AppError.Forbidden), result)
        coVerify(exactly = 1) { activities.create(any()) }
    }
}
