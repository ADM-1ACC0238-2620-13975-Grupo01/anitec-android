package com.anitec.platform.app

import kotlinx.serialization.Serializable

// Signed-out graph
@Serializable data object SignInRoute
@Serializable data object SignUpRoute
@Serializable data object TermsRoute

// Signed-in graph: bottom-bar destinations
@Serializable data object HomeRoute
@Serializable data object AnimalsRoute
@Serializable data object PatientsRoute
@Serializable data object HealthRoute
@Serializable data object ActivitiesRoute
@Serializable data object MoreRoute

// Livestock
@Serializable data object HerdsRoute
@Serializable data class HerdFormRoute(val herdId: Int? = null)
@Serializable data object CorralsRoute
@Serializable data class CorralFormRoute(val corralId: Int? = null)
@Serializable data class AnimalFormRoute(val animalId: Int? = null)

// Sanitary
@Serializable data class HealthFormRoute(val eventId: Int? = null, val animalId: Int? = null)

/** Stand-in for sections that are not built yet; replaced by real routes as each phase lands. */
@Serializable data class PlaceholderRoute(val titleRes: Int)
