package com.anitec.platform.sanitary.infrastructure

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.onSuccess
import com.anitec.platform.core.network.safeApiCall
import com.anitec.platform.core.outbox.OutboxKinds
import com.anitec.platform.core.outbox.OutboxQueue
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

fun HealthEventDto.toDomain() = HealthEvent(id, animalId, type, date, description, veterinarian, diagnosis, treatment, prescription, followUp, nextDueDate)
fun HealthEvent.toEntity() = HealthEventEntity(id, animalId, type, date, description, veterinarian, diagnosis, treatment, prescription, followUp, nextDueDate)
fun HealthEventEntity.toDomain() = HealthEvent(id, animalId, type, date, description, veterinarian, diagnosis, treatment, prescription, followUp, nextDueDate)
fun HealthEventDraft.toDto() = HealthEventDto(
    animalId = animalId, type = type, date = date, description = description, veterinarian = veterinarian,
    diagnosis = diagnosis, treatment = treatment, prescription = prescription, followUp = followUp, nextDueDate = nextDueDate,
)

@Singleton
class SanitaryRepositoryImpl @Inject constructor(
    private val api: SanitaryApi,
    private val dao: SanitaryDao,
    private val livestockDao: LivestockDao,
    private val outbox: OutboxQueue,
) : SanitaryRepository {

    override fun observeEvents(): Flow<List<HealthEvent>> = dao.observeEvents().map { list -> list.map { it.toDomain() } }

    override suspend fun refresh(): AppResult<Unit> = safeApiCall {
        val events = api.getEvents().map { it.toDomain() }
        val visible = SanitaryScope.visibleEvents(events, livestockDao.animalIds().toSet())
        dao.replaceAll(visible.map { it.toEntity() })
    }

    override suspend fun create(draft: HealthEventDraft): AppResult<HealthEvent> {
        val body = draft.toDto()
        val result = safeApiCall { api.createEvent(body).toDomain() }
        if (result is AppResult.Failure && result.error == AppError.Network) {
            // No connection: keep the record locally under a temporary id and send it later.
            val localId = outbox.enqueue(OutboxKinds.CREATE_HEALTH_EVENT, HealthEventDto.serializer(), body)
            val pending = body.toDomain().copy(id = localId)
            dao.upsert(pending.toEntity())
            return AppResult.Success(pending)
        }
        return result.onSuccess { dao.upsert(it.toEntity()) }
    }

    override suspend fun update(id: Int, draft: HealthEventDraft): AppResult<HealthEvent> =
        safeApiCall { api.updateEvent(id, draft.toDto()).toDomain() }.onSuccess { dao.upsert(it.toEntity()) }

    override suspend fun delete(id: Int): AppResult<Unit> =
        safeApiCall { api.deleteEvent(id) }.onSuccess { dao.delete(id) }
}
