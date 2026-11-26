package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.ExchangeRateHistoryEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.domain.PeriodClosingRules
import java.math.BigDecimal
import java.util.UUID

/**
 * Posts a permanent, immutable period-closing journal.
 * Closed ranges are encoded in the journal source key so the feature adds no
 * destructive schema migration and remains part of existing backup exports.
 */
data class ActivePeriodClosure(
    val closingEntry: JournalEntryEntity,
    val currencyCode: String,
    val periodStartInclusive: Long,
    val periodEndInclusive: Long
)

/** A persisted closing journal whose encoded date range cannot be trusted. */
data class MalformedPeriodClosure(
    val closingEntry: JournalEntryEntity
)

/**
 * Closing and reopening are represented by immutable, balanced journal entries.
 * A reopening never edits or deletes the original closing entry.
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
        require(currency == "SYP" || currency == "USD") { "عملة الإغلاق غير مدعومة" }
        require(periodStartInclusive >= 0L && periodEndInclusive >= periodStartInclusive) {
            "نطاق فترة الإغلاق غير صالح"
        }
        return database.withTransaction {
            val journalDao = database.journalEntryDao()
            val journalEntries = journalDao.getAll()
            val reversedCloseIds = journalEntries.asSequence()
                .filter { it.sourceType == REVERSAL_SOURCE_TYPE }
                .map { it.sourceId }
                .toSet()
            val overlaps = journalEntries.asSequence()
                .filter { it.sourceType == SOURCE_TYPE && it.currencyCode == currency && it.id !in reversedCloseIds }
                .any { entry ->
                    val range = decodeRange(entry.sourceId) ?: return@any true
                    range.first <= periodEndInclusive && periodStartInclusive <= range.second
                }
            require(!overlaps) { "توجد فترة مغلقة تتداخل مع النطاق المحدد" }

            val draft = PeriodClosingRules.build(
                periodStartInclusive = periodStartInclusive,
                periodEndInclusive = periodEndInclusive,
                currencyCode = currency,
                accounts = database.accountDao().getAll(),
                entries = journalEntries,
                lines = database.journalLineDao().getAll(),
                retainedEarningsAccountId = retainedEarningsAccountId
            )
            require(journalDao.findByNumber(number) == null) { "رقم القيد مستخدم مسبقاً" }

            val exchangeRateSnapshot = resolveExchangeRateSnapshot(
                currencyCode = currency,
                effectiveAt = periodEndInclusive,
                journalEntries = journalEntries
            )
            val entryId = UUID.randomUUID().toString()
            val sourceId = encodeRange(currency, periodStartInclusive, periodEndInclusive, entryId)
            val now = System.currentTimeMillis()
            journalDao.insert(
                JournalEntryEntity(
                    id = entryId,
                    journalNumber = number,
                    sourceType = SOURCE_TYPE,
                    sourceId = sourceId,
                    currencyCode = currency,
                    exchangeRateSnapshot = exchangeRateSnapshot,
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

    /**
     * Reopens a period by posting the exact opposite of its closing journal.
     * The reversal is dated after the closed range and includes the operator's reason.
     */
    suspend fun reopenPeriod(
        closingEntryId: String,
        reversalJournalNumber: String,
        occurredAt: Long,
        reason: String
    ): String {
        val number = reversalJournalNumber.trim()
        val cleanReason = reason.trim()
        require(number.isNotEmpty()) { "رقم قيد إعادة الفتح مطلوب" }
        require(cleanReason.isNotEmpty()) { "سبب إعادة فتح الفترة مطلوب" }
        require(occurredAt >= 0L) { "تاريخ إعادة فتح الفترة غير صالح" }

        return database.withTransaction {
            val journalDao = database.journalEntryDao()
            val closingEntry = journalDao.findById(closingEntryId)
                ?: throw IllegalArgumentException("قيد إقفال الفترة غير موجود")
            require(closingEntry.sourceType == SOURCE_TYPE) { "القيد المحدد ليس قيد إقفال فترة" }
            val range = decodeRange(closingEntry.sourceId)
                ?: throw IllegalArgumentException("بيانات نطاق الفترة المغلقة غير صالحة")
            require(range.second < Long.MAX_VALUE && occurredAt > range.second) {
                "يجب تسجيل إعادة الفتح بتاريخ يلي نهاية الفترة المغلقة"
            }
            require(journalDao.findBySource(REVERSAL_SOURCE_TYPE, closingEntry.id) == null) {
                "تمت إعادة فتح هذه الفترة مسبقاً"
            }
            require(journalDao.findByNumber(number) == null) { "رقم قيد إعادة الفتح مستخدم مسبقاً" }

            // The opposite journal must not itself be posted into another active closed range.
            PeriodClosingGuard.ensureOpen(database, closingEntry.currencyCode, occurredAt)

            val originalLines = database.journalLineDao().getForEntry(closingEntry.id)
            require(originalLines.size >= 2) { "قيد الإقفال الأصلي غير مكتمل" }
            val originalDebit = originalLines.fold(BigDecimal.ZERO) { total, line ->
                total.add(BigDecimal(line.debit))
            }
            val originalCredit = originalLines.fold(BigDecimal.ZERO) { total, line ->
                total.add(BigDecimal(line.credit))
            }
            require(originalDebit.compareTo(originalCredit) == 0) { "قيد الإقفال الأصلي غير متوازن" }

            val reversalId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            journalDao.insert(
                JournalEntryEntity(
                    id = reversalId,
                    journalNumber = number,
                    sourceType = REVERSAL_SOURCE_TYPE,
                    sourceId = closingEntry.id,
                    currencyCode = closingEntry.currencyCode,
                    exchangeRateSnapshot = closingEntry.exchangeRateSnapshot,
                    occurredAt = occurredAt,
                    description = "عكس قيد إقفال الفترة " + closingEntry.journalNumber + " — " + cleanReason,
                    createdAt = now
                )
            )
            database.journalLineDao().insertAll(
                originalLines.mapIndexed { index, line ->
                    JournalLineEntity(
                        id = UUID.randomUUID().toString(),
                        journalEntryId = reversalId,
                        lineNumber = index + 1,
                        accountId = line.accountId,
                        debit = line.credit,
                        credit = line.debit,
                        memo = "عكس الإقفال: " + line.memo
                    )
                }
            )
            reversalId
        }
    }

    private suspend fun resolveExchangeRateSnapshot(
        currencyCode: String,
        effectiveAt: Long,
        journalEntries: List<JournalEntryEntity>
    ): String {
        if (currencyCode == "SYP") return "1"

        val latestHistoryRate = database.exchangeRateHistoryDao().getAll()
            .asSequence()
            .filter {
                it.currencyPair == "USD/SYP" && !it.isArchived && it.effectiveAt <= effectiveAt
            }
            .maxWithOrNull(
                compareBy<ExchangeRateHistoryEntity> { it.effectiveAt }
                    .thenBy { it.recordedAt }
                    .thenBy { it.id }
            )
            ?.rate
        val fallbackJournalRate = journalEntries.asSequence()
            .filter { it.currencyCode == "USD" && it.occurredAt <= effectiveAt }
            .maxWithOrNull(
                compareBy<JournalEntryEntity> { it.occurredAt }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            )
            ?.exchangeRateSnapshot
        val rawRate = latestHistoryRate ?: fallbackJournalRate
            ?: throw IllegalStateException("لا يوجد سعر صرف موثق بالدولار بتاريخ نهاية الفترة")
        val rate = try {
            BigDecimal(rawRate)
        } catch (error: NumberFormatException) {
            throw IllegalStateException("سعر الصرف المحفوظ لإغلاق الفترة غير صالح", error)
        }
        require(rate.signum() > 0) { "سعر الصرف المحفوظ لإغلاق الفترة يجب أن يكون أكبر من صفر" }
        return rate.stripTrailingZeros().toPlainString()
    }

    companion object {
        const val SOURCE_TYPE = "PERIOD_CLOSE"
        const val REVERSAL_SOURCE_TYPE = "PERIOD_CLOSE_REVERSAL"

        fun activeClosures(entries: List<JournalEntryEntity>): List<ActivePeriodClosure> {
            val reversedCloseIds = entries.asSequence()
                .filter { it.sourceType == REVERSAL_SOURCE_TYPE }
                .map { it.sourceId }
                .toSet()
            return entries.asSequence()
                .filter { it.sourceType == SOURCE_TYPE && it.id !in reversedCloseIds }
                .mapNotNull { entry ->
                    val range = decodeRange(entry.sourceId) ?: return@mapNotNull null
                    ActivePeriodClosure(entry, entry.currencyCode, range.first, range.second)
                }
                .sortedWith(
                    compareByDescending<ActivePeriodClosure> { it.periodEndInclusive }
                        .thenByDescending { it.closingEntry.createdAt }
                )
                .toList()
        }

        /**
         * Returns active close journals with an invalid or unreadable encoded range.
         * These records remain visible for manual review and are deliberately not
         * eligible for automatic reopening because their actual date range is unknown.
         */
        fun malformedClosures(entries: List<JournalEntryEntity>): List<MalformedPeriodClosure> {
            val reversedCloseIds = entries.asSequence()
                .filter { it.sourceType == REVERSAL_SOURCE_TYPE }
                .map { it.sourceId }
                .toSet()
            return entries.asSequence()
                .filter { it.sourceType == SOURCE_TYPE && it.id !in reversedCloseIds }
                .filter { decodeRange(it.sourceId) == null }
                .map(::MalformedPeriodClosure)
                .sortedWith(
                    compareByDescending<MalformedPeriodClosure> { it.closingEntry.occurredAt }
                        .thenByDescending { it.closingEntry.createdAt }
                )
                .toList()
        }

        fun isClosedDate(entries: List<JournalEntryEntity>, currencyCode: String, occurredAt: Long): Boolean {
            val reversedCloseIds = entries.asSequence()
                .filter { it.sourceType == REVERSAL_SOURCE_TYPE }
                .map { it.sourceId }
                .toSet()
            return entries.asSequence()
                .filter {
                    it.sourceType == SOURCE_TYPE &&
                        it.currencyCode == currencyCode &&
                        it.id !in reversedCloseIds
                }
                .any { entry ->
                    val range = decodeRange(entry.sourceId) ?: return@any true
                    occurredAt in range.first..range.second
                }
        }

        private fun encodeRange(currency: String, start: Long, end: Long, closeId: String): String {
            require(currency == "SYP" || currency == "USD") { "عملة الإغلاق غير مدعومة" }
            require(start >= 0L && end >= start) { "نطاق الإغلاق غير صالح" }
            require(closeId.isNotBlank()) { "مرجع قيد الإغلاق مطلوب" }
            return "$currency:$start:$end:$closeId"
        }

        private fun decodeRange(sourceId: String): Pair<Long, Long>? {
            val parts = sourceId.split(':')
            if (parts.size !in 3..4 || (parts[0] != "SYP" && parts[0] != "USD")) return null
            if (parts.size == 4 && parts[3].isBlank()) return null
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