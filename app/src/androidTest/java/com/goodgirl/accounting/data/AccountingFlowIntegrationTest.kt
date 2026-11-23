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

            invoices.postInvoice(
                invoiceNumber = "FLOW-SALE-1",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "2", "15")),
                paidAmount = "30",
                issuedAt = 1_000L
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
                issuedAt = 2_000L
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
                issuedAt = 3_000L
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

            invoices.postInvoice(
                invoiceNumber = "PRICE-SALE-1",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "1", "15")),
                paidAmount = "15",
                issuedAt = 1_000L
            )
            val firstInvoice = database.invoiceDao().getAllInvoices().single()
            val firstLine = database.invoiceDao().getLines(firstInvoice.id).single()

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
                issuedAt = 2_000L
            )

            val saleLines = database.invoiceDao().getAllLines()
                .sortedBy { it.createdAt }
            assertEquals(2, saleLines.size)
            assertDecimal("15", saleLines[0].unitPriceSnapshot)
            assertDecimal("17.5", saleLines[1].unitPriceSnapshot)
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
                        issuedAt = 4_000L
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
