package com.anitec.platform.sync

import com.anitec.platform.activities.infrastructure.local.ActivitiesDao
import com.anitec.platform.activities.infrastructure.remote.ActivitiesApi
import com.anitec.platform.activities.infrastructure.remote.FarmActivityDto
import com.anitec.platform.activities.infrastructure.toDomain
import com.anitec.platform.activities.infrastructure.toEntity
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.network.safeApiCall
import com.anitec.platform.core.outbox.OutboxDao
import com.anitec.platform.core.outbox.OutboxFailures
import com.anitec.platform.core.outbox.OutboxKinds
import com.anitec.platform.core.outbox.PendingOperationEntity
import com.anitec.platform.livestock.infrastructure.local.LivestockDao
import com.anitec.platform.livestock.infrastructure.remote.AnimalDto
import com.anitec.platform.livestock.infrastructure.remote.LivestockApi
import com.anitec.platform.livestock.infrastructure.toDomain
import com.anitec.platform.livestock.infrastructure.toEntity
import com.anitec.platform.sanitary.infrastructure.local.SanitaryDao
import com.anitec.platform.sanitary.infrastructure.remote.HealthEventDto
import com.anitec.platform.sanitary.infrastructure.remote.SanitaryApi
import com.anitec.platform.sanitary.infrastructure.toDomain
import com.anitec.platform.sanitary.infrastructure.toEntity
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncOutcome {
    /** Nothing left to send (changes that were refused stay marked as failed). */
    Done,

    /** The connection or the server failed; try again later. */
    Retry,

    /** The session ended; the cache, and the queue with it, is being cleared. */
    Stopped,
}

/**
 * Sends the changes made offline, oldest first, so a record created for an animal that was itself created
 * offline goes after it. Each change either reaches the server (and its local copy is swapped for the real
 * record), is refused (marked failed, for the user to retry or discard), or stops the run to be retried.
 */
