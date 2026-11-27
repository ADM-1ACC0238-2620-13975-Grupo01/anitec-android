package com.anitec.platform.activities

// Test coverage marker: attend, reschedule and cancel an activity (US-026, US-028).

import com.anitec.platform.R
import com.anitec.platform.activities.application.DeleteActivityUseCase
import com.anitec.platform.activities.application.ObserveActivitiesUseCase
import com.anitec.platform.activities.application.RefreshActivitiesUseCase
import com.anitec.platform.activities.application.SaveActivityUseCase
import com.anitec.platform.activities.domain.ActivitiesRepository
import com.anitec.platform.activities.domain.Activity
import com.anitec.platform.activities.domain.ActivityDraft
import com.anitec.platform.activities.domain.ActivityRules
import com.anitec.platform.activities.domain.ActivityStatuses
import com.anitec.platform.activities.interfaces.viewmodel.ActivityFormUiState
import com.anitec.platform.activities.interfaces.viewmodel.ActivityListViewModel
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.veterinary.application.RefreshClientsUseCase
import com.anitec.platform.veterinary.domain.VeterinaryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

private val today: LocalDate = LocalDate.now()

private fun open(id: Int = 1, date: String = today.plusDays(2).toString(), version: Int = 3) = Activity(
    id, ownerId = 10, veterinarianId = null, title = "Dip cattle", type = "Sanitario", date = date,
    priority = "Alta", status = ActivityStatuses.PENDING, version = version,
)

class ActivityRulesDatesTest {
    @Test
    fun `an activity can be moved to today or a later day only`() {
        assertTrue(ActivityRules.canScheduleOn(today.toString(), today))
        assertTrue(ActivityRules.canScheduleOn(today.plusDays(30).toString(), today))
        assertFalse(ActivityRules.canScheduleOn(today.minusDays(1).toString(), today))
        assertFalse(ActivityRules.canScheduleOn("tomorrow", today))
        assertFalse(ActivityRules.canScheduleOn("", today))
    }

    @Test
    fun `the draft of an activity keeps its values, the version it is based on and what changes`() {
        val activity = open(version = 5)

        val attended = activity.toDraft(status = ActivityStatuses.COMPLETED)
        val moved = activity.toDraft(date = "2027-01-15")

        assertEquals(ActivityStatuses.COMPLETED, attended.status)
        assertEquals(activity.date, attended.date)
        assertEquals(5, attended.baseVersion)
        assertEquals("2027-01-15", moved.date)
        assertEquals(ActivityStatuses.PENDING, moved.status)
        assertEquals(listOf(activity.title, activity.type, activity.priority, activity.ownerId), listOf(moved.title, moved.type, moved.priority, moved.ownerId))
    }

    @Test
    fun `an open activity cannot be moved to a past day in the form, but an unchanged overdue date is allowed`() {
        val yesterday = today.minusDays(1).toString()

        val moved = ActivityFormUiState(isEdit = true, title = "T", date = yesterday, status = ActivityStatuses.PENDING, originalDate = today.plusDays(3).toString())
        val untouched = ActivityFormUiState(isEdit = true, title = "T", date = yesterday, status = ActivityStatuses.PENDING, originalDate = yesterday)
        val completed = ActivityFormUiState(isEdit = true, title = "T", date = yesterday, status = ActivityStatuses.COMPLETED, originalDate = today.plusDays(3).toString())
        val created = ActivityFormUiState(title = "T", date = yesterday)

        assertTrue(moved.dateInPast)
        assertFalse(moved.isValid)
        assertFalse(untouched.dateInPast)
        assertTrue(untouched.isValid)
        assertFalse(completed.dateInPast)
        assertTrue(created.dateInPast)
        assertFalse(ActivityFormUiState(title = "T", date = today.toString()).dateInPast)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityListActionsTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val repository = mockk<ActivitiesRepository>()
    private val saved = slot<ActivityDraft>()

    private fun viewModel(scope: kotlinx.coroutines.CoroutineScope, saveResult: AppResult<Activity> = AppResult.Success(open())): ActivityListViewModel {
        every { repository.observeActivities() } returns flowOf(listOf(open()))
        coEvery { repository.refresh() } returns AppResult.Success(Unit)
        coEvery { repository.update(any(), capture(saved)) } returns saveResult
        val sessionStore = mockk<SessionStore>()
        every { sessionStore.state } returns MutableStateFlow<SessionState>(SessionState.SignedIn(UserSession(10, "u", "U", UserRole.Rancher, "t")))
        val veterinary = mockk<VeterinaryRepository>()
        coEvery { veterinary.refreshClients() } returns AppResult.Success(Unit)
        val viewModel = ActivityListViewModel(
            ObserveActivitiesUseCase(repository), RefreshActivitiesUseCase(repository), RefreshClientsUseCase(veterinary),
            DeleteActivityUseCase(repository), SaveActivityUseCase(repository), sessionStore,
        )
        scope.launch { viewModel.state.collect {} }
        return viewModel
    }

    @Test
    fun `marking an activity as attended completes it and keeps its date`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel(backgroundScope)

        viewModel.markAttended(open(date = "2026-12-01", version = 3))

        assertEquals(ActivityStatuses.COMPLETED, saved.captured.status)
        assertEquals("2026-12-01", saved.captured.date)
        assertEquals(3, saved.captured.baseVersion)
        assertEquals(R.string.activity_attended_message, viewModel.state.value.messageRes)
    }

    @Test
    fun `cancelling asks first, and does nothing if the user keeps the activity`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel(backgroundScope)
        val activity = open()

        viewModel.requestCancel(activity)
        assertEquals(activity, viewModel.state.value.pendingCancel)
        viewModel.dismissCancel()

        assertNull(viewModel.state.value.pendingCancel)
        coVerify(exactly = 0) { repository.update(any(), any()) }
    }

    @Test
    fun `confirming the cancellation saves the cancelled status and tells the user`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel(backgroundScope)

        viewModel.requestCancel(open())
        viewModel.confirmCancel()

        assertEquals(ActivityStatuses.CANCELLED, saved.captured.status)
        assertEquals(R.string.activity_cancelled_message, viewModel.state.value.messageRes)
        assertNull(viewModel.state.value.pendingCancel)
    }

    @Test
    fun `rescheduling to a later day saves the new date and keeps the status`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel(backgroundScope)
        val newDate = today.plusDays(10).toString()

        viewModel.requestReschedule(open())
        viewModel.reschedule(newDate)

        assertEquals(newDate, saved.captured.date)
        assertEquals(ActivityStatuses.PENDING, saved.captured.status)
        assertEquals(R.string.activity_rescheduled_message, viewModel.state.value.messageRes)
    }

    @Test
    fun `rescheduling to a past day is refused and nothing is saved`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel(backgroundScope)

        viewModel.requestReschedule(open())
        viewModel.reschedule(today.minusDays(1).toString())

        coVerify(exactly = 0) { repository.update(any(), any()) }
        assertEquals(R.string.activity_date_past, viewModel.state.value.messageRes)
        assertNull(viewModel.state.value.pendingReschedule)
    }

    @Test
    fun `choosing the same day changes nothing`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel(backgroundScope)
        val activity = open()

        viewModel.requestReschedule(activity)
        viewModel.reschedule(activity.date)

        coVerify(exactly = 0) { repository.update(any(), any()) }
    }

    @Test
    fun `a change that the server refuses is reported`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel(backgroundScope, AppResult.Failure(AppError.OutOfDate(4)))

        viewModel.markAttended(open())

        assertEquals(AppError.OutOfDate(4).messageRes(), viewModel.state.value.messageRes)
    }
}
