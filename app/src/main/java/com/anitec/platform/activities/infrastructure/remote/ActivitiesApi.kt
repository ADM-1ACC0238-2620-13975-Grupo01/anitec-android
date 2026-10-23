package com.anitec.platform.activities.infrastructure.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class FarmActivityDto(
    val id: Int = 0,
    val ownerId: Int? = null,
    val veterinarianId: Int? = null,
    val title: String,
    val type: String = "",
    val date: String,
    val priority: String = "",
    val status: String = "",
)

/** The route is `farm-events`, not `farm-activities`. */
interface ActivitiesApi {
    @GET("farm-events") suspend fun getActivities(): List<FarmActivityDto>
    @POST("farm-events") suspend fun create(@Body body: FarmActivityDto): FarmActivityDto
    @PUT("farm-events/{id}") suspend fun update(@Path("id") id: Int, @Body body: FarmActivityDto): FarmActivityDto
    @DELETE("farm-events/{id}") suspend fun delete(@Path("id") id: Int)
}