@Singleton
class OutboxProcessor @Inject constructor(
    private val outboxDao: OutboxDao,
    private val livestockApi: LivestockApi,
    private val livestockDao: LivestockDao,
    private val sanitaryApi: SanitaryApi,
    private val sanitaryDao: SanitaryDao,
    private val activitiesApi: ActivitiesApi,
    private val activitiesDao: ActivitiesDao,
    private val json: Json,
) {
    private enum class Step { Next, Retry, Stop }

    suspend fun run(): SyncOutcome {
        while (true) {
            // Read again each time: sending an animal rewrites the payloads of the records that depend on it.
            val operation = outboxDao.pending().firstOrNull() ?: return SyncOutcome.Done
            when (send(operation)) {
                Step.Next -> Unit
                Step.Retry -> return SyncOutcome.Retry
                Step.Stop -> return SyncOutcome.Stopped
            }
        }
    }

    /** Puts every refused change back in line. */
    suspend fun retryFailed() = outboxDao.resetFailed()

    /** Gives up on the refused changes and removes the local records they had created. */
    suspend fun discardFailed() {
        outboxDao.failed().forEach { operation ->
            removeLocalRecord(operation)
            outboxDao.delete(operation.seq)
        }
    }

    private suspend fun send(operation: PendingOperationEntity): Step = when (operation.kind) {
        OutboxKinds.CREATE_ANIMAL -> sendAnimal(operation)
        OutboxKinds.CREATE_HEALTH_EVENT -> sendHealthEvent(operation)
        OutboxKinds.CREATE_ACTIVITY -> sendActivity(operation)
        else -> fail(operation, OutboxFailures.REJECTED)
    }

    private suspend fun sendAnimal(operation: PendingOperationEntity): Step {
        val body = json.decodeFromString(AnimalDto.serializer(), operation.payload)
        val result = safeApiCall {
            // After an ambiguous failure the first try may have succeeded without us hearing back: look for it.
            val existing = if (operation.attempts > 0) {
                livestockApi.getAnimals().firstOrNull { it.tag == body.tag && it.herdId == body.herdId }
            } else {
                null
            }
            existing ?: livestockApi.createAnimal(body)
        }
        return when (result) {
            is AppResult.Success -> {
                val created = result.value
                livestockDao.deleteAnimal(operation.localId)
                livestockDao.upsertAnimal(created.toDomain().toEntity())
                sanitaryDao.reassignAnimal(operation.localId, created.id)
                repointDependents(operation.localId, created.id)
                outboxDao.delete(operation.seq)
                Step.Next
            }
            is AppResult.Failure -> failure(operation, result.error)
        }
    }

    private suspend fun sendHealthEvent(operation: PendingOperationEntity): Step {
        val body = json.decodeFromString(HealthEventDto.serializer(), operation.payload)
        // Its animal was created offline too and has not reached the server (or was refused).
        if (body.animalId < 0) return fail(operation, OutboxFailures.DEPENDENCY)
        return when (val result = safeApiCall { sanitaryApi.createEvent(body) }) {
            is AppResult.Success -> {
                sanitaryDao.delete(operation.localId)
                sanitaryDao.upsert(result.value.toDomain().toEntity())
                outboxDao.delete(operation.seq)
                Step.Next
            }
            is AppResult.Failure -> failure(operation, result.error)
        }
    }

    private suspend fun sendActivity(operation: PendingOperationEntity): Step {
        val body = json.decodeFromString(FarmActivityDto.serializer(), operation.payload)
        return when (val result = safeApiCall { activitiesApi.create(body) }) {
            is AppResult.Success -> {
                activitiesDao.delete(operation.localId)
                activitiesDao.upsert(result.value.toDomain().toEntity())
                outboxDao.delete(operation.seq)
                Step.Next
            }
            is AppResult.Failure -> failure(operation, result.error)
        }
    }

    /** Health records queued for an animal that just got its real id must point to it. */
    private suspend fun repointDependents(localAnimalId: Int, realAnimalId: Int) {
        outboxDao.all().filter { it.kind == OutboxKinds.CREATE_HEALTH_EVENT }.forEach { dependent ->
            val body = json.decodeFromString(HealthEventDto.serializer(), dependent.payload)
            if (body.animalId == localAnimalId) {
                outboxDao.updatePayload(dependent.seq, json.encodeToString(HealthEventDto.serializer(), body.copy(animalId = realAnimalId)))
            }
        }
    }

    private suspend fun failure(operation: PendingOperationEntity, error: AppError): Step = when (error) {
        AppError.Unauthorized -> Step.Stop
        // The device or the server is not answering: keep the change and try again later.
        AppError.Network -> {
            outboxDao.recordAttempt(operation.seq)
            Step.Retry
        }
        is AppError.Server, is AppError.Unknown -> {
            if (operation.attempts + 1 >= MAX_SERVER_ATTEMPTS) {
                fail(operation, OutboxFailures.SERVER)
            } else {
                outboxDao.recordAttempt(operation.seq)
                Step.Retry
            }
        }
        is AppError.Conflict -> fail(operation, OutboxFailures.CONFLICT)
        AppError.Forbidden -> fail(operation, OutboxFailures.FORBIDDEN)
        AppError.NotFound -> fail(operation, OutboxFailures.NOT_FOUND)
        is AppError.Validation -> fail(operation, OutboxFailures.REJECTED)
    }

    private suspend fun fail(operation: PendingOperationEntity, reason: String): Step {
        outboxDao.markFailed(operation.seq, reason)
        return Step.Next
    }

    private suspend fun removeLocalRecord(operation: PendingOperationEntity) {
        when (operation.kind) {
            OutboxKinds.CREATE_ANIMAL -> livestockDao.deleteAnimal(operation.localId)
            OutboxKinds.CREATE_HEALTH_EVENT -> sanitaryDao.delete(operation.localId)
            OutboxKinds.CREATE_ACTIVITY -> activitiesDao.delete(operation.localId)
        }
    }

    private companion object {
        const val MAX_SERVER_ATTEMPTS = 5
    }
}
