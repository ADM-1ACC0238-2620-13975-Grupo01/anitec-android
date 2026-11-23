package com.anitec.platform.reminders

// Test coverage marker: activity reminder scenarios (TS-009, US-026, US-027, US-028).

import com.anitec.platform.activities.domain.ActivitiesRepository
import com.anitec.platform.activities.domain.Activity
import com.anitec.platform.activities.domain.ActivityDraft
import com.anitec.platform.activities.domain.ActivityStatuses
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.reminders.domain.Reminder
import com.anitec.platform.reminders.domain.ReminderPlanner
import com.anitec.platform.reminders.domain.ReminderScheduler
import com.anitec.platform.reminders.domain.ReminderSettings
import com.anitec.platform.reminders.infrastructure.ReminderCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val lima = ZoneId.of("America/Lima")

private fun activity(id: Int, date: String, status: String = ActivityStatuses.PENDING, title: String = "Activity $id") =
    Activity(id, ownerId = 10, veterinarianId = null, title = title, type = "Sanitario", date = date, priority = "Alta", status = status)

private fun at(text: String): ZonedDateTime = ZonedDateTime.parse("${text}[America/Lima]")

private fun triggerOf(day: String, hour: Int = 8): Long =
    LocalDate.parse(day).atTime(hour, 0).atZone(lima).toInstant().toEpochMilli()

class ReminderPlannerTest {
    private val now = at("2026-11-23T10:00:00-05:00")

    @Test
    fun `an open activity gets one reminder at 8 in the morning of its day`() {
        val plan = ReminderPlanner.plan(listOf(activity(1, "2026-11-25")), now)

        assertEquals(listOf(Reminder(1, triggerOf("2026-11-25"))), plan)
    }

    @Test
    fun `the time is the one of the device zone`() {
        val inMadrid = ZonedDateTime.parse("2026-11-23T10:00:00+01:00[Europe/Madrid]")

        val plan = ReminderPlanner.plan(listOf(activity(1, "2026-11-25")), inMadrid)

        assertEquals(LocalDate.parse("2026-11-25").atTime(8, 0).atZone(ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli(), plan.single().triggerAtMillis)
    }

    @Test
    fun `an activity of today gets a reminder only if 8 has not passed`() {
        val early = ReminderPlanner.plan(listOf(activity(1, "2026-11-23")), at("2026-11-23T07:30:00-05:00"))
        val late = ReminderPlanner.plan(listOf(activity(1, "2026-11-23")), at("2026-11-23T08:30:00-05:00"))

        assertEquals(1, early.size)
        assertTrue(late.isEmpty())
    }

    @Test
    fun `past activities get nothing`() {
        assertTrue(ReminderPlanner.plan(listOf(activity(1, "2026-11-20")), now).isEmpty())
    }

    @Test
    fun `completed and cancelled activities get no reminder`() {
        val plan = ReminderPlanner.plan(
            listOf(
                activity(1, "2026-11-25", ActivityStatuses.COMPLETED),
                activity(2, "2026-11-25", ActivityStatuses.CANCELLED),
                activity(3, "2026-11-25", ActivityStatuses.SCHEDULED),
                activity(4, "2026-11-25", "cancelado"),
            ),
            now,
        )

        assertEquals(listOf(3), plan.map { it.activityId })
    }

    @Test
    fun `an activity without a readable date gets nothing and does not break the others`() {
        val plan = ReminderPlanner.plan(listOf(activity(1, "soon"), activity(2, ""), activity(3, "2026-11-26")), now)

        assertEquals(listOf(3), plan.map { it.activityId })
    }

    @Test
    fun `reminders are sorted by time and an id is never repeated`() {
        val plan = ReminderPlanner.plan(listOf(activity(1, "2026-12-01"), activity(2, "2026-11-24"), activity(1, "2026-12-01")), now)

        assertEquals(listOf(2, 1), plan.map { it.activityId })
    }

    @Test
    fun `activities created offline, with a negative id, are planned like any other`() {
        assertEquals(listOf(-3), ReminderPlanner.plan(listOf(activity(-3, "2026-11-25")), now).map { it.activityId })
    }

    @Test
    fun `an activity is due today only when it is open and its day is today`() {
        val today = LocalDate.parse("2026-11-23")

        assertTrue(ReminderPlanner.isDueToday(activity(1, "2026-11-23"), today))
        assertFalse(ReminderPlanner.isDueToday(activity(1, "2026-11-24"), today))
        assertFalse(ReminderPlanner.isDueToday(activity(1, "2026-11-23", ActivityStatuses.COMPLETED), today))
        assertFalse(ReminderPlanner.isDueToday(activity(1, "2026-11-23", ActivityStatuses.CANCELLED), today))
        assertFalse(ReminderPlanner.isDueToday(activity(1, "no date"), today))
    }
}

class CancelledActivitiesTest {
    private val today = LocalDate.parse("2026-11-23")

    @Test
    fun `a cancelled activity is not open and never upcoming`() {
        val cancelled = activity(1, "2026-12-01", ActivityStatuses.CANCELLED)

        assertFalse(cancelled.isOpen)
        assertFalse(cancelled.isUpcoming(today))
    }

