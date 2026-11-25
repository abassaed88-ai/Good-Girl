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
 * Carries a newly-created product's opening inventory value into the
 * general journal. It is deliberately not a P&L or cash-ledger entry.
 * The caller must run this inside the product-creation Room transaction.
 */
internal object ProductOpeningStockJournalPoster {
    private const val INVENTORY = "SYS-1200"
    private const val EQUITY = "SYS-3000"

    suspend fun post(
        database: AccountingDatabase,
        productId: String,
        quantity: String,
        unitCost: String,
        currencyCode: String,
        exchangeRateSnapshot: String,
        occurredAt: Long
    ) {
        val value = quantity.toBigDecimal().multiply(unitCost.toBigDecimal())
        if (value.compareTo(BigDecimal.ZERO) <= 0) return
        require(quantity.toBigDecimal() > BigDecimal.ZERO) { "كمية المخزون الافتتاحية غير صالحة" }
        require(unitCost.toBigDecimal() >= BigDecimal.ZERO) { "كلفة المخزون الافتتاحية غير صالحة" }

        val now = System.currentTimeMillis()
        database.accountDao().insertIgnore(
            listOf(
                AccountEntity(INVENTORY, "1200", "المخزون", "ASSET", "DEBIT", true, true, now),
                AccountEntity(EQUITY, "3000", "رأس المال", "EQUITY", "CREDIT", true, true, now)
            )
        )
        require(database.journalEntryDao().findBySource("PRODUCT_OPENING_STOCK", productId) == null) {
            "تم ترحيل رصيد مخزون المنتج الافتتاحي مسبقاً"
        }

        val amount = value.stripTrailingZeros().toPlainString()
        val normalized = JournalEntryRules.normalize(
            currencyCode = currencyCode,
            exchangeRateSnapshot = exchangeRateSnapshot,
            lines = listOf(
                JournalLineDraft(INVENTORY, debit = amount, memo = "إثبات قيمة المخزون الافتتاحي"),
                JournalLineDraft(EQUITY, credit = amount, memo = "مقابل المخزون الافتتاحي")
            )
        )
        PeriodClosingGuard.ensureOpen(database, normalized.currencyCode, occurredAt)
        val entryId = UUID.randomUUID().toString()
        database.journalEntryDao().insert(
            JournalEntryEntity(
                id = entryId,
                journalNumber = "AUTO-PRODUCT-OPENING-$productId",
                sourceType = "PRODUCT_OPENING_STOCK",
                sourceId = productId,
                currencyCode = normalized.currencyCode,
                exchangeRateSnapshot = normalized.exchangeRateSnapshot,
                occurredAt = occurredAt,
                description = "قيد رصيد مخزون افتتاحي لمنتج",
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
