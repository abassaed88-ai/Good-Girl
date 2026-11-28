package com.goodgirl.accounting.data

import android.content.Context
import android.net.Uri
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.ACCOUNTING_DATABASE_VERSION
import com.goodgirl.accounting.data.local.CategoryEntity
import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.data.local.PartyEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.data.local.ProductBarcodeEntity
import com.goodgirl.accounting.data.local.ProductUnitEntity
import com.goodgirl.accounting.data.local.StockMovementEntity
import com.goodgirl.accounting.data.local.StockCostBaselineEntity
import com.goodgirl.accounting.data.local.StockCostRecalculationAuditEntity
import com.goodgirl.accounting.data.local.StockCostRecalculationItemEntity
import com.goodgirl.accounting.data.local.ExchangeRateHistoryEntity
import com.goodgirl.accounting.data.local.ExchangeRateAuditEntity
import com.goodgirl.accounting.data.local.ExchangeRateCorrectionItemEntity
import com.goodgirl.accounting.data.local.DailySalesDraftEntity
import com.goodgirl.accounting.data.local.DailySalesDraftLineEntity
import com.goodgirl.accounting.data.local.DailySalesDraftExpenseEntity
import com.goodgirl.accounting.data.local.LatestPurchaseCostPolicyMigrator
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

data class AccountingBackupResult(
    val recordCount: Int,
    val exportedAt: Long,
    val wasLegacyUnencrypted: Boolean = false
)

private class WipingByteArrayOutputStream : ByteArrayOutputStream() {
    fun wipe() {
        buf.fill(0)
        reset()
    }
}

/**
 * Logical JSON backup. All values are serialized from Room entities, preserving
 * decimal strings and avoiding unsafe copying of an open SQLite/WAL database.
 */
