package com.anitec.platform.activities.application

import com.anitec.platform.activities.domain.Activity
import com.anitec.platform.activities.domain.ActivitiesRepository
import com.anitec.platform.activities.domain.ActivityDraft
import com.anitec.platform.core.common.AppResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Cached activities the user can see, including ones still waiting to sync. */
class ObserveActivitiesUseCase @Inject constructor(private val repository: ActivitiesRepository) {
    operator fun invoke(): Flow<List<Activity>> = repository.observeActivities()
}

/** Downloads the activities and replaces the cache. */
class RefreshActivitiesUseCase @Inject constructor(private val repository: ActivitiesRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.refresh()
}

class SaveActivityUseCase @Inject constructor(private val repository: ActivitiesRepository) {
    /** Creates the activity when [id] is null, otherwise updates it. */
    suspend operator fun invoke(id: Int?, draft: ActivityDraft): AppResult<Activity> =
        if (id == null) repository.create(draft) else repository.update(id, draft)
}

/** Deletes an activity on the server and in the cache. */
class DeleteActivityUseCase @Inject constructor(private val repository: ActivitiesRepository) {
    suspend operator fun invoke(id: Int): AppResult<Unit> = repository.delete(id)
}
