package com.anitec.platform.veterinary.infrastructure

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.onSuccess
import com.anitec.platform.core.network.safeApiCall
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.veterinary.domain.AvailableRancher
import com.anitec.platform.veterinary.domain.Client
import com.anitec.platform.veterinary.domain.VeterinaryRepository
import com.anitec.platform.veterinary.infrastructure.local.ClientEntity
import com.anitec.platform.veterinary.infrastructure.local.VeterinaryDao
import com.anitec.platform.veterinary.infrastructure.remote.VeterinaryApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VeterinaryRepositoryImpl @Inject constructor(
    private val api: VeterinaryApi,
    private val dao: VeterinaryDao,
    private val sessionStore: SessionStore,
) : VeterinaryRepository {

    private fun veterinarianId(): Int? = (sessionStore.state.value as? SessionState.SignedIn)?.session?.userId

    override fun observeClients(): Flow<List<Client>> =
        dao.observeClients().map { list -> list.map { Client(it.rancherId, it.rancherName, it.status, it.herds, it.animals) } }

    override suspend fun refreshClients(): AppResult<Unit> {
        val id = veterinarianId() ?: return AppResult.Failure(AppError.Unauthorized)
        return safeApiCall {
            val clients = api.getClients(id).map { ClientEntity(it.rancherId, it.rancherName, it.status, it.herds, it.animals) }
            dao.replaceAll(clients)
        }
    }

    override suspend fun availableRanchers(): AppResult<List<AvailableRancher>> {
        val id = veterinarianId() ?: return AppResult.Failure(AppError.Unauthorized)
        return safeApiCall { api.getAvailableRanchers(id).map { AvailableRancher(it.id, it.username, it.fullName, it.herds, it.animals) } }
    }

    override suspend fun addClient(rancherId: Int): AppResult<Unit> {
        val id = veterinarianId() ?: return AppResult.Failure(AppError.Unauthorized)
        val result = safeApiCall { api.addClient(id, rancherId) }
        if (result is AppResult.Failure) return result
        refreshClients()
        return AppResult.Success(Unit)
    }

    override suspend fun removeClient(rancherId: Int): AppResult<Unit> {
        val id = veterinarianId() ?: return AppResult.Failure(AppError.Unauthorized)
        return safeApiCall { api.removeClient(id, rancherId) }.onSuccess { dao.delete(rancherId) }
    }
}
