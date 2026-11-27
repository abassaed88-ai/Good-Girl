package com.goodgirl.accounting.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.InvoiceLineDraft
import com.goodgirl.accounting.data.InvoiceRepository
import com.goodgirl.accounting.data.ProductRepository
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LatestPurchaseCostPolicyMigrationIntegrationTest {

    @Test
    fun roomVersionTenUpgradeAppliesLatestPurchaseCostMigrationAtomically() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "latest-purchase-cost-v10-${System.nanoTime()}.db"
        val databaseFile = context.getDatabasePath(databaseName)
        val setupDatabase = Room.databaseBuilder(
            context,
            AccountingDatabase::class.java,
            databaseName
        ).allowMainThreadQueries().build()
        try {

            val products = ProductRepository(setupDatabase)
            val invoices = InvoiceRepository(setupDatabase)
            products.addProduct(
                sku = "ROOM-V10-COST-001",
                name = "ترقية قاعدة قديمة",
                unit = "مل",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "10",
                note = "تجهيز بيانات إصدار 10 للاختبار"
            )
            val product = setupDatabase.productDao().getAll().single()
            val baselineAt = setupDatabase.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            val sale = invoices.postInvoice(
                invoiceNumber = "ROOM-V10-SALE",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "2", "15")),
                paidAmount = "30",
                issuedAt = baselineAt + 1_000L
            )
            val saleLineBefore = setupDatabase.invoiceDao().getLines(sale.invoiceId).single()
            val saleJournal = setupDatabase.journalEntryDao().findBySource("SALE_INVOICE", sale.invoiceId)!!
            val saleJournalLinesBefore = setupDatabase.journalLineDao().getForEntry(saleJournal.id)

            val purchase = invoices.postInvoice(
                invoiceNumber = "ROOM-V10-PURCHASE",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "8", "20")),
                paidAmount = "160",
                issuedAt = baselineAt + 2_000L
            )
            assertDecimal("16", setupDatabase.productDao().findById(product.id)!!.stockQuantity)

            // Recreate the v10 persisted state: a weighted-average current
            // product cost, with no v11 stock-revaluation journal or report row.
            setupDatabase.openHelper.writableDatabase.execSQL(
                "DELETE FROM journal_lines WHERE journalEntryId IN " +
                    "(SELECT id FROM journal_entries WHERE sourceType = 'PURCHASE_INVENTORY_REVALUATION' AND sourceId = ?)",
                arrayOf(purchase.invoiceId)
            )
            setupDatabase.openHelper.writableDatabase.execSQL(
                "DELETE FROM journal_entries WHERE sourceType = 'PURCHASE_INVENTORY_REVALUATION' AND sourceId = ?",
                arrayOf(purchase.invoiceId)
            )
            setupDatabase.ledgerDao().getAll()
                .filter { it.sourceId == purchase.invoiceId && it.sourceType.startsWith("PURCHASE_INVENTORY_REVALUATION_") }
                .forEach { setupDatabase.ledgerDao().deleteById(it.id) }
            val legacyProduct = setupDatabase.productDao().findById(product.id)!!
            setupDatabase.productDao().update(legacyProduct.copy(purchaseCost = "15"))
            setupDatabase.close()

            // The v11 schema is unchanged from v10; downgrade only the
            // SQLite user_version so Room executes the registered 10 -> 11 data migration.
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { it.version = 10 }

            val upgraded = Room.databaseBuilder(
                context,
                AccountingDatabase::class.java,
                databaseName
            ).addMigrations(AccountingDatabase.MIGRATION_10_11)
                .allowMainThreadQueries()
                .build()
            try {
                val updatedProduct = upgraded.productDao().findById(product.id)!!
                assertDecimal("20", updatedProduct.purchaseCost)
                assertDecimal("16", updatedProduct.stockQuantity)
                assertDecimal("10", upgraded.invoiceDao().getLines(sale.invoiceId).single().unitCostSnapshot)
                assertEquals(saleLineBefore, upgraded.invoiceDao().getLines(sale.invoiceId).single())
                assertEquals(saleJournalLinesBefore, upgraded.journalLineDao().getForEntry(saleJournal.id))

                val migrationJournal = upgraded.journalEntryDao()
                    .findBySource("MIGRATION_LATEST_PURCHASE_COST", product.id)!!
                val accounts = upgraded.accountDao().getAll().associate { it.id to it.code }
                val migrationLines = upgraded.journalLineDao().getForEntry(migrationJournal.id)
                assertDecimal("80", migrationLines.single { accounts[it.accountId] == "1200" }.debit)
                assertDecimal("80", migrationLines.single { accounts[it.accountId] == "4100" }.credit)
                val migrationLedger = upgraded.ledgerDao().getAll().single {
                    it.sourceType == "MIGRATION_STOCK_REVALUATION_GAIN" && it.sourceId == product.id
                }
                assertEquals("INCOME", migrationLedger.entryType)
                assertDecimal("80", migrationLedger.amount)

                upgraded.close()
                val reopened = Room.databaseBuilder(
                    context,
                    AccountingDatabase::class.java,
                    databaseName
                ).addMigrations(AccountingDatabase.MIGRATION_10_11)
                    .allowMainThreadQueries()
                    .build()
                try {
                    reopened.openHelper.writableDatabase
                    assertEquals(
                        1,
                        reopened.journalEntryDao().getAll().count {
                            it.sourceType == "MIGRATION_LATEST_PURCHASE_COST" && it.sourceId == product.id
                        }
                    )
                    assertDecimal("20", reopened.productDao().findById(product.id)!!.purchaseCost)
                } finally {
                    reopened.close()
                }
            } catch (error: Throwable) {
                runCatching { upgraded.close() }
                throw error
            }
        } finally {
            runCatching { setupDatabase.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migratesLegacyWeightedAverageToLatestPurchaseCostWithoutRewritingHistoricalSales() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)
            products.addProduct(
                sku = "LEGACY-COST-001",
                name = "مادة بمتوسط قديم",
                unit = "مل",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "10",
                note = "محاكاة قاعدة بيانات قبل سياسة أحدث شراء"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            val sale = invoices.postInvoice(
                invoiceNumber = "LEGACY-COST-SALE",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "2", "15")),
                paidAmount = "30",
                issuedAt = baselineAt + 1_000L
            )
            val saleLineBefore = database.invoiceDao().getLines(sale.invoiceId).single()
            assertDecimal("10", saleLineBefore.unitCostSnapshot)
            val saleJournal = database.journalEntryDao().findBySource("SALE_INVOICE", sale.invoiceId)!!
            val saleJournalLinesBefore = database.journalLineDao().getForEntry(saleJournal.id)

            val purchase = invoices.postInvoice(
                invoiceNumber = "LEGACY-COST-PURCHASE",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "8", "20")),
                paidAmount = "160",
                issuedAt = baselineAt + 2_000L
            )
            assertDecimal("16", database.productDao().findById(product.id)!!.stockQuantity)

            // Simulate the v10 state: product.purchaseCost held the weighted
            // average ($15), and no purchase-triggered revaluation journal or
            // report ledger row had been recorded.
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM journal_lines WHERE journalEntryId IN " +
                    "(SELECT id FROM journal_entries WHERE sourceType = 'PURCHASE_INVENTORY_REVALUATION' AND sourceId = ?)",
                arrayOf(purchase.invoiceId)
            )
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM journal_entries WHERE sourceType = 'PURCHASE_INVENTORY_REVALUATION' AND sourceId = ?",
                arrayOf(purchase.invoiceId)
            )
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM ledger_entries WHERE sourceId = ? AND sourceType IN " +
                    "('PURCHASE_INVENTORY_REVALUATION_GAIN', 'PURCHASE_INVENTORY_REVALUATION_LOSS')",
                arrayOf(purchase.invoiceId)
            )
            val legacyProduct = database.productDao().findById(product.id)!!
            database.productDao().update(legacyProduct.copy(purchaseCost = "15"))

            val appliedAt = System.currentTimeMillis() + 60_000L
            database.withTransaction {
                LatestPurchaseCostPolicyMigrator.migrate(
                    database.openHelper.writableDatabase,
                    appliedAt
                )
            }

            val upgradedProduct = database.productDao().findById(product.id)!!
            assertDecimal("20", upgradedProduct.purchaseCost)
            assertDecimal("16", upgradedProduct.stockQuantity)

            val saleLineAfter = database.invoiceDao().getLines(sale.invoiceId).single()
            assertDecimal("10", saleLineAfter.unitCostSnapshot)
            assertEquals(
                saleJournalLinesBefore,
                database.journalLineDao().getForEntry(saleJournal.id)
            )

            val migrationJournal = database.journalEntryDao()
                .findBySource("MIGRATION_LATEST_PURCHASE_COST", product.id)
            assertNotNull(migrationJournal)
            assertEquals(appliedAt, migrationJournal!!.occurredAt)
            val accounts = database.accountDao().getAll().associate { it.id to it.code }
            val migrationLines = database.journalLineDao().getForEntry(migrationJournal.id)
            assertDecimal(
                "80",
                migrationLines.single { accounts[it.accountId] == "1200" }.debit
            )
            assertDecimal(
                "80",
                migrationLines.single { accounts[it.accountId] == "4100" }.credit
            )

            val migrationLedger = database.ledgerDao().getAll().single {
                it.sourceType == "MIGRATION_STOCK_REVALUATION_GAIN" && it.sourceId == product.id
            }
            assertEquals("INCOME", migrationLedger.entryType)
            assertDecimal("80", migrationLedger.amount)

            // Applying the conversion again must be harmless and must not
            // create a second valuation journal or duplicate income row.
            database.withTransaction {
                LatestPurchaseCostPolicyMigrator.migrate(
                    database.openHelper.writableDatabase,
                    appliedAt + 1_000L
                )
            }
            assertEquals(
                1,
                database.journalEntryDao().getAll().count {
                    it.sourceType == "MIGRATION_LATEST_PURCHASE_COST" && it.sourceId == product.id
                }
            )
            assertEquals(
                1,
                database.ledgerDao().getAll().count {
                    it.sourceType == "MIGRATION_STOCK_REVALUATION_GAIN" && it.sourceId == product.id
                }
            )
            assertDecimal("20", database.productDao().findById(product.id)!!.purchaseCost)
        } finally {
            database.close()
        }
    }

    private fun newInMemoryDatabase(): AccountingDatabase {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, AccountingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun assertDecimal(expected: String, actual: String) {
        assertEquals(0, BigDecimal(expected).compareTo(BigDecimal(actual)))
    }
}
