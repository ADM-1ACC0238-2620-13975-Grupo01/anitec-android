package com.anitec.platform.core.outbox

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records created without a connection get a negative local id until the server assigns the real one, so
 * "pending sync" needs no extra column: any cached record whose id is below zero is still waiting.
 */
val Int.isPendingSync: Boolean get() = this < 0

object OutboxKinds {
    const val CREATE_ANIMAL = "create_animal"
    const val CREATE_HEALTH_EVENT = "create_health_event"
    const val CREATE_ACTIVITY = "create_activity"
}

/** Why a change was given up on; shown to the user as a generic message, never the server text. */
object OutboxFailures {
    const val REJECTED = "rejected"
    const val CONFLICT = "conflict"
    const val FORBIDDEN = "forbidden"
    const val NOT_FOUND = "not_found"
    const val SERVER = "server"
    const val DEPENDENCY = "dependency"
}

/** One change made offline, waiting to be sent. [payload] is the JSON of the request body. */
@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val kind: String,
    /** Negative id of the cached record this change created. */
    val localId: Int,
    val payload: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val failed: Boolean = false,
    val lastError: String? = null,
)

@Dao
interface OutboxDao {
    @Query("SELECT * FROM pending_operations ORDER BY seq")
    fun observeAll(): Flow<List<PendingOperationEntity>>

    @Query("SELECT * FROM pending_operations ORDER BY seq")
    suspend fun all(): List<PendingOperationEntity>

    @Query("SELECT * FROM pending_operations WHERE failed = 0 ORDER BY seq")
    suspend fun pending(): List<PendingOperationEntity>

    @Query("SELECT * FROM pending_operations WHERE failed = 1 ORDER BY seq")
    suspend fun failed(): List<PendingOperationEntity>

    @Query("SELECT COUNT(*) FROM pending_operations")
    suspend fun count(): Int

    @Insert
    suspend fun insert(operation: PendingOperationEntity): Long

    @Query("SELECT MIN(localId) FROM pending_operations")
    suspend fun minLocalId(): Int?

    @Query("DELETE FROM pending_operations WHERE seq = :seq")
    suspend fun delete(seq: Long)

    @Query("UPDATE pending_operations SET attempts = attempts + 1 WHERE seq = :seq")
    suspend fun recordAttempt(seq: Long)

    @Query("UPDATE pending_operations SET failed = 1, lastError = :error WHERE seq = :seq")
    suspend fun markFailed(seq: Long, error: String)

    @Query("UPDATE pending_operations SET failed = 0, attempts = 0, lastError = NULL WHERE failed = 1")
    suspend fun resetFailed()

    @Query("UPDATE pending_operations SET payload = :payload WHERE seq = :seq")
    suspend fun updatePayload(seq: Long, payload: String)

    /** Stores the change and returns the negative id its local record must use. */
    @Transaction
    suspend fun enqueue(kind: String, payload: String, now: Long): Int {
        val localId = minOf(minLocalId() ?: 0, 0) - 1
        insert(PendingOperationEntity(kind = kind, localId = localId, payload = payload, createdAt = now))
        return localId
    }
}

/** Starts (or chains) a background send of the pending changes. Implemented with WorkManager. */
interface OutboxScheduler {
    fun schedule()
}

/** What repositories use to park a change while the device is offline. */
@Singleton
class OutboxQueue @Inject constructor(
    private val dao: OutboxDao,
    private val json: Json,
    private val scheduler: OutboxScheduler,
) {
    /** Stores [body] as a change of [kind], schedules the send and returns the negative id of the local record. */
    suspend fun <T> enqueue(kind: String, serializer: KSerializer<T>, body: T): Int {
        val localId = dao.enqueue(kind, json.encodeToString(serializer, body), System.currentTimeMillis())
        scheduler.schedule()
        return localId
    }
}
