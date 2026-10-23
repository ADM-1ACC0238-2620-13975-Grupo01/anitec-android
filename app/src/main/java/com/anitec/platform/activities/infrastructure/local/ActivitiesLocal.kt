package com.anitec.platform.activities.infrastructure.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey val id: Int,
    val ownerId: Int?,
    val veterinarianId: Int?,
    val title: String,
    val type: String,
    val date: String,
    val priority: String,
    val status: String,
)

@Dao
interface ActivitiesDao {
    @Query("SELECT * FROM activities ORDER BY date ASC, id ASC")
    fun observeActivities(): Flow<List<ActivityEntity>>

    @Upsert suspend fun upsert(activity: ActivityEntity)
    @Upsert suspend fun upsertAll(activities: List<ActivityEntity>)
    @Query("DELETE FROM activities WHERE id = :id") suspend fun delete(id: Int)
    // Activities created offline have negative ids and must survive a refresh until they are sent.
    @Query("DELETE FROM activities WHERE id >= 0") suspend fun clear()

    @Transaction
    suspend fun replaceAll(activities: List<ActivityEntity>) {
        clear()
        upsertAll(activities)
    }
}
