package com.goodgirl.accounting.data

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.domain.JournalEntryRules
import com.goodgirl.accounting.domain.JournalLineDraft
import java.math.BigDecimal
import java.util.UUID

/**
 * Posts manual cash income and expense transactions to the general journal.
 *
 * The caller must invoke this inside the same Room transaction as the cash
 * and profit/loss ledger rows.
 */
internal object ManualCashJournalPoster {
    private const val CASH = "SYS-1000"
    private const val OTHER_INCOME = "SYS-6000"
    private const val OPERATING_EXPENSES = "SYS-7000"

    suspend fun post(
        database: AccountingDatabase,
        sourceId: String,
        kind: String,
        amount: String,
        currencyCode: String,
        exchangeRateSnapshot: String,
        occurredAt: Long,
        description: String
    ) {
        val isIncome = kind.trim().uppercase() == "INCOME"
        require(isIncome || kind.trim().uppercase() == "EXPENSE") {
            "نوع الحركة غير مدعوم للقيد المحاسبي"
        }

        val value = amount.toBigDecimal()
        require(value.compareTo(BigDecimal.ZERO) > 0) { "مبلغ القيد يجب أن يكون أكبر من صفر" }
        val now = System.currentTimeMillis()
        database.accountDao().insertIgnore(
            listOf(
                AccountEntity(CASH, "1000", "الصندوق", "ASSET", "DEBIT", true, true, now),
                AccountEntity(OTHER_INCOME, "6000", "إيرادات أخرى", "REVENUE", "CREDIT", true, true, now),
                AccountEntity(OPERATING_EXPENSES, "7000", "مصروفات تشغيلية", "EXPENSE", "DEBIT", true, true, now)
            )
        )

        val cleanAmount = value.stripTrailingZeros().toPlainString()
        val sourceType = if (isIncome) "MANUAL_CASH_INCOME" else "MANUAL_CASH_EXPENSE"
        val lines = if (isIncome) {
            listOf(
                JournalLineDraft(CASH, debit = cleanAmount, memo = "إيداع إيراد نقدي: $description"),
                JournalLineDraft(OTHER_INCOME, credit = cleanAmount, memo = "إثبات إيراد آخر: $description")
            )
        } else {
            listOf(
                JournalLineDraft(OPERATING_EXPENSES, debit = cleanAmount, memo = "إثبات مصروف تشغيلي: $description"),
                JournalLineDraft(CASH, credit = cleanAmount, memo = "صرف نقدي: $description")
            )
        }
        val normalized = JournalEntryRules.normalize(currencyCode, exchangeRateSnapshot, lines)
        require(database.journalEntryDao().findBySource(sourceType, sourceId) == null) {
            "تم إنشاء قيد محاسبي لهذه الحركة مسبقاً"
        }

        PeriodClosingGuard.ensureOpen(database, normalized.currencyCode, occurredAt)
        val entryId = UUID.randomUUID().toString()
        database.journalEntryDao().insert(
            JournalEntryEntity(
                id = entryId,
                journalNumber = "AUTO-MANUAL-CASH-$sourceId",
                sourceType = sourceType,
                sourceId = sourceId,
                currencyCode = normalized.currencyCode,
                exchangeRateSnapshot = normalized.exchangeRateSnapshot,
                occurredAt = occurredAt,
                description = description,
                createdAt = now
            )
        )
        database.journalLineDao().insertAll(
            normalized.lines.mapIndexed { index, line ->
                JournalLineEntity(
                    id = UUID.randomUUID().toString(),
                    journalEntryId = entryId,
                    lineNumber = index + 1,
                    accountId = line.accountId,
                    debit = line.debit,
                    credit = line.credit,
                    memo = line.memo
                )
            }
        )
    }
}
