package com.anitec.platform.veterinary.domain

import com.anitec.platform.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * A rancher the signed-in veterinarian asked for access, or has it from. [status] is the server's state of the link
 * (Pending, Accepted, Rejected or Revoked); only an accepted one gives access. [herds] and [animals] are the server's totals.
 */
data class Client(
    val rancherId: Int,
    val rancherName: String,
    val status: String,
    val herds: Int,
    val animals: Int,
) {
    /** True when the rancher accepted: only then are their farms, animals and records visible. */
    val isActive: Boolean get() = status.equals("Accepted", ignoreCase = true)

    /** True while the rancher has not answered the request. */
    val isPending: Boolean get() = status.equals("Pending", ignoreCase = true)
}

/** A rancher the veterinarian could still add as a client. */
data class AvailableRancher(
    val id: Int,
    val username: String,
    val fullName: String,
    val herds: Int,
    val animals: Int,
) {
    /** Name shown in lists; falls back to the username when the profile has no full name. */
    val displayName: String get() = fullName.ifBlank { username }
}

/** Up to two initials of a person's name, for avatars. */
fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

interface VeterinaryRepository {
    fun observeClients(): Flow<List<Client>>
    suspend fun refreshClients(): AppResult<Unit>
    suspend fun availableRanchers(): AppResult<List<AvailableRancher>>

    /** Asks the rancher for access. It starts as pending; their herds and animals appear once the rancher accepts. */
    suspend fun addClient(rancherId: Int): AppResult<Unit>
    suspend fun removeClient(rancherId: Int): AppResult<Unit>
}
