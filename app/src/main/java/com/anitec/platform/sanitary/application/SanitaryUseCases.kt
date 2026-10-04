package com.anitec.platform.sanitary.application

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.sanitary.domain.HealthEvent
import com.anitec.platform.sanitary.domain.HealthEventDraft
import com.anitec.platform.sanitary.domain.SanitaryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Application-layer sanitary (health records) use cases.
 * ViewModels call these instead of [SanitaryRepository] directly.
 */

/** Streams cached health events for the signed-in user (Room-backed Flow). */
class ObserveHealthEventsUseCase @Inject constructor(private val repository: SanitaryRepository) {
    operator fun invoke(): Flow<List<HealthEvent>> = repository.observeEvents()
}

/** Pulls the latest health events from the API and replaces the local cache. */
class RefreshSanitaryUseCase @Inject constructor(private val repository: SanitaryRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.refresh()
}

/** Creates or updates a health event from a draft. */
class SaveHealthEventUseCase @Inject constructor(private val repository: SanitaryRepository) {
    /** Creates the record when [id] is null, otherwise updates it. */
    suspend operator fun invoke(id: Int?, draft: HealthEventDraft): AppResult<HealthEvent> =
        if (id == null) repository.create(draft) else repository.update(id, draft)
}

/** Deletes a health event by server id and updates the local cache. */
class DeleteHealthEventUseCase @Inject constructor(private val repository: SanitaryRepository) {
    suspend operator fun invoke(id: Int): AppResult<Unit> = repository.delete(id)
}
