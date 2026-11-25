package com.goodgirl.accounting.data

import android.content.Context
import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.ExchangeRateAuditEntity
import com.goodgirl.accounting.data.local.ExchangeRateCorrectionItemEntity
import com.goodgirl.accounting.data.local.ExchangeRateHistoryEntity
import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import java.math.BigDecimal
import java.util.UUID

/**
 * Keeps the current-rate preference compatible with existing workflows while
 * persisting its historical versions in Room for backup, restore, and corrections.
 */
object ExchangeRateHistoryRepository {
    private const val PREFERENCES = "good_girl_settings"
    private const val LEGACY_IMPORTED_KEY = "usd_syp_rate_history_migrated_to_room"

    suspend fun importLegacyPreferences(context: Context, database: AccountingDatabase) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getBoolean(LEGACY_IMPORTED_KEY, false)) return

        val history = linkedMapOf<String, ExchangeRateHistoryEntity>()
        preferences.getStringSet("usd_syp_rate_history", emptySet()).orEmpty().forEach { item ->
            val parts = item.split("|", limit = 2)
            val timestamp = parts.firstOrNull()?.toLongOrNull() ?: return@forEach
            val rate = normalizeRate(parts.getOrNull(1)) ?: return@forEach
            if (timestamp <= 0L) return@forEach
            val id = legacyId(timestamp, rate)
            history[id] = ExchangeRateHistoryEntity(
                id = id, currencyPair = "USD/SYP", rate = rate,
                effectiveAt = timestamp, recordedAt = timestamp, source = "LEGACY_IMPORT"
            )
        }

        val currentRate = normalizeRate(preferences.getString("usd_syp_rate", "1"))
        val currentUpdatedAt = preferences.getLong("usd_syp_rate_updated_at", 0L)
        if (currentRate != null && currentUpdatedAt > 0L) {
            val id = legacyId(currentUpdatedAt, currentRate)
            history.putIfAbsent(
                id,
                ExchangeRateHistoryEntity(
                    id = id, currencyPair = "USD/SYP", rate = currentRate,
                    effectiveAt = currentUpdatedAt, recordedAt = currentUpdatedAt,
                    source = "LEGACY_CURRENT"
                )
            )
        }

        database.withTransaction {
            if (database.exchangeRateHistoryDao().getAll().isEmpty() && history.isNotEmpty()) {
                val rows = history.values.sortedWith(
                    compareBy(ExchangeRateHistoryEntity::effectiveAt, ExchangeRateHistoryEntity::id)
                )
                database.exchangeRateHistoryDao().insertAll(rows)
                database.exchangeRateAuditDao().insertAll(
                    rows.map { row ->
                        ExchangeRateAuditEntity(
                            id = "legacy-audit-" + row.id,
                            historyId = row.id,
                            eventType = "RATE_IMPORTED",
                            oldRate = "",
                            newRate = row.rate,
                            reason = "استيراد سجل سعر الصرف القديم من إعدادات الجهاز",
                            correctedAt = row.recordedAt
                        )
                    }
                )
            }
        }
        // Persist the marker only after the Room transaction has completed.
        preferences.edit().putBoolean(LEGACY_IMPORTED_KEY, true).commit()
    }

    suspend fun saveCurrentRate(
        context: Context,
        database: AccountingDatabase,
        enteredRate: String,
        reason: String = "اعتماد سعر جديد للعمليات الجديدة فقط؛ لم يتم تعديل العمليات السابقة"
    ): Long {
        val rate = normalizeRate(enteredRate)
            ?: throw IllegalArgumentException("سعر الصرف يجب أن يكون رقماً أكبر من صفر")
        importLegacyPreferences(context, database)

        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val oldRate = normalizeRate(preferences.getString("usd_syp_rate", "1")) ?: "1"
        val previousUpdate = preferences.getLong("usd_syp_rate_updated_at", 0L)
        if (oldRate == rate && previousUpdate > 0L) return previousUpdate

        val now = System.currentTimeMillis()
        val historyId = UUID.randomUUID().toString()
        database.withTransaction {
            database.exchangeRateHistoryDao().insert(
                ExchangeRateHistoryEntity(
                    id = historyId, currencyPair = "USD/SYP", rate = rate,
                    effectiveAt = now, recordedAt = now, source = "USER"
                )
            )
            database.exchangeRateAuditDao().insert(
                ExchangeRateAuditEntity(
                    id = UUID.randomUUID().toString(),
                    historyId = historyId,
                    eventType = "RATE_SET",
                    oldRate = oldRate,
                    newRate = rate,
                    reason = reason,
                    correctedAt = now,
                    affectedInvoiceCount = 0,
                    affectedLedgerCount = 0,
                    affectedJournalCount = 0,
                    financialAmountsChanged = false
                )
            )
        }
        preferences.edit()
            .putString("usd_syp_rate", rate)
            .putLong("usd_syp_rate_updated_at", now)
            .apply()
        return now
    }

    private fun normalizeRate(raw: String?): String? = runCatching {
        val value = BigDecimal(raw?.trim()?.replace(',', '.') ?: return null)
        if (value <= BigDecimal.ZERO) null else value.stripTrailingZeros().toPlainString()
    }.getOrNull()

    private fun legacyId(timestamp: Long, rate: String): String =
        "legacy-" + UUID.nameUUIDFromBytes("$timestamp|$rate".toByteArray(Charsets.UTF_8))
}


