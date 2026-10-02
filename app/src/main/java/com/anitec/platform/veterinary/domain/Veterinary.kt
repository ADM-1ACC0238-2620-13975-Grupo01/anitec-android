package com.anitec.platform.veterinary.domain

import com.anitec.platform.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/** A rancher linked to the signed-in veterinarian. [herds] and [animals] are the server's totals. */
data class Client(
    val rancherId: Int,
    val rancherName: String,
    val status: String,
    val herds: Int,
    val animals: Int,
)

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

    /** Links the rancher to the signed-in veterinarian. Their herds and animals appear after the next livestock refresh. */
    suspend fun addClient(rancherId: Int): AppResult<Unit>
    suspend fun removeClient(rancherId: Int): AppResult<Unit>
}
