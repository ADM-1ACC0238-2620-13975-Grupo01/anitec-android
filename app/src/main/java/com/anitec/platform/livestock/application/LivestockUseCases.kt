package com.anitec.platform.livestock.application

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.livestock.domain.Animal
import com.anitec.platform.livestock.domain.AnimalBatchDraft
import com.anitec.platform.livestock.domain.AnimalDraft
import com.anitec.platform.livestock.domain.AnimalImageUploader
import com.anitec.platform.livestock.domain.AnimalPlacementPolicy
import com.anitec.platform.livestock.domain.Corral
import com.anitec.platform.livestock.domain.CorralDraft
import com.anitec.platform.livestock.domain.Herd
import com.anitec.platform.livestock.domain.HerdDraft
import com.anitec.platform.livestock.domain.LivestockRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Codes carried in [AppError.Validation] when a rule is broken before any request is made. */
object LivestockValidation {
    const val INVALID_PLACEMENT = "livestock.invalid_placement"
    const val INVALID_BATCH_SIZE = "livestock.invalid_batch_size"
    const val NOTHING_SELECTED = "livestock.nothing_selected"
}

private fun invalid(code: String) = AppResult.Failure(AppError.Validation(listOf(code)))

// --- reading ---

class ObserveHerdsUseCase @Inject constructor(private val repository: LivestockRepository) {
    operator fun invoke(): Flow<List<Herd>> = repository.observeHerds()
}

class ObserveCorralsUseCase @Inject constructor(private val repository: LivestockRepository) {
    operator fun invoke(): Flow<List<Corral>> = repository.observeCorrals()
}

class ObserveAnimalsUseCase @Inject constructor(private val repository: LivestockRepository) {
    operator fun invoke(): Flow<List<Animal>> = repository.observeAnimals()
}

class RefreshLivestockUseCase @Inject constructor(private val repository: LivestockRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.refresh()
}

// --- herds ---

class SaveHerdUseCase @Inject constructor(private val repository: LivestockRepository) {
    /** Creates the herd when [id] is null, otherwise updates it. */
    suspend operator fun invoke(id: Int?, draft: HerdDraft): AppResult<Herd> =
        if (id == null) repository.createHerd(draft) else repository.updateHerd(id, draft)
}

class DeleteHerdUseCase @Inject constructor(private val repository: LivestockRepository) {
    suspend operator fun invoke(id: Int): AppResult<Unit> = repository.deleteHerd(id)
}

// --- corrals ---

class SaveCorralUseCase @Inject constructor(private val repository: LivestockRepository) {
    suspend operator fun invoke(id: Int?, draft: CorralDraft): AppResult<Corral> =
        if (id == null) repository.createCorral(draft) else repository.updateCorral(id, draft)
}

class DeleteCorralUseCase @Inject constructor(private val repository: LivestockRepository) {
    suspend operator fun invoke(id: Int): AppResult<Unit> = repository.deleteCorral(id)
}

// --- animals ---

class SaveAnimalUseCase @Inject constructor(private val repository: LivestockRepository) {
    /** [corrals] are the corrals known locally; the corral must belong to the chosen herd (the API does not check). */
    suspend operator fun invoke(id: Int?, draft: AnimalDraft, corrals: List<Corral>): AppResult<Animal> {
        if (!AnimalPlacementPolicy.isValidPlacement(draft.herdId, draft.corralId, corrals)) {
            return invalid(LivestockValidation.INVALID_PLACEMENT)
        }
        return if (id == null) repository.createAnimal(draft) else repository.updateAnimal(id, draft)
    }
}

class RegisterAnimalBatchUseCase @Inject constructor(private val repository: LivestockRepository) {
    suspend operator fun invoke(draft: AnimalBatchDraft, corrals: List<Corral>): AppResult<List<Animal>> {
        if (!AnimalPlacementPolicy.isValidBatchSize(draft.quantity)) return invalid(LivestockValidation.INVALID_BATCH_SIZE)
        if (!AnimalPlacementPolicy.isValidPlacement(draft.herdId, draft.corralId, corrals)) {
            return invalid(LivestockValidation.INVALID_PLACEMENT)
        }
        return repository.createAnimalBatch(draft)
    }
}

class DeleteAnimalUseCase @Inject constructor(private val repository: LivestockRepository) {
    suspend operator fun invoke(id: Int): AppResult<Unit> = repository.deleteAnimal(id)
}

class ChangeAnimalsStatusUseCase @Inject constructor(private val repository: LivestockRepository) {
    suspend operator fun invoke(ids: Collection<Int>, status: String): AppResult<List<Animal>> {
        if (ids.isEmpty()) return invalid(LivestockValidation.NOTHING_SELECTED)
        return repository.updateAnimalsStatus(ids.toList(), status)
    }
}

class DeleteAnimalsUseCase @Inject constructor(private val repository: LivestockRepository) {
    suspend operator fun invoke(ids: Collection<Int>): AppResult<Unit> {
        if (ids.isEmpty()) return invalid(LivestockValidation.NOTHING_SELECTED)
        return repository.deleteAnimals(ids.toList())
    }
}

class UploadAnimalImageUseCase @Inject constructor(private val uploader: AnimalImageUploader) {
    suspend operator fun invoke(uri: String): AppResult<String> = uploader.upload(uri)
}
