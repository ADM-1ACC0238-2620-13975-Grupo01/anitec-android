package com.anitec.platform.reminders.infrastructure

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.anitec.platform.activities.infrastructure.local.ActivitiesDao
import com.anitec.platform.activities.infrastructure.toDomain
import com.anitec.platform.reminders.domain.Reminder
import com.anitec.platform.reminders.domain.ReminderPlanner
import com.anitec.platform.reminders.domain.ReminderScheduler
import com.anitec.platform.reminders.domain.ReminderSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows the reminder of one activity when its time comes. It looks at the activity again first: if it was
 * completed, cancelled, deleted or moved to another day meanwhile, or the user turned reminders off, nothing is shown.
 */
@HiltWorker
class ActivityReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val activities: ActivitiesDao,
    private val settings: ReminderSettings,
    private val notifications: ReminderNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val activityId = inputData.getInt(KEY_ACTIVITY_ID, Int.MIN_VALUE)
        if (activityId == Int.MIN_VALUE || !settings.isEnabled()) return Result.success()
        val activity = activities.byId(activityId)?.toDomain() ?: return Result.success()
        if (ReminderPlanner.isDueToday(activity, LocalDate.now())) notifications.show(activity)
        return Result.success()
    }

    companion object {
        const val KEY_ACTIVITY_ID = "activity_id"
    }
}

/**
 * Keeps one delayed WorkManager job for each planned reminder. WorkManager survives restarts of the app and the
 * device and needs no exact-alarm permission; the reminder may arrive a little after its time when the device is idle.
 */
@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    @Synchronized
    override fun reconcile(reminders: List<Reminder>) {
        val desired = reminders.associateBy { workName(it.activityId) }
        val scheduled = workManager.getWorkInfosByTag(TAG).get().filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }

        // Whatever is scheduled but no longer planned (completed, cancelled, deleted, signed out) is removed...
        scheduled.forEach { info ->
            if (info.tags.firstOrNull { it.startsWith(NAME_PREFIX) } !in desired) workManager.cancelWorkById(info.id)
        }
        // ...and each planned one is (re)scheduled unless it is already waiting for the same moment.
        desired.forEach { (name, reminder) ->
            val alreadyThere = scheduled.any { name in it.tags && atTag(reminder.triggerAtMillis) in it.tags }
            if (!alreadyThere) workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request(name, reminder))
        }
    }

    @Synchronized
    override fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
    }

    private fun request(name: String, reminder: Reminder) =
        OneTimeWorkRequestBuilder<ActivityReminderWorker>()
            .setInitialDelay((reminder.triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ActivityReminderWorker.KEY_ACTIVITY_ID to reminder.activityId))
            .addTag(TAG)
            .addTag(name)
            .addTag(atTag(reminder.triggerAtMillis))
            .build()

    private fun workName(activityId: Int) = "$NAME_PREFIX$activityId"

    private fun atTag(triggerAtMillis: Long) = "reminder-at-$triggerAtMillis"

    private companion object {
        const val TAG = "activity-reminders"
        const val NAME_PREFIX = "activity-reminder-"
    }
}
