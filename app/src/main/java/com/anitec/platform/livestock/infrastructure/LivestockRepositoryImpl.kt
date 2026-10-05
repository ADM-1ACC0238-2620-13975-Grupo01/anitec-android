package com.anitec.platform.livestock.infrastructure

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.onSuccess
import com.anitec.platform.core.network.safeApiCall
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.livestock.domain.Animal
import com.anitec.platform.livestock.domain.AnimalBatchDraft
import com.anitec.platform.livestock.domain.AnimalDraft
import com.anitec.platform.livestock.domain.Corral
import com.anitec.platform.livestock.domain.CorralDraft
import com.anitec.platform.livestock.domain.Herd
import com.anitec.platform.livestock.domain.HerdDraft
import com.anitec.platform.livestock.domain.LivestockRepository
import com.anitec.platform.livestock.domain.LivestockScope
import com.anitec.platform.livestock.infrastructure.local.LivestockDao
import com.anitec.platform.livestock.infrastructure.remote.AnimalIdsDto
import com.anitec.platform.livestock.infrastructure.remote.AnimalsStatusDto
import com.anitec.platform.livestock.infrastructure.remote.LivestockApi
import com.anitec.platform.veterinary.infrastructure.remote.VeterinaryApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class LivestockRepositoryImpl @Inject constructor(
    private val api: LivestockApi,
    private val veterinaryApi: VeterinaryApi,
    private val dao: LivestockDao,
    private val sessionStore: SessionStore,
) : LivestockRepository {

    override fun observeHerds(): Flow<List<Herd>> = dao.observeHerds().map { list -> list.map { it.toDomain() } }
    override fun observeCorrals(): Flow<List<Corral>> = dao.observeCorrals().map { list -> list.map { it.toDomain() } }
    override fun observeAnimals(): Flow<List<Animal>> = dao.observeAnimals().map { list -> list.map { it.toDomain() } }

    override suspend fun refresh(): AppResult<Unit> {
        val session = (sessionStore.state.value as? SessionState.SignedIn)?.session
            ?: return AppResult.Failure(AppError.Unauthorized)
        return safeApiCall {
            coroutineScope {
                val herds = async { api.getHerds().map { it.toDomain() } }
                val corrals = async { api.getCorrals().map { it.toDomain() } }
                val animals = async { api.getAnimals().map { it.toDomain() } }
                val clientIds = if (session.role == UserRole.Veterinarian) {
                    async { veterinaryApi.getClients(session.userId).map { it.rancherId }.toSet() }
                } else {
                    null
                }

                // The API returns every tenant's data, so the visible subset is decided here.
                val visibleHerds = LivestockScope.visibleHerds(
                    role = session.role,
                    userId = session.userId,
                    clientRancherIds = clientIds?.await().orEmpty(),
                    herds = herds.await(),
                )
                dao.replaceAll(
                    herds = visibleHerds.map { it.toEntity() },
                    corrals = LivestockScope.visibleCorrals(corrals.await(), visibleHerds).map { it.toEntity() },
                    animals = LivestockScope.visibleAnimals(animals.await(), visibleHerds).map { it.toEntity() },
                )
            }
        }
    }

    // --- herds ---

    override suspend fun createHerd(draft: HerdDraft): AppResult<Herd> =
        safeApiCall { api.createHerd(draft.toDto()).toDomain() }.onSuccess { dao.upsertHerd(it.toEntity()) }

    override suspend fun updateHerd(id: Int, draft: HerdDraft): AppResult<Herd> =
        safeApiCall { api.updateHerd(id, draft.toDto()).toDomain() }.onSuccess { dao.upsertHerd(it.toEntity()) }

    override suspend fun deleteHerd(id: Int): AppResult<Unit> =
        safeApiCall { api.deleteHerd(id) }.onSuccess { dao.deleteHerd(id) }

    // --- corrals ---

    override suspend fun createCorral(draft: CorralDraft): AppResult<Corral> =
        safeApiCall { api.createCorral(draft.toDto()).toDomain() }.onSuccess { dao.upsertCorral(it.toEntity()) }

    override suspend fun updateCorral(id: Int, draft: CorralDraft): AppResult<Corral> =
        safeApiCall { api.updateCorral(id, draft.toDto()).toDomain() }.onSuccess { dao.upsertCorral(it.toEntity()) }

    override suspend fun deleteCorral(id: Int): AppResult<Unit> =
        safeApiCall { api.deleteCorral(id) }.onSuccess {
            dao.deleteCorral(id)
            dao.detachAnimalsFromCorral(id)
        }

    // --- animals ---

    override suspend fun createAnimal(draft: AnimalDraft): AppResult<Animal> =
        safeApiCall { api.createAnimal(draft.toDto()).toDomain() }.onSuccess { dao.upsertAnimal(it.toEntity()) }

    override suspend fun updateAnimal(id: Int, draft: AnimalDraft): AppResult<Animal> =
        safeApiCall { api.updateAnimal(id, draft.toDto()).toDomain() }.onSuccess { dao.upsertAnimal(it.toEntity()) }

    override suspend fun deleteAnimal(id: Int): AppResult<Unit> =
        safeApiCall { api.deleteAnimal(id) }.onSuccess { dao.deleteAnimal(id) }

    override suspend fun createAnimalBatch(draft: AnimalBatchDraft): AppResult<List<Animal>> =
        safeApiCall { api.createAnimalBatch(draft.toDto()).map { it.toDomain() } }
            .onSuccess { created -> dao.upsertAnimals(created.map { it.toEntity() }) }

    override suspend fun updateAnimalsStatus(ids: List<Int>, status: String): AppResult<List<Animal>> =
        safeApiCall { api.updateAnimalsStatus(AnimalsStatusDto(ids, status)).map { it.toDomain() } }
            .onSuccess { updated -> dao.upsertAnimals(updated.map { it.toEntity() }) }

    override suspend fun deleteAnimals(ids: List<Int>): AppResult<Unit> =
        safeApiCall { api.deleteAnimals(AnimalIdsDto(ids)) }.onSuccess { dao.deleteAnimals(ids) }
}

