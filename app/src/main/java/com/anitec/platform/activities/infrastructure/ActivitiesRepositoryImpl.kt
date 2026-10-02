package com.anitec.platform.activities.infrastructure

import com.anitec.platform.activities.domain.Activity
import com.anitec.platform.activities.domain.ActivitiesRepository
import com.anitec.platform.activities.domain.ActivityDraft
import com.anitec.platform.activities.domain.ActivityScope
import com.anitec.platform.activities.infrastructure.local.ActivitiesDao
import com.anitec.platform.activities.infrastructure.local.ActivityEntity
import com.anitec.platform.activities.infrastructure.remote.ActivitiesApi
import com.anitec.platform.activities.infrastructure.remote.FarmActivityDto
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.onSuccess
import com.anitec.platform.core.network.safeApiCall
import com.anitec.platform.core.outbox.OutboxKinds
import com.anitec.platform.core.outbox.OutboxQueue
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.veterinary.infrastructure.local.VeterinaryDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

fun FarmActivityDto.toDomain() = Activity(id, ownerId, veterinarianId, title, type, date, priority, status)
fun Activity.toEntity() = ActivityEntity(id, ownerId, veterinarianId, title, type, date, priority, status)
fun ActivityEntity.toDomain() = Activity(id, ownerId, veterinarianId, title, type, date, priority, status)
fun ActivityDraft.toDto() = FarmActivityDto(
    ownerId = ownerId, veterinarianId = veterinarianId, title = title, type = type, date = date, priority = priority, status = status,
)

@Singleton
class ActivitiesRepositoryImpl @Inject constructor(
    private val api: ActivitiesApi,
    private val dao: ActivitiesDao,
    private val veterinaryDao: VeterinaryDao,
    private val sessionStore: SessionStore,
    private val outbox: OutboxQueue,
) : ActivitiesRepository {

    override fun observeActivities(): Flow<List<Activity>> = dao.observeActivities().map { list -> list.map { it.toDomain() } }

    override suspend fun refresh(): AppResult<Unit> {
        val session = (sessionStore.state.value as? SessionState.SignedIn)?.session
            ?: return AppResult.Failure(AppError.Unauthorized)
        return safeApiCall {
            val all = api.getActivities().map { it.toDomain() }
            // A veterinarian also sees what was scheduled for their clients; those ids come from the clients cache.
            val clientIds = veterinaryDao.rancherIds().toSet()
            dao.replaceAll(ActivityScope.visible(all, session.role, session.userId, clientIds).map { it.toEntity() })
        }
    }

    override suspend fun create(draft: ActivityDraft): AppResult<Activity> {
        val body = draft.toDto()
        val result = safeApiCall { api.create(body).toDomain() }
        if (result is AppResult.Failure && result.error == AppError.Network) {
            // No connection: keep the activity locally under a temporary id and send it later.
            val localId = outbox.enqueue(OutboxKinds.CREATE_ACTIVITY, FarmActivityDto.serializer(), body)
            val pending = body.toDomain().copy(id = localId)
            dao.upsert(pending.toEntity())
            return AppResult.Success(pending)
        }
        return result.onSuccess { dao.upsert(it.toEntity()) }
    }

    override suspend fun update(id: Int, draft: ActivityDraft): AppResult<Activity> =
        safeApiCall { api.update(id, draft.toDto()).toDomain() }.onSuccess { dao.upsert(it.toEntity()) }

    override suspend fun delete(id: Int): AppResult<Unit> =
        safeApiCall { api.delete(id) }.onSuccess { dao.delete(id) }
}
