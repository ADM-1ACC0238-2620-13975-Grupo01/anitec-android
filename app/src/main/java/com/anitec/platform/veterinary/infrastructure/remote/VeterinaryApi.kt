package com.anitec.platform.veterinary.infrastructure.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
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

/** Veterinarian-only endpoints. The rest of the clients flow is built in the veterinary phase. */
interface VeterinaryApi {
    @GET("veterinarian/{veterinarianId}/clients")
    suspend fun getClients(@Path("veterinarianId") veterinarianId: Int): List<VeterinarianClientDto>
}
