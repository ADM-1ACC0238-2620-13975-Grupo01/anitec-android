package com.anitec.platform.livestock.domain

/**
 * List-row projection: an [Animal] plus the display names of its herd and corral,
 * so the UI can search and show location without joining tables again.
 */
data class AnimalView(
    val animal: Animal,
    val herdName: String,
    val corralName: String?,
)

/**
 * Filters animals with the same rules as the web livestock list.
 *
 * Matching is a case-insensitive substring over tag, name, species, breed, gender,
 * status, weight, birth date, herd name and corral name. When [corralId] is non-null,
 * only animals in that corral are kept. An empty/blank [query] returns every animal
 * that passes the optional corral filter.
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
