package com.anitec.platform.sanitary.infrastructure

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.explainingConflict
import com.anitec.platform.core.common.ifMatchOf
import com.anitec.platform.core.common.onSuccess
import com.anitec.platform.core.network.safeApiCall
import com.anitec.platform.core.outbox.OutboxKinds
import com.anitec.platform.core.outbox.OutboxQueue
import com.anitec.platform.core.outbox.newIdempotencyKey
import com.anitec.platform.livestock.infrastructure.local.LivestockDao
import com.anitec.platform.sanitary.domain.HealthEvent
import com.anitec.platform.sanitary.domain.HealthEventDraft
import com.anitec.platform.sanitary.domain.SanitaryRepository
import com.anitec.platform.sanitary.domain.SanitaryScope
import com.anitec.platform.sanitary.infrastructure.local.HealthEventEntity
import com.anitec.platform.sanitary.infrastructure.local.SanitaryDao
import com.anitec.platform.sanitary.infrastructure.remote.HealthEventDto
import com.anitec.platform.sanitary.infrastructure.remote.SanitaryApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Mappers between the API DTO, the Room entity and the domain [HealthEvent]. */
fun HealthEventDto.toDomain() = HealthEvent(id, animalId, type, date, description, veterinarian, diagnosis, treatment, prescription, followUp, nextDueDate, version)
fun HealthEvent.toEntity() = HealthEventEntity(id, animalId, type, date, description, veterinarian, diagnosis, treatment, prescription, followUp, nextDueDate, version)
fun HealthEventEntity.toDomain() = HealthEvent(id, animalId, type, date, description, veterinarian, diagnosis, treatment, prescription, followUp, nextDueDate, version)
fun HealthEventDraft.toDto() = HealthEventDto(
    animalId = animalId, type = type, date = date, description = description, veterinarian = veterinarian,
    diagnosis = diagnosis, treatment = treatment, prescription = prescription, followUp = followUp, nextDueDate = nextDueDate,
)

/** Health events repository backed by the REST API and cached in Room. */
@Singleton
class SanitaryRepositoryImpl @Inject constructor(
    private val api: SanitaryApi,
    private val dao: SanitaryDao,
    private val livestockDao: LivestockDao,
    private val outbox: OutboxQueue,
) : SanitaryRepository {

    override fun observeEvents(): Flow<List<HealthEvent>> = dao.observeEvents().map { list -> list.map { it.toDomain() } }

    /** Downloads the health events and caches those of the animals the user can see. */
    override suspend fun refresh(): AppResult<Unit> = safeApiCall {
        val events = api.getEvents().map { it.toDomain() }
        val visible = SanitaryScope.visibleEvents(events, livestockDao.animalIds().toSet())
        dao.replaceAll(visible.map { it.toEntity() })
    }

    /** Creates a health event; offline, it is stored locally and sent later by the outbox. */
    override suspend fun create(draft: HealthEventDraft): AppResult<HealthEvent> {
        val body = draft.toDto()
        // One key for this operation: if the try below reaches the server but the answer is lost, the queued retry reuses it.
        val key = newIdempotencyKey()
        val result = safeApiCall { api.createEvent(body, key).toDomain() }
        if (result is AppResult.Failure && result.error == AppError.Network) {
            // No connection: keep the record locally under a temporary id and send it later.
            val localId = outbox.enqueue(OutboxKinds.CREATE_HEALTH_EVENT, HealthEventDto.serializer(), body, key)
            val pending = body.toDomain().copy(id = localId)
            dao.upsert(pending.toEntity())
            return AppResult.Success(pending)
        }
        return result.onSuccess { dao.upsert(it.toEntity()) }
    }

    /** Updates a health event against the version the edit started from; a change made elsewhere is not overwritten. */
    override suspend fun update(id: Int, draft: HealthEventDraft): AppResult<HealthEvent> {
        val body = draft.toDto()
        return safeApiCall { api.updateEvent(id, body, ifMatchOf(draft.baseVersion)).toDomain() }
            .explainingConflict(HealthEventDto.serializer(), body) { server -> dao.upsert(server.toDomain().toEntity()) }
            .onSuccess { dao.upsert(it.toEntity()) }
    }

    override suspend fun delete(id: Int): AppResult<Unit> =
        safeApiCall { api.deleteEvent(id) }.onSuccess { dao.delete(id) }
}
