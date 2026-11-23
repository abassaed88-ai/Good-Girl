package com.goodgirl.accounting.data

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.domain.JournalEntryRules
import com.goodgirl.accounting.domain.JournalLineDraft
import java.math.BigDecimal
import java.util.UUID

/**
 * Posts inventory gains/losses as balanced journal entries and non-cash
 * P&L ledger rows. The stock repository calls this within its Room transaction.
 */
internal object StockAdjustmentJournalPoster {
    private const val INVENTORY = "SYS-1200"
    private const val OTHER_INCOME = "SYS-6000"
    private const val OPERATING_EXPENSES = "SYS-7000"

    suspend fun post(
        database: AccountingDatabase,
        movementId: String,
        quantityDelta: String,
        amount: String,
        currencyCode: String,
        exchangeRateSnapshot: String,
        occurredAt: Long,
        description: String,
        sourcePrefix: String
    ) {
        val value = amount.toBigDecimal()
        if (value.compareTo(BigDecimal.ZERO) <= 0) return

        val isGain = quantityDelta.toBigDecimal() > BigDecimal.ZERO
        val sourceType = when {
            sourcePrefix == "STOCKTAKE" && isGain -> "STOCKTAKE_GAIN"
            sourcePrefix == "STOCKTAKE" -> "STOCKTAKE_LOSS"
            isGain -> "MANUAL_STOCK_GAIN"
            else -> "MANUAL_STOCK_LOSS"
        }
        val now = System.currentTimeMillis()
        database.accountDao().insertIgnore(
            listOf(
                AccountEntity(INVENTORY, "1200", "المخزون", "ASSET", "DEBIT", true, true, now),
                AccountEntity(OTHER_INCOME, "6000", "إيرادات أخرى", "REVENUE", "CREDIT", true, true, now),
                AccountEntity(OPERATING_EXPENSES, "7000", "مصروفات تشغيلية", "EXPENSE", "DEBIT", true, true, now)
            )
        )
        require(database.journalEntryDao().findBySource(sourceType, movementId) == null) {
            "تم ترحيل أثر حركة المخزون محاسبياً مسبقاً"
        }

        val cleanAmount = value.stripTrailingZeros().toPlainString()
        val entryType = if (isGain) "INCOME" else "EXPENSE"
        database.ledgerDao().insert(
            LedgerEntryEntity(
                id = UUID.randomUUID().toString(),
                entryType = entryType,
                amount = cleanAmount,
                currencyCode = currencyCode,
                exchangeRateSnapshot = exchangeRateSnapshot,
                sourceType = sourceType,
                sourceId = movementId,
                occurredAt = occurredAt,
                note = description
            )
        )

        val drafts = if (isGain) {
            listOf(
                JournalLineDraft(INVENTORY, debit = cleanAmount, memo = "زيادة مخزون مكتشفة: $description"),
                JournalLineDraft(OTHER_INCOME, credit = cleanAmount, memo = "إيراد تسوية مخزون: $description")
            )
        } else {
            listOf(
                JournalLineDraft(OPERATING_EXPENSES, debit = cleanAmount, memo = "خسارة مخزون: $description"),
                JournalLineDraft(INVENTORY, credit = cleanAmount, memo = "تخفيض المخزون: $description")
            )
        }
        val normalized = JournalEntryRules.normalize(currencyCode, exchangeRateSnapshot, drafts)
        val entryId = UUID.randomUUID().toString()
        database.journalEntryDao().insert(
            JournalEntryEntity(
                id = entryId,
                journalNumber = "AUTO-$sourceType-$movementId",
                sourceType = sourceType,
                sourceId = movementId,
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
