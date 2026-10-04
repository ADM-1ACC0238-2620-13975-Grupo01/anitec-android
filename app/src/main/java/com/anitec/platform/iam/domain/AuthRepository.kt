package com.anitec.platform.iam.domain

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession

/**
 * Domain contract for authentication against the AniTec API.
 *
 * Implementations persist a successful [UserSession] (encrypted token) and clear it on sign-out.
 * Use cases and ViewModels depend on this interface, never on Retrofit directly.
 */
interface AuthRepository {
    /** Authenticates credentials and returns a persisted [UserSession] on success. */
    suspend fun signIn(username: String, password: String): AppResult<UserSession>

    /**
     * Registers a new account for [role].
     * The API's sign-up returns no token, so a successful registration is followed by a sign-in.
     */
    suspend fun signUp(fullName: String, username: String, password: String, role: UserRole): AppResult<UserSession>

    /** Clears the local session without calling the server. */
    suspend fun signOut()
}
