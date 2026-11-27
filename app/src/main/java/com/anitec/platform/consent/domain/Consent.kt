package com.anitec.platform.consent.domain

import com.anitec.platform.core.common.AppResult

/**
 * State of a veterinarian's request for access to a rancher's data. Only [Accepted] gives access.
 * A request starts as [Pending]; the rancher accepts or rejects it, and can later revoke an accepted one.
 */
enum class LinkStatus {
    Pending, Accepted, Rejected, Revoked,

    /** A status this version of the app does not know; shown as closed, never as access. */
    Unknown;

    companion object {
        fun parse(value: String): LinkStatus = entries.firstOrNull { it != Unknown && it.name.equals(value, ignoreCase = true) } ?: Unknown
    }
}

/** A veterinarian who asked a rancher for access, or has it. [requestedAt] is the server's ISO date-time. */
data class VeterinarianLink(
    val id: Int,
    val veterinarianId: Int,
    val veterinarianName: String,
    val veterinarianUsername: String,
    val status: LinkStatus,
    val requestedAt: String,
) {
    /** Name shown in lists; falls back to the username when the profile has no full name. */
    val displayName: String get() = veterinarianName.ifBlank { veterinarianUsername }

    /** The day of the request (yyyy-MM-dd). */
    val requestedDay: String get() = requestedAt.take(10)
}

/** Requests waiting for an answer, newest first. */
fun List<VeterinarianLink>.pending(): List<VeterinarianLink> =
    filter { it.status == LinkStatus.Pending }.sortedByDescending { it.requestedAt }

/** Veterinarians who currently have access. */
fun List<VeterinarianLink>.withAccess(): List<VeterinarianLink> =
    filter { it.status == LinkStatus.Accepted }.sortedBy { it.displayName.lowercase() }

/** Requests that were rejected and accesses that were revoked, newest first. */
fun List<VeterinarianLink>.closed(): List<VeterinarianLink> =
    filter { it.status != LinkStatus.Pending && it.status != LinkStatus.Accepted }.sortedByDescending { it.requestedAt }

/** The rancher's side of the consent flow: see the requests, answer them and withdraw access. */
interface ConsentRepository {
    /** Every request and link addressed to the signed-in rancher. */
    suspend fun links(): AppResult<List<VeterinarianLink>>

    /** Grants the veterinarian access. Fails with a conflict if the request was already answered. */
    suspend fun accept(linkId: Int): AppResult<Unit>

    /** Closes the request without granting access. Fails with a conflict if it was already answered. */
    suspend fun reject(linkId: Int): AppResult<Unit>

    /** Withdraws an active access. Fails with not found when the veterinarian has no active access. */
    suspend fun revoke(linkId: Int): AppResult<Unit>
}
