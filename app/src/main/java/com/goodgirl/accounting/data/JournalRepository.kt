package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.domain.JournalEntryRules
import com.goodgirl.accounting.domain.JournalLineDraft
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Transaction-safe general journal foundation.
 *
 * A journal is immutable by source: a sourceType/sourceId pair can be posted
 * only once. Reversals should be represented by a separate opposite journal,
 * never by editing posted lines.
 */
class JournalRepository(private val database: AccountingDatabase) {
    fun observeAccounts(): Flow<List<AccountEntity>> = database.accountDao().observeAll()

    fun observeEntries(): Flow<List<JournalEntryEntity>> = database.journalEntryDao().observeAll()

    suspend fun getLines(entryId: String): List<JournalLineEntity> =
        database.journalLineDao().getForEntry(entryId)

    suspend fun ensureSystemAccounts() {
        val now = System.currentTimeMillis()
        val defaults = listOf(
            systemAccount("SYS-1000", "1000", "الصندوق", "ASSET", "DEBIT", now),
            systemAccount("SYS-1100", "1100", "ذمم العملاء", "ASSET", "DEBIT", now),
            systemAccount("SYS-1200", "1200", "المخزون", "ASSET", "DEBIT", now),
            systemAccount("SYS-2000", "2000", "ذمم الموردين", "LIABILITY", "CREDIT", now),
            systemAccount("SYS-3000", "3000", "رأس المال", "EQUITY", "CREDIT", now),
            systemAccount("SYS-3100", "3100", "الأرباح المحتجزة", "EQUITY", "CREDIT", now),
            systemAccount("SYS-4000", "4000", "المبيعات", "REVENUE", "CREDIT", now),
            systemAccount("SYS-4100", "4100", "أرباح إعادة تقييم المخزون", "REVENUE", "CREDIT", now),
            systemAccount("SYS-5000", "5000", "تكلفة البضاعة المباعة", "EXPENSE", "DEBIT", now),
            systemAccount("SYS-6000", "6000", "إيرادات أخرى", "REVENUE", "CREDIT", now),
            systemAccount("SYS-7000", "7000", "مصروفات تشغيلية", "EXPENSE", "DEBIT", now),
            systemAccount("SYS-7100", "7100", "خسائر إعادة تقييم المخزون", "EXPENSE", "DEBIT", now)
        )
        database.withTransaction {
            defaults.forEach { required ->
                val existingByCode = database.accountDao().findByCode(required.code)
                require(existingByCode == null || existingByCode.id == required.id) {
                    "رمز الحساب ${required.code} محجوز لحساب آخر؛ لا يمكن تجهيز الحسابات النظامية بأمان"
                }
                val existingById = database.accountDao().findById(required.id)
                require(existingById == null || existingById.code == required.code) {
                    "معرّف الحساب النظامي ${required.id} مرتبط برمز مختلف"
                }
            }
            database.accountDao().insertIgnore(defaults)
        }
    }

