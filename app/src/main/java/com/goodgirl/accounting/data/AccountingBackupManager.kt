package com.goodgirl.accounting.data

import android.content.Context
import android.net.Uri
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.CategoryEntity
import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.data.local.PartyEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.data.local.ProductUnitEntity
import com.goodgirl.accounting.data.local.StockMovementEntity
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class AccountingBackupResult(
    val recordCount: Int,
    val exportedAt: Long
)

/**
 * Logical JSON backup. All values are serialized from Room entities, preserving
 * decimal strings and avoiding unsafe copying of an open SQLite/WAL database.
 */
class AccountingBackupManager(
    private val context: Context,
    private val database: AccountingDatabase
) {
    suspend fun exportBackup(destination: Uri): AccountingBackupResult = withContext(Dispatchers.IO) {
        val exportedAt = System.currentTimeMillis()
        val snapshot = database.withTransaction {
            val categories = database.categoryDao().getAll()
            val products = database.productDao().getAll()
            val units = database.productUnitDao().getAll()
            val parties = database.partyDao().getAll()
            val invoices = database.invoiceDao().getAllInvoices()
            val invoiceLines = database.invoiceDao().getAllLines()
            val movements = database.stockMovementDao().getAll()
            val ledgerEntries = database.ledgerDao().getAll()

            val json = JSONObject()
                .put("format", BACKUP_FORMAT)
                .put("formatVersion", BACKUP_FORMAT_VERSION)
                .put("applicationId", APPLICATION_ID)
                .put("databaseVersion", DATABASE_VERSION)
                .put("exportedAt", exportedAt)
                .put("categories", categories.toJsonArray { it.toBackupJson() })
                .put("products", products.toJsonArray { it.toBackupJson() })
                .put("productUnits", units.toJsonArray { it.toBackupJson() })
                .put("parties", parties.toJsonArray { it.toBackupJson() })
                .put("invoices", invoices.toJsonArray { it.toBackupJson() })
                .put("invoiceLines", invoiceLines.toJsonArray { it.toBackupJson() })
                .put("stockMovements", movements.toJsonArray { it.toBackupJson() })
                .put("ledgerEntries", ledgerEntries.toJsonArray { it.toBackupJson() })

            json to (
                categories.size + products.size + units.size + parties.size +
                    invoices.size + invoiceLines.size + movements.size + ledgerEntries.size
                )
        }

        val bytes = snapshot.first.toString().toByteArray(Charsets.UTF_8)
        val output = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IllegalStateException("تعذر فتح ملف النسخة الاحتياطية للكتابة")
        output.use { it.write(bytes); it.flush() }
        AccountingBackupResult(snapshot.second, exportedAt)
    }

    suspend fun restoreBackup(source: Uri): AccountingBackupResult = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IllegalStateException("تعذر فتح ملف النسخة الاحتياطية")
        val bytes = input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_BACKUP_BYTES) { "حجم ملف النسخة الاحتياطية أكبر من الحد المسموح" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        require(bytes.isNotEmpty()) { "ملف النسخة الاحتياطية فارغ" }

        val root = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (error: Exception) {
            throw IllegalArgumentException("ملف النسخة الاحتياطية تالف أو غير صالح", error)
        }
        require(root.getString("format") == BACKUP_FORMAT) { "الملف المحدد ليس نسخة احتياطية لتطبيق Good Girl" }
        require(root.getInt("formatVersion") == BACKUP_FORMAT_VERSION) { "صيغة النسخة الاحتياطية غير مدعومة" }
        require(root.getString("applicationId") == APPLICATION_ID) { "النسخة الاحتياطية لا تخص هذا التطبيق" }
        require(root.getInt("databaseVersion") == DATABASE_VERSION) {
            "إصدار قاعدة البيانات في النسخة لا يتوافق مع إصدار التطبيق الحالي"
        }

        val categories = root.arrayOf("categories") { it.toCategoryEntity() }
        val products = root.arrayOf("products") { it.toProductEntity() }
        val units = root.arrayOf("productUnits") { it.toProductUnitEntity() }
        val parties = root.arrayOf("parties") { it.toPartyEntity() }
        val invoices = root.arrayOf("invoices") { it.toInvoiceEntity() }
        val invoiceLines = root.arrayOf("invoiceLines") { it.toInvoiceLineEntity() }
        val movements = root.arrayOf("stockMovements") { it.toStockMovementEntity() }
        val ledgerEntries = root.arrayOf("ledgerEntries") { it.toLedgerEntryEntity() }
        val totalRecords = categories.size + products.size + units.size + parties.size +
            invoices.size + invoiceLines.size + movements.size + ledgerEntries.size
        val exportedAt = root.getLong("exportedAt")

        // The replacement is atomic: an invalid insert rolls back and preserves current data.
        database.withTransaction {
            database.invoiceDao().deleteAllLines()
            database.invoiceDao().deleteAllInvoices()
            database.ledgerDao().deleteAll()
            database.stockMovementDao().deleteAll()
            database.productUnitDao().deleteAll()
            database.partyDao().deleteAll()
            database.productDao().deleteAll()
            database.categoryDao().deleteAll()

            database.categoryDao().insertAll(categories)
            database.productDao().insertAll(products)
            database.productUnitDao().insertAll(units)
            database.partyDao().insertAll(parties)
            database.invoiceDao().insertAllInvoices(invoices)
            database.invoiceDao().insertAllLines(invoiceLines)
            database.stockMovementDao().insertAll(movements)
            database.ledgerDao().insertAll(ledgerEntries)
        }

        AccountingBackupResult(totalRecords, exportedAt)
    }

    private fun <T> List<T>.toJsonArray(serializer: (T) -> JSONObject): JSONArray =
        JSONArray().also { array -> forEach { array.put(serializer(it)) } }

    private fun JSONObject.arrayOf(key: String, parser: (JSONObject) -> Any): List<Any> {
        val array = getJSONArray(key)
        return List(array.length()) { index -> parser(array.getJSONObject(index)) }
    }

    private inline fun <reified T> JSONObject.arrayOf(key: String, noinline parser: (JSONObject) -> T): List<T> {
        val array = getJSONArray(key)
        return List(array.length()) { index -> parser(array.getJSONObject(index)) }
    }

    private fun CategoryEntity.toBackupJson() = JSONObject()
        .put("id", id).put("name", name).put("note", note).put("createdAt", createdAt)

    private fun ProductEntity.toBackupJson() = JSONObject()
        .put("id", id).put("sku", sku).put("name", name).put("categoryId", categoryId ?: JSONObject.NULL)
        .put("baseUnit", baseUnit).put("purchaseCost", purchaseCost).put("salePrice", salePrice)
        .put("priceCurrencyCode", priceCurrencyCode).put("stockQuantity", stockQuantity)
        .put("lowStockThreshold", lowStockThreshold).put("note", note).put("isActive", isActive)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

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
        .put("sourceType", sourceType).put("sourceId", sourceId).put("occurredAt", occurredAt).put("note", note)

    private fun LedgerEntryEntity.toBackupJson() = JSONObject()
        .put("id", id).put("partyId", partyId ?: JSONObject.NULL).put("entryType", entryType)
        .put("amount", amount).put("currencyCode", currencyCode).put("exchangeRateSnapshot", exchangeRateSnapshot)
        .put("sourceType", sourceType).put("sourceId", sourceId).put("occurredAt", occurredAt).put("note", note)

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
        occurredAt = getLong("occurredAt"), note = getString("note")
    )

    private fun JSONObject.toLedgerEntryEntity() = LedgerEntryEntity(
        id = getString("id"), partyId = nullableString("partyId"), entryType = getString("entryType"),
        amount = getString("amount"), currencyCode = getString("currencyCode"),
        exchangeRateSnapshot = getString("exchangeRateSnapshot"), sourceType = getString("sourceType"),
        sourceId = getString("sourceId"), occurredAt = getLong("occurredAt"), note = getString("note")
    )

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else getString(key)

    companion object {
        private const val BACKUP_FORMAT = "good-girl-accounting-backup"
        private const val BACKUP_FORMAT_VERSION = 1
        private const val APPLICATION_ID = "com.goodgirl.accounting"
        private const val DATABASE_VERSION = 4
        private const val MAX_BACKUP_BYTES = 50 * 1024 * 1024
    }
}
