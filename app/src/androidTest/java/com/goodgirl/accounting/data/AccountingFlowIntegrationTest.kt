package com.goodgirl.accounting.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.domain.FinancialReportCalculator
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises invoice posting against a real in-memory SQLite/Room database.
 * It protects the historical-cost rule: a later purchase may change the
 * current weighted-average product cost, but must not rewrite previous sales.
 */
@RunWith(AndroidJUnit4::class)
class AccountingFlowIntegrationTest {

    @Test
    fun weightedAveragePurchaseDoesNotRewriteHistoricalSaleCostOrProfit() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)

            products.addProduct(
                sku = "FLOW-001",
                name = "مادة اختبار",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "10",
                note = "اختبار تكامل"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            invoices.postInvoice(
                invoiceNumber = "FLOW-SALE-1",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "2", "15")),
                paidAmount = "30",
                issuedAt = baselineAt + 1_000L
            )

            val firstSaleLine = database.invoiceDao()
                .getLines(database.invoiceDao().getAllInvoices().single().id)
                .single()
            assertDecimal("10", firstSaleLine.unitCostSnapshot)
            assertDecimal("8", database.productDao().findById(product.id)!!.stockQuantity)

            invoices.postInvoice(
                invoiceNumber = "FLOW-PURCHASE-1",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "4", "20")),
                paidAmount = "80",
                issuedAt = baselineAt + 2_000L
            )

            assertDecimal("13.33333333", database.productDao().findById(product.id)!!.purchaseCost)
            assertDecimal("12", database.productDao().findById(product.id)!!.stockQuantity)
            assertDecimal(
                "10",
                database.invoiceDao().getLines(firstSaleLine.invoiceId).single().unitCostSnapshot
            )

            invoices.postInvoice(
                invoiceNumber = "FLOW-SALE-2",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "1", "15")),
                paidAmount = "15",
                issuedAt = baselineAt + 3_000L
            )

            val sales = database.invoiceDao().getAllInvoices()
                .filter { it.kind == "SALE" }
                .sortedBy { it.issuedAt }
            val saleLines = sales.flatMap { database.invoiceDao().getLines(it.id) }

            assertEquals(2, sales.size)
            assertEquals(2, saleLines.size)
            assertDecimal("10", saleLines[0].unitCostSnapshot)
            assertDecimal("13.33333333", saleLines[1].unitCostSnapshot)
            assertDecimal("11", database.productDao().findById(product.id)!!.stockQuantity)

            val report = FinancialReportCalculator.calculate(
                fromInclusive = 0L,
                toInclusive = Long.MAX_VALUE,
                invoices = database.invoiceDao().getAllInvoices(),
                lines = database.invoiceDao().getAllLines(),
                ledgerEntries = database.ledgerDao().getAll()
            )
            val summary = report.currencySummaries.single { it.currencyCode == "SYP" }
            assertDecimal("45", summary.sales.toPlainString())
            assertDecimal("33.33333333", summary.costOfGoodsSold.toPlainString())
            assertDecimal("11.66666667", summary.grossProfit.toPlainString())
            assertDecimal("11.66666667", report.products.single().profit.toPlainString())
        } finally {
            database.close()
        }
    }

    @Test
    fun changingSalePriceAffectsFutureSalesWithoutRewritingHistoryOrStock() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)

            products.addProduct(
                sku = "PRICE-001",
                name = "مادة تعديل السعر",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "10",
                note = "اختبار تحديث سعر البيع"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            invoices.postInvoice(
                invoiceNumber = "PRICE-SALE-1",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "1", "15")),
                paidAmount = "15",
                issuedAt = baselineAt + 1_000L
            )
            val firstInvoice = database.invoiceDao().getAllInvoices().single()

            assertTrue(products.updateSalePrice(product.id, "17.50"))

            val updatedProduct = database.productDao().findById(product.id)!!
            assertDecimal("17.5", updatedProduct.salePrice)
            assertDecimal("10", updatedProduct.purchaseCost)
            assertDecimal("9", updatedProduct.stockQuantity)

            val historicalLine = database.invoiceDao().getLines(firstInvoice.id).single()
            assertDecimal("15", historicalLine.unitPriceSnapshot)
            assertDecimal("10", historicalLine.unitCostSnapshot)

            invoices.postInvoice(
                invoiceNumber = "PRICE-SALE-2",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "1", updatedProduct.salePrice)),
                paidAmount = "17.5",
                issuedAt = baselineAt + 2_000L
            )

            val saleInvoices = database.invoiceDao().getAllInvoices().sortedBy { it.issuedAt }
            assertEquals(2, saleInvoices.size)
            val firstSaleLine = database.invoiceDao().getLines(saleInvoices[0].id).single()
            val secondSaleLine = database.invoiceDao().getLines(saleInvoices[1].id).single()
            assertDecimal("15", firstSaleLine.unitPriceSnapshot)
            assertDecimal("17.5", secondSaleLine.unitPriceSnapshot)
            assertDecimal("8", database.productDao().findById(product.id)!!.stockQuantity)
        } finally {
            database.close()
        }
    }

    @Test
    fun rejectedSaleRollsBackWithoutChangingStockOrCreatingInvoice() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)
            products.addProduct(
                sku = "FLOW-002",
                name = "مخزون محدود",
                unit = "قطعة",
                purchaseCost = "4",
                salePrice = "7",
                openingStock = "2",
                note = "اختبار رفض البيع"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    invoices.postInvoice(
                        invoiceNumber = "FLOW-SALE-INVALID",
                        kind = "SALE",
                        partyId = null,
                        currencyCode = "SYP",
                        exchangeRateSnapshot = "1",
                        lines = listOf(InvoiceLineDraft(product.id, "3", "7")),
                        paidAmount = "21",
                        issuedAt = baselineAt + 4_000L
                    )
                }
            }

            assertDecimal("2", database.productDao().findById(product.id)!!.stockQuantity)
            assertTrue(database.invoiceDao().getAllInvoices().isEmpty())
            assertEquals(1, database.stockMovementDao().getAll().size)
        } finally {
            database.close()
        }
    }


    @Test
    fun saleAndPurchaseInvoicesCreateBalancedAutomaticJournalEntries() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)
            products.addProduct(
                sku = "JOURNAL-001",
                name = "مادة القيود",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "5",
                note = "اختبار ترحيل القيود"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            val sale = invoices.postInvoice(
                invoiceNumber = "JOURNAL-SALE-1",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "2", "15")),
                paidAmount = "30",
                issuedAt = baselineAt + 10_000L
            )
            val purchase = invoices.postInvoice(
                invoiceNumber = "JOURNAL-PURCHASE-1",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "2", "20")),
                paidAmount = "40",
                issuedAt = baselineAt + 20_000L
            )

            val entries = database.journalEntryDao().getAll()
            assertEquals(3, entries.size)
            assertEquals(
                setOf("PRODUCT_OPENING_STOCK", "SALE_INVOICE", "PURCHASE_INVOICE"),
                entries.map { it.sourceType }.toSet()
            )
            val accounts = database.accountDao().getAll().associateBy { it.id }
            val openingEntry = entries.single { it.sourceType == "PRODUCT_OPENING_STOCK" }
            assertEquals(product.id, openingEntry.sourceId)
            val openingLines = database.journalLineDao().getForEntry(openingEntry.id)
            assertEquals(2, openingLines.size)
            assertDecimal("50", openingLines.single { accounts[it.accountId]!!.code == "1200" }.debit)
            assertDecimal("50", openingLines.single { accounts[it.accountId]!!.code == "3000" }.credit)
            assertEquals(setOf(sale.invoiceId, purchase.invoiceId), entries.filter { it.sourceType != "PRODUCT_OPENING_STOCK" }.map { it.sourceId }.toSet())
            val saleEntry = entries.single { it.sourceId == sale.invoiceId }
            val saleLines = database.journalLineDao().getForEntry(saleEntry.id)
            assertEquals(4, saleLines.size)
            assertDecimal("30", saleLines.single { accounts[it.accountId]!!.code == "1000" }.debit)
            assertDecimal("30", saleLines.single { accounts[it.accountId]!!.code == "4000" }.credit)
            assertDecimal("20", saleLines.single { accounts[it.accountId]!!.code == "5000" }.debit)
            assertDecimal("20", saleLines.single { accounts[it.accountId]!!.code == "1200" }.credit)

            val purchaseEntry = entries.single { it.sourceId == purchase.invoiceId }
            val purchaseLines = database.journalLineDao().getForEntry(purchaseEntry.id)
            assertEquals(2, purchaseLines.size)
            assertDecimal("40", purchaseLines.single { accounts[it.accountId]!!.code == "1200" }.debit)
            assertDecimal("40", purchaseLines.single { accounts[it.accountId]!!.code == "1000" }.credit)

            entries.forEach { entry ->
                val lines = database.journalLineDao().getForEntry(entry.id)
                val debit = lines.fold(BigDecimal.ZERO) { sum, line -> sum.add(BigDecimal(line.debit)) }
                val credit = lines.fold(BigDecimal.ZERO) { sum, line -> sum.add(BigDecimal(line.credit)) }
                assertEquals(0, debit.compareTo(credit))
                assertEquals("SYP", entry.currencyCode)
                assertEquals("1", entry.exchangeRateSnapshot)
            }
        } finally {
            database.close()
        }
    }


    @Test
    fun backdatedPurchaseRecalculatesPriorSaleAndPostsBalancedCostAdjustment() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)
            products.addProduct(
                sku = "BACKDATE-001",
                name = "مادة فاتورة مؤرخة بأثر رجعي",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "10",
                note = "اختبار إعادة احتساب الكلفة"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt
            val saleAt = baselineAt + 3_000L
            val backdatedPurchaseAt = baselineAt + 2_000L

            val sale = invoices.postInvoice(
                invoiceNumber = "BACKDATE-SALE-1",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "2", "15")),
                paidAmount = "30",
                issuedAt = saleAt
            )
            assertDecimal("10", database.invoiceDao().getLines(sale.invoiceId).single().unitCostSnapshot)

            invoices.postInvoice(
                invoiceNumber = "BACKDATE-PURCHASE-1",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "4", "20")),
                paidAmount = "80",
                issuedAt = backdatedPurchaseAt
            )

            val unchangedSaleLine = database.invoiceDao().getLines(sale.invoiceId).single()
            assertDecimal("10", unchangedSaleLine.unitCostSnapshot)
            assertDecimal("12.85714286", database.productDao().findById(product.id)!!.purchaseCost)
            assertDecimal("12", database.productDao().findById(product.id)!!.stockQuantity)

            val audit = database.stockCostRecalculationAuditDao().getAll()
                .single { it.triggerType == "PURCHASE" }
            assertEquals(1, audit.affectedStockOutCount)
            val item = database.stockCostRecalculationItemDao().getForAudit(audit.id).single()
            assertEquals(sale.invoiceId, item.invoiceId)
            assertEquals(unchangedSaleLine.id, item.sourceId)
            assertDecimal("10", item.oldUnitCost)
            assertDecimal("12.85714286", item.newUnitCost)
            assertDecimal("20", item.oldExtendedCost)
            assertDecimal("25.71428572", item.newExtendedCost)

            val adjustment = database.journalEntryDao().getAll()
                .single { it.sourceType == "STOCK_COST_RECALCULATION" }
            assertEquals(saleAt, adjustment.occurredAt)
            val accounts = database.accountDao().getAll().associateBy { it.id }
            val adjustmentLines = database.journalLineDao().getForEntry(adjustment.id)
            assertEquals(2, adjustmentLines.size)
            assertDecimal(
                "5.71428572",
                adjustmentLines.single { accounts[it.accountId]!!.code == "5000" }.debit
            )
            assertDecimal(
                "5.71428572",
                adjustmentLines.single { accounts[it.accountId]!!.code == "1200" }.credit
            )
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
