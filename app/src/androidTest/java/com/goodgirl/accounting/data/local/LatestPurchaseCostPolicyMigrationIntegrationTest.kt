package com.goodgirl.accounting.data.local

import android.content.Context
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
