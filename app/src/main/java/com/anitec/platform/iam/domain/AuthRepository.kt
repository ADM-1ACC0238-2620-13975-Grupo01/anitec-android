package com.anitec.platform.iam.domain

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession

interface AuthRepository {
    suspend fun signIn(username: String, password: String): AppResult<UserSession>

    /** The API's sign-up returns no token, so a successful registration is followed by a sign-in. */
    suspend fun signUp(fullName: String, username: String, password: String, role: UserRole): AppResult<UserSession>

    suspend fun signOut()
}
