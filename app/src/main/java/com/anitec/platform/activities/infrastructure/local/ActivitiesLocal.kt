package com.anitec.platform.activities.infrastructure.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Cached farm activity. A negative [id] means it was created offline and is not sent yet. */
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

/**
 * Data Access Object (DAO) for the [ActivityEntity] table.
 *
 * Provides local database access methods for managing offline-first farm activities
 * using Room persistence library.
 */
@Dao
interface ActivitiesDao {
    /**
     * Observes all stored activities ordered chronologically by date and identifier.
     *
     * @return A [Flow] emitting the updated list of [ActivityEntity] whenever data changes.
     */
    @Query("SELECT * FROM activities ORDER BY date ASC, id ASC")
    fun observeActivities(): Flow<List<ActivityEntity>>
    /**
     * Inserts or updates a single activity entity in the local database.
     *
     * @param activity The [ActivityEntity] instance to insert or modify.
     */
    @Upsert suspend fun upsert(activity: ActivityEntity)
    @Upsert suspend fun upsertAll(activities: List<ActivityEntity>)
    @Query("DELETE FROM activities WHERE id = :id") suspend fun delete(id: Int)
    // Activities created offline have negative ids and must survive a refresh until they are sent.
    @Query("DELETE FROM activities WHERE id >= 0") suspend fun clear()

    /** Replaces the synced activities in one transaction, keeping the pending ones. */
    @Transaction
    suspend fun replaceAll(activities: List<ActivityEntity>) {
        clear()
        upsertAll(activities)
    }
}
