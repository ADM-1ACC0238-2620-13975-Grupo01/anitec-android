package com.anitec.platform.livestock.domain

/** An animal with the names of the herd and corral it belongs to, ready to be searched and displayed. */
data class AnimalView(
    val animal: Animal,
    val herdName: String,
    val corralName: String?,
)

/**
 * Same matching as the web list: a case-insensitive substring over code, name, species, breed, gender,
 * status, weight, birth date, herd and corral, optionally narrowed to one corral.
 */
fun List<AnimalView>.filterBy(query: String, corralId: Int?): List<AnimalView> {
    val term = query.trim().lowercase()
    return filter { view ->
        val animal = view.animal
        if (corralId != null && animal.corralId != corralId) return@filter false
        if (term.isEmpty()) return@filter true
        listOf(
            animal.tag, animal.name, animal.species, animal.breed, animal.gender, animal.status,
            animal.weight.toString(), animal.birthDate, view.herdName, view.corralName,
        ).any { it?.lowercase()?.contains(term) == true }
    }
}
