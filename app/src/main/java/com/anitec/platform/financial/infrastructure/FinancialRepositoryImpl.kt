package com.anitec.platform.financial.infrastructure

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.explainingConflict
import com.anitec.platform.core.common.ifMatchOf
import com.anitec.platform.core.common.onSuccess
import com.anitec.platform.core.network.safeApiCall
import com.anitec.platform.core.outbox.OutboxKinds
import com.anitec.platform.core.outbox.OutboxQueue
import com.anitec.platform.core.outbox.newIdempotencyKey
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.financial.domain.FinancialRecord
import com.anitec.platform.financial.domain.FinancialRecordDraft
import com.anitec.platform.financial.domain.FinancialRepository
import com.anitec.platform.financial.domain.FinancialScope
import com.anitec.platform.financial.infrastructure.local.FinancialDao
import com.anitec.platform.financial.infrastructure.local.FinancialRecordEntity
import com.anitec.platform.financial.infrastructure.remote.FinancialApi
import com.anitec.platform.financial.infrastructure.remote.FinancialRecordDto
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

/** Mappers between the API DTO, the Room entity and the domain [FinancialRecord]. */
fun FinancialRecordDto.toDomain() = FinancialRecord(id, ownerId, type, category, amount, date, description, version)
fun FinancialRecord.toEntity() = FinancialRecordEntity(id, ownerId, type, category, amount, date, description, version)
fun FinancialRecordEntity.toDomain() = FinancialRecord(id, ownerId, type, category, amount, date, description, version)
fun FinancialRecordDraft.toDto() = FinancialRecordDto(
    ownerId = ownerId, type = type, category = category, amount = amount, date = date, description = description,
)

/**
 * Income and expense repository backed by the REST API and cached in Room.
 *
 * Only the signed-in rancher's records are cached; new ones are queued in the outbox when offline.
 */
@Singleton
class FinancialRepositoryImpl @Inject constructor(
    private val api: FinancialApi,
    private val dao: FinancialDao,
    private val sessionStore: SessionStore,
    private val outbox: OutboxQueue,
) : FinancialRepository {

    override fun observeRecords(): Flow<List<FinancialRecord>> = dao.observeRecords().map { list -> list.map { it.toDomain() } }

    override suspend fun refresh(): AppResult<Unit> {
        val session = (sessionStore.state.value as? SessionState.SignedIn)?.session
            ?: return AppResult.Failure(AppError.Unauthorized)
        return safeApiCall {
            val mine = FinancialScope.visible(api.getRecords().map { it.toDomain() }, session.userId)
            dao.replaceAll(mine.map { it.toEntity() })
        }
    }

    /** Creates a record; offline, it is stored locally and sent later by the outbox. */
    override suspend fun create(draft: FinancialRecordDraft): AppResult<FinancialRecord> {
        val body = draft.toDto()
        // One key for this operation: if the try below reaches the server but the answer is lost, the queued retry reuses it.
        val key = newIdempotencyKey()
        val result = safeApiCall { api.create(body, key).toDomain() }
        if (result is AppResult.Failure && result.error == AppError.Network) {
            // No connection: keep the record locally under a temporary id and send it later.
            val localId = outbox.enqueue(OutboxKinds.CREATE_FINANCIAL_RECORD, FinancialRecordDto.serializer(), body, key)
            val pending = body.toDomain().copy(id = localId)
            dao.upsert(pending.toEntity())
            return AppResult.Success(pending)
        }
        return result.onSuccess { dao.upsert(it.toEntity()) }
    }

    /** Updates a record against the version the edit started from; a change made elsewhere is not overwritten. */
    override suspend fun update(id: Int, draft: FinancialRecordDraft): AppResult<FinancialRecord> {
        val body = draft.toDto()
        return safeApiCall { api.update(id, body, ifMatchOf(draft.baseVersion)).toDomain() }
            .explainingConflict(FinancialRecordDto.serializer(), body) { server -> dao.upsert(server.toDomain().toEntity()) }
            .onSuccess { dao.upsert(it.toEntity()) }
    }

    override suspend fun delete(id: Int): AppResult<Unit> =
        safeApiCall { api.delete(id) }.onSuccess { dao.delete(id) }
}

/** Binds [FinancialRepositoryImpl] as the [FinancialRepository] used by the app. */
@Module
@InstallIn(SingletonComponent::class)
abstract class FinancialBindingsModule {
    @Binds
    abstract fun bindFinancialRepository(impl: FinancialRepositoryImpl): FinancialRepository
}

/** Provides the Retrofit client of the financial API. */
@Module
@InstallIn(SingletonComponent::class)
object FinancialApiModule {
    @Provides
    @Singleton
    fun provideFinancialApi(retrofit: Retrofit): FinancialApi = retrofit.create(FinancialApi::class.java)
}
