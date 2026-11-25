package com.goodgirl.accounting.data

import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.domain.JournalEntryRules
import com.goodgirl.accounting.domain.JournalLineDraft
import java.math.BigDecimal
import java.util.UUID

/**
 * Posts opening balances as balanced opening journals in the same transaction
 * that creates the party and its opening ledger balance.
 */
internal object PartyOpeningBalanceJournalPoster {
    private const val RECEIVABLES = "SYS-1100"
    private const val PAYABLES = "SYS-2000"
    private const val EQUITY = "SYS-3000"

    suspend fun post(
        database: AccountingDatabase,
        partyId: String,
        partyKind: String,
        amount: String,
        currencyCode: String,
        exchangeRateSnapshot: String,
        occurredAt: Long
    ) {
        require(partyKind == "CUSTOMER" || partyKind == "SUPPLIER") {
            "نوع جهة التعامل غير مدعوم للقيد الافتتاحي"
        }
        val value = amount.toBigDecimal()
        require(value > BigDecimal.ZERO) { "الرصيد الافتتاحي يجب أن يكون أكبر من صفر" }

        val now = System.currentTimeMillis()
        database.accountDao().insertIgnore(
            listOf(
                AccountEntity(RECEIVABLES, "1100", "ذمم العملاء", "ASSET", "DEBIT", true, true, now),
                AccountEntity(PAYABLES, "2000", "ذمم الموردين", "LIABILITY", "CREDIT", true, true, now),
                AccountEntity(EQUITY, "3000", "رأس المال", "EQUITY", "CREDIT", true, true, now)
            )
        )

        val sourceType = if (partyKind == "CUSTOMER") {
            "CUSTOMER_OPENING_BALANCE"
        } else {
            "SUPPLIER_OPENING_BALANCE"
        }
        require(database.journalEntryDao().findBySource(sourceType, partyId) == null) {
            "تم ترحيل القيد الافتتاحي لجهة التعامل مسبقاً"
        }

        val cleanAmount = value.stripTrailingZeros().toPlainString()
        val lines = if (partyKind == "CUSTOMER") {
            listOf(
                JournalLineDraft(RECEIVABLES, debit = cleanAmount, memo = "رصيد افتتاحي مستحق لنا"),
                JournalLineDraft(EQUITY, credit = cleanAmount, memo = "مقابل رصيد العميل الافتتاحي")
            )
        } else {
            listOf(
                JournalLineDraft(EQUITY, debit = cleanAmount, memo = "مقابل رصيد المورد الافتتاحي"),
                JournalLineDraft(PAYABLES, credit = cleanAmount, memo = "رصيد افتتاحي مستحق علينا")
            )
        }
        val normalized = JournalEntryRules.normalize(currencyCode, exchangeRateSnapshot, lines)
        PeriodClosingGuard.ensureOpen(database, normalized.currencyCode, occurredAt)
        val entryId = UUID.randomUUID().toString()
        database.journalEntryDao().insert(
            JournalEntryEntity(
                id = entryId,
                journalNumber = "AUTO-OPENING-$partyId",
                sourceType = sourceType,
                sourceId = partyId,
                currencyCode = normalized.currencyCode,
                exchangeRateSnapshot = normalized.exchangeRateSnapshot,
                occurredAt = occurredAt,
                description = if (partyKind == "CUSTOMER") {
                    "قيد رصيد افتتاحي لعميل"
                } else {
                    "قيد رصيد افتتاحي لمورد"
                },
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
