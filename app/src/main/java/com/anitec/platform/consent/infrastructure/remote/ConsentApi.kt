package com.anitec.platform.consent.infrastructure.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** A veterinarian's request or link, as the rancher sees it. Only the fields the app shows. */
@Serializable
data class VeterinarianLinkDto(
    val id: Int,
    val veterinarianId: Int = 0,
    val veterinarianName: String = "",
    val veterinarianUsername: String = "",
    val status: String = "",
    val requestedAt: String = "",
)

/** Rancher-only endpoints (the controller is restricted to the Rancher role on the server). */
interface ConsentApi {
    @GET("rancher/{rancherId}/veterinarian-links")
    suspend fun getLinks(@Path("rancherId") rancherId: Int, @Query("status") status: String? = null): List<VeterinarianLinkDto>

    @POST("rancher/{rancherId}/veterinarian-links/{linkId}/accept")
    suspend fun accept(@Path("rancherId") rancherId: Int, @Path("linkId") linkId: Int): VeterinarianLinkDto

    @POST("rancher/{rancherId}/veterinarian-links/{linkId}/reject")
    suspend fun reject(@Path("rancherId") rancherId: Int, @Path("linkId") linkId: Int): VeterinarianLinkDto

    @POST("rancher/{rancherId}/veterinarian-links/{linkId}/revoke")
    suspend fun revoke(@Path("rancherId") rancherId: Int, @Path("linkId") linkId: Int): VeterinarianLinkDto
}
