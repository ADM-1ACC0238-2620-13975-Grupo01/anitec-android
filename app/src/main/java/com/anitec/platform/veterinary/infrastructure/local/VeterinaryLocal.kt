package com.anitec.platform.veterinary.infrastructure.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val rancherId: Int,
    val rancherName: String,
    val status: String,
    val herds: Int,
    val animals: Int,
)

@Dao
interface VeterinaryDao {
    @Query("SELECT * FROM clients ORDER BY rancherName COLLATE NOCASE")
    fun observeClients(): Flow<List<ClientEntity>>

    /** Ids of the ranchers currently linked; other contexts use them to scope what a veterinarian sees. */
    @Query("SELECT rancherId FROM clients")
    suspend fun rancherIds(): List<Int>

    @Upsert suspend fun upsertAll(clients: List<ClientEntity>)
    @Query("DELETE FROM clients WHERE rancherId = :rancherId") suspend fun delete(rancherId: Int)
    @Query("DELETE FROM clients") suspend fun clear()

    @Transaction
    suspend fun replaceAll(clients: List<ClientEntity>) {
        clear()
        upsertAll(clients)
    }
}
