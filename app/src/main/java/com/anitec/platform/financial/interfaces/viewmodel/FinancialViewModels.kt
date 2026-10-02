package com.anitec.platform.financial.interfaces.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anitec.platform.app.FinancialFormRoute
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.financial.application.DeleteFinancialRecordUseCase
import com.anitec.platform.financial.application.ObserveFinancialRecordsUseCase
import com.anitec.platform.financial.application.RefreshFinancialUseCase
import com.anitec.platform.financial.application.SaveFinancialRecordUseCase
import com.anitec.platform.financial.domain.FinancialRecord
import com.anitec.platform.financial.domain.FinancialRecordDraft
import com.anitec.platform.financial.domain.FinancialScope
import com.anitec.platform.financial.domain.FinancialSummary
import com.anitec.platform.financial.domain.RecordCategories
import com.anitec.platform.financial.domain.RecordTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Reads an amount typed by the user; accepts a decimal comma. Null when it is not a number. */
fun parseAmount(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

data class FinancialListUiState(
    val items: List<FinancialRecord> = emptyList(),
    val summary: FinancialSummary = FinancialSummary.Empty,
    val pendingDelete: FinancialRecord? = null,
    val detail: FinancialRecord? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    @StringRes val messageRes: Int? = null,
)

private data class FinancialListLocal(
    val pendingDelete: FinancialRecord? = null,
    val detail: FinancialRecord? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val messageRes: Int? = null,
)

@HiltViewModel
class FinancialListViewModel @Inject constructor(
    observeRecords: ObserveFinancialRecordsUseCase,
    private val refreshFinancial: RefreshFinancialUseCase,
    private val deleteRecord: DeleteFinancialRecordUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(FinancialListLocal())

    val state: StateFlow<FinancialListUiState> = combine(observeRecords(), local) { records, local ->
        FinancialListUiState(
            items = records,
            summary = FinancialScope.summarize(records),
            pendingDelete = local.pendingDelete,
            detail = local.detail,
            loading = local.loading && records.isEmpty(),
            refreshing = local.refreshing,
            messageRes = local.messageRes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinancialListUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            val result = refreshFinancial()
            local.update { it.copy(refreshing = false, loading = false, messageRes = (result as? AppResult.Failure)?.error?.messageRes()) }
        }
    }

    fun showDetail(record: FinancialRecord) = local.update { it.copy(detail = record) }
    fun dismissDetail() = local.update { it.copy(detail = null) }
    fun requestDelete(record: FinancialRecord) = local.update { it.copy(pendingDelete = record, detail = null) }
    fun dismissDelete() = local.update { it.copy(pendingDelete = null) }
    fun onMessageShown() = local.update { it.copy(messageRes = null) }

    fun confirmDelete() {
        val record = local.value.pendingDelete ?: return
        local.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val result = deleteRecord(record.id)
            if (result is AppResult.Failure) local.update { it.copy(messageRes = result.error.messageRes()) }
        }
    }
}

data class FinancialFormUiState(
    val isEdit: Boolean = false,
    val type: String = RecordTypes.INCOME,
    val category: String = RecordCategories.MILK_SALES,
    val amount: String = "",
    val date: String = LocalDate.now().toString(),
    val description: String = "",
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    @StringRes val messageRes: Int? = null,
) {
    private val parsedAmount get() = parseAmount(amount)
    val amountInvalid get() = showErrors && parsedAmount?.let { FinancialScope.isValidAmount(it) } != true
    val dateMissing get() = showErrors && date.isBlank()
    val isValid get() = parsedAmount?.let { FinancialScope.isValidAmount(it) } == true && date.isNotBlank()
}

@HiltViewModel
class FinancialFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeRecords: ObserveFinancialRecordsUseCase,
    private val saveRecord: SaveFinancialRecordUseCase,
    sessionStore: SessionStore,
) : ViewModel() {

    private val recordId: Int? = savedStateHandle.toRoute<FinancialFormRoute>().recordId
    private val session = (sessionStore.state.value as? SessionState.SignedIn)?.session
    private val records = observeRecords()

    private val _state = MutableStateFlow(FinancialFormUiState(isEdit = recordId != null))
    val state: StateFlow<FinancialFormUiState> = _state.asStateFlow()

    init {
        if (recordId != null) {
            viewModelScope.launch {
                val record = records.first().firstOrNull { it.id == recordId }
                if (record == null) {
                    _state.update { it.copy(done = true) }
                } else {
                    _state.update {
                        it.copy(
                            type = record.type, category = record.category, date = record.date, description = record.description,
                            amount = record.amount.toBigDecimal().stripTrailingZeros().toPlainString(),
                        )
                    }
                }
            }
        }
    }

    fun onTypeChange(value: String) = _state.update { it.copy(type = value) }
    fun onCategoryChange(value: String) = _state.update { it.copy(category = value) }
    fun onAmountChange(value: String) = _state.update { it.copy(amount = value) }
    fun onDateChange(value: String) = _state.update { it.copy(date = value) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }
    fun onMessageShown() = _state.update { it.copy(messageRes = null) }

    fun save() {
        val current = _state.value
        val user = session ?: return
        if (current.saving) return
        if (!current.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true, messageRes = null) }
        viewModelScope.launch {
            val draft = FinancialRecordDraft(
                ownerId = user.userId,
                type = current.type,
                category = current.category,
                amount = parseAmount(current.amount) ?: 0.0,
                date = current.date,
                description = current.description.trim(),
            )
            _state.update {
                when (val result = saveRecord(recordId, draft)) {
                    is AppResult.Success -> it.copy(saving = false, done = true)
                    is AppResult.Failure -> it.copy(saving = false, messageRes = result.error.messageRes())
                }
            }
        }
    }
}
