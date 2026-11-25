package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.domain.PeriodClosingRules
import java.util.UUID

/**
 * Posts a permanent, immutable period-closing journal.
 * Closed ranges are encoded in the journal source key so the feature adds no
 * destructive schema migration and remains part of existing backup exports.
 */
class PeriodClosingRepository(private val database: AccountingDatabase) {
    suspend fun closePeriod(
        periodStartInclusive: Long,
        periodEndInclusive: Long,
        currencyCode: String,
        retainedEarningsAccountId: String,
        journalNumber: String
    ): String {
        val currency = currencyCode.trim().uppercase()
        val number = journalNumber.trim()
        require(number.isNotEmpty()) { "رقم قيد الإغلاق مطلوب" }
        val sourceId = encodeRange(currency, periodStartInclusive, periodEndInclusive)
        return database.withTransaction {
            val journalDao = database.journalEntryDao()
            val existingClosures = journalDao.getAll().filter {
                it.sourceType == SOURCE_TYPE && it.currencyCode == currency
            }
            val overlaps = existingClosures.any { entry ->
                val range = decodeRange(entry.sourceId) ?: return@any true
                range.first <= periodEndInclusive && periodStartInclusive <= range.second
            }
            require(!overlaps) { "توجد فترة مغلقة تتداخل مع النطاق المحدد" }

            val draft = PeriodClosingRules.build(
                periodStartInclusive = periodStartInclusive,
                periodEndInclusive = periodEndInclusive,
                currencyCode = currency,
                accounts = database.accountDao().getAll(),
                entries = journalDao.getAll(),
                lines = database.journalLineDao().getAll(),
                retainedEarningsAccountId = retainedEarningsAccountId
            )
            require(journalDao.findByNumber(number) == null) { "رقم القيد مستخدم مسبقاً" }

            val entryId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            journalDao.insert(
                JournalEntryEntity(
                    id = entryId,
                    journalNumber = number,
                    sourceType = SOURCE_TYPE,
                    sourceId = sourceId,
                    currencyCode = currency,
                    exchangeRateSnapshot = "1",
                    occurredAt = periodEndInclusive,
                    description = "إقفال الفترة من $periodStartInclusive إلى $periodEndInclusive",
                    createdAt = now
                )
            )
            database.journalLineDao().insertAll(
                draft.lines.mapIndexed { index, line ->
                    JournalLineEntity(
                        id = UUID.randomUUID().toString(),
                        journalEntryId = entryId,
                        lineNumber = index + 1,
                        accountId = line.accountId,
                        debit = line.debit.stripTrailingZeros().toPlainString(),
                        credit = line.credit.stripTrailingZeros().toPlainString(),
                        memo = line.memo
                    )
                }
            )
            entryId
        }
    }

    companion object {
        const val SOURCE_TYPE = "PERIOD_CLOSE"

        fun isClosedDate(entries: List<JournalEntryEntity>, currencyCode: String, occurredAt: Long): Boolean =
            entries.asSequence()
                .filter { it.sourceType == SOURCE_TYPE && it.currencyCode == currencyCode }
                .any { entry ->
                val range = decodeRange(entry.sourceId) ?: return@any true
                occurredAt in range.first..range.second
            }

        private fun encodeRange(currency: String, start: Long, end: Long): String {
            require(currency == "SYP" || currency == "USD") { "عملة الإغلاق غير مدعومة" }
            require(start >= 0L && end >= start) { "نطاق الإغلاق غير صالح" }
            return "$currency:$start:$end"
        }

        private fun decodeRange(sourceId: String): Pair<Long, Long>? {
            val parts = sourceId.split(':')
            if (parts.size != 3 || (parts[0] != "SYP" && parts[0] != "USD")) return null
            val start = parts[1].toLongOrNull() ?: return null
            val end = parts[2].toLongOrNull() ?: return null
            if (start < 0L || end < start) return null
            return start to end
        }
    }
}

/** Shared posting guard. Call inside the same Room transaction as source posting. */
internal object PeriodClosingGuard {
    suspend fun ensureOpen(database: AccountingDatabase, currencyCode: String, occurredAt: Long) {
        val closed = PeriodClosingRepository.isClosedDate(
            database.journalEntryDao().getAll(),
            currencyCode.trim().uppercase(),
            occurredAt
        )
        require(!closed) { "لا يمكن ترحيل حركة بتاريخ يقع ضمن فترة محاسبية مغلقة" }
    }
}