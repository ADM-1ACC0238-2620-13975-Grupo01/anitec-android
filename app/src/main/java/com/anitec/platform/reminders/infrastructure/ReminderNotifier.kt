package com.anitec.platform.reminders.infrastructure

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anitec.platform.MainActivity
import com.anitec.platform.R
import com.anitec.platform.activities.domain.Activity
import com.anitec.platform.activities.interfaces.ui.activityPriorityRes
import com.anitec.platform.activities.interfaces.ui.activityTypeRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** What the app asks the system to show, and whether the system lets it. */
interface ReminderNotifications {
    /** True when the system lets the app show notifications (permission granted and not turned off in settings). */
    fun canNotify(): Boolean

    /** Shows the reminder of [activity]; returns false, showing nothing, when notifications are not allowed. */
    fun show(activity: Activity): Boolean
}

/**
 * Shows the reminders of activities as system notifications on their own channel.
 *
 * Tapping a notification opens the app on that activity: the intent carries its id (see [ReminderIntents]), so the
 * notification always refers to a valid activity (TS-009 scenario 1).
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderNotifications {

    override fun canNotify(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // Checked in hasPermission(): the permission only exists from Android 13.
    override fun show(activity: Activity): Boolean {
        if (!canNotify() || !hasPermission()) return false
        ensureChannel()

        val type = activityTypeRes(activity.type)?.let(context::getString) ?: activity.type
        val priority = activityPriorityRes(activity.priority)?.let(context::getString) ?: activity.priority
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(activity.title)
            .setContentText(context.getString(R.string.reminder_text, type, priority))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openActivityIntent(activity.id))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(activity.id), notification)
        return true
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openActivityIntent(activityId: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            notificationId(activityId),
            ReminderIntents.openActivity(context, activityId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    internal companion object {
        const val CHANNEL_ID = "activity_reminders"

        /** One notification per activity, so showing it again replaces it instead of stacking. */
        fun notificationId(activityId: Int): Int = 10_000 + activityId
    }
}

/** The intent that a reminder notification carries, and how the app reads it back. */
object ReminderIntents {
    const val ACTION_OPEN_ACTIVITY = "com.anitec.platform.action.OPEN_ACTIVITY"
    const val EXTRA_ACTIVITY_ID = "activity_id"

    /** An intent that brings the app to the front on the activity with [activityId]. */
    fun openActivity(context: Context, activityId: Int): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_ACTIVITY
            putExtra(EXTRA_ACTIVITY_ID, activityId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    /** The activity id a reminder intent points to, or null for any other intent. */
    fun activityIdOf(intent: Intent?): Int? =
        if (intent?.action == ACTION_OPEN_ACTIVITY && intent.hasExtra(EXTRA_ACTIVITY_ID)) intent.getIntExtra(EXTRA_ACTIVITY_ID, 0) else null
}
