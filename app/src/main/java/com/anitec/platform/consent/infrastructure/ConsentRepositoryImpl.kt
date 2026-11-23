package com.anitec.platform.consent.infrastructure

import com.anitec.platform.consent.domain.ConsentRepository
import com.anitec.platform.consent.domain.LinkStatus
import com.anitec.platform.consent.domain.VeterinarianLink
import com.anitec.platform.consent.infrastructure.remote.ConsentApi
import com.anitec.platform.consent.infrastructure.remote.VeterinarianLinkDto
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.map
import com.anitec.platform.core.network.safeApiCall
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

fun VeterinarianLinkDto.toDomain() = VeterinarianLink(
    id = id,
    veterinarianId = veterinarianId,
    veterinarianName = veterinarianName,
    veterinarianUsername = veterinarianUsername,
    status = LinkStatus.parse(status),
    requestedAt = requestedAt,
)

/**
 * Consent requests of the signed-in rancher. They are not cached: access is decided on the server, so the screen
 * always shows the server's answer and an action without a connection simply fails with a network error.
 */
@Singleton
class ConsentRepositoryImpl @Inject constructor(
    private val api: ConsentApi,
    private val sessionStore: SessionStore,
) : ConsentRepository {

    private fun rancherId(): Int? = (sessionStore.state.value as? SessionState.SignedIn)?.session?.userId

    override suspend fun links(): AppResult<List<VeterinarianLink>> {
        val id = rancherId() ?: return AppResult.Failure(AppError.Unauthorized)
        return safeApiCall { api.getLinks(id).map { it.toDomain() } }
    }

    override suspend fun accept(linkId: Int): AppResult<Unit> = answer { id -> api.accept(id, linkId) }

    override suspend fun reject(linkId: Int): AppResult<Unit> = answer { id -> api.reject(id, linkId) }

    override suspend fun revoke(linkId: Int): AppResult<Unit> = answer { id -> api.revoke(id, linkId) }

    private suspend fun answer(call: suspend (rancherId: Int) -> VeterinarianLinkDto): AppResult<Unit> {
        val id = rancherId() ?: return AppResult.Failure(AppError.Unauthorized)
        return safeApiCall { call(id) }.map { }
    }
}

/** Binds the consent repository and provides its Retrofit client. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConsentBindingsModule {
    @Binds
    abstract fun bindConsentRepository(impl: ConsentRepositoryImpl): ConsentRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ConsentApiModule {
    @Provides
    @Singleton
    fun provideConsentApi(retrofit: Retrofit): ConsentApi = retrofit.create(ConsentApi::class.java)
}
