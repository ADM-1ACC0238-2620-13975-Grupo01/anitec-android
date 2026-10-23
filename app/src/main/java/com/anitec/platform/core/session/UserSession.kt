package com.anitec.platform.core.session

/** Roles exactly as the API names them. */
enum class UserRole(val apiValue: String) {
    Rancher("Rancher"),
    Veterinarian("Veterinarian");

    companion object {
        fun fromApi(value: String?): UserRole? = entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) }
    }
}

data class UserSession(
    val userId: Int,
    val username: String,
    val fullName: String,
    val role: UserRole,
    val token: String,
    val email: String? = null,
)

sealed interface SessionState {
    /** The stored session is still being read from disk. */
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val session: UserSession) : SessionState
}
