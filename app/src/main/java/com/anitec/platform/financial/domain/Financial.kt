package com.anitec.platform.financial.domain

import com.anitec.platform.core.common.AppResult
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode

/** One income or expense of a rancher. Dates are ISO (yyyy-MM-dd); [type] and [category] keep the API text. */
data class FinancialRecord(
    val id: Int,
    val ownerId: Int,
    val type: String,
    val category: String,
    val amount: Double,
    val date: String,
    val description: String,
) {
    val isIncome get() = type.equals(RecordTypes.INCOME, ignoreCase = true)
    val isExpense get() = type.equals(RecordTypes.EXPENSE, ignoreCase = true)
}

data class FinancialRecordDraft(
    val ownerId: Int,
    val type: String,
    val category: String,
    val amount: Double,
    val date: String,
    val description: String,
)

object RecordTypes {
    const val INCOME = "Ingreso"
    const val EXPENSE = "Egreso"
    val all = listOf(INCOME, EXPENSE)
}

object RecordCategories {
    const val MILK_SALES = "Venta de leche"
    const val LIVESTOCK_SALES = "Venta de ganado"
    const val FEED = "Alimento"
    const val VETERINARY = "Veterinaria"
    const val TRANSPORT = "Transporte"
    const val OTHER = "Otros"
    val all = listOf(MILK_SALES, LIVESTOCK_SALES, FEED, VETERINARY, TRANSPORT, OTHER)
}

data class FinancialSummary(val income: Double, val expenses: Double) {
    val balance get() = round2(income - expenses)

    companion object {
        val Empty = FinancialSummary(0.0, 0.0)
    }
}

private fun round2(value: Double) = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

object FinancialScope {
    /** The API returns every rancher's records, so the client keeps only the signed-in rancher's. */
    fun visible(records: List<FinancialRecord>, userId: Int): List<FinancialRecord> = records.filter { it.ownerId == userId }

    /** Totals are added as decimals so that cents do not drift. */
    fun summarize(records: List<FinancialRecord>): FinancialSummary {
        fun total(selected: List<FinancialRecord>) =
            selected.fold(BigDecimal.ZERO) { sum, record -> sum + BigDecimal.valueOf(record.amount) }.toDouble()
        return FinancialSummary(
            income = total(records.filter { it.isIncome }),
            expenses = total(records.filter { it.isExpense }),
        )
    }

    /** Amounts must be positive and carry at most two decimals. */
    fun isValidAmount(amount: Double): Boolean =
        amount.isFinite() && amount > 0 && BigDecimal.valueOf(amount).stripTrailingZeros().scale() <= 2
}

interface FinancialRepository {
    fun observeRecords(): Flow<List<FinancialRecord>>
    suspend fun refresh(): AppResult<Unit>
    suspend fun create(draft: FinancialRecordDraft): AppResult<FinancialRecord>
    suspend fun update(id: Int, draft: FinancialRecordDraft): AppResult<FinancialRecord>
    suspend fun delete(id: Int): AppResult<Unit>
}
