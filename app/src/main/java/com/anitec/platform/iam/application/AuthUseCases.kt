package com.anitec.platform.iam.application

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.iam.domain.AuthRepository
import javax.inject.Inject

/**
 * Application-layer auth use cases: thin wrappers over [AuthRepository].
 * ViewModels call these instead of talking to infrastructure directly.
 */

/** Signs in with username/password; trims the username before calling the repository. */
class SignInUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(username: String, password: String): AppResult<UserSession> =
        repository.signIn(username.trim(), password)
}

/**
 * Registers a new rancher or veterinarian account.
 * Trims [fullName] and [username]; on success the repository also signs the user in.
 */
class SignUpUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(
        fullName: String,
        username: String,
        password: String,
        role: UserRole,
    ): AppResult<UserSession> = repository.signUp(fullName.trim(), username.trim(), password, role)
}

/** Clears the persisted session (local sign-out). */
class SignOutUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.signOut()
}