    suspend fun postJournalEntry(
        journalNumber: String,
        sourceType: String,
        sourceId: String,
        currencyCode: String,
        exchangeRateSnapshot: String,
        occurredAt: Long,
        description: String,
        lines: List<JournalLineDraft>
    ): String {
        val cleanNumber = journalNumber.trim()
        val cleanSourceType = sourceType.trim().uppercase()
        val cleanSourceId = sourceId.trim()
        val cleanDescription = description.trim()
        require(cleanNumber.isNotEmpty()) { "رقم القيد مطلوب" }
        require(cleanSourceType.isNotEmpty()) { "نوع مصدر القيد مطلوب" }
        require(cleanSourceId.isNotEmpty()) { "مرجع مصدر القيد مطلوب" }
        require(cleanDescription.isNotEmpty()) { "بيان القيد مطلوب" }
        require(occurredAt >= 0L) { "تاريخ القيد غير صالح" }

        val normalized = JournalEntryRules.normalize(
            currencyCode = currencyCode,
            exchangeRateSnapshot = exchangeRateSnapshot,
            lines = lines
        )
        val entryId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        database.withTransaction {
            PeriodClosingGuard.ensureOpen(database, normalized.currencyCode, occurredAt)
            require(database.journalEntryDao().findBySource(cleanSourceType, cleanSourceId) == null) {
                "تم ترحيل قيد لهذا المستند مسبقاً"
            }
            require(database.journalEntryDao().findByNumber(cleanNumber) == null) {
                "رقم القيد مستخدم مسبقاً"
            }

            normalized.lines.forEach { line ->
                val account = database.accountDao().findById(line.accountId)
                    ?: throw IllegalArgumentException("أحد حسابات القيد غير موجود")
                require(account.isActive) { "لا يمكن استخدام حساب موقوف في قيد جديد: " + account.name }
            }

            database.journalEntryDao().insert(
                JournalEntryEntity(
                    id = entryId,
                    journalNumber = cleanNumber,
                    sourceType = cleanSourceType,
                    sourceId = cleanSourceId,
                    currencyCode = normalized.currencyCode,
                    exchangeRateSnapshot = normalized.exchangeRateSnapshot,
                    occurredAt = occurredAt,
                    description = cleanDescription,
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
        return entryId
    }

    /**
     * Creates an immutable opposite journal for an existing entry.
     * The original entry and its lines are never modified or deleted.
     * A source-keyed reversal can be posted only once.
     */
    suspend fun reverseJournalEntry(
        originalEntryId: String,
        reversalJournalNumber: String,
        occurredAt: Long,
        reason: String
    ): String {
        val original = database.journalEntryDao().findById(originalEntryId)
            ?: throw IllegalArgumentException("القيد الأصلي غير موجود")
        val reversalSourceType = when (original.sourceType) {
            "MANUAL_JOURNAL" -> "REVERSAL"
            "MANUAL_JOURNAL_OPERATING" -> "REVERSAL_OPERATING"
            "MANUAL_JOURNAL_INVESTING" -> "REVERSAL_INVESTING"
            "MANUAL_JOURNAL_FINANCING" -> "REVERSAL_FINANCING"
            else -> throw IllegalArgumentException(
                "العكس المباشر متاح حالياً للقيود اليدوية فقط؛ عكس الفواتير وحركات المخزون والتحصيل يتطلب مساراً مرتبطاً بالمستند الأصلي"
            )
        }
        require(occurredAt >= original.occurredAt) {
            "تاريخ القيد العكسي لا يجوز أن يسبق تاريخ القيد الأصلي"
        }
        val cleanReason = reason.trim()
        require(cleanReason.isNotEmpty()) { "سبب عكس القيد مطلوب" }
        val originalLines = database.journalLineDao().getForEntry(originalEntryId)
        require(originalLines.isNotEmpty()) { "القيد الأصلي لا يحتوي على أسطر محاسبية" }

        return postJournalEntry(
            journalNumber = reversalJournalNumber,
            sourceType = reversalSourceType,
            sourceId = original.id,
            currencyCode = original.currencyCode,
            exchangeRateSnapshot = original.exchangeRateSnapshot,
            occurredAt = occurredAt,
            description = "قيد عكسي للقيد " + original.journalNumber + " — " + cleanReason,
            lines = originalLines.map { line ->
                JournalLineDraft(
                    accountId = line.accountId,
                    debit = line.credit,
                    credit = line.debit,
                    memo = "عكس: " + line.memo
                )
            }
        )
    }

    private fun systemAccount(
        id: String,
        code: String,
        name: String,
        type: String,
        normalSide: String,
        createdAt: Long
    ) = AccountEntity(
        id = id,
        code = code,
        name = name,
        accountType = type,
        normalSide = normalSide,
        isSystem = true,
        isActive = true,
        createdAt = createdAt
    )
}
