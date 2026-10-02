package com.anitec.platform.devices.infrastructure.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class DeviceDto(
    val id: Int = 0,
    val name: String,
    val type: String = "",
    val serialNumber: String = "",
    val status: String = "",
    val herdId: Int? = null,
    val animalId: Int? = null,
)

@Serializable
data class DeviceMetricDto(
    val id: Int = 0,
    val deviceId: Int,
    val type: String = "",
    val value: Double = 0.0,
    val unit: String = "",
    val recordedAt: String = "",
)

/** Reading a device is open to both roles; changing one is rancher-only. */
interface DevicesApi {
    @GET("devices") suspend fun getDevices(): List<DeviceDto>
    @POST("devices") suspend fun create(@Body body: DeviceDto): DeviceDto
    @PUT("devices/{id}") suspend fun update(@Path("id") id: Int, @Body body: DeviceDto): DeviceDto
    @DELETE("devices/{id}") suspend fun delete(@Path("id") id: Int)

    @GET("device-metrics") suspend fun getMetrics(): List<DeviceMetricDto>
}
