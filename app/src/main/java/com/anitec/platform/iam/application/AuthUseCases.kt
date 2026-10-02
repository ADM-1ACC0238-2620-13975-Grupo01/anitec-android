package com.anitec.platform.iam.application

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.EmailValidator
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.iam.domain.AuthRepository
import javax.inject.Inject

class SignInUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(username: String, password: String): AppResult<UserSession> =
        repository.signIn(username.trim(), password)
}

class SignUpUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(
        fullName: String,
        username: String,
        password: String,
        role: UserRole,
        email: String? = null,
    ): AppResult<UserSession> = repository.signUp(fullName.trim(), username.trim(), password, role, email?.let(EmailValidator::normalize))
}

class SignOutUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.signOut()
}
