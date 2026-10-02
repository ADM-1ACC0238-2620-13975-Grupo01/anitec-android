package com.anitec.platform.livestock.domain

import com.anitec.platform.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/** Read side of the local cache plus the sync that fills it. Writes go to the API and then update the cache. */
interface LivestockRepository {
    fun observeHerds(): Flow<List<Herd>>
    fun observeCorrals(): Flow<List<Corral>>
    fun observeAnimals(): Flow<List<Animal>>

    /** Downloads herds, corrals and animals the signed-in user may see and replaces the cache. */
    suspend fun refresh(): AppResult<Unit>

    suspend fun createHerd(draft: HerdDraft): AppResult<Herd>
    suspend fun updateHerd(id: Int, draft: HerdDraft): AppResult<Herd>
    suspend fun deleteHerd(id: Int): AppResult<Unit>

    suspend fun createCorral(draft: CorralDraft): AppResult<Corral>
    suspend fun updateCorral(id: Int, draft: CorralDraft): AppResult<Corral>
    suspend fun deleteCorral(id: Int): AppResult<Unit>

    suspend fun createAnimal(draft: AnimalDraft): AppResult<Animal>
    suspend fun updateAnimal(id: Int, draft: AnimalDraft): AppResult<Animal>
    suspend fun deleteAnimal(id: Int): AppResult<Unit>
    suspend fun createAnimalBatch(draft: AnimalBatchDraft): AppResult<List<Animal>>
    suspend fun updateAnimalsStatus(ids: List<Int>, status: String): AppResult<List<Animal>>
    suspend fun deleteAnimals(ids: List<Int>): AppResult<Unit>
}

/** Uploads an animal photo and returns its server-relative path. */
interface AnimalImageUploader {
    /** [uri] is a content or file URI; the image is downscaled and compressed before it is sent. */
    suspend fun upload(uri: String): AppResult<String>
}
