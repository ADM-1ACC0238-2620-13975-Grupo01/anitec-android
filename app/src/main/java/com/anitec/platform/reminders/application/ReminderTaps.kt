package com.anitec.platform.reminders.application

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The activity that the user opened from a reminder notification, until the app has shown it.
 *
 * The notification starts [com.anitec.platform.MainActivity] with the activity id; the activity hands it here and
 * the signed-in shell consumes it to open the activity. Holding it here (and not navigating from the activity) is
 * what makes a tap work whether the app was closed, in the background or already open.
 */
@Singleton
class ReminderTaps @Inject constructor() {
    private val _pending = MutableStateFlow<Int?>(null)
    val pending: StateFlow<Int?> = _pending.asStateFlow()

    fun onActivityOpened(activityId: Int) {
        _pending.value = activityId
    }

    fun consume() {
        _pending.value = null
    }
}
