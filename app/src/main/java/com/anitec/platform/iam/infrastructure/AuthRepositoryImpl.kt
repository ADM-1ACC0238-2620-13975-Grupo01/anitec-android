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

class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val sessionStore: SessionStore,
) : AuthRepository {

    override suspend fun signIn(username: String, password: String): AppResult<UserSession> {
        val result = safeApiCall { api.signIn(SignInRequestDto(username, password)) }
        return when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val dto = result.value
                val role = UserRole.fromApi(dto.role)
                    ?: return AppResult.Failure(AppError.Unknown("Unsupported role: ${dto.role}"))
                val session = UserSession(dto.id, dto.username, dto.fullName.ifBlank { dto.username }, role, dto.token, dto.email)
                sessionStore.save(session)
                AppResult.Success(session)
            }
        }
    }

    override suspend fun signUp(
        fullName: String,
        username: String,
        password: String,
        role: UserRole,
        email: String?,
    ): AppResult<UserSession> {
        val created = safeApiCall { api.signUp(SignUpRequestDto(username, password, fullName, role.apiValue, email)) }
        return when (created) {
            is AppResult.Failure -> created
            is AppResult.Success -> signIn(username, password)
        }
    }

    override suspend fun signOut() {
        sessionStore.clear()
    }
}
