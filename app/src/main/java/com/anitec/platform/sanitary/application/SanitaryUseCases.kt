package com.anitec.platform.sanitary.application

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.sanitary.domain.HealthEvent
import com.anitec.platform.sanitary.domain.HealthEventDraft
import com.anitec.platform.sanitary.domain.SanitaryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveHealthEventsUseCase @Inject constructor(private val repository: SanitaryRepository) {
    operator fun invoke(): Flow<List<HealthEvent>> = repository.observeEvents()
}

class RefreshSanitaryUseCase @Inject constructor(private val repository: SanitaryRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.refresh()
}

class SaveHealthEventUseCase @Inject constructor(private val repository: SanitaryRepository) {
    /** Creates the record when [id] is null, otherwise updates it. */
    suspend operator fun invoke(id: Int?, draft: HealthEventDraft): AppResult<HealthEvent> =
        if (id == null) repository.create(draft) else repository.update(id, draft)
}

class DeleteHealthEventUseCase @Inject constructor(private val repository: SanitaryRepository) {
    suspend operator fun invoke(id: Int): AppResult<Unit> = repository.delete(id)
}