data class ExchangeRateCorrectionTarget(
    val recordType: String,
    val recordId: String,
    val displayName: String,
    val sourceType: String,
    val sourceId: String,
    val currencyCode: String,
    val amountSummary: String,
    val occurredAt: Long,
    val oldRate: String
) {
    val key: String get() = "$recordType:$recordId"
}

data class ExchangeRateCorrectionPreview(
    val historyId: String,
    val oldRate: String,
    val proposedRate: String,
    val effectiveFrom: Long,
    val effectiveUntilExclusive: Long?,
    val records: List<ExchangeRateCorrectionTarget>
) {
    val invoiceCount: Int get() = records.count { it.recordType == "INVOICE" }
    val ledgerCount: Int get() = records.count { it.recordType == "LEDGER" }
    val journalCount: Int get() = records.count { it.recordType == "JOURNAL" }
}

data class ExchangeRateCorrectionResult(
    val auditId: String,
    val updatedInvoiceCount: Int,
    val updatedLedgerCount: Int,
    val updatedJournalCount: Int,
    val currentRateUpdated: Boolean
)

private fun sameRate(left: String, right: String): Boolean = runCatching {
    BigDecimal(left).compareTo(BigDecimal(right)) == 0
}.getOrDefault(false)

private val exchangeRateTimelineOrder =
    compareBy<ExchangeRateHistoryEntity> { it.effectiveAt }
        .thenBy { it.recordedAt }
        .thenBy { it.id }

