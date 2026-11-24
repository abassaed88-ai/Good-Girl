package com.goodgirl.accounting.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.ACCOUNTING_DATABASE_VERSION
import com.goodgirl.accounting.data.local.AccountingDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises authenticated encrypted restore against a real Room/SQLite database. */
@RunWith(AndroidJUnit4::class)
class EncryptedBackupRestoreIntegrationTest {

    @Test
    fun encryptedRestoreAtomicallyReplacesProductsUnitsAndStockMovements() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            AccountingDatabase::class.java
        ).allowMainThreadQueries().build()
        val backupFile = File.createTempFile("good-girl-restore-", ".ggbackup", context.cacheDir)
        val password = "Restore-RoundTrip-Password".toCharArray()
        try {
            ProductRepository(database).addProduct(
                sku = "OLD-RESTORE-001",
                name = "سجل قديم يجب استبداله",
                unit = "قطعة",
                purchaseCost = "1",
                salePrice = "2",
                openingStock = "3",
                note = "بيانات قبل الاستعادة"
            )
            assertEquals("OLD-RESTORE-001", database.productDao().getAll().single().sku)

            val productId = "restored-product-001"
            val root = JSONObject()
                .put("format", "good-girl-accounting-backup")
                .put("formatVersion", 5)
                .put("applicationId", "com.goodgirl.accounting")
                .put("databaseVersion", ACCOUNTING_DATABASE_VERSION)
                .put("exportedAt", 1_700_000_000_000L)
                .put("settings", JSONObject()
                    .put("usdSypRate", "15000")
                    .put("usdSypRateUpdatedAt", 1_700_000_000_000L)
                    .put("usdSypRateHistory", JSONArray()))
                .put("categories", JSONArray())
                .put("products", JSONArray().put(
                    JSONObject()
                        .put("id", productId)
                        .put("sku", "RESTORE-001")
                        .put("name", "مادة مستعادة")
                        .put("categoryId", JSONObject.NULL)
                        .put("baseUnit", "قطعة")
                        .put("purchaseCost", "2.50")
                        .put("salePrice", "4.00")
                        .put("priceCurrencyCode", "SYP")
                        .put("stockQuantity", "7")
                        .put("lowStockThreshold", "1")
                        .put("note", "سجل من النسخة المشفرة")
                        .put("isActive", true)
                        .put("createdAt", 100L)
                        .put("updatedAt", 200L)
                ))
                .put("productBarcodes", JSONArray().put(
                    JSONObject()
                        .put("id", "restored-barcode-001")
                        .put("productId", productId)
                        .put("barcodeValue", "6291234567890")
                        .put("format", "EAN_13")
                        .put("origin", "ORIGINAL")
                        .put("createdAt", 150L)
                ))
                .put("productUnits", JSONArray().put(
                    JSONObject()
                        .put("id", "restored-unit-001")
                        .put("productId", productId)
                        .put("unitName", "قطعة")
                        .put("baseFactor", "1")
                        .put("isBaseUnit", true)
                ))
                .put("parties", JSONArray())
                .put("invoices", JSONArray())
                .put("invoiceLines", JSONArray())
                .put("stockMovements", JSONArray().put(
                    JSONObject()
                        .put("id", "restored-movement-001")
                        .put("productId", productId)
                        .put("quantityDelta", "7")
                        .put("unitCostSnapshot", "2.50")
                        .put("currencyCode", "SYP")
                        .put("sourceType", "OPENING_STOCK")
                        .put("sourceId", productId)
                        .put("occurredAt", 100L)
                        .put("note", "رصيد افتتاحي مستعاد")
                ))
                .put("ledgerEntries", JSONArray())
                .put("accounts", JSONArray()
                    .put(JSONObject()
                        .put("id", "restored-account-cash")
                        .put("code", "1000")
                        .put("name", "الصندوق")
                        .put("accountType", "ASSET")
                        .put("normalSide", "DEBIT")
                        .put("isSystem", true)
                        .put("isActive", true)
                        .put("createdAt", 100L))
                    .put(JSONObject()
                        .put("id", "restored-account-sales")
                        .put("code", "4000")
                        .put("name", "المبيعات")
                        .put("accountType", "REVENUE")
                        .put("normalSide", "CREDIT")
                        .put("isSystem", true)
                        .put("isActive", true)
                        .put("createdAt", 100L)))
                .put("journalEntries", JSONArray().put(
                    JSONObject()
                        .put("id", "restored-journal-001")
                        .put("journalNumber", "RESTORE-J-001")
                        .put("sourceType", "MANUAL_JOURNAL")
                        .put("sourceId", "restore-fixture-001")
                        .put("currencyCode", "SYP")
                        .put("exchangeRateSnapshot", "1")
                        .put("occurredAt", 150L)
                        .put("description", "قيد تجريبي مستعاد")
                        .put("createdAt", 160L)))
                .put("journalLines", JSONArray()
                    .put(JSONObject()
                        .put("id", "restored-journal-line-001")
                        .put("journalEntryId", "restored-journal-001")
                        .put("lineNumber", 1)
                        .put("accountId", "restored-account-cash")
                        .put("debit", "10")
                        .put("credit", "0")
                        .put("memo", "مدين الصندوق"))
                    .put(JSONObject()
                        .put("id", "restored-journal-line-002")
                        .put("journalEntryId", "restored-journal-001")
                        .put("lineNumber", 2)
                        .put("accountId", "restored-account-sales")
                        .put("debit", "0")
                        .put("credit", "10")
                        .put("memo", "دائن المبيعات")))
                .put("exchangeRateHistory", JSONArray().put(
                    JSONObject()
                        .put("id", "restored-rate-001")
                        .put("currencyPair", "USD/SYP")
                        .put("rate", "15000")
                        .put("effectiveAt", 100L)
                        .put("recordedAt", 100L)
                        .put("source", "USER")
                        .put("isArchived", false)
                ))
                .put("exchangeRateAudits", JSONArray().put(
                    JSONObject()
                        .put("id", "restored-rate-audit-001")
                        .put("historyId", "restored-rate-001")
                        .put("eventType", "RATE_CORRECTION")
                        .put("oldRate", "14500")
                        .put("newRate", "15000")
                        .put("reason", "تصحيح تجريبي")
                        .put("correctedAt", 200L)
                        .put("affectedInvoiceCount", 1)
                        .put("affectedLedgerCount", 0)
                        .put("affectedJournalCount", 0)
                        .put("financialAmountsChanged", false)
                ))
                .put("exchangeRateCorrectionItems", JSONArray().put(
                    JSONObject()
                        .put("id", "restored-rate-item-001")
                        .put("auditId", "restored-rate-audit-001")
                        .put("recordType", "INVOICE")
                        .put("recordId", "invoice-1")
                        .put("sourceType", "SALE_INVOICE")
                        .put("sourceId", "invoice-1")
                        .put("oldRate", "14500")
                        .put("newRate", "15000")
                ))

            val plaintext = root.toString().toByteArray(Charsets.UTF_8)
            val encrypted = try {
                EncryptedBackupCodec.encrypt(plaintext, password)
            } finally {
                plaintext.fill(0)
            }
            try {
                backupFile.writeBytes(encrypted)
            } finally {
                encrypted.fill(0)
            }

            val restorePassword = "Restore-RoundTrip-Password".toCharArray()
            val result = AccountingBackupManager(context, database)
                .restoreBackup(android.net.Uri.fromFile(backupFile), restorePassword)

            assertEquals(12, result.recordCount)
            assertTrue(restorePassword.all { it == '\u0000' })

            val products = database.productDao().getAll()
            assertEquals(1, products.size)
            val restored = products.single()
            assertEquals(productId, restored.id)
            assertEquals("RESTORE-001", restored.sku)
            assertEquals("مادة مستعادة", restored.name)
            assertEquals("7", restored.stockQuantity)
            assertEquals("2.50", restored.purchaseCost)

            val restoredBarcodes = database.productBarcodeDao().getAll()
            assertEquals(1, restoredBarcodes.size)
            assertEquals(productId, restoredBarcodes.single().productId)
            assertEquals("6291234567890", restoredBarcodes.single().barcodeValue)

            val units = database.productUnitDao().getAll()
            assertEquals(1, units.size)
            assertEquals(productId, units.single().productId)
            assertEquals("قطعة", units.single().unitName)

            val movements = database.stockMovementDao().getAll()
            assertEquals(1, movements.size)
            assertEquals(productId, movements.single().productId)
            assertEquals("7", movements.single().quantityDelta)

            assertNotNull(database.productDao().findById(productId))
            assertTrue(products.none { it.sku == "OLD-RESTORE-001" })

            val accounts = database.accountDao().getAll()
            assertEquals(2, accounts.size)
            assertEquals(setOf("restored-account-cash", "restored-account-sales"), accounts.map { it.id }.toSet())

            val journals = database.journalEntryDao().getAll()
            assertEquals(1, journals.size)
            assertEquals("RESTORE-J-001", journals.single().journalNumber)

            val journalLines = database.journalLineDao().getAll()
            assertEquals(2, journalLines.size)
            assertEquals(setOf("restored-account-cash", "restored-account-sales"), journalLines.map { it.accountId }.toSet())
            assertEquals("restored-journal-001", journalLines.first().journalEntryId)

            val restoredRates = database.exchangeRateHistoryDao().getAll()
            assertEquals(1, restoredRates.size)
            assertEquals("15000", restoredRates.single().rate)
            val restoredRateAudits = database.exchangeRateAuditDao().getAll()
            assertEquals(1, restoredRateAudits.size)
            assertEquals("14500", restoredRateAudits.single().oldRate)
            val restoredCorrectionItems = database.exchangeRateCorrectionItemDao().getAll()
            assertEquals(1, restoredCorrectionItems.size)
            assertEquals("invoice-1", restoredCorrectionItems.single().recordId)
        } finally {
            password.fill('\u0000')
            backupFile.delete()
            database.close()
        }
    }
}
