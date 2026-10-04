package com.anitec.platform.iam.infrastructure

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.network.safeApiCall
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.iam.domain.AuthRepository
import com.anitec.platform.iam.infrastructure.remote.AuthApi
import com.anitec.platform.iam.infrastructure.remote.SignInRequestDto
import com.anitec.platform.iam.infrastructure.remote.SignUpRequestDto
import javax.inject.Inject

/**
 * Retrofit-backed [AuthRepository]: talks to [AuthApi], maps DTOs to [UserSession],
 * and persists or clears the session through [SessionStore].
 */
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val sessionStore: SessionStore,
) : AuthRepository {

    /**
     * Calls the sign-in endpoint via [safeApiCall], rejects unknown roles,
     * then saves the session (encrypted token) before returning success.
     */
    override suspend fun signIn(username: String, password: String): AppResult<UserSession> {
        val result = safeApiCall { api.signIn(SignInRequestDto(username, password)) }
        return when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val dto = result.value
                val role = UserRole.fromApi(dto.role)
                    ?: return AppResult.Failure(AppError.Unknown("Unsupported role: ${dto.role}"))
                val session = UserSession(dto.id, dto.username, dto.fullName.ifBlank { dto.username }, role, dto.token)
                sessionStore.save(session)
                AppResult.Success(session)
            }
        }
    }

    /**
     * Creates the account, then signs in because the register response has no token.
     * Registration failures are returned as-is without attempting sign-in.
     */
    override suspend fun signUp(
        fullName: String,
        username: String,
        password: String,
        role: UserRole,
    ): AppResult<UserSession> {
        val created = safeApiCall { api.signUp(SignUpRequestDto(username, password, fullName, role.apiValue)) }
        return when (created) {
            is AppResult.Failure -> created
            is AppResult.Success -> signIn(username, password)
        }
    }

    /** Local-only: clears the encrypted session from DataStore. */
    override suspend fun signOut() {
        sessionStore.clear()
    }
}
