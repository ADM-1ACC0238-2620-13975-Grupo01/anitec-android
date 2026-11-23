package com.anitec.platform.activities.domain

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.UserRole
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * A scheduled farm task. [ownerId] is the rancher it concerns; [veterinarianId] is the professional who
 * created it, when one did. Dates are ISO (yyyy-MM-dd). [type], [priority] and [status] keep the API text.
 */
data class Activity(
    val id: Int,
    val ownerId: Int?,
    val veterinarianId: Int?,
    val title: String,
    val type: String,
    val date: String,
    val priority: String,
    val status: String,
    /** Version the server holds; an edit sends it back so a change made elsewhere is not overwritten. */
    val version: Int = 1,
) {
    /**
     * The values of this activity as a draft, to save a change of [status] or [date] against the version the user
     * is looking at: an edit made elsewhere in the meantime is reported as a conflict instead of overwritten.
     */
    fun toDraft(status: String = this.status, date: String = this.date) = ActivityDraft(
        ownerId = ownerId, veterinarianId = veterinarianId, title = title, type = type, date = date,
        priority = priority, status = status, baseVersion = version,
    )

    /** Still to be attended: neither completed nor cancelled. */
    val isOpen: Boolean
        get() = !status.equals(ActivityStatuses.COMPLETED, ignoreCase = true) &&
            !status.equals(ActivityStatuses.CANCELLED, ignoreCase = true)

    /** Still to be done: open and not in the past. */
    fun isUpcoming(today: LocalDate): Boolean {
        if (!isOpen) return false
        val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
        return !day.isBefore(today)
    }
}

/** Values of an activity before it is saved. */
data class ActivityDraft(
    val ownerId: Int?,
    val veterinarianId: Int?,
    val title: String,
    val type: String,
    val date: String,
    val priority: String,
    val status: String,
    /** Version of the record this edit started from; null for a new record. */
    val baseVersion: Int? = null,
)

/** Activity types, in the Spanish text the API stores. */
object ActivityTypes {
    const val HEALTH = "Sanitario"
    const val VET_VISIT = "Visita veterinaria"
    const val PRODUCTION = "Productivo"
    const val FINANCIAL = "Financiero"
    const val REPRODUCTIVE = "Reproductivo"
    val all = listOf(HEALTH, VET_VISIT, PRODUCTION, FINANCIAL, REPRODUCTIVE)
}

/** Priorities, from most to least urgent. */
object ActivityPriorities {
    const val HIGH = "Alta"
    const val MEDIUM = "Media"
    const val LOW = "Baja"
    val all = listOf(HIGH, MEDIUM, LOW)
}

/** Statuses; a [COMPLETED] or [CANCELLED] activity is never shown as upcoming and gets no reminder. */
object ActivityStatuses {
    const val PENDING = "Pendiente"
    const val SCHEDULED = "Programado"
    const val COMPLETED = "Completado"
    const val CANCELLED = "Cancelado"
    val all = listOf(PENDING, SCHEDULED, COMPLETED, CANCELLED)
}

object ActivityRules {
    /** A pending activity can only be moved to today or a later day (US-028 scenario 2). */
    fun canScheduleOn(date: String, today: LocalDate): Boolean =
        runCatching { !LocalDate.parse(date).isBefore(today) }.getOrDefault(false)
}

object ActivityScope {
    /**
     * The API returns every activity, so the client filters. A rancher sees the ones about them; a
     * veterinarian sees the ones they created and the ones about their linked clients. (The web only
     * matched the creator, which hid a veterinarian's activities from the rancher they were for.)
     */
    fun visible(activities: List<Activity>, role: UserRole, userId: Int, clientRancherIds: Set<Int>): List<Activity> =
        when (role) {
            UserRole.Rancher -> activities.filter { it.ownerId == userId }
            UserRole.Veterinarian -> activities.filter { it.veterinarianId == userId || it.ownerId in clientRancherIds }
        }

    /** Who a new activity belongs to and who created it, from the author's role. */
    fun ownership(role: UserRole, userId: Int, selectedClientId: Int?): Pair<Int?, Int?> = when (role) {
        UserRole.Rancher -> userId to null
        UserRole.Veterinarian -> selectedClientId to userId
    }
}

/** Cached activities plus the operations that keep them in sync with the API. */
interface ActivitiesRepository {
    fun observeActivities(): Flow<List<Activity>>
    suspend fun refresh(): AppResult<Unit>
    suspend fun create(draft: ActivityDraft): AppResult<Activity>
    suspend fun update(id: Int, draft: ActivityDraft): AppResult<Activity>
    suspend fun delete(id: Int): AppResult<Unit>
}