class AccountingBackupManager(
    private val context: Context,
    private val database: AccountingDatabase
) {
    suspend fun exportBackup(destination: Uri, password: CharArray): AccountingBackupResult = withContext(Dispatchers.IO) {
        try {
        ExchangeRateHistoryRepository.importLegacyPreferences(context, database)
        val exportedAt = System.currentTimeMillis()
        val settingsPreferences = context.getSharedPreferences("good_girl_settings", Context.MODE_PRIVATE)
        val savedRate = settingsPreferences.getString("usd_syp_rate", "1") ?: "1"
        val savedRateUpdatedAt = settingsPreferences.getLong("usd_syp_rate_updated_at", 0L)
        val savedRateHistory = settingsPreferences.getStringSet("usd_syp_rate_history", emptySet())?.toList().orEmpty()
        val snapshot = database.withTransaction {
            val categories = database.categoryDao().getAll()
            val products = database.productDao().getAll()
            val productBarcodes = database.productBarcodeDao().getAll()
            val units = database.productUnitDao().getAll()
            val parties = database.partyDao().getAll()
            val invoices = database.invoiceDao().getAllInvoices()
            val invoiceLines = database.invoiceDao().getAllLines()
            val movements = database.stockMovementDao().getAll()
            val stockCostBaselines = database.stockCostBaselineDao().getAll()
            val stockCostRecalculationAudits = database.stockCostRecalculationAuditDao().getAll()
            val stockCostRecalculationItems = database.stockCostRecalculationItemDao().getAll()
            val ledgerEntries = database.ledgerDao().getAll()
            val accounts = database.accountDao().getAll()
            val journalEntries = database.journalEntryDao().getAll()
            val journalLines = journalEntries.flatMap { entry ->
                database.journalLineDao().getForEntry(entry.id)
            }
            val exchangeRateHistory = database.exchangeRateHistoryDao().getAll()
            val exchangeRateAudits = database.exchangeRateAuditDao().getAll()
            val exchangeRateCorrectionItems = database.exchangeRateCorrectionItemDao().getAll()
            val dailySalesDraft = database.dailySalesDraftDao().getActiveDraft()
            val dailySalesDraftLines = database.dailySalesDraftDao().getLines("active")
            val dailySalesDraftExpenses = database.dailySalesDraftDao().getExpenses("active")

            val json = JSONObject()
                .put("format", BACKUP_FORMAT)
                .put("formatVersion", BACKUP_FORMAT_VERSION)
                .put("settings", JSONObject()
                    .put("usdSypRate", savedRate)
                    .put("usdSypRateUpdatedAt", savedRateUpdatedAt)
                    .put("usdSypRateHistory", JSONArray().also { array ->
                        savedRateHistory.forEach { array.put(it) }
                    }))
                .put("applicationId", APPLICATION_ID)
                .put("databaseVersion", DATABASE_VERSION)
                .put("exportedAt", exportedAt)
                .put("categories", categories.toJsonArray { it.toBackupJson() })
                .put("products", products.toJsonArray { it.toBackupJson() })
                .put("productBarcodes", productBarcodes.toJsonArray { it.toBackupJson() })
                .put("productUnits", units.toJsonArray { it.toBackupJson() })
                .put("parties", parties.toJsonArray { it.toBackupJson() })
                .put("invoices", invoices.toJsonArray { it.toBackupJson() })
                .put("invoiceLines", invoiceLines.toJsonArray { it.toBackupJson() })
                .put("stockMovements", movements.toJsonArray { it.toBackupJson() })
                .put("stockCostBaselines", stockCostBaselines.toJsonArray { it.toBackupJson() })
                .put("stockCostRecalculationAudits", stockCostRecalculationAudits.toJsonArray { it.toBackupJson() })
                .put("stockCostRecalculationItems", stockCostRecalculationItems.toJsonArray { it.toBackupJson() })
                .put("ledgerEntries", ledgerEntries.toJsonArray { it.toBackupJson() })
                .put("accounts", accounts.toJsonArray { it.toBackupJson() })
                .put("journalEntries", journalEntries.toJsonArray { it.toBackupJson() })
                .put("journalLines", journalLines.toJsonArray { it.toBackupJson() })
                .put("exchangeRateHistory", exchangeRateHistory.toJsonArray { it.toBackupJson() })
                .put("exchangeRateAudits", exchangeRateAudits.toJsonArray { it.toBackupJson() })
                .put("exchangeRateCorrectionItems", exchangeRateCorrectionItems.toJsonArray { it.toBackupJson() })
                .put("dailySalesDraft", dailySalesDraft?.toBackupJson() ?: JSONObject.NULL)
                .put("dailySalesDraftLines", dailySalesDraftLines.toJsonArray { it.toBackupJson() })
                .put("dailySalesDraftExpenses", dailySalesDraftExpenses.toJsonArray { it.toBackupJson() })

            json to (
                categories.size + products.size + productBarcodes.size + units.size + parties.size +
                    invoices.size + invoiceLines.size + movements.size + ledgerEntries.size +
                    stockCostBaselines.size + stockCostRecalculationAudits.size + stockCostRecalculationItems.size + accounts.size + journalEntries.size + journalLines.size + exchangeRateHistory.size +
                    exchangeRateAudits.size + exchangeRateCorrectionItems.size +
                    (if (dailySalesDraft == null) 0 else 1) + dailySalesDraftLines.size +
                    dailySalesDraftExpenses.size
                )
        }

        val plaintext = snapshot.first.toString().toByteArray(Charsets.UTF_8)
        try {
            val encrypted = EncryptedBackupCodec.encrypt(plaintext, password)
            try {
                val output = context.contentResolver.openOutputStream(destination, "wt")
                    ?: throw IllegalStateException("تعذر فتح ملف النسخة الاحتياطية للكتابة")
                output.use { it.write(encrypted); it.flush() }
                AccountingBackupResult(snapshot.second, exportedAt)
            } finally {
                encrypted.fill(0)
            }
        } finally {
            plaintext.fill(0)
        }
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun restoreBackup(
        source: Uri,
        password: CharArray = charArrayOf()
    ): AccountingBackupResult = withContext(Dispatchers.IO) {
        try {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IllegalStateException("تعذر فتح ملف النسخة الاحتياطية")
        val encodedBytes = input.use { stream ->
            val output = WipingByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            buffer.useAndWipe { readBuffer ->
                try {
                    var total = 0
                    while (true) {
                        val read = stream.read(readBuffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_ENCRYPTED_BACKUP_BYTES) {
                            "حجم ملف النسخة الاحتياطية أكبر من الحد المسموح"
                        }
                        output.write(readBuffer, 0, read)
                    }
                    output.toByteArray()
                } finally {
                    output.wipe()
                }
            }
        }
        require(encodedBytes.isNotEmpty()) { "ملف النسخة الاحتياطية فارغ" }

        val wasLegacyUnencrypted = !EncryptedBackupCodec.isEncrypted(encodedBytes)
        val plaintext = encodedBytes.useAndWipe { sourceBytes ->
            if (wasLegacyUnencrypted) {
                require(isLegacyJson(sourceBytes)) {
                    "ملف النسخة الاحتياطية غير معروف أو تالف"
                }
                sourceBytes.copyOf()
            } else {
                EncryptedBackupCodec.decrypt(sourceBytes, password)
            }
        }
        val root = plaintext.useAndWipe { payload ->
            require(payload.size <= MAX_BACKUP_BYTES) {
                "حجم محتوى النسخة الاحتياطية أكبر من الحد المسموح"
            }
            try {
                JSONObject(String(payload, Charsets.UTF_8))
            } catch (error: Exception) {
                throw IllegalArgumentException("ملف النسخة الاحتياطية تالف أو غير صالح", error)
            }
        }
        require(root.getString("format") == BACKUP_FORMAT) { "الملف المحدد ليس نسخة احتياطية لتطبيق Good Girl" }
        val formatVersion = root.getInt("formatVersion")
        require(formatVersion in 1..BACKUP_FORMAT_VERSION) { "صيغة النسخة الاحتياطية غير مدعومة" }
        require(root.getString("applicationId") == APPLICATION_ID) { "النسخة الاحتياطية لا تخص هذا التطبيق" }
        val backupDatabaseVersion = root.getInt("databaseVersion")
        require(backupDatabaseVersion in MIN_SUPPORTED_BACKUP_DATABASE_VERSION..DATABASE_VERSION) {
            "إصدار قاعدة البيانات في النسخة الاحتياطية غير مدعوم"
        }

        val categories = root.parseArray("categories") { it.toCategoryEntity() }
        val products = root.parseArray("products") { it.toProductEntity() }
        val productBarcodes = root.parseOptionalArray("productBarcodes") { it.toProductBarcodeEntity() }
        val units = root.parseArray("productUnits") { it.toProductUnitEntity() }
        val parties = root.parseArray("parties") { it.toPartyEntity() }
        val invoices = root.parseArray("invoices") { it.toInvoiceEntity() }
        val invoiceLines = root.parseArray("invoiceLines") { it.toInvoiceLineEntity() }
        val importedMovements = root.parseArray("stockMovements") { it.toStockMovementEntity() }
        val movements = BackupStockMovementMigrator.migrateToCurrentVersion(
            databaseVersion = backupDatabaseVersion,
            products = products,
            movements = importedMovements,
            occurredAt = System.currentTimeMillis()
        )
        val restoredAt = System.currentTimeMillis()
        val stockCostBaselines = if (root.has("stockCostBaselines")) {
            root.parseOptionalArray("stockCostBaselines") { it.toStockCostBaselineEntity() }
        } else {
            require(backupDatabaseVersion < ACCOUNTING_DATABASE_VERSION) {
                "النسخة الاحتياطية الحديثة لا تحتوي على نقاط بداية كلفة المخزون"
            }
            val fallbackBoundary = maxOf(
                restoredAt,
                movements.maxOfOrNull { it.occurredAt } ?: 0L,
                movements.maxOfOrNull { it.recordedAt } ?: 0L
            )
            products.map { product ->
                StockCostBaselineEntity(
                    productId = product.id,
                    quantity = product.stockQuantity,
                    averageCost = product.purchaseCost,
                    currencyCode = product.priceCurrencyCode,
                    effectiveAt = fallbackBoundary,
                    recordedAt = fallbackBoundary,
                    source = "LEGACY_BACKUP_FALLBACK"
                )
            }
        }
        require(stockCostBaselines.size == products.size) {
            "عدد نقاط بداية كلفة المخزون لا يطابق عدد المنتجات"
        }
        val stockCostRecalculationAudits = root.parseOptionalArray("stockCostRecalculationAudits") { it.toStockCostRecalculationAuditEntity() }
        val stockCostRecalculationItems = root.parseOptionalArray("stockCostRecalculationItems") { it.toStockCostRecalculationItemEntity() }
        val ledgerEntries = root.parseArray("ledgerEntries") { it.toLedgerEntryEntity() }
        val accounts = root.parseOptionalArray("accounts") { it.toAccountEntity() }
        val journalEntries = root.parseOptionalArray("journalEntries") { it.toJournalEntryEntity() }
        val journalLines = root.parseOptionalArray("journalLines") { it.toJournalLineEntity() }
        val exchangeRateHistory = root.parseOptionalArray("exchangeRateHistory") { it.toExchangeRateHistoryEntity() }
        val exchangeRateAudits = root.parseOptionalArray("exchangeRateAudits") { it.toExchangeRateAuditEntity() }
        val exchangeRateCorrectionItems = root.parseOptionalArray("exchangeRateCorrectionItems") { it.toExchangeRateCorrectionItemEntity() }
        val dailySalesDraft = root.optJSONObject("dailySalesDraft")?.toDailySalesDraftEntity()
        val dailySalesDraftLines = root.parseOptionalArray("dailySalesDraftLines") { it.toDailySalesDraftLineEntity() }
        val dailySalesDraftExpenses = root.parseOptionalArray("dailySalesDraftExpenses") { it.toDailySalesDraftExpenseEntity() }

        // Validate the complete snapshot before opening the destructive replacement transaction.
        BackupIntegrityValidator.validate(
            categories = categories,
            products = products,
            productBarcodes = productBarcodes,
            units = units,
            parties = parties,
            invoices = invoices,
            invoiceLines = invoiceLines,
            movements = movements,
            ledgerEntries = ledgerEntries,
            accounts = accounts,
            journalEntries = journalEntries,
            journalLines = journalLines,
            exchangeRateHistory = exchangeRateHistory,
            exchangeRateAudits = exchangeRateAudits,
            exchangeRateCorrectionItems = exchangeRateCorrectionItems,
            stockCostBaselines = stockCostBaselines,
            stockCostRecalculationAudits = stockCostRecalculationAudits,
            stockCostRecalculationItems = stockCostRecalculationItems,
            dailySalesDraft = dailySalesDraft,
            dailySalesDraftLines = dailySalesDraftLines,
            dailySalesDraftExpenses = dailySalesDraftExpenses
        )

        val totalRecords = categories.size + products.size + productBarcodes.size + units.size + parties.size +
            invoices.size + invoiceLines.size + movements.size + ledgerEntries.size +
            stockCostBaselines.size + stockCostRecalculationAudits.size + stockCostRecalculationItems.size + accounts.size + journalEntries.size + journalLines.size + exchangeRateHistory.size +
            exchangeRateAudits.size + exchangeRateCorrectionItems.size +
            (if (dailySalesDraft == null) 0 else 1) + dailySalesDraftLines.size +
            dailySalesDraftExpenses.size
        val exportedAt = root.getLong("exportedAt")

        val settingsObject = if (formatVersion >= 2) root.optJSONObject("settings") else null
        val restoredRate = settingsObject?.optString("usdSypRate", "1")?.takeIf { it.isNotBlank() } ?: "1"
        val parsedRestoredRate = runCatching { BigDecimal(restoredRate) }.getOrNull()
        require(parsedRestoredRate != null && parsedRestoredRate > BigDecimal.ZERO) {
            "سعر الصرف الموجود في النسخة الاحتياطية غير صالح"
        }
        val restoredRateUpdatedAt = settingsObject?.optLong("usdSypRateUpdatedAt", 0L) ?: 0L
        val restoredRateHistory = buildSet {
            val array = settingsObject?.optJSONArray("usdSypRateHistory")
            if (array != null) {
                for (index in 0 until array.length()) {
                    val item = array.optString(index, "")
                    val parts = item.split("|", limit = 2)
                    val timestamp = parts.firstOrNull()?.toLongOrNull()
                    val historyRate = parts.getOrNull(1)?.let { runCatching { BigDecimal(it) }.getOrNull() }
                    if (timestamp != null && timestamp > 0L && historyRate != null && historyRate > BigDecimal.ZERO) {
                        add(timestamp.toString() + "|" + historyRate.stripTrailingZeros().toPlainString())
                    }
                }
            }
        }.take(30).toSet()

        // The replacement is atomic: an invalid insert rolls back and preserves current data.
        database.withTransaction {
            database.exchangeRateCorrectionItemDao().deleteAll()
            database.stockCostRecalculationItemDao().deleteAll()
            database.stockCostRecalculationAuditDao().deleteAll()
            database.exchangeRateAuditDao().deleteAll()
            database.exchangeRateHistoryDao().deleteAll()
            database.journalLineDao().deleteAll()
            database.journalEntryDao().deleteAll()
            database.accountDao().deleteAll()
            database.invoiceDao().deleteAllLines()
            database.invoiceDao().deleteAllInvoices()
            database.ledgerDao().deleteAll()
            database.stockMovementDao().deleteAll()
            database.stockCostBaselineDao().deleteAll()
            database.productBarcodeDao().deleteAll()
            database.productUnitDao().deleteAll()
            database.partyDao().deleteAll()
            database.productDao().deleteAll()
            database.categoryDao().deleteAll()

            database.categoryDao().insertAll(categories)
            database.productDao().insertAll(products)
            database.productBarcodeDao().insertAll(productBarcodes)
            database.productUnitDao().insertAll(units)
            database.partyDao().insertAll(parties)
            database.invoiceDao().insertAllInvoices(invoices)
            database.invoiceDao().insertAllLines(invoiceLines)
            database.stockMovementDao().insertAll(movements)
            database.stockCostBaselineDao().insertAll(stockCostBaselines)
            database.stockCostRecalculationAuditDao().insertAll(stockCostRecalculationAudits)
            database.stockCostRecalculationItemDao().insertAll(stockCostRecalculationItems)
            database.ledgerDao().insertAll(ledgerEntries)
            database.accountDao().insertAll(accounts)
            database.journalEntryDao().insertAll(journalEntries)
            database.journalLineDao().insertAll(journalLines)
            database.exchangeRateHistoryDao().insertAll(exchangeRateHistory)
            database.exchangeRateAuditDao().insertAll(exchangeRateAudits)
            database.exchangeRateCorrectionItemDao().insertAll(exchangeRateCorrectionItems)
            database.dailySalesDraftDao().deleteLines("active")
            database.dailySalesDraftDao().deleteDraft("active")
            if (dailySalesDraft != null) database.dailySalesDraftDao().saveDraft(dailySalesDraft)
            if (dailySalesDraftLines.isNotEmpty()) database.dailySalesDraftDao().saveLines(dailySalesDraftLines)
            if (dailySalesDraftExpenses.isNotEmpty()) database.dailySalesDraftDao().saveExpenses(dailySalesDraftExpenses)

            // Legacy backups were captured before latest-purchase-cost policy.
            // Convert them inside the same atomic restore transaction so the
            // restored product references and inventory valuation journal agree.
            if (backupDatabaseVersion < 11) {
                LatestPurchaseCostPolicyMigrator.migrate(
                    database.openHelper.writableDatabase,
                    System.currentTimeMillis()
                )
            }
        }

        context.getSharedPreferences("good_girl_settings", Context.MODE_PRIVATE).edit()
            .putString("usd_syp_rate", parsedRestoredRate!!.stripTrailingZeros().toPlainString())
            .putLong("usd_syp_rate_updated_at", restoredRateUpdatedAt)
            .putStringSet("usd_syp_rate_history", restoredRateHistory)
            .putBoolean("usd_syp_rate_history_migrated_to_room", exchangeRateHistory.isNotEmpty())
            .apply()

        AccountingBackupResult(
            recordCount = totalRecords,
            exportedAt = exportedAt,
            wasLegacyUnencrypted = wasLegacyUnencrypted
        )
        } finally {
            password.fill('\u0000')
        }
    }

    private fun isLegacyJson(bytes: ByteArray): Boolean {
        val firstContentByte = bytes.firstOrNull { byte ->
            !(byte.toInt() and 0xff).toChar().isWhitespace()
        } ?: return false
        return firstContentByte == '{'.code.toByte()
    }

    private fun <T> List<T>.toJsonArray(serializer: (T) -> JSONObject): JSONArray =
        JSONArray().also { array -> forEach { array.put(serializer(it)) } }

    private fun <T> JSONObject.parseArray(key: String, parser: (JSONObject) -> T): List<T> {
        val array = getJSONArray(key)
        return List(array.length()) { index -> parser(array.getJSONObject(index)) }
    }

    private fun <T> JSONObject.parseOptionalArray(key: String, parser: (JSONObject) -> T): List<T> {
        val array = optJSONArray(key) ?: return emptyList()
        return List(array.length()) { index -> parser(array.getJSONObject(index)) }
    }

    private fun DailySalesDraftEntity.toBackupJson() = JSONObject()
        .put("id", id).put("selectedDay", selectedDay)
        .put("selectedProductId", selectedProductId ?: JSONObject.NULL)
        .put("selectedProductNameSnapshot", selectedProductNameSnapshot)
        .put("selectedUnitName", selectedUnitName ?: JSONObject.NULL)
        .put("productSearchQuery", productSearchQuery)
        .put("selectedProductCategoryId", selectedProductCategoryId ?: JSONObject.NULL)
        .put("quantity", quantity).put("salePrice", salePrice).put("lineDiscount", lineDiscount)
        .put("currencyCode", currencyCode).put("exchangeRate", exchangeRate)
        .put("paidAmount", paidAmount).put("paidAmountTouched", paidAmountTouched)
        .put("selectedPartyId", selectedPartyId ?: JSONObject.NULL)
        .put("note", note).put("updatedAt", updatedAt)

    private fun DailySalesDraftLineEntity.toBackupJson() = JSONObject()
        .put("id", id).put("draftId", draftId).put("lineOrder", lineOrder)
        .put("productId", productId).put("productNameSnapshot", productNameSnapshot)
        .put("quantity", quantity).put("unitName", unitName).put("unitFactor", unitFactor)
        .put("unitPrice", unitPrice).put("discount", discount).put("currencyCode", currencyCode)
        .put("exchangeRate", exchangeRate).put("paidAmount", paidAmount)
        .put("customerId", customerId ?: JSONObject.NULL).put("note", note).put("issuedAt", issuedAt)
        .put("purchaseCostSnapshot", purchaseCostSnapshot ?: JSONObject.NULL)

    private fun DailySalesDraftExpenseEntity.toBackupJson() = JSONObject()
        .put("id", id).put("draftId", draftId).put("lineId", lineId)
        .put("description", description).put("amount", amount)
        .put("currencyCode", currencyCode).put("exchangeRateSnapshot", exchangeRateSnapshot)
        .put("createdAt", createdAt)

    private fun CategoryEntity.toBackupJson() = JSONObject()
        .put("id", id).put("name", name).put("note", note).put("createdAt", createdAt)

    private fun ProductEntity.toBackupJson() = JSONObject()
        .put("id", id).put("sku", sku).put("name", name).put("categoryId", categoryId ?: JSONObject.NULL)
        .put("baseUnit", baseUnit).put("purchaseCost", purchaseCost).put("salePrice", salePrice)
        .put("priceCurrencyCode", priceCurrencyCode).put("stockQuantity", stockQuantity)
        .put("lowStockThreshold", lowStockThreshold).put("note", note).put("isActive", isActive)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun ProductBarcodeEntity.toBackupJson() = JSONObject()
        .put("id", id).put("productId", productId).put("barcodeValue", barcodeValue)
        .put("format", format).put("origin", origin).put("createdAt", createdAt)

    private fun ProductUnitEntity.toBackupJson() = JSONObject()
        .put("id", id).put("productId", productId).put("unitName", unitName)
        .put("baseFactor", baseFactor).put("isBaseUnit", isBaseUnit)

    private fun PartyEntity.toBackupJson() = JSONObject()
        .put("id", id).put("name", name).put("kind", kind).put("phone", phone).put("address", address)
        .put("openingBalance", openingBalance).put("currencyCode", currencyCode)
        .put("openingExchangeRate", openingExchangeRate).put("note", note).put("createdAt", createdAt)

    private fun InvoiceEntity.toBackupJson() = JSONObject()
        .put("id", id).put("invoiceNumber", invoiceNumber).put("kind", kind)
        .put("partyId", partyId ?: JSONObject.NULL).put("currencyCode", currencyCode)
        .put("exchangeRateSnapshot", exchangeRateSnapshot).put("subtotal", subtotal)
        .put("discount", discount).put("total", total).put("paid", paid).put("remaining", remaining)
        .put("status", status).put("issuedAt", issuedAt).put("note", note).put("createdAt", createdAt)

    private fun InvoiceLineEntity.toBackupJson() = JSONObject()
        .put("id", id).put("invoiceId", invoiceId).put("productId", productId ?: JSONObject.NULL)
        .put("productNameSnapshot", productNameSnapshot).put("unitSnapshot", unitSnapshot)
        .put("quantity", quantity).put("unitCostSnapshot", unitCostSnapshot)
        .put("unitPriceSnapshot", unitPriceSnapshot).put("discount", discount).put("lineTotal", lineTotal)
        .put("unitFactorSnapshot", unitFactorSnapshot).put("baseQuantity", baseQuantity)

    private fun StockMovementEntity.toBackupJson() = JSONObject()
        .put("id", id).put("productId", productId).put("quantityDelta", quantityDelta)
        .put("unitCostSnapshot", unitCostSnapshot).put("currencyCode", currencyCode)
        .put("sourceType", sourceType).put("sourceId", sourceId).put("occurredAt", occurredAt)
        .put("recordedAt", recordedAt).put("note", note)

    private fun StockCostBaselineEntity.toBackupJson() = JSONObject()
        .put("productId", productId).put("quantity", quantity).put("averageCost", averageCost)
        .put("currencyCode", currencyCode).put("effectiveAt", effectiveAt)
        .put("recordedAt", recordedAt).put("source", source)

    private fun StockCostRecalculationAuditEntity.toBackupJson() = JSONObject()
        .put("id", id).put("productId", productId).put("triggerType", triggerType)
        .put("triggerSourceId", triggerSourceId).put("reason", reason).put("createdAt", createdAt)
        .put("currencyCode", currencyCode).put("oldEndingQuantity", oldEndingQuantity)
        .put("newEndingQuantity", newEndingQuantity).put("oldEndingAverageCost", oldEndingAverageCost)
        .put("newEndingAverageCost", newEndingAverageCost).put("affectedStockOutCount", affectedStockOutCount)

    private fun StockCostRecalculationItemEntity.toBackupJson() = JSONObject()
        .put("id", id).put("auditId", auditId).put("movementId", movementId)
        .put("sourceType", sourceType).put("sourceId", sourceId).put("invoiceId", invoiceId)
        .put("currencyCode", currencyCode).put("quantity", quantity).put("oldUnitCost", oldUnitCost)
        .put("newUnitCost", newUnitCost).put("oldExtendedCost", oldExtendedCost)
        .put("newExtendedCost", newExtendedCost)

    private fun LedgerEntryEntity.toBackupJson() = JSONObject()
        .put("id", id).put("partyId", partyId ?: JSONObject.NULL).put("entryType", entryType)
        .put("amount", amount).put("currencyCode", currencyCode).put("exchangeRateSnapshot", exchangeRateSnapshot)
        .put("sourceType", sourceType).put("sourceId", sourceId).put("occurredAt", occurredAt).put("note", note)

    private fun AccountEntity.toBackupJson() = JSONObject()
        .put("id", id).put("code", code).put("name", name).put("accountType", accountType)
        .put("normalSide", normalSide).put("isSystem", isSystem).put("isActive", isActive)
        .put("createdAt", createdAt)

    private fun JournalEntryEntity.toBackupJson() = JSONObject()
        .put("id", id).put("journalNumber", journalNumber).put("sourceType", sourceType)
        .put("sourceId", sourceId).put("currencyCode", currencyCode)
        .put("exchangeRateSnapshot", exchangeRateSnapshot).put("occurredAt", occurredAt)
        .put("description", description).put("createdAt", createdAt)

    private fun JournalLineEntity.toBackupJson() = JSONObject()
        .put("id", id).put("journalEntryId", journalEntryId).put("lineNumber", lineNumber)
        .put("accountId", accountId).put("debit", debit).put("credit", credit).put("memo", memo)

    private fun ExchangeRateHistoryEntity.toBackupJson() = JSONObject()
        .put("id", id).put("currencyPair", currencyPair).put("rate", rate)
        .put("effectiveAt", effectiveAt).put("recordedAt", recordedAt)
        .put("source", source).put("isArchived", isArchived)

    private fun ExchangeRateAuditEntity.toBackupJson() = JSONObject()
        .put("id", id).put("historyId", historyId).put("eventType", eventType)
        .put("oldRate", oldRate).put("newRate", newRate).put("reason", reason)
        .put("correctedAt", correctedAt).put("affectedInvoiceCount", affectedInvoiceCount)
        .put("affectedLedgerCount", affectedLedgerCount).put("affectedJournalCount", affectedJournalCount)
        .put("financialAmountsChanged", financialAmountsChanged)

    private fun ExchangeRateCorrectionItemEntity.toBackupJson() = JSONObject()
        .put("id", id).put("auditId", auditId).put("recordType", recordType)
        .put("recordId", recordId).put("sourceType", sourceType).put("sourceId", sourceId)
        .put("oldRate", oldRate).put("newRate", newRate)

    private fun JSONObject.toDailySalesDraftEntity() = DailySalesDraftEntity(
        id = getString("id"),
        selectedDay = getLong("selectedDay"),
        selectedProductId = nullableString("selectedProductId"),
        selectedProductNameSnapshot = getString("selectedProductNameSnapshot"),
        selectedUnitName = nullableString("selectedUnitName"),
        productSearchQuery = getString("productSearchQuery"),
        selectedProductCategoryId = nullableString("selectedProductCategoryId"),
        quantity = getString("quantity"),
        salePrice = getString("salePrice"),
        lineDiscount = getString("lineDiscount"),
        currencyCode = getString("currencyCode"),
        exchangeRate = getString("exchangeRate"),
        paidAmount = getString("paidAmount"),
        paidAmountTouched = getBoolean("paidAmountTouched"),
        selectedPartyId = nullableString("selectedPartyId"),
        note = getString("note"),
        updatedAt = getLong("updatedAt")
    )

    private fun JSONObject.toDailySalesDraftLineEntity() = DailySalesDraftLineEntity(
        id = getString("id"),
        draftId = getString("draftId"),
        lineOrder = getInt("lineOrder"),
        productId = getString("productId"),
        productNameSnapshot = getString("productNameSnapshot"),
        quantity = getString("quantity"),
        unitName = getString("unitName"),
        unitFactor = getString("unitFactor"),
        unitPrice = getString("unitPrice"),
        discount = getString("discount"),
        currencyCode = getString("currencyCode"),
        purchaseCostSnapshot = nullableString("purchaseCostSnapshot"),
        exchangeRate = getString("exchangeRate"),
        paidAmount = getString("paidAmount"),
        customerId = nullableString("customerId"),
        note = getString("note"),
        issuedAt = getLong("issuedAt")
    )

    private fun JSONObject.toDailySalesDraftExpenseEntity() = DailySalesDraftExpenseEntity(
        id = getString("id"),
        draftId = getString("draftId"),
        lineId = getString("lineId"),
        description = getString("description"),
        amount = getString("amount"),
        currencyCode = getString("currencyCode"),
        exchangeRateSnapshot = getString("exchangeRateSnapshot"),
        createdAt = getLong("createdAt")
    )

    private fun JSONObject.toCategoryEntity() = CategoryEntity(
        id = getString("id"), name = getString("name"), note = getString("note"), createdAt = getLong("createdAt")
    )

    private fun JSONObject.toProductEntity() = ProductEntity(
        id = getString("id"), sku = getString("sku"), name = getString("name"),
        categoryId = nullableString("categoryId"), baseUnit = getString("baseUnit"),
        purchaseCost = getString("purchaseCost"), salePrice = getString("salePrice"),
        priceCurrencyCode = getString("priceCurrencyCode"), stockQuantity = getString("stockQuantity"),
        lowStockThreshold = getString("lowStockThreshold"), note = getString("note"),
        isActive = getBoolean("isActive"), createdAt = getLong("createdAt"), updatedAt = getLong("updatedAt")
    )

    private fun JSONObject.toProductBarcodeEntity() = ProductBarcodeEntity(
        id = getString("id"), productId = getString("productId"),
        barcodeValue = getString("barcodeValue"), format = getString("format"),
        origin = getString("origin"), createdAt = getLong("createdAt")
    )

    private fun JSONObject.toProductUnitEntity() = ProductUnitEntity(
        id = getString("id"), productId = getString("productId"), unitName = getString("unitName"),
        baseFactor = getString("baseFactor"), isBaseUnit = getBoolean("isBaseUnit")
    )

    private fun JSONObject.toPartyEntity() = PartyEntity(
        id = getString("id"), name = getString("name"), kind = getString("kind"), phone = getString("phone"),
        address = getString("address"), openingBalance = getString("openingBalance"),
        currencyCode = getString("currencyCode"), openingExchangeRate = getString("openingExchangeRate"),
        note = getString("note"), createdAt = getLong("createdAt")
    )

    private fun JSONObject.toInvoiceEntity() = InvoiceEntity(
        id = getString("id"), invoiceNumber = getString("invoiceNumber"), kind = getString("kind"),
        partyId = nullableString("partyId"), currencyCode = getString("currencyCode"),
        exchangeRateSnapshot = getString("exchangeRateSnapshot"), subtotal = getString("subtotal"),
        discount = getString("discount"), total = getString("total"), paid = getString("paid"),
        remaining = getString("remaining"), status = getString("status"), issuedAt = getLong("issuedAt"),
        note = getString("note"), createdAt = getLong("createdAt")
    )

    private fun JSONObject.toInvoiceLineEntity() = InvoiceLineEntity(
        id = getString("id"), invoiceId = getString("invoiceId"), productId = nullableString("productId"),
        productNameSnapshot = getString("productNameSnapshot"), unitSnapshot = getString("unitSnapshot"),
        quantity = getString("quantity"), unitCostSnapshot = getString("unitCostSnapshot"),
        unitPriceSnapshot = getString("unitPriceSnapshot"), discount = getString("discount"),
        lineTotal = getString("lineTotal"), unitFactorSnapshot = getString("unitFactorSnapshot"),
        baseQuantity = getString("baseQuantity")
    )

    private fun JSONObject.toStockMovementEntity() = StockMovementEntity(
        id = getString("id"), productId = getString("productId"), quantityDelta = getString("quantityDelta"),
        unitCostSnapshot = getString("unitCostSnapshot"), currencyCode = getString("currencyCode"),
        sourceType = getString("sourceType"), sourceId = getString("sourceId"),
        occurredAt = getLong("occurredAt"), recordedAt = optLong("recordedAt", getLong("occurredAt")),
        note = getString("note")
    )

    private fun JSONObject.toStockCostBaselineEntity() = StockCostBaselineEntity(
        productId = getString("productId"), quantity = getString("quantity"),
        averageCost = getString("averageCost"), currencyCode = getString("currencyCode"),
        effectiveAt = getLong("effectiveAt"), recordedAt = getLong("recordedAt"),
        source = getString("source")
    )

    private fun JSONObject.toStockCostRecalculationAuditEntity() = StockCostRecalculationAuditEntity(
        id = getString("id"), productId = getString("productId"), triggerType = getString("triggerType"),
        triggerSourceId = getString("triggerSourceId"), reason = getString("reason"), createdAt = getLong("createdAt"),
        currencyCode = getString("currencyCode"), oldEndingQuantity = getString("oldEndingQuantity"),
        newEndingQuantity = getString("newEndingQuantity"), oldEndingAverageCost = getString("oldEndingAverageCost"),
        newEndingAverageCost = getString("newEndingAverageCost"), affectedStockOutCount = getInt("affectedStockOutCount")
    )

    private fun JSONObject.toStockCostRecalculationItemEntity() = StockCostRecalculationItemEntity(
        id = getString("id"), auditId = getString("auditId"), movementId = getString("movementId"),
        sourceType = getString("sourceType"), sourceId = getString("sourceId"), invoiceId = getString("invoiceId"),
        currencyCode = getString("currencyCode"), quantity = getString("quantity"),
        oldUnitCost = getString("oldUnitCost"), newUnitCost = getString("newUnitCost"),
        oldExtendedCost = getString("oldExtendedCost"), newExtendedCost = getString("newExtendedCost")
    )

    private fun JSONObject.toLedgerEntryEntity() = LedgerEntryEntity(
        id = getString("id"), partyId = nullableString("partyId"), entryType = getString("entryType"),
        amount = getString("amount"), currencyCode = getString("currencyCode"),
        exchangeRateSnapshot = getString("exchangeRateSnapshot"), sourceType = getString("sourceType"),
        sourceId = getString("sourceId"), occurredAt = getLong("occurredAt"), note = getString("note")
    )

    private fun JSONObject.toAccountEntity() = AccountEntity(
        id = getString("id"), code = getString("code"), name = getString("name"),
        accountType = getString("accountType"), normalSide = getString("normalSide"),
        isSystem = getBoolean("isSystem"), isActive = getBoolean("isActive"),
        createdAt = getLong("createdAt")
    )

    private fun JSONObject.toJournalEntryEntity() = JournalEntryEntity(
        id = getString("id"), journalNumber = getString("journalNumber"),
        sourceType = getString("sourceType"), sourceId = getString("sourceId"),
        currencyCode = getString("currencyCode"), exchangeRateSnapshot = getString("exchangeRateSnapshot"),
        occurredAt = getLong("occurredAt"), description = getString("description"),
        createdAt = getLong("createdAt")
    )

    private fun JSONObject.toJournalLineEntity() = JournalLineEntity(
        id = getString("id"), journalEntryId = getString("journalEntryId"),
        lineNumber = getInt("lineNumber"), accountId = getString("accountId"),
        debit = getString("debit"), credit = getString("credit"), memo = getString("memo")
    )

    private fun JSONObject.toExchangeRateHistoryEntity() = ExchangeRateHistoryEntity(
        id = getString("id"), currencyPair = getString("currencyPair"), rate = getString("rate"),
        effectiveAt = getLong("effectiveAt"), recordedAt = getLong("recordedAt"),
        source = getString("source"), isArchived = getBoolean("isArchived")
    )

    private fun JSONObject.toExchangeRateAuditEntity() = ExchangeRateAuditEntity(
        id = getString("id"), historyId = getString("historyId"), eventType = getString("eventType"),
        oldRate = getString("oldRate"), newRate = getString("newRate"), reason = getString("reason"),
        correctedAt = getLong("correctedAt"), affectedInvoiceCount = getInt("affectedInvoiceCount"),
        affectedLedgerCount = getInt("affectedLedgerCount"), affectedJournalCount = getInt("affectedJournalCount"),
        financialAmountsChanged = getBoolean("financialAmountsChanged")
    )

    private fun JSONObject.toExchangeRateCorrectionItemEntity() = ExchangeRateCorrectionItemEntity(
        id = getString("id"), auditId = getString("auditId"), recordType = getString("recordType"),
        recordId = getString("recordId"), sourceType = getString("sourceType"),
        sourceId = getString("sourceId"), oldRate = getString("oldRate"), newRate = getString("newRate")
    )

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else getString(key)

    companion object {
        private const val BACKUP_FORMAT = "good-girl-accounting-backup"
        private const val BACKUP_FORMAT_VERSION = 6
        private const val APPLICATION_ID = "com.goodgirl.accounting"
        private const val MIN_SUPPORTED_BACKUP_DATABASE_VERSION = 4
        private const val DATABASE_VERSION = ACCOUNTING_DATABASE_VERSION
        private const val MAX_BACKUP_BYTES = 50 * 1024 * 1024
        private const val MAX_ENCRYPTED_BACKUP_BYTES =
            MAX_BACKUP_BYTES + EncryptedBackupCodec.ENVELOPE_OVERHEAD
    }
}
