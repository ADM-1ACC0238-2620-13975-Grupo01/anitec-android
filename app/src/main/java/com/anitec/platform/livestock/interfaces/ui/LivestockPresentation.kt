package com.anitec.platform.livestock.interfaces.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.anitec.platform.R
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.core.designsystem.Severity
import com.anitec.platform.livestock.application.LivestockValidation
import com.anitec.platform.livestock.domain.AnimalStatus

/** A value the API stores (free text, Spanish) with the localized label the app shows. */
data class ApiOption(val value: String, @StringRes val label: Int)

object LivestockOptions {
    val species = listOf(
        ApiOption("Bovino", R.string.species_cattle),
        ApiOption("Ovino", R.string.species_sheep),
        ApiOption("Caprino", R.string.species_goat),
        ApiOption("Porcino", R.string.species_pig),
        ApiOption("Equino", R.string.species_horse),
        ApiOption("Pollo", R.string.species_chicken),
        ApiOption("Pato", R.string.species_duck),
        ApiOption("Gallina", R.string.species_hen),
        ApiOption("Pavo", R.string.species_turkey),
        ApiOption("Cuy", R.string.species_guinea_pig),
        ApiOption("Otro", R.string.species_other),
    )
    val gender = listOf(
        ApiOption("Hembra", R.string.gender_female),
        ApiOption("Macho", R.string.gender_male),
        ApiOption("Mixto", R.string.gender_mixed),
    )
    val source = listOf(
        ApiOption("Comprado", R.string.source_purchased),
        ApiOption("Nacido en la finca", R.string.source_born_on_farm),
        ApiOption("Donacion", R.string.source_donation),
        ApiOption("Otro", R.string.source_other),
    )
    val ageRange = listOf(
        ApiOption("Cria", R.string.age_young),
        ApiOption("Juvenil", R.string.age_juvenile),
        ApiOption("Adulto", R.string.age_adult),
    )
    val herdType = listOf(
        ApiOption("Bovino", R.string.species_cattle),
        ApiOption("Ovino", R.string.species_sheep),
        ApiOption("Caprino", R.string.species_goat),
        ApiOption("Porcino", R.string.species_pig),
        ApiOption("Aves", R.string.herdtype_poultry),
        ApiOption("Mixto", R.string.gender_mixed),
        ApiOption("Otro", R.string.species_other),
    )
}

/** Localized label for a stored value; values the app does not know are shown as stored. */
@Composable
fun List<ApiOption>.labelFor(value: String?): String {
    if (value == null) return ""
    val option = firstOrNull { it.value.equals(value, ignoreCase = true) }
    return if (option != null) stringResource(option.label) else value
}

@StringRes
fun AnimalStatus.labelRes(): Int? = when (this) {
    AnimalStatus.Healthy -> R.string.status_healthy
    AnimalStatus.Observation -> R.string.status_observation
    AnimalStatus.InTreatment -> R.string.status_in_treatment
    AnimalStatus.Sold -> R.string.status_sold
    AnimalStatus.Unknown -> null
}

/** Same coloring as the web: healthy green, sold blue, anything else needs attention. */
fun AnimalStatus.severity(): Severity = when (this) {
    AnimalStatus.Healthy -> Severity.Success
    AnimalStatus.Sold -> Severity.Info
    AnimalStatus.Observation, AnimalStatus.InTreatment -> Severity.Warn
    AnimalStatus.Unknown -> Severity.Neutral
}

@Composable
fun statusLabel(raw: String): String {
    val res = AnimalStatus.fromApi(raw).labelRes()
    return if (res != null) stringResource(res) else raw
}

/** Message for a failed livestock operation, including the rules checked before any request is sent. */
@StringRes
fun AppError.livestockMessageRes(): Int {
    if (this is AppError.Validation) {
        when {
            LivestockValidation.INVALID_PLACEMENT in messages -> return R.string.animal_error_placement
            LivestockValidation.INVALID_BATCH_SIZE in messages -> return R.string.animal_error_batch_size
            LivestockValidation.NOTHING_SELECTED in messages -> return R.string.animal_error_nothing_selected
        }
    }
    return messageRes()
}
