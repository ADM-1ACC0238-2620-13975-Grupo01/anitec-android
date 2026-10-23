package com.anitec.platform.devices.infrastructure.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val type: String,
    val serialNumber: String,
    val status: String,
    val herdId: Int?,
    val animalId: Int?,
    val readingType: String?,
    val readingValue: Double?,
    val readingUnit: String?,
    val readingAt: String?,
    val readingCount: Int,
)

@Dao
interface DevicesDao {
    @Query("SELECT * FROM devices ORDER BY name COLLATE NOCASE")
    fun observeDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun find(id: Int): DeviceEntity?

    @Upsert suspend fun upsert(device: DeviceEntity)
    @Upsert suspend fun upsertAll(devices: List<DeviceEntity>)
    @Query("DELETE FROM devices WHERE id = :id") suspend fun delete(id: Int)
    @Query("DELETE FROM devices") suspend fun clear()

    @Transaction
    suspend fun replaceAll(devices: List<DeviceEntity>) {
        clear()
        upsertAll(devices)
    }
}