private suspend fun buildCorrectionPreview(
    database: AccountingDatabase,
    historyId: String,
    proposedRate: String
): ExchangeRateCorrectionPreview {
    val selected = database.exchangeRateHistoryDao().findById(historyId)
        ?: throw IllegalArgumentException("سجل سعر الصرف غير موجود")
    require(selected.currencyPair == "USD/SYP") { "زوج العملة غير مدعوم" }
    require(!selected.isArchived) { "لا يمكن تصحيح سعر مؤرشف" }
    require(!sameRate(selected.rate, proposedRate)) { "السعر الجديد مطابق للسعر المسجل" }

    val activeRates = database.exchangeRateHistoryDao().getAll()
        .filter { it.currencyPair == selected.currencyPair && !it.isArchived }
        .sortedWith(exchangeRateTimelineOrder)
    val selectedIndex = activeRates.indexOfFirst { it.id == selected.id }
    check(selectedIndex >= 0) { "سجل سعر الصرف غير موجود ضمن السجل النشط" }
    // The next row in timeline order is the boundary, even when its effectiveAt
    // is equal. In that case the earlier row has an empty, unambiguous time window.
    val nextEffectiveAt = activeRates.getOrNull(selectedIndex + 1)?.effectiveAt

    fun inSelectedPeriod(timestamp: Long): Boolean =
        timestamp >= selected.effectiveAt &&
            (nextEffectiveAt == null || timestamp < nextEffectiveAt)

    val targets = mutableListOf<ExchangeRateCorrectionTarget>()

    database.invoiceDao().getAllInvoices()
        .asSequence()
        .filter {
            it.currencyCode == "USD" &&
                inSelectedPeriod(it.issuedAt) &&
                sameRate(it.exchangeRateSnapshot, selected.rate)
        }
        .forEach { invoice ->
            targets += ExchangeRateCorrectionTarget(
                recordType = "INVOICE",
                recordId = invoice.id,
                displayName = "فاتورة " + invoice.invoiceNumber,
                sourceType = invoice.kind + "_INVOICE",
                sourceId = invoice.id,
                currencyCode = invoice.currencyCode,
                amountSummary = invoice.total + " " + invoice.currencyCode,
                occurredAt = invoice.issuedAt,
                oldRate = invoice.exchangeRateSnapshot
            )
        }

    database.ledgerDao().getAll()
        .asSequence()
        .filter {
            it.currencyCode == "USD" &&
                inSelectedPeriod(it.occurredAt) &&
                sameRate(it.exchangeRateSnapshot, selected.rate)
        }
        .forEach { entry ->
            targets += ExchangeRateCorrectionTarget(
                recordType = "LEDGER",
                recordId = entry.id,
                displayName = entry.note.ifBlank { entry.entryType },
                sourceType = entry.sourceType,
                sourceId = entry.sourceId,
                currencyCode = entry.currencyCode,
                amountSummary = entry.amount + " " + entry.currencyCode,
                occurredAt = entry.occurredAt,
                oldRate = entry.exchangeRateSnapshot
            )
        }

    database.journalEntryDao().getAll()
        .asSequence()
        .filter {
            it.currencyCode == "USD" &&
                inSelectedPeriod(it.occurredAt) &&
                sameRate(it.exchangeRateSnapshot, selected.rate)
        }
        .forEach { entry ->
            targets += ExchangeRateCorrectionTarget(
                recordType = "JOURNAL",
                recordId = entry.id,
                displayName = "قيد " + entry.journalNumber + " — " + entry.description,
                sourceType = entry.sourceType,
                sourceId = entry.sourceId,
                currencyCode = entry.currencyCode,
                amountSummary = "قيد بعملة " + entry.currencyCode,
                occurredAt = entry.occurredAt,
                oldRate = entry.exchangeRateSnapshot
            )
        }

    return ExchangeRateCorrectionPreview(
        historyId = selected.id,
        oldRate = selected.rate,
        proposedRate = proposedRate,
        effectiveFrom = selected.effectiveAt,
        effectiveUntilExclusive = nextEffectiveAt,
        records = targets.sortedWith(
            compareBy<ExchangeRateCorrectionTarget> { it.occurredAt }
                .thenBy { it.recordType }
                .thenBy { it.recordId }
        )
    )
}

/** Returns a preview only. Nothing is changed until applyExchangeRateCorrection is called. */
suspend fun previewExchangeRateCorrection(
    database: AccountingDatabase,
    historyId: String,
    proposedRate: String
): ExchangeRateCorrectionPreview {
    val normalized = runCatching {
        val value = BigDecimal(proposedRate.trim().replace(',', '.'))
        require(value > BigDecimal.ZERO)
        value.stripTrailingZeros().toPlainString()
    }.getOrElse { throw IllegalArgumentException("أدخل سعر تصحيح أكبر من صفر") }
    return buildCorrectionPreview(database, historyId, normalized)
}

/**
 * Applies only the selected historical rate snapshot correction, atomically.
 * Invoice totals, ledger amounts and journal debit/credit lines are never changed.
 */
