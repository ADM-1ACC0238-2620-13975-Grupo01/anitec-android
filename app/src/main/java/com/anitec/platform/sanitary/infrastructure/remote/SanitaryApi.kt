package com.anitec.platform.sanitary.infrastructure.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class HealthEventDto(
    val id: Int = 0,
    val animalId: Int,
    val type: String,
    val date: String,
    val description: String = "",
    val veterinarian: String = "",
    val diagnosis: String = "",
    val treatment: String = "",
    val prescription: String = "",
    val followUp: String = "",
    val nextDueDate: String? = null,
)

interface SanitaryApi {
    @GET("health-events") suspend fun getEvents(): List<HealthEventDto>
    @POST("health-events") suspend fun createEvent(@Body body: HealthEventDto): HealthEventDto
    @PUT("health-events/{id}") suspend fun updateEvent(@Path("id") id: Int, @Body body: HealthEventDto): HealthEventDto
    @DELETE("health-events/{id}") suspend fun deleteEvent(@Path("id") id: Int)
}
