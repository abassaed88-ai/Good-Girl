package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.domain.CashTransactionRules
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class CashTransactionResult(
    val transactionId: String,
    val entryType: String,
    val amount: String,
    val currencyCode: String
)

class CashTransactionRepository(private val database: AccountingDatabase) {
    fun observeEntries(): Flow<List<LedgerEntryEntity>> = database.ledgerDao().observeAll()

    suspend fun recordTransaction(
        kind: String,
        amount: String,
        currencyCode: String,
        exchangeRate: String,
        description: String
    ): CashTransactionResult {
        val cleanKind = kind.trim().uppercase()
        require(cleanKind == "INCOME" || cleanKind == "EXPENSE") { "نوع الحركة غير صحيح" }
        val values = CashTransactionRules.normalize(amount, currencyCode, exchangeRate)
        val cleanDescription = description.trim()
        require(cleanDescription.isNotEmpty()) { "بيان الحركة مطلوب" }
        val transactionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val isIncome = cleanKind == "INCOME"

        database.withTransaction {
            database.ledgerDao().insert(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    entryType = if (isIncome) "CASH_IN" else "CASH_OUT",
                    amount = values.amount,
                    currencyCode = values.currencyCode,
                    exchangeRateSnapshot = values.exchangeRate,
                    sourceType = if (isIncome) "MANUAL_INCOME_CASH" else "MANUAL_EXPENSE_CASH",
                    sourceId = transactionId,
                    occurredAt = now,
                    note = cleanDescription
                )
            )
            database.ledgerDao().insert(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    entryType = cleanKind,
                    amount = values.amount,
                    currencyCode = values.currencyCode,
                    exchangeRateSnapshot = values.exchangeRate,
                    sourceType = if (isIncome) "MANUAL_INCOME" else "MANUAL_EXPENSE",
                    sourceId = transactionId,
                    occurredAt = now,
                    note = cleanDescription
                )
            )

            // Keep the cash ledger, profit-and-loss classification, and general
            // journal atomic: a failure in any one of them rolls back the whole
            // manual cash transaction.
            ManualCashJournalPoster.post(
                database = database,
                sourceId = transactionId,
                kind = cleanKind,
                amount = values.amount,
                currencyCode = values.currencyCode,
                exchangeRateSnapshot = values.exchangeRate,
                occurredAt = now,
                description = cleanDescription
            )
        }

        return CashTransactionResult(
            transactionId = transactionId,
            entryType = cleanKind,
            amount = values.amount,
            currencyCode = values.currencyCode
        )
    }
}
