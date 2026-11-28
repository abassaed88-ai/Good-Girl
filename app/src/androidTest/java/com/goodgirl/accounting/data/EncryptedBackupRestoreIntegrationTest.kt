package com.goodgirl.accounting.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.DailySalesDraftEntity
import com.goodgirl.accounting.data.local.DailySalesDraftLineEntity
import com.goodgirl.accounting.data.local.DailySalesDraftExpenseEntity
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
                .put("formatVersion", 6)
                .put("applicationId", "com.goodgirl.accounting")
                .put("databaseVersion", 10)
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
                        .put("purchaseCost", "2.86363636")
                        .put("salePrice", "4.00")
                        .put("priceCurrencyCode", "SYP")
                        .put("stockQuantity", "11")
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
                .put("invoices", JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "restored-invoice-001")
                            .put("invoiceNumber", "RESTORE-S-001")
                            .put("kind", "SALE")
                            .put("partyId", JSONObject.NULL)
                            .put("currencyCode", "SYP")
                            .put("exchangeRateSnapshot", "1")
                            .put("subtotal", "8.00")
                            .put("discount", "0")
                            .put("total", "8.00")
                            .put("paid", "8.00")
                            .put("remaining", "0")
                            .put("status", "POSTED")
                            .put("issuedAt", 250L)
                            .put("note", "اختبار استعادة")
                            .put("createdAt", 260L)
                    )
                    .put(
                        JSONObject()
                            .put("id", "restored-purchase-invoice-001")
                            .put("invoiceNumber", "RESTORE-P-001")
                            .put("kind", "PURCHASE")
                            .put("partyId", JSONObject.NULL)
                            .put("currencyCode", "SYP")
                            .put("exchangeRateSnapshot", "1")
                            .put("subtotal", "14.00")
                            .put("discount", "0")
                            .put("total", "14.00")
                            .put("paid", "14.00")
                            .put("remaining", "0")
                            .put("status", "POSTED")
                            .put("issuedAt", 400L)
                            .put("note", "آخر فاتورة شراء في النسخة القديمة")
                            .put("createdAt", 410L)
                    )
                )
                .put("invoiceLines", JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "restored-invoice-line-001")
                            .put("invoiceId", "restored-invoice-001")
                            .put("productId", productId)
                            .put("productNameSnapshot", "مادة مستعادة")
                            .put("unitSnapshot", "قطعة")
                            .put("quantity", "2")
                            .put("unitCostSnapshot", "2.50")
                            .put("unitPriceSnapshot", "4.00")
                            .put("discount", "0")
                            .put("lineTotal", "8.00")
                            .put("unitFactorSnapshot", "1")
                            .put("baseQuantity", "2")
                    )
                    .put(
                        JSONObject()
                            .put("id", "restored-purchase-line-001")
                            .put("invoiceId", "restored-purchase-invoice-001")
                            .put("productId", productId)
                            .put("productNameSnapshot", "مادة مستعادة")
                            .put("unitSnapshot", "قطعة")
                            .put("quantity", "4")
                            .put("unitCostSnapshot", "3.50")
                            .put("unitPriceSnapshot", "3.50")
                            .put("discount", "0")
                            .put("lineTotal", "14.00")
                            .put("unitFactorSnapshot", "1")
                            .put("baseQuantity", "4")
                    )
                )
                .put("stockMovements", JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "restored-movement-001")
                            .put("productId", productId)
                            .put("quantityDelta", "9")
                            .put("unitCostSnapshot", "2.50")
                            .put("currencyCode", "SYP")
                            .put("sourceType", "OPENING_STOCK")
                            .put("sourceId", productId)
                            .put("occurredAt", 100L)
                            .put("recordedAt", 100L)
                            .put("note", "رصيد افتتاحي مستعاد")
                    )
                    .put(
                        JSONObject()
                            .put("id", "restored-sale-movement-001")
                            .put("productId", productId)
                            .put("quantityDelta", "-2")
                            .put("unitCostSnapshot", "2.50")
                            .put("currencyCode", "SYP")
                            .put("sourceType", "SALE_INVOICE")
                            .put("sourceId", "restored-invoice-line-001")
                            .put("occurredAt", 300L)
                            .put("recordedAt", 300L)
                            .put("note", "بيع مستعاد")
                    )
                    .put(
                        JSONObject()
                            .put("id", "restored-purchase-movement-001")
                            .put("productId", productId)
                            .put("quantityDelta", "4")
                            .put("unitCostSnapshot", "3.50")
                            .put("currencyCode", "SYP")
                            .put("sourceType", "PURCHASE_INVOICE")
                            .put("sourceId", "restored-purchase-line-001")
                            .put("occurredAt", 400L)
                            .put("recordedAt", 410L)
                            .put("note", "فاتورة الشراء الأخيرة")
                    )
                )
                .put("stockCostBaselines", JSONArray().put(
                    JSONObject()
                        .put("productId", productId)
                        .put("quantity", "0")
                        .put("averageCost", "0")
                        .put("currencyCode", "SYP")
                        .put("effectiveAt", 99L)
                        .put("recordedAt", 99L)
                        .put("source", "PRODUCT_ZERO_OPENING")
                ))
                .put("stockCostRecalculationAudits", JSONArray().put(
                    JSONObject()
                        .put("id", "restored-cost-audit-001")
                        .put("productId", productId)
                        .put("triggerType", "BACKDATED_PURCHASE")
                        .put("triggerSourceId", "restored-purchase-001")
                        .put("reason", "تصحيح كلفة تاريخية ضمن اختبار النسخ")
                        .put("createdAt", 400L)
                        .put("currencyCode", "SYP")
                        .put("oldEndingQuantity", "7")
                        .put("newEndingQuantity", "7")
                        .put("oldEndingAverageCost", "2.50")
                        .put("newEndingAverageCost", "2.75")
                        .put("affectedStockOutCount", 1)
                ))
                .put("stockCostRecalculationItems", JSONArray().put(
                    JSONObject()
                        .put("id", "restored-cost-item-001")
                        .put("auditId", "restored-cost-audit-001")
                        .put("movementId", "restored-sale-movement-001")
                        .put("sourceType", "SALE_INVOICE")
                        .put("sourceId", "restored-invoice-line-001")
                        .put("invoiceId", "restored-invoice-001")
                        .put("currencyCode", "SYP")
                        .put("quantity", "2")
                        .put("oldUnitCost", "2.50")
                        .put("newUnitCost", "2.75")
                        .put("oldExtendedCost", "5.00")
                        .put("newExtendedCost", "5.50")
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
                        .put("createdAt", 100L))
                    .put(JSONObject()
                        .put("id", "restored-account-retained")
                        .put("code", "3100")
                        .put("name", "الأرباح المحتجزة")
                        .put("accountType", "EQUITY")
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
                        .put("createdAt", 160L))
                    .put(JSONObject()
                        .put("id", "restored-period-close-001")
                        .put("journalNumber", "CLOSE-RESTORE-001")
                        .put("sourceType", "PERIOD_CLOSE")
                        .put("sourceId", "SYP:1000:2000")
                        .put("currencyCode", "SYP")
                        .put("exchangeRateSnapshot", "1")
                        .put("occurredAt", 2000L)
                        .put("description", "إقفال فترة مستعادة — اختبار")
                        .put("createdAt", 2001L))
                    .put(JSONObject()
                        .put("id", "restored-period-close-reversal-001")
                        .put("journalNumber", "REOPEN-RESTORE-001")
                        .put("sourceType", "PERIOD_CLOSE_REVERSAL")
                        .put("sourceId", "restored-period-close-001")
                        .put("currencyCode", "SYP")
                        .put("exchangeRateSnapshot", "1")
                        .put("occurredAt", 2_100L)
                        .put("description", "عكس الإقفال — اختبار")
                        .put("createdAt", 2_101L)))
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
                        .put("memo", "دائن المبيعات"))
                    .put(JSONObject()
                        .put("id", "restored-period-close-line-001")
                        .put("journalEntryId", "restored-period-close-001")
                        .put("lineNumber", 1)
                        .put("accountId", "restored-account-sales")
                        .put("debit", "10")
                        .put("credit", "0")
                        .put("memo", "إقفال إيراد الفترة"))
                    .put(JSONObject()
                        .put("id", "restored-period-close-line-002")
                        .put("journalEntryId", "restored-period-close-001")
                        .put("lineNumber", 2)
                        .put("accountId", "restored-account-retained")
                        .put("debit", "0")
                        .put("credit", "10")
                        .put("memo", "ترحيل إلى الأرباح المحتجزة"))
                    .put(JSONObject()
                        .put("id", "restored-period-close-reversal-line-001")
                        .put("journalEntryId", "restored-period-close-reversal-001")
                        .put("lineNumber", 1)
                        .put("accountId", "restored-account-sales")
                        .put("debit", "0")
                        .put("credit", "10")
                        .put("memo", "عكس إقفال إيراد الفترة"))
                    .put(JSONObject()
                        .put("id", "restored-period-close-reversal-line-002")
                        .put("journalEntryId", "restored-period-close-reversal-001")
                        .put("lineNumber", 2)
                        .put("accountId", "restored-account-retained")
                        .put("debit", "10")
                        .put("credit", "0")
                        .put("memo", "عكس ترحيل الأرباح المحتجزة")))
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

            assertEquals(28, result.recordCount)
            assertTrue(restorePassword.all { it == '\u0000' })

            val products = database.productDao().getAll()
            assertEquals(1, products.size)
            val restored = products.single()
            assertEquals(productId, restored.id)
            assertEquals("RESTORE-001", restored.sku)
            assertEquals("مادة مستعادة", restored.name)
            assertEquals("11", restored.stockQuantity)
            assertEquals(
                0,
                java.math.BigDecimal("3.50").compareTo(java.math.BigDecimal(restored.purchaseCost))
            )
            assertEquals(
                "2.50",
                database.invoiceDao().getLines("restored-invoice-001").single().unitCostSnapshot
            )
            val migrationIncome = database.ledgerDao().getAll().single {
                it.sourceType == "MIGRATION_STOCK_REVALUATION_GAIN" && it.sourceId == productId
            }
            assertEquals("INCOME", migrationIncome.entryType)
            assertEquals("7.00000004", migrationIncome.amount)

            val restoredBarcodes = database.productBarcodeDao().getAll()
            assertEquals(1, restoredBarcodes.size)
            assertEquals(productId, restoredBarcodes.single().productId)
            assertEquals("6291234567890", restoredBarcodes.single().barcodeValue)

            val units = database.productUnitDao().getAll()
            assertEquals(1, units.size)
            assertEquals(productId, units.single().productId)
            assertEquals("قطعة", units.single().unitName)

            val movements = database.stockMovementDao().getAll()
            assertEquals(3, movements.size)
            val openingMovement = movements.single { it.id == "restored-movement-001" }
            assertEquals(productId, openingMovement.productId)
            assertEquals("9", openingMovement.quantityDelta)
            assertEquals(100L, openingMovement.recordedAt)
            val saleMovement = movements.single { it.id == "restored-sale-movement-001" }
            assertEquals("-2", saleMovement.quantityDelta)
            assertEquals(300L, saleMovement.recordedAt)
            val purchaseMovement = movements.single { it.id == "restored-purchase-movement-001" }
            assertEquals("4", purchaseMovement.quantityDelta)
            assertEquals("3.50", purchaseMovement.unitCostSnapshot)

            val restoredBaselines = database.stockCostBaselineDao().getAll()
            assertEquals(1, restoredBaselines.size)
            assertEquals(productId, restoredBaselines.single().productId)
            assertEquals("PRODUCT_ZERO_OPENING", restoredBaselines.single().source)
            assertEquals("0", restoredBaselines.single().quantity)
            assertEquals(99L, restoredBaselines.single().recordedAt)

            val restoredCostAudits = database.stockCostRecalculationAuditDao().getAll()
            assertEquals(1, restoredCostAudits.size)
            assertEquals("restored-cost-audit-001", restoredCostAudits.single().id)
            assertEquals(1, restoredCostAudits.single().affectedStockOutCount)
            val restoredCostItems = database.stockCostRecalculationItemDao().getAll()
            assertEquals(1, restoredCostItems.size)
            assertEquals("restored-sale-movement-001", restoredCostItems.single().movementId)
            assertEquals("2.75", restoredCostItems.single().newUnitCost)

            assertNotNull(database.productDao().findById(productId))
            assertTrue(products.none { it.sku == "OLD-RESTORE-001" })

            val accounts = database.accountDao().getAll()
            assertEquals(5, accounts.size)
            assertTrue(accounts.any { it.id == "SYS-1200" && it.code == "1200" })
            assertTrue(accounts.any { it.id == "SYS-4100" && it.code == "4100" })
            assertTrue(accounts.any { it.id == "restored-account-cash" })
            assertTrue(accounts.any { it.id == "restored-account-sales" })
            assertTrue(accounts.any { it.id == "restored-account-retained" })

            val journals = database.journalEntryDao().getAll()
            assertEquals(4, journals.size)
            assertEquals("RESTORE-J-001", journals.single { it.id == "restored-journal-001" }.journalNumber)
            val migrationRevaluation = journals.single {
                it.sourceType == "MIGRATION_LATEST_PURCHASE_COST" && it.sourceId == productId
            }
            assertTrue(migrationRevaluation.occurredAt > 410L)
            val restoredClose = journals.single { it.id == "restored-period-close-001" }
            assertEquals("PERIOD_CLOSE", restoredClose.sourceType)
            val restoredReopening = journals.single { it.id == "restored-period-close-reversal-001" }
            assertEquals("PERIOD_CLOSE_REVERSAL", restoredReopening.sourceType)
            assertEquals(restoredClose.id, restoredReopening.sourceId)
            assertTrue(!PeriodClosingRepository.isClosedDate(journals, "SYP", 1_500L))
            assertTrue(PeriodClosingRepository.activeClosures(journals).isEmpty())

            val journalLines = database.journalLineDao().getAll()
            assertEquals(8, journalLines.size)
            assertTrue(journalLines.any { it.accountId == "SYS-1200" && it.debit == "7.00000004" })
            assertTrue(journalLines.any { it.accountId == "SYS-4100" && it.credit == "7.00000004" })
            assertTrue(journalLines.any { it.accountId == "restored-account-cash" })
            assertTrue(journalLines.any { it.accountId == "restored-account-sales" })
            assertTrue(journalLines.any { it.accountId == "restored-account-retained" })
            assertEquals(2, journalLines.count { it.journalEntryId == "restored-journal-001" })
            assertEquals(2, journalLines.count { it.journalEntryId == migrationRevaluation.id })
            assertEquals(2, journalLines.count { it.journalEntryId == "restored-period-close-001" })
            assertEquals(2, journalLines.count { it.journalEntryId == "restored-period-close-reversal-001" })

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

    @Test
    fun encryptedBackupRoundTripPreservesDailySalesDraftExpenses() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            AccountingDatabase::class.java
        ).allowMainThreadQueries().build()
        val backupFile = File.createTempFile("good-girl-draft-expenses-", ".ggbackup", context.cacheDir)
        try {
            val header = DailySalesDraftEntity(
                selectedDay = 1_800_000_000_000L,
                selectedProductId = null,
                selectedProductNameSnapshot = "",
                selectedUnitName = null,
                productSearchQuery = "",
                selectedProductCategoryId = null,
                quantity = "1",
                salePrice = "0",
                lineDiscount = "0",
                currencyCode = "SYP",
                exchangeRate = "1",
                paidAmount = "0",
                paidAmountTouched = false,
                selectedPartyId = null,
                note = "",
                updatedAt = 1_800_000_000_100L
            )
            val line = DailySalesDraftLineEntity(
                id = "backup-draft-line-001",
                draftId = DailySalesDraftRepository.ACTIVE_DRAFT_ID,
                lineOrder = 0,
                productId = "backup-product-not-yet-posted",
                productNameSnapshot = "مادة مسودة النسخة الاحتياطية",
                quantity = "2",
                unitName = "قطعة",
                unitFactor = "1",
                unitPrice = "10",
                purchaseCostSnapshot = "4",
                discount = "0",
                currencyCode = "SYP",
                exchangeRate = "1",
                paidAmount = "20",
                customerId = null,
                note = "سطر محفوظ",
                issuedAt = 1_800_000_000_100L
            )
            val expense = DailySalesDraftExpenseEntity(
                id = "backup-draft-expense-001",
                draftId = DailySalesDraftRepository.ACTIVE_DRAFT_ID,
                lineId = line.id,
                description = "مصروف توصيل",
                amount = "2.5",
                currencyCode = "USD",
                exchangeRateSnapshot = "15000",
                createdAt = 1_800_000_000_101L
            )
            val drafts = DailySalesDraftRepository(database)
            drafts.saveSnapshot(header, listOf(line), listOf(expense))

            val exportPassword = "Draft-Expense-Backup-Password".toCharArray()
            AccountingBackupManager(context, database).exportBackup(
                android.net.Uri.fromFile(backupFile),
                exportPassword
            )
            assertTrue(exportPassword.all { it == '\u0000' })

            drafts.clearDraft()
            assertTrue(drafts.loadSnapshot().lines.isEmpty())

            val restorePassword = "Draft-Expense-Backup-Password".toCharArray()
            AccountingBackupManager(context, database).restoreBackup(
                android.net.Uri.fromFile(backupFile),
                restorePassword
            )
            assertTrue(restorePassword.all { it == '\u0000' })

            val restored = drafts.loadSnapshot()
            assertEquals(1, restored.lines.size)
            assertEquals("backup-draft-line-001", restored.lines.single().id)
            assertEquals("4", restored.lines.single().purchaseCostSnapshot)
            assertEquals(1, restored.expenses.size)
            assertEquals("backup-draft-expense-001", restored.expenses.single().id)
            assertEquals(line.id, restored.expenses.single().lineId)
            assertEquals("مصروف توصيل", restored.expenses.single().description)
            assertEquals("2.5", restored.expenses.single().amount)
            assertEquals("USD", restored.expenses.single().currencyCode)
            assertEquals("15000", restored.expenses.single().exchangeRateSnapshot)
        } finally {
            backupFile.delete()
            database.close()
        }
    }
}
