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
 * Posts the double-entry counterpart of a customer receipt or supplier payment.
 * Call only inside the same Room transaction as the source ledger/cash rows.
 */
internal object SettlementJournalPoster {
    suspend fun post(
        database: AccountingDatabase,
        sourceType: String,
        sourceId: String,
        partyKind: String,
        amount: String,
        currencyCode: String,
        exchangeRateSnapshot: String,
        occurredAt: Long,
        description: String
    ) {
        require(partyKind == "CUSTOMER" || partyKind == "SUPPLIER") {
            "نوع جهة التعامل غير مدعوم للقيد"
        }
        val value = amount.toBigDecimal()
        require(value > BigDecimal.ZERO) { "مبلغ التسوية يجب أن يكون أكبر من صفر" }

        val cash = "SYS-1000"
        val receivables = "SYS-1100"
        val payables = "SYS-2000"
        database.accountDao().insertIgnore(
            listOf(
                AccountEntity(cash, "1000", "الصندوق", "ASSET", "DEBIT", true, true, System.currentTimeMillis()),
                AccountEntity(receivables, "1100", "ذمم العملاء", "ASSET", "DEBIT", true, true, System.currentTimeMillis()),
                AccountEntity(payables, "2000", "ذمم الموردين", "LIABILITY", "CREDIT", true, true, System.currentTimeMillis())
            )
        )

        val cleanAmount = value.stripTrailingZeros().toPlainString()
        val lines = if (partyKind == "CUSTOMER") {
            listOf(
                JournalLineDraft(cash, debit = cleanAmount, memo = "قبض نقدي: $description"),
                JournalLineDraft(receivables, credit = cleanAmount, memo = "تسوية ذمم عميل: $description")
            )
        } else {
            listOf(
                JournalLineDraft(payables, debit = cleanAmount, memo = "تسوية ذمم مورد: $description"),
                JournalLineDraft(cash, credit = cleanAmount, memo = "دفع نقدي: $description")
            )
        }
        val normalized = JournalEntryRules.normalize(currencyCode, exchangeRateSnapshot, lines)
        require(database.journalEntryDao().findBySource(sourceType, sourceId) == null) {
            "تم إنشاء قيد محاسبي لهذه التسوية مسبقاً"
        }
        val entryId = UUID.randomUUID().toString()
        database.journalEntryDao().insert(
            JournalEntryEntity(
                id = entryId,
                journalNumber = "AUTO-SETTLEMENT-$sourceId",
                sourceType = sourceType,
                sourceId = sourceId,
                currencyCode = normalized.currencyCode,
                exchangeRateSnapshot = normalized.exchangeRateSnapshot,
                occurredAt = occurredAt,
                description = description,
                createdAt = System.currentTimeMillis()
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
