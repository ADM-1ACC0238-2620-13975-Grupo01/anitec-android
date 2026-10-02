package com.anitec.platform.sanitary.infrastructure.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "health_events", indices = [Index("animalId")])
data class HealthEventEntity(
    @PrimaryKey val id: Int,
    val animalId: Int,
    val type: String,
    val date: String,
    val description: String,
    val veterinarian: String,
    val diagnosis: String,
    val treatment: String,
    val prescription: String,
    val followUp: String,
    val nextDueDate: String?,
)

@Dao
interface SanitaryDao {
    @Query("SELECT * FROM health_events ORDER BY date DESC, id DESC")
    fun observeEvents(): Flow<List<HealthEventEntity>>

    @Upsert suspend fun upsert(event: HealthEventEntity)
    @Upsert suspend fun upsertAll(events: List<HealthEventEntity>)
    @Query("DELETE FROM health_events WHERE id = :id") suspend fun delete(id: Int)
    @Query("DELETE FROM health_events") suspend fun clear()

    @Transaction
    suspend fun replaceAll(events: List<HealthEventEntity>) {
        clear()
        upsertAll(events)
    }
}
