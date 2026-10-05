package com.anitec.platform.sanitary.domain

import com.anitec.platform.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/** A health record of one animal. Dates are ISO (yyyy-MM-dd). */

data class HealthEvent(
    val id: Int,
    val animalId: Int,
    val type: String,
    val date: String,
    val description: String,
    val veterinarian: String,
    val diagnosis: String,
    val treatment: String,
    val prescription: String,
    val followUp: String,
    val nextDueDate: String?,
) {
    /** Open events carry a next due date; the dashboard counts them as pending follow-ups. */
    
    val hasFollowUp: Boolean get() = nextDueDate != null
}

/**
 * Data transfer object representing a draft version of a health event prior to persistence.
 */
 
data class HealthEventDraft(
    val animalId: Int,
    val type: String,
    val date: String,
    val description: String,
    val veterinarian: String,
    val diagnosis: String,
    val treatment: String,
    val prescription: String,
    val followUp: String,
    val nextDueDate: String?,
)

/** Kinds of record the web offers; the API stores them as free text. */

object HealthTypes {
    const val INCIDENT = "Incidencia"
    const val VACCINE = "Vacuna"
    const val TREATMENT = "Tratamiento"
    const val DIAGNOSIS = "Diagnostico"
    const val CHECKUP = "Revision"
    val all = listOf(INCIDENT, VACCINE, TREATMENT, DIAGNOSIS, CHECKUP)
}

object SanitaryScope {
    /** The API returns every record; a user only sees the ones of animals they can see. */
    
    fun visibleEvents(events: List<HealthEvent>, visibleAnimalIds: Set<Int>): List<HealthEvent> =
        events.filter { it.animalId in visibleAnimalIds }
}

interface SanitaryRepository {
    fun observeEvents(): Flow<List<HealthEvent>>

    /** Replaces the cached records with the ones the signed-in user may see. Run after the livestock refresh. */
    suspend fun refresh(): AppResult<Unit>

    suspend fun create(draft: HealthEventDraft): AppResult<HealthEvent>
    suspend fun update(id: Int, draft: HealthEventDraft): AppResult<HealthEvent>
    suspend fun delete(id: Int): AppResult<Unit>
}

