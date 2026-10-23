package com.anitec.platform.financial.infrastructure.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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

@Dao
interface FinancialDao {
    @Query("SELECT * FROM financial_records ORDER BY date DESC, id DESC")
    fun observeRecords(): Flow<List<FinancialRecordEntity>>

    @Upsert suspend fun upsert(record: FinancialRecordEntity)
    @Upsert suspend fun upsertAll(records: List<FinancialRecordEntity>)
    @Query("DELETE FROM financial_records WHERE id = :id") suspend fun delete(id: Int)
    @Query("DELETE FROM financial_records") suspend fun clear()

    @Transaction
    suspend fun replaceAll(records: List<FinancialRecordEntity>) {
        clear()
        upsertAll(records)
    }
}
