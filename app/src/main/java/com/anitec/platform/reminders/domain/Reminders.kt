package com.anitec.platform.reminders.domain

import com.anitec.platform.activities.domain.Activity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** A notification that must be shown for an activity at [triggerAtMillis] (epoch milliseconds). */
data class Reminder(val activityId: Int, val triggerAtMillis: Long)

/**
 * Decides which reminders should exist for a list of activities (TS-009, US-026, US-027, US-028).
 *
 * The plan is a pure function of the activities and the clock, so it is recomputed after every change to the cache
 * and compared with what is scheduled: a cancelled, completed or deleted activity loses its reminder, and a
 * rescheduled one gets a new one that replaces the old (US-026 scenario 2, US-028 scenario 2).
 */
object ReminderPlanner {
    /** Time of day, in the device's time zone, at which the reminder of an activity is shown. */
    val REMINDER_TIME: LocalTime = LocalTime.of(8, 0)

    /**
     * One reminder for each open activity whose reminder time has not passed yet. An activity with no valid date,
     * one that is completed or cancelled, and one whose day already began get none.
     */
    fun plan(activities: List<Activity>, now: ZonedDateTime): List<Reminder> =
        activities
            .filter { it.isOpen }
            .mapNotNull { activity ->
                val day = runCatching { LocalDate.parse(activity.date) }.getOrNull() ?: return@mapNotNull null
                val trigger = day.atTime(REMINDER_TIME).atZone(now.zone)
                if (trigger.isAfter(now)) Reminder(activity.id, trigger.toInstant().toEpochMilli()) else null
            }
            .distinctBy { it.activityId }
            .sortedBy { it.triggerAtMillis }

    /** True when the notification of [activity] should still be shown on [today] (the day it is due). */
    fun isDueToday(activity: Activity, today: LocalDate): Boolean =
        activity.isOpen && runCatching { LocalDate.parse(activity.date) }.getOrNull() == today
}

/** Puts the planned reminders in the system so they are shown on time, even if the app is closed. */
interface ReminderScheduler {
    /** Makes the scheduled reminders exactly [reminders]: stale ones are removed, changed ones replaced. */
    fun reconcile(reminders: List<Reminder>)

    /** Removes every scheduled reminder. */
    fun cancelAll()
}

/** The user's choice of getting reminders at all. */
interface ReminderSettings {
    /** Emits the current choice and every change. Reminders are on by default. */
    val enabled: Flow<Boolean>

    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)
}