suspend fun applyExchangeRateCorrection(
    context: Context,
    database: AccountingDatabase,
    historyId: String,
    proposedRate: String,
    reason: String
): ExchangeRateCorrectionResult {
    val normalized = runCatching {
        val value = BigDecimal(proposedRate.trim().replace(',', '.'))
        require(value > BigDecimal.ZERO)
        value.stripTrailingZeros().toPlainString()
    }.getOrElse { throw IllegalArgumentException("أدخل سعر تصحيح أكبر من صفر") }
    val cleanReason = reason.trim()
    require(cleanReason.isNotEmpty()) { "سبب التصحيح مطلوب لحفظ سجل التدقيق" }

    val committed = database.withTransaction {
        val preview = buildCorrectionPreview(database, historyId, normalized)
        val historyDao = database.exchangeRateHistoryDao()
        val selected = historyDao.findById(historyId)
            ?: throw IllegalArgumentException("سجل سعر الصرف غير موجود")
        val auditId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        preview.records.forEach { target ->
            when (target.recordType) {
                "INVOICE" -> {
                    val row = database.invoiceDao().findById(target.recordId)
                        ?: throw IllegalStateException("تغيرت الفاتورة أثناء معاينة التصحيح")
                    require(sameRate(row.exchangeRateSnapshot, preview.oldRate)) {
                        "تغير سعر الفاتورة أثناء معاينة التصحيح"
                    }
                    database.invoiceDao().updateInvoice(row.copy(exchangeRateSnapshot = normalized))
                }
                "LEDGER" -> {
                    val row = database.ledgerDao().findById(target.recordId)
                        ?: throw IllegalStateException("تغيرت حركة الحساب أثناء معاينة التصحيح")
                    require(sameRate(row.exchangeRateSnapshot, preview.oldRate)) {
                        "تغير سعر حركة الحساب أثناء معاينة التصحيح"
                    }
                    database.ledgerDao().update(row.copy(exchangeRateSnapshot = normalized))
                }
                "JOURNAL" -> {
                    val row = database.journalEntryDao().findById(target.recordId)
                        ?: throw IllegalStateException("تغير قيد اليومية أثناء معاينة التصحيح")
                    require(sameRate(row.exchangeRateSnapshot, preview.oldRate)) {
                        "تغير سعر قيد اليومية أثناء معاينة التصحيح"
                    }
                    database.journalEntryDao().update(row.copy(exchangeRateSnapshot = normalized))
                }
                else -> throw IllegalStateException("نوع سجل غير معروف في معاينة التصحيح")
            }
        }

        database.exchangeRateAuditDao().insert(
            ExchangeRateAuditEntity(
                id = auditId,
                historyId = historyId,
                eventType = "RATE_CORRECTION",
                oldRate = selected.rate,
                newRate = normalized,
                reason = cleanReason,
                correctedAt = now,
                affectedInvoiceCount = preview.invoiceCount,
                affectedLedgerCount = preview.ledgerCount,
                affectedJournalCount = preview.journalCount,
                financialAmountsChanged = false
            )
        )
        if (preview.records.isNotEmpty()) {
            database.exchangeRateCorrectionItemDao().insertAll(
                preview.records.map { target ->
                    ExchangeRateCorrectionItemEntity(
                        id = UUID.randomUUID().toString(),
                        auditId = auditId,
                        recordType = target.recordType,
                        recordId = target.recordId,
                        sourceType = target.sourceType,
                        sourceId = target.sourceId,
                        oldRate = target.oldRate,
                        newRate = normalized
                    )
                }
            )
        }
        historyDao.update(selected.copy(rate = normalized))

        val latest = historyDao.getAll()
            .filter { it.currencyPair == selected.currencyPair && !it.isArchived }
            .maxWithOrNull(exchangeRateTimelineOrder)
        ExchangeRateCorrectionResult(
            auditId = auditId,
            updatedInvoiceCount = preview.invoiceCount,
            updatedLedgerCount = preview.ledgerCount,
            updatedJournalCount = preview.journalCount,
            currentRateUpdated = latest?.id == selected.id
        )
    }

    if (committed.currentRateUpdated) {
        context.getSharedPreferences("good_girl_settings", Context.MODE_PRIVATE).edit()
            .putString("usd_syp_rate", normalized)
            .putLong("usd_syp_rate_updated_at", System.currentTimeMillis())
            .apply()
    }
    return committed
}

/** Archives a historic row without deleting it or changing any transaction snapshot. */
suspend fun archiveExchangeRate(
    database: AccountingDatabase,
    historyId: String,
    reason: String
) {
    val cleanReason = reason.trim()
    require(cleanReason.isNotEmpty()) { "سبب الأرشفة مطلوب" }
    database.withTransaction {
        val selected = database.exchangeRateHistoryDao().findById(historyId)
            ?: throw IllegalArgumentException("سجل سعر الصرف غير موجود")
        require(!selected.isArchived) { "السعر مؤرشف مسبقاً" }
        val active = database.exchangeRateHistoryDao().getAll()
            .filter { it.currencyPair == selected.currencyPair && !it.isArchived }
        val latest = active.maxWithOrNull(exchangeRateTimelineOrder)
        require(latest?.id != selected.id) { "لا يمكن أرشفة سعر الصرف الحالي" }
        database.exchangeRateAuditDao().insert(
            ExchangeRateAuditEntity(
                id = UUID.randomUUID().toString(),
                historyId = selected.id,
                eventType = "RATE_ARCHIVED",
                oldRate = selected.rate,
                newRate = selected.rate,
                reason = cleanReason,
                correctedAt = System.currentTimeMillis()
            )
        )
        database.exchangeRateHistoryDao().archive(selected.id)
    }
}
