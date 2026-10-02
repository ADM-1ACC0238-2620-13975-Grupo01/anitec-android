package com.anitec.platform.iam.infrastructure.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class SignInRequestDto(val username: String, val password: String)

@Serializable
data class SignUpRequestDto(
    val username: String,
    val password: String,
    val fullName: String,
    /** "Rancher" or "Veterinarian", exactly as the API expects. */
    val role: String,
)

@Serializable
data class AuthenticatedUserDto(
    val id: Int,
    val username: String,
    val fullName: String = "",
    val role: String,
    val token: String,
)

@Serializable
data class MessageDto(val message: String? = null)

interface AuthApi {
    @POST("authentication/sign-in")
    suspend fun signIn(@Body body: SignInRequestDto): AuthenticatedUserDto

    @POST("authentication/sign-up")
    suspend fun signUp(@Body body: SignUpRequestDto): MessageDto
}
