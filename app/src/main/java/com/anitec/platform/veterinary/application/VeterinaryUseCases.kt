package com.anitec.platform.veterinary.application

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.veterinary.domain.AvailableRancher
import com.anitec.platform.veterinary.domain.Client
import com.anitec.platform.veterinary.domain.VeterinaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Cached ranchers who accepted the signed-in veterinarian: the ones whose data they can see. */
class ObserveClientsUseCase @Inject constructor(private val repository: VeterinaryRepository) {
    operator fun invoke(): Flow<List<Client>> = repository.observeClients().map { clients -> clients.filter { it.isActive } }
}

/** Every request and link of the veterinarian, whatever its state: pending, accepted, rejected or revoked. */
class ObserveClientLinksUseCase @Inject constructor(private val repository: VeterinaryRepository) {
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

/** Asks the rancher with [rancherId] for access; the link starts as pending. */
class AddClientUseCase @Inject constructor(private val repository: VeterinaryRepository) {
    suspend operator fun invoke(rancherId: Int): AppResult<Unit> = repository.addClient(rancherId)
}

/** Withdraws the request or removes the link with the rancher with [rancherId], whatever its state. */
class RemoveClientUseCase @Inject constructor(private val repository: VeterinaryRepository) {
    suspend operator fun invoke(rancherId: Int): AppResult<Unit> = repository.removeClient(rancherId)
}

/** Case-insensitive substring match over the rancher's name and username. */
fun List<AvailableRancher>.search(query: String): List<AvailableRancher> {
    val term = query.trim().lowercase()
    if (term.isEmpty()) return this
    return filter { it.displayName.lowercase().contains(term) || it.username.lowercase().contains(term) }
}
