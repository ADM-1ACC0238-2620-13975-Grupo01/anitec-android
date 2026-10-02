package com.anitec.platform.livestock.infrastructure.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "herds")
data class HerdEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val location: String,
    val owner: String,
    val ownerId: Int,
    val veterinarianId: Int?,
    val mainType: String,
)

@Entity(tableName = "corrals", indices = [Index("herdId")])
data class CorralEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val herdId: Int,
)

@Entity(tableName = "animals", indices = [Index("herdId"), Index("corralId")])
data class AnimalEntity(
    @PrimaryKey val id: Int,
    val tag: String,
    val name: String,
    val species: String,
    val breed: String,
    val gender: String,
    val birthDate: String?,
    val weight: Double,
    val status: String,
    val herdId: Int,
    val corralId: Int?,
    val source: String?,
    val ageRange: String?,
    val imageUrl: String?,
)

@Dao
interface LivestockDao {
    @Query("SELECT * FROM herds ORDER BY name COLLATE NOCASE")
    fun observeHerds(): Flow<List<HerdEntity>>

    @Query("SELECT * FROM corrals ORDER BY name COLLATE NOCASE")
    fun observeCorrals(): Flow<List<CorralEntity>>

    @Query("SELECT * FROM animals ORDER BY name COLLATE NOCASE")
    fun observeAnimals(): Flow<List<AnimalEntity>>

    /** Ids of the animals the user may see; other contexts scope their own data with them. */
    @Query("SELECT id FROM animals")
    suspend fun animalIds(): List<Int>

    @Upsert suspend fun upsertHerd(herd: HerdEntity)
    @Upsert suspend fun upsertCorral(corral: CorralEntity)
    @Upsert suspend fun upsertAnimal(animal: AnimalEntity)
    @Upsert suspend fun upsertAnimals(animals: List<AnimalEntity>)

    @Query("DELETE FROM herds WHERE id = :id") suspend fun deleteHerd(id: Int)
    @Query("DELETE FROM corrals WHERE id = :id") suspend fun deleteCorral(id: Int)
    @Query("DELETE FROM animals WHERE id = :id") suspend fun deleteAnimal(id: Int)
    @Query("DELETE FROM animals WHERE id IN (:ids)") suspend fun deleteAnimals(ids: List<Int>)

    // The server clears corral_id on animals when their corral is deleted (ON DELETE SET NULL).
    @Query("UPDATE animals SET corralId = NULL WHERE corralId = :corralId")
    suspend fun detachAnimalsFromCorral(corralId: Int)

    @Query("DELETE FROM herds") suspend fun clearHerds()
    @Query("DELETE FROM corrals") suspend fun clearCorrals()
    @Query("DELETE FROM animals") suspend fun clearAnimals()

    @Upsert suspend fun upsertHerds(herds: List<HerdEntity>)
    @Upsert suspend fun upsertCorrals(corrals: List<CorralEntity>)

    /** Replaces the whole cache atomically so readers never see a half-synced state. */
    @Transaction
    suspend fun replaceAll(herds: List<HerdEntity>, corrals: List<CorralEntity>, animals: List<AnimalEntity>) {
        clearAnimals()
        clearCorrals()
        clearHerds()
        upsertHerds(herds)
        upsertCorrals(corrals)
        upsertAnimals(animals)
    }
}
