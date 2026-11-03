package com.anitec.platform.iam.infrastructure.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.POST

/** Body of the sign-in request. */
@Serializable
data class SignInRequestDto(val username: String, val password: String)

/** Body of the sign-up request. */
@Serializable
data class SignUpRequestDto(
    val username: String,
    val password: String,
    val fullName: String,
    /** "Rancher" or "Veterinarian", exactly as the API expects. */
    val role: String,
    /** Optional; the server rejects a malformed or already registered address. */
    val email: String? = null,
)

/** Signed-in user returned by sign-in; [token] is sent as Bearer on later requests. */
@Serializable
data class AuthenticatedUserDto(
    val id: Int,
    val username: String,
    val fullName: String = "",
    val role: String,
    val token: String,
    val email: String? = null,
)

/**
 * Body of a successful sign-up. The server wraps a localized message, which arrives as a plain string or as an
 * object (`{name, value, resourceNotFound...}`) depending on its resources, so any JSON shape is accepted and ignored.
 */
@Serializable
data class MessageDto(val message: JsonElement? = null)

/** Retrofit contract of the authentication endpoints. */
interface AuthApi {
    @POST("authentication/sign-in")
    suspend fun signIn(@Body body: SignInRequestDto): AuthenticatedUserDto

    @POST("authentication/sign-up")
    suspend fun signUp(@Body body: SignUpRequestDto): MessageDto
}
