package com.anitec.platform.veterinary.application

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.veterinary.domain.AvailableRancher
import com.anitec.platform.veterinary.domain.Client
import com.anitec.platform.veterinary.domain.VeterinaryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Cached ranchers linked to the signed-in veterinarian. */
class ObserveClientsUseCase @Inject constructor(private val repository: VeterinaryRepository) {
    operator fun invoke(): Flow<List<Client>> = repository.observeClients()
}

/** Downloads the veterinarian's clients and replaces the cache. */
class RefreshClientsUseCase @Inject constructor(private val repository: VeterinaryRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.refreshClients()
}

/** Ranchers the veterinarian can still add as clients. */
class ListAvailableRanchersUseCase @Inject constructor(private val repository: VeterinaryRepository) {
    suspend operator fun invoke(): AppResult<List<AvailableRancher>> = repository.availableRanchers()
}

/** Links the rancher with [rancherId] to the veterinarian. */
class AddClientUseCase @Inject constructor(private val repository: VeterinaryRepository) {
    suspend operator fun invoke(rancherId: Int): AppResult<Unit> = repository.addClient(rancherId)
}

/** Unlinks the rancher with [rancherId] from the veterinarian. */
class RemoveClientUseCase @Inject constructor(private val repository: VeterinaryRepository) {
    suspend operator fun invoke(rancherId: Int): AppResult<Unit> = repository.removeClient(rancherId)
}

/** Case-insensitive substring match over the rancher's name and username. */
fun List<AvailableRancher>.search(query: String): List<AvailableRancher> {
    val term = query.trim().lowercase()
    if (term.isEmpty()) return this
    return filter { it.displayName.lowercase().contains(term) || it.username.lowercase().contains(term) }
}
