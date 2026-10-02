package com.anitec.platform.livestock.infrastructure.remote

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

@Serializable
data class HerdDto(
    val id: Int = 0,
    val name: String,
    val location: String,
    val owner: String,
    val ownerId: Int,
    val veterinarianId: Int? = null,
    val mainType: String,
)

@Serializable
data class CorralDto(val id: Int = 0, val name: String, val herdId: Int)

@Serializable
data class AnimalDto(
    val id: Int = 0,
    val tag: String,
    val name: String,
    val species: String,
    val breed: String,
    val gender: String,
    val birthDate: String? = null,
    val weight: Double = 0.0,
    val status: String,
    val herdId: Int,
    val corralId: Int? = null,
    val source: String? = null,
    val ageRange: String? = null,
    val imageUrl: String? = null,
)

@Serializable
data class AnimalBatchDto(
    val species: String,
    val breed: String,
    val gender: String,
    val birthDate: String? = null,
    val weight: Double,
    val status: String,
    val herdId: Int,
    val corralId: Int,
    val quantity: Int,
    val source: String? = null,
    val ageRange: String? = null,
    val imageUrl: String? = null,
)

@Serializable
data class AnimalIdsDto(val animalIds: List<Int>)

@Serializable
data class AnimalsStatusDto(val animalIds: List<Int>, val status: String)

@Serializable
data class UploadedImageDto(val url: String)

/** Retrofit contract of the Livestock endpoints. PUT bodies are the full resource, as the API expects. */
interface LivestockApi {
    @GET("herds") suspend fun getHerds(): List<HerdDto>
    @POST("herds") suspend fun createHerd(@Body body: HerdDto): HerdDto
    @PUT("herds/{id}") suspend fun updateHerd(@Path("id") id: Int, @Body body: HerdDto): HerdDto
    @DELETE("herds/{id}") suspend fun deleteHerd(@Path("id") id: Int)

    @GET("corrals") suspend fun getCorrals(): List<CorralDto>
    @POST("corrals") suspend fun createCorral(@Body body: CorralDto): CorralDto
    @PUT("corrals/{id}") suspend fun updateCorral(@Path("id") id: Int, @Body body: CorralDto): CorralDto
    @DELETE("corrals/{id}") suspend fun deleteCorral(@Path("id") id: Int)

    @GET("animals") suspend fun getAnimals(): List<AnimalDto>
    @POST("animals") suspend fun createAnimal(@Body body: AnimalDto): AnimalDto
    @PUT("animals/{id}") suspend fun updateAnimal(@Path("id") id: Int, @Body body: AnimalDto): AnimalDto
    @DELETE("animals/{id}") suspend fun deleteAnimal(@Path("id") id: Int)

    @POST("animals/bulk") suspend fun createAnimalBatch(@Body body: AnimalBatchDto): List<AnimalDto>
    @PATCH("animals/bulk-status") suspend fun updateAnimalsStatus(@Body body: AnimalsStatusDto): List<AnimalDto>

    // The API expects a JSON body on this DELETE, which Retrofit only sends with an explicit @HTTP.
    @HTTP(method = "DELETE", path = "animals/bulk", hasBody = true)
    suspend fun deleteAnimals(@Body body: AnimalIdsDto)

    @Multipart
    @POST("animals/upload-image")
    suspend fun uploadImage(@Part file: MultipartBody.Part): UploadedImageDto
}