    @Test
    fun `pending and scheduled activities are open, completed ones are not`() {
        assertTrue(activity(1, "2026-12-01", ActivityStatuses.PENDING).isOpen)
        assertTrue(activity(1, "2026-12-01", ActivityStatuses.SCHEDULED).isOpen)
        assertFalse(activity(1, "2026-12-01", ActivityStatuses.COMPLETED).isOpen)
        assertTrue(ActivityStatuses.CANCELLED in ActivityStatuses.all)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderCoordinatorTest {

    private class FakeRepository(initial: List<Activity>) : ActivitiesRepository {
        val cache = MutableStateFlow(initial)
        override fun observeActivities(): Flow<List<Activity>> = cache
        override suspend fun refresh() = AppResult.Success(Unit)
        override suspend fun create(draft: ActivityDraft): AppResult<Activity> = error("not used")
        override suspend fun update(id: Int, draft: ActivityDraft): AppResult<Activity> = error("not used")
        override suspend fun delete(id: Int) = AppResult.Success(Unit)
    }

    private class FakeSettings(initial: Boolean = true) : ReminderSettings {
        private val state = MutableStateFlow(initial)
        override val enabled: StateFlow<Boolean> get() = state
        override fun isEnabled() = state.value
        override fun setEnabled(enabled: Boolean) { state.value = enabled }
    }

    private class RecordingScheduler : ReminderScheduler {
        val calls = mutableListOf<List<Int>>()
        override fun reconcile(reminders: List<Reminder>) { calls.add(reminders.map { it.activityId }.sorted()) }
        override fun cancelAll() { calls.add(emptyList()) }
    }

    private val tomorrow = LocalDate.now().plusDays(1).toString()
    private val nextWeek = LocalDate.now().plusDays(7).toString()
    private val scheduler = RecordingScheduler()

    private fun start(scope: CoroutineScope, repository: FakeRepository, settings: FakeSettings = FakeSettings()) =
        ReminderCoordinator(repository, settings, scheduler).start(scope)

    @Test
    fun `the pending activities are scheduled when the app starts`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeRepository(listOf(activity(1, tomorrow), activity(2, nextWeek), activity(3, tomorrow, ActivityStatuses.COMPLETED)))

        start(backgroundScope, repository)

        assertEquals(listOf(listOf(1, 2)), scheduler.calls)
    }

    @Test
    fun `cancelling an activity removes its reminder`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeRepository(listOf(activity(1, tomorrow), activity(2, nextWeek)))
        start(backgroundScope, repository)

        repository.cache.value = listOf(activity(1, tomorrow, ActivityStatuses.CANCELLED), activity(2, nextWeek))

        assertEquals(listOf(2), scheduler.calls.last())
    }

    @Test
    fun `completing an activity removes its reminder`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeRepository(listOf(activity(1, tomorrow)))
        start(backgroundScope, repository)

        repository.cache.value = listOf(activity(1, tomorrow, ActivityStatuses.COMPLETED))

        assertEquals(emptyList<Int>(), scheduler.calls.last())
    }

    @Test
    fun `rescheduling an activity replaces its reminder with one for the new day`() = runTest(UnconfinedTestDispatcher()) {
        val recorded = mutableListOf<List<Reminder>>()
        val capturing = object : ReminderScheduler {
            override fun reconcile(reminders: List<Reminder>) { recorded.add(reminders) }
            override fun cancelAll() = Unit
        }
        val repository = FakeRepository(listOf(activity(1, tomorrow)))
        ReminderCoordinator(repository, FakeSettings(), capturing).start(backgroundScope)

        repository.cache.value = listOf(activity(1, nextWeek))

        assertEquals(2, recorded.size)
        assertEquals(1, recorded[1].single().activityId)
        assertEquals(triggerOf(nextWeek, 8, ZoneId.systemDefault()), recorded[1].single().triggerAtMillis)
        assertTrue(recorded[0].single().triggerAtMillis != recorded[1].single().triggerAtMillis)
    }

    @Test
    fun `an activity deleted or an emptied cache, as after signing out, leaves nothing scheduled`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeRepository(listOf(activity(1, tomorrow), activity(2, nextWeek)))
        start(backgroundScope, repository)

        repository.cache.value = emptyList()

        assertEquals(emptyList<Int>(), scheduler.calls.last())
    }

    @Test
    fun `turning reminders off removes them all and turning them on brings them back`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeRepository(listOf(activity(1, tomorrow)))
        val settings = FakeSettings()
        start(backgroundScope, repository, settings)

        settings.setEnabled(false)
        assertEquals(emptyList<Int>(), scheduler.calls.last())
        settings.setEnabled(true)

        assertEquals(listOf(1), scheduler.calls.last())
    }

    @Test
    fun `a change that does not alter the plan does not touch the scheduler again`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeRepository(listOf(activity(1, tomorrow, title = "Old title")))
        start(backgroundScope, repository)

        repository.cache.value = listOf(activity(1, tomorrow, title = "New title"))

        assertEquals(1, scheduler.calls.size)
    }

    private fun triggerOf(day: String, hour: Int, zone: ZoneId): Long =
        LocalDate.parse(day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
}
