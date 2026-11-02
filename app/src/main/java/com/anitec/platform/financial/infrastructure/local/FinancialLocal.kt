package com.anitec.platform.financial.infrastructure.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Cached income or expense. A negative [id] means it was created offline and is not sent yet. */
@Entity(tableName = "financial_records")
data class FinancialRecordEntity(
    @PrimaryKey val id: Int,
    val ownerId: Int,
    val type: String,
    val category: String,
    val amount: Double,
    val date: String,
    val description: String,
)

/** Room access to the cached financial records, newest first. */
@Dao
interface FinancialDao {
    @Query("SELECT * FROM financial_records ORDER BY date DESC, id DESC")
    fun observeRecords(): Flow<List<FinancialRecordEntity>>

    @Upsert suspend fun upsert(record: FinancialRecordEntity)
    @Upsert suspend fun upsertAll(records: List<FinancialRecordEntity>)
    @Query("DELETE FROM financial_records WHERE id = :id") suspend fun delete(id: Int)
    // Records created offline have a negative id and stay until they are sent.
    @Query("DELETE FROM financial_records WHERE id >= 0") suspend fun clear()

    /** Replaces the synced records in one transaction, keeping the pending ones. */
    @Transaction
    suspend fun replaceAll(records: List<FinancialRecordEntity>) {
        clear()
        upsertAll(records)
    }
}
