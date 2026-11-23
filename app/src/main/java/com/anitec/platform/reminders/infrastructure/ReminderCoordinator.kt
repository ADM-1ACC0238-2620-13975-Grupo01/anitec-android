package com.anitec.platform.reminders.infrastructure

import android.content.Context
import com.anitec.platform.activities.domain.ActivitiesRepository
import com.anitec.platform.reminders.domain.Reminder
import com.anitec.platform.reminders.domain.ReminderPlanner
import com.anitec.platform.reminders.domain.ReminderScheduler
import com.anitec.platform.reminders.domain.ReminderSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the scheduled reminders equal to the plan for the cached activities.
 *
 * It watches the activities and the user's choice, and after every change makes the scheduler match
 * [ReminderPlanner.plan]. Creating, rescheduling, completing, cancelling or deleting an activity, a refresh from the
 * server (which also gives a record its real id after an offline sync), turning reminders off and signing out
 * (which clears the cache) all end up here, so none of them needs its own scheduling code.
 */
@Singleton
class ReminderCoordinator @Inject constructor(
    private val activities: ActivitiesRepository,
    private val settings: ReminderSettings,
    private val scheduler: ReminderScheduler,
) {
    /** Starts following the cache in [scope]; call it once, when the process starts. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(activities.observeActivities(), settings.enabled) { list, enabled ->
                if (enabled) ReminderPlanner.plan(list, ZonedDateTime.now()) else emptyList<Reminder>()
            }
                .distinctUntilChanged()
                .collect { scheduler.reconcile(it) }
        }
    }
}

/** The user's choice of getting reminders, kept in the app's private preferences. Reminders are on by default. */
@Singleton
class SharedPreferencesReminderSettings @Inject constructor(
    @ApplicationContext context: Context,
) : ReminderSettings {

    private val preferences = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, true))

    override val enabled: StateFlow<Boolean> get() = state.asStateFlow()

    override fun isEnabled(): Boolean = state.value

    override fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        state.value = enabled
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderBindingsModule {
    @Binds
    abstract fun bindScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler

    @Binds
    abstract fun bindSettings(impl: SharedPreferencesReminderSettings): ReminderSettings

    @Binds
    abstract fun bindNotifications(impl: ReminderNotifier): ReminderNotifications
}
