package com.anitec.platform.veterinary.infrastructure.remote

import kotlinx.serialization.Serializable
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class VeterinarianClientDto(
    val id: Int = 0,
    val veterinarianId: Int = 0,
    val rancherId: Int,
    val rancherName: String = "",
    val status: String = "",
    val herds: Int = 0,
    val animals: Int = 0,
)

@Serializable
data class AvailableRancherDto(
    val id: Int,
    val username: String = "",
    val fullName: String = "",
    val herds: Int = 0,
    val animals: Int = 0,
)

/** Link created by POST; only the fields the app needs. */
@Serializable
data class ClientLinkDto(val id: Int = 0, val veterinarianId: Int = 0, val rancherId: Int = 0, val status: String = "")

/** Veterinarian-only endpoints (the controller is restricted to the Veterinarian role on the server). */
interface VeterinaryApi {
    @GET("veterinarian/{veterinarianId}/clients")
    suspend fun getClients(@Path("veterinarianId") veterinarianId: Int): List<VeterinarianClientDto>

    @GET("veterinarian/{veterinarianId}/available-ranchers")
    suspend fun getAvailableRanchers(@Path("veterinarianId") veterinarianId: Int): List<AvailableRancherDto>

    @POST("veterinarian/{veterinarianId}/clients/{rancherId}")
    suspend fun addClient(@Path("veterinarianId") veterinarianId: Int, @Path("rancherId") rancherId: Int): ClientLinkDto

    @DELETE("veterinarian/{veterinarianId}/clients/{rancherId}")
    suspend fun removeClient(@Path("veterinarianId") veterinarianId: Int, @Path("rancherId") rancherId: Int)
}
