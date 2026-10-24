package com.anitec.platform.app

import kotlinx.serialization.Serializable

// Signed-out graph
@Serializable data object SignInRoute
@Serializable data object SignUpRoute
@Serializable data object TermsRoute

// Signed-in graph: bottom-bar destinations
@Serializable data object HomeRoute
@Serializable data class AnimalsRoute(val openAnimalId: Int? = null)
@Serializable data class PatientsRoute(val clientId: Int? = null)
@Serializable data object HealthRoute
@Serializable data object ActivitiesRoute
@Serializable data object MoreRoute

// Livestock
@Serializable data object HerdsRoute
@Serializable data class HerdFormRoute(val herdId: Int? = null)
@Serializable data object CorralsRoute
@Serializable data class CorralFormRoute(val corralId: Int? = null)
@Serializable data class AnimalFormRoute(val animalId: Int? = null)

// Activities
@Serializable data class ActivityFormRoute(val activityId: Int? = null)

// Financial
@Serializable data object FinancialRoute
@Serializable data class FinancialFormRoute(val recordId: Int? = null)

// Subscriptions
@Serializable data object SubscriptionsRoute

/** The screen after Stripe: [sessionId] is the checkout to confirm, or [cancelled] when the user left without paying. */
@Serializable data class SubscriptionResultRoute(val sessionId: String? = null, val cancelled: Boolean = false)

// Scanner
@Serializable data object ScannerRoute

// Analytics
@Serializable data object AnalyticsRoute

// Devices
@Serializable data object DevicesRoute
@Serializable data class DeviceFormRoute(val deviceId: Int? = null)
@Serializable data class DeviceHistoryRoute(val deviceId: Int)

// Veterinary
@Serializable data object ClientsRoute
@Serializable data object AddClientRoute
@Serializable data class ClinicalHistoryRoute(val animalId: Int)

// Sanitary
@Serializable data class HealthFormRoute(val eventId: Int? = null, val animalId: Int? = null)
