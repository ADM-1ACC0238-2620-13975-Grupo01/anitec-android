package com.anitec.platform.financial.infrastructure.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class FinancialRecordDto(
    val id: Int = 0,
    val ownerId: Int,
    val type: String = "",
    val category: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val description: String = "",
)

/** Rancher-only endpoints. */
interface FinancialApi {
    @GET("financial-records") suspend fun getRecords(): List<FinancialRecordDto>
    @POST("financial-records") suspend fun create(@Body body: FinancialRecordDto): FinancialRecordDto
    @PUT("financial-records/{id}") suspend fun update(@Path("id") id: Int, @Body body: FinancialRecordDto): FinancialRecordDto
    @DELETE("financial-records/{id}") suspend fun delete(@Path("id") id: Int)
}
