package com.anitec.platform.financial.application

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.financial.domain.FinancialRecord
import com.anitec.platform.financial.domain.FinancialRecordDraft
import com.anitec.platform.financial.domain.FinancialRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveFinancialRecordsUseCase @Inject constructor(private val repository: FinancialRepository) {
    operator fun invoke(): Flow<List<FinancialRecord>> = repository.observeRecords()
}

class RefreshFinancialUseCase @Inject constructor(private val repository: FinancialRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.refresh()
}

class SaveFinancialRecordUseCase @Inject constructor(private val repository: FinancialRepository) {
    /** Creates the record when [id] is null, otherwise updates it. */
    suspend operator fun invoke(id: Int?, draft: FinancialRecordDraft): AppResult<FinancialRecord> =
        if (id == null) repository.create(draft) else repository.update(id, draft)
}

class DeleteFinancialRecordUseCase @Inject constructor(private val repository: FinancialRepository) {
    suspend operator fun invoke(id: Int): AppResult<Unit> = repository.delete(id)
}
