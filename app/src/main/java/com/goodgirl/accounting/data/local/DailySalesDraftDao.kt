package com.goodgirl.accounting.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailySalesDraftDao {
    @Query("SELECT * FROM daily_sales_drafts WHERE id = 'active' LIMIT 1")
    suspend fun getActiveDraft(): DailySalesDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: DailySalesDraftEntity)

    @Query("SELECT * FROM daily_sales_draft_lines WHERE draftId = :draftId ORDER BY lineOrder ASC")
    suspend fun getLines(draftId: String): List<DailySalesDraftLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLines(lines: List<DailySalesDraftLineEntity>)

    @Query("SELECT * FROM daily_sales_draft_expenses WHERE draftId = :draftId ORDER BY createdAt ASC, id ASC")
    suspend fun getExpenses(draftId: String): List<DailySalesDraftExpenseEntity>

    @Query("SELECT * FROM daily_sales_draft_expenses WHERE lineId = :lineId ORDER BY createdAt ASC, id ASC")
    suspend fun getExpensesForLine(lineId: String): List<DailySalesDraftExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveExpenses(expenses: List<DailySalesDraftExpenseEntity>)

    @Query("DELETE FROM daily_sales_draft_expenses WHERE draftId = :draftId")
    suspend fun deleteExpenses(draftId: String)

    @Query("DELETE FROM daily_sales_draft_lines WHERE draftId = :draftId")
    suspend fun deleteLines(draftId: String)

    @Query("DELETE FROM daily_sales_draft_lines WHERE id = :lineId")
    suspend fun deleteLine(lineId: String)

    @Query("DELETE FROM daily_sales_drafts WHERE id = :draftId")
    suspend fun deleteDraft(draftId: String)
}
