package com.anitec.platform.core.common

import androidx.annotation.StringRes
import com.anitec.platform.R
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** One field that differs between the edit the user made and the copy the server holds now. */
data class FieldDifference(@StringRes val labelRes: Int, val mine: String, val server: String)

/**
 * What the form shows when the server refused a save because the record changed after it was opened (US-032):
 * the version the server holds now and what differs. Both copies are kept: the form still has the user's
 * edit and the cache already has the server's.
 */
data class SaveConflict(val serverVersion: Int, val differences: List<FieldDifference>)

/** The conflict to show for this failure, or null when it is not a refused-because-outdated save. */
fun AppError.asConflict(): SaveConflict? = (this as? AppError.OutOfDate)?.let { SaveConflict(it.currentVersion, it.differences) }

private val comparingJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

/** The label of a field of a record, or null for the ones the user does not edit (ids, version, image). */
@StringRes
fun fieldLabelRes(key: String): Int? = when (key) {
    "tag" -> R.string.field_tag
    "name" -> R.string.field_name
    "species" -> R.string.field_species
    "breed" -> R.string.field_breed
    "gender" -> R.string.field_gender
    "birthDate" -> R.string.field_birth_date
    "weight" -> R.string.field_weight
    "status" -> R.string.field_status
    "source" -> R.string.field_source
    "ageRange" -> R.string.field_age_range
    "type" -> R.string.field_type
    "date" -> R.string.field_date
    "description" -> R.string.field_description
    "veterinarian" -> R.string.field_veterinarian
    "diagnosis" -> R.string.field_diagnosis
    "treatment" -> R.string.field_treatment
    "prescription" -> R.string.field_prescription
    "followUp" -> R.string.field_follow_up
    "nextDueDate" -> R.string.field_next_due_date
    "title" -> R.string.field_title
    "priority" -> R.string.field_priority
    "category" -> R.string.field_category
    "amount" -> R.string.field_amount
    else -> null
}

private fun display(element: JsonElement?): String =
    (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() } ?: "—"

/** The labelled fields whose value differs between [mine] and [server]; blank and missing count as the same. */
fun differencesBetween(mine: JsonObject, server: JsonObject): List<FieldDifference> =
    mine.keys.mapNotNull { key ->
        val label = fieldLabelRes(key) ?: return@mapNotNull null
        val mineText = display(mine[key])
        val serverText = display(server[key])
        if (mineText == serverText) null else FieldDifference(label, mineText, serverText)
    }

/**
 * Handles the answer of an update that the server refused with 412 because the record had changed: the server's
 * copy is handed to [keepServerCopy] (so the cache shows it) and the failure comes back with what differs from
 * [mine]. Any other result is returned unchanged.
 */
suspend fun <T, D> AppResult<T>.explainingConflict(
    serializer: KSerializer<D>,
    mine: D,
    keepServerCopy: suspend (D) -> Unit,
): AppResult<T> {
    val error = (this as? AppResult.Failure)?.error as? AppError.OutOfDate ?: return this
    val copy = error.serverCopy ?: return this
    val server = runCatching { comparingJson.decodeFromString(serializer, copy) }.getOrNull() ?: return this
    keepServerCopy(server)
    val differences = differencesBetween(
        comparingJson.encodeToJsonElement(serializer, mine).jsonObject,
        comparingJson.encodeToJsonElement(serializer, server).jsonObject,
    )
    return AppResult.Failure(error.copy(differences = differences))
}

/** The value of the `If-Match` header for an edit based on [version], or null when the version is not known. */
fun ifMatchOf(version: Int?): String? = version?.let { "\"$it\"" }
