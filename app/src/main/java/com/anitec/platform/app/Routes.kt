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

/** Stand-in for sections that are not built yet; replaced by real routes as each phase lands. */
@Serializable data class PlaceholderRoute(val titleRes: Int)
