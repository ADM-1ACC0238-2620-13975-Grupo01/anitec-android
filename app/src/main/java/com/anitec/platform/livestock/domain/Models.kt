package com.anitec.platform.livestock.domain

/** A farm (the web UI calls it "Finca"). It groups corrals and animals. */
data class Herd(
    val id: Int,
    val name: String,
    val location: String,
    val owner: String,
    val ownerId: Int,
    val veterinarianId: Int?,
    val mainType: String,
)

/** A physical enclosure inside a herd. */
data class Corral(
    val id: Int,
    val name: String,
    val herdId: Int,
)

/**
 * [status], [species], [gender], [source] and [ageRange] keep the raw API strings: the backend stores free
 * text in mixed languages, so unknown values must survive a round trip untouched.
 */
data class Animal(
    val id: Int,
    val tag: String,
    val name: String,
    val species: String,
    val breed: String,
    val gender: String,
    /** ISO date (yyyy-MM-dd) or null. */
    val birthDate: String?,
    val weight: Double,
    val status: String,
    val herdId: Int,
    val corralId: Int?,
    val source: String?,
    val ageRange: String?,
    /** Path relative to the server root, as returned by the upload endpoint. */
    val imageUrl: String?,
) {
    val healthStatus: AnimalStatus get() = AnimalStatus.fromApi(status)

    /** Archived animals are kept but left out of the herd counts and of the working lists. */
    val isArchived: Boolean get() = healthStatus == AnimalStatus.Archived
}

/** Data needed to create or update a herd; the server assigns the id. */
data class HerdDraft(
    val name: String,
    val location: String,
    val owner: String,
    val ownerId: Int,
    val veterinarianId: Int?,
    val mainType: String,
)

data class CorralDraft(val name: String, val herdId: Int)

data class AnimalDraft(
    val tag: String,
    val name: String,
    val species: String,
    val breed: String,
    val gender: String,
    val birthDate: String?,
    val weight: Double,
    val status: String,
    val herdId: Int,
    val corralId: Int?,
    val source: String?,
    val ageRange: String?,
    val imageUrl: String?,
)

/**
 * Register [quantity] identical animals inside one corral; the server generates their codes.
 * With a [name] each animal is called "name-001", "name-002"...; without it they take their code as name.
 */
data class AnimalBatchDraft(
    val species: String,
    val breed: String,
    val gender: String,
    val birthDate: String?,
    val weight: Double,
    val status: String,
    val herdId: Int,
    val corralId: Int,
    val quantity: Int,
    val source: String?,
    val ageRange: String?,
    val imageUrl: String?,
    val name: String? = null,
)
