package com.goodgirl.accounting.data

import android.content.Context
import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.ExchangeRateAuditEntity
import com.goodgirl.accounting.data.local.ExchangeRateHistoryEntity
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
