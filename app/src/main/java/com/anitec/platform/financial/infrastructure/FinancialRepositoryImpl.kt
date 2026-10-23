package com.anitec.platform.financial.infrastructure

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.onSuccess
import com.anitec.platform.core.network.safeApiCall
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

fun FinancialRecordDto.toDomain() = FinancialRecord(id, ownerId, type, category, amount, date, description)
fun FinancialRecord.toEntity() = FinancialRecordEntity(id, ownerId, type, category, amount, date, description)
fun FinancialRecordEntity.toDomain() = FinancialRecord(id, ownerId, type, category, amount, date, description)
fun FinancialRecordDraft.toDto() = FinancialRecordDto(
    ownerId = ownerId, type = type, category = category, amount = amount, date = date, description = description,
)

@Singleton
class FinancialRepositoryImpl @Inject constructor(
    private val api: FinancialApi,
    private val dao: FinancialDao,
    private val sessionStore: SessionStore,
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

    override suspend fun create(draft: FinancialRecordDraft): AppResult<FinancialRecord> =
        safeApiCall { api.create(draft.toDto()).toDomain() }.onSuccess { dao.upsert(it.toEntity()) }

    override suspend fun update(id: Int, draft: FinancialRecordDraft): AppResult<FinancialRecord> =
        safeApiCall { api.update(id, draft.toDto()).toDomain() }.onSuccess { dao.upsert(it.toEntity()) }

    override suspend fun delete(id: Int): AppResult<Unit> =
        safeApiCall { api.delete(id) }.onSuccess { dao.delete(id) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FinancialBindingsModule {
    @Binds
    abstract fun bindFinancialRepository(impl: FinancialRepositoryImpl): FinancialRepository
}

@Module
@InstallIn(SingletonComponent::class)
object FinancialApiModule {
    @Provides
    @Singleton
    fun provideFinancialApi(retrofit: Retrofit): FinancialApi = retrofit.create(FinancialApi::class.java)
}
