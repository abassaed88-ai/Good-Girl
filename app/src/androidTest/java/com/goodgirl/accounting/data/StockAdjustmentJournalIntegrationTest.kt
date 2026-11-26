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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StockAdjustmentJournalIntegrationTest {

    @Test
    fun productOpeningStockPostsInventoryAgainstEquityAndKeepsCurrencySnapshot() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            products.addProduct(
                sku = "OPEN-USD",
                name = "مخزون افتتاحي بالدولار",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "5",
                note = "اختبار رصيد مخزون افتتاحي",
                priceCurrencyCode = "USD",
                openingExchangeRate = "15200"
            )
            products.addProduct(
                sku = "ZERO-COST",
                name = "منتج بلا قيمة مخزنية",
                unit = "قطعة",
                purchaseCost = "0",
                salePrice = "2",
                openingStock = "3",
                note = "لا ينشأ قيد بقيمة صفر"
            )

            val journal = database.journalEntryDao().getAll().single()
            assertEquals("PRODUCT_OPENING_STOCK", journal.sourceType)
            assertEquals("USD", journal.currencyCode)
            assertEquals("15200", journal.exchangeRateSnapshot)
            val accounts = database.accountDao().getAll().associate { it.id to it.code }
            val lines = database.journalLineDao().getForEntry(journal.id)
            assertEquals(2, lines.size)
            assertEquals("1200", accounts.getValue(lines.single { it.debit != "0" }.accountId))
            assertEquals("3000", accounts.getValue(lines.single { it.credit != "0" }.accountId))
            assertDecimal("50", lines.single { it.debit != "0" }.debit)
            assertDecimal("50", lines.single { it.credit != "0" }.credit)
            assertTrue(database.ledgerDao().getAll().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun stockGainsAndLossesPostBalancedNonCashEntriesAndAffectProfitReportsOnce() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val stock = StockRepository(database)
            products.addProduct(
                sku = "STOCK-JOURNAL",
                name = "مادة اختبار تسوية المخزون",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "10",
                note = "اختبار الجرد",
                priceCurrencyCode = "USD",
                openingExchangeRate = "15000"
            )
            val product = database.productDao().getAll().single()

            stock.adjustStock(
                productId = product.id,
                quantityDelta = "5",
                unitCost = "10",
                note = "إضافة مخزون يدوية",
                exchangeRateSnapshot = "15250"
            )
            stock.adjustStock(
                productId = product.id,
                quantityDelta = "-2",
                unitCost = "",
                note = "سحب مخزون يدوي",
                exchangeRateSnapshot = "15250"
            )
            val stocktakeLoss = stock.reconcileStocktake(
                productId = product.id,
                countedQuantity = "12",
                unitCost = "",
                note = "نقص مكتشف بالجرد",
                exchangeRateSnapshot = "15300"
            )
            assertEquals("-1", stocktakeLoss.adjustmentDelta)
            val stocktakeGain = stock.reconcileStocktake(
                productId = product.id,
                countedQuantity = "14",
                unitCost = "10",
                note = "زيادة مكتشفة بالجرد",
                exchangeRateSnapshot = "15400"
            )
            assertEquals("2", stocktakeGain.adjustmentDelta)
            assertEquals("14", stocktakeGain.resultingBalance)

            val journals = database.journalEntryDao().getAll()
            assertEquals(5, journals.size)
            assertEquals(1, journals.count { it.sourceType == "PRODUCT_OPENING_STOCK" })
            assertEquals(1, journals.count { it.sourceType == "MANUAL_STOCK_GAIN" })
            assertEquals(1, journals.count { it.sourceType == "MANUAL_STOCK_LOSS" })
            assertEquals(1, journals.count { it.sourceType == "STOCKTAKE_LOSS" })
            assertEquals(1, journals.count { it.sourceType == "STOCKTAKE_GAIN" })

            val accounts = database.accountDao().getAll().associate { it.id to it.code }
            journals.forEach { journal ->
                val lines = database.journalLineDao().getForEntry(journal.id)
                val debit = lines.fold(BigDecimal.ZERO) { sum, line -> sum + line.debit.toBigDecimal() }
                val credit = lines.fold(BigDecimal.ZERO) { sum, line -> sum + line.credit.toBigDecimal() }
                assertEquals(0, debit.compareTo(credit))
                assertEquals("USD", journal.currencyCode)
                assertTrue(journal.exchangeRateSnapshot.toBigDecimal() > BigDecimal.ZERO)
            }

            val manualGain = journals.single { it.sourceType == "MANUAL_STOCK_GAIN" }
            val manualGainLines = database.journalLineDao().getForEntry(manualGain.id)
            assertDecimal("50", manualGainLines.single { accounts.getValue(it.accountId) == "1200" }.debit)
            assertDecimal("50", manualGainLines.single { accounts.getValue(it.accountId) == "6000" }.credit)
            assertEquals("15250", manualGain.exchangeRateSnapshot)

            val manualLoss = journals.single { it.sourceType == "MANUAL_STOCK_LOSS" }
            val manualLossLines = database.journalLineDao().getForEntry(manualLoss.id)
            assertDecimal("20", manualLossLines.single { accounts.getValue(it.accountId) == "7000" }.debit)
            assertDecimal("20", manualLossLines.single { accounts.getValue(it.accountId) == "1200" }.credit)

            val ledger = database.ledgerDao().getAll()
            assertEquals(2, ledger.count { it.entryType == "INCOME" })
            assertEquals(2, ledger.count { it.entryType == "EXPENSE" })
            assertEquals(0, ledger.count { it.entryType == "CASH_IN" || it.entryType == "CASH_OUT" })
            assertDecimal(
                "70",
                ledger.filter { it.entryType == "INCOME" }
                    .fold(BigDecimal.ZERO) { sum, row -> sum + row.amount.toBigDecimal() }.toPlainString()
            )
            assertDecimal(
                "30",
                ledger.filter { it.entryType == "EXPENSE" }
                    .fold(BigDecimal.ZERO) { sum, row -> sum + row.amount.toBigDecimal() }.toPlainString()
            )

            val report = FinancialReportCalculator.calculate(
                fromInclusive = 0L,
                toInclusive = Long.MAX_VALUE,
                invoices = emptyList(),
                lines = emptyList(),
                ledgerEntries = ledger
            )
            val summary = report.currencySummaries.single { it.currencyCode == "USD" }
            assertDecimal("70", summary.otherIncome.toPlainString())
            assertDecimal("30", summary.expenses.toPlainString())
            assertDecimal("40", summary.netProfit.toPlainString())
        } finally {
            database.close()
        }
    }

    @Test
    fun newPurchaseRevaluesOnlyRemainingStockAndPreservesPriorManualLosses() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val stock = StockRepository(database)
            val invoices = InvoiceRepository(database)
            products.addProduct(
                sku = "HIST-LOSS-001",
                name = "مادة خسارة المخزون التاريخية",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "10",
                note = "اختبار حماية تكلفة خسائر المخزون السابقة"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            stock.adjustStock(
                productId = product.id,
                quantityDelta = "-2",
                unitCost = "",
                note = "خسارة مخزون يدوية"
            )
            val stocktake = stock.reconcileStocktake(
                productId = product.id,
                countedQuantity = "7",
                unitCost = "",
                note = "نقص مكتشف بالجرد"
            )
            assertEquals("-1", stocktake.adjustmentDelta)

            val lossLedgerBefore = database.ledgerDao().getAll()
                .filter { it.sourceType == "MANUAL_STOCK_LOSS" || it.sourceType == "STOCKTAKE_LOSS" }
                .sortedBy { it.sourceType }
            assertEquals(2, lossLedgerBefore.size)
            assertDecimal("20", lossLedgerBefore.single { it.sourceType == "MANUAL_STOCK_LOSS" }.amount)
            assertDecimal("10", lossLedgerBefore.single { it.sourceType == "STOCKTAKE_LOSS" }.amount)

            val lossJournalsBefore = database.journalEntryDao().getAll()
                .filter { it.sourceType == "MANUAL_STOCK_LOSS" || it.sourceType == "STOCKTAKE_LOSS" }
                .associate { it.sourceType to database.journalLineDao().getForEntry(it.id) }

            // The invoice is entered later with an earlier issue date.
            val purchase = invoices.postInvoice(
                invoiceNumber = "HIST-LOSS-PURCHASE-1",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "10", "20")),
                paidAmount = "200",
                issuedAt = baselineAt + 1L
            )

            val currentProduct = database.productDao().findById(product.id)!!
            assertDecimal("17", currentProduct.stockQuantity)
            assertDecimal("20", currentProduct.purchaseCost)

            // The purchase reprices only the quantity still on hand. It must
            // not generate new cost-replay audit rows for old stock losses.
            assertTrue(database.stockCostRecalculationAuditDao().getAll().isEmpty())
            assertTrue(database.stockCostRecalculationItemDao().getAll().isEmpty())
            assertTrue(
                database.journalEntryDao().getAll().none {
                    it.sourceType == "STOCK_COST_RECALCULATION"
                }
            )
            val lossLedgerAfter = database.ledgerDao().getAll()
                .filter { it.sourceType == "MANUAL_STOCK_LOSS" || it.sourceType == "STOCKTAKE_LOSS" }
                .sortedBy { it.sourceType }
            assertEquals(lossLedgerBefore, lossLedgerAfter)
            val lossJournalsAfter = database.journalEntryDao().getAll()
                .filter { it.sourceType == "MANUAL_STOCK_LOSS" || it.sourceType == "STOCKTAKE_LOSS" }
                .associate { it.sourceType to database.journalLineDao().getForEntry(it.id) }
            assertEquals(lossJournalsBefore, lossJournalsAfter)

            val purchaseInvoice = database.invoiceDao().findById(purchase.invoiceId)!!
            val revaluation = database.journalEntryDao().findBySource(
                "PURCHASE_INVENTORY_REVALUATION",
                purchase.invoiceId
            )!!
            assertTrue(revaluation.occurredAt >= purchaseInvoice.createdAt)
            assertTrue(revaluation.occurredAt > purchaseInvoice.issuedAt)
            val accounts = database.accountDao().getAll().associateBy { it.id }
            val revaluationLines = database.journalLineDao().getForEntry(revaluation.id)
            assertDecimal(
                "70",
                revaluationLines.single { accounts[it.accountId]!!.code == "1200" }.debit
            )
            assertDecimal(
                "70",
                revaluationLines.single { accounts[it.accountId]!!.code == "4100" }.credit
            )

            val report = FinancialReportCalculator.calculate(
                fromInclusive = 0L,
                toInclusive = Long.MAX_VALUE,
                invoices = database.invoiceDao().getAllInvoices(),
                lines = database.invoiceDao().getAllLines(),
                ledgerEntries = database.ledgerDao().getAll()
            )
            val summary = report.currencySummaries.single { it.currencyCode == "SYP" }
            assertDecimal("200", summary.purchases.toPlainString())
            assertDecimal("70", summary.otherIncome.toPlainString())
            assertDecimal("30", summary.expenses.toPlainString())
            assertDecimal("40", summary.netProfit.toPlainString())

            val statements = com.goodgirl.accounting.domain.GeneralLedgerFinancialStatementsCalculator.calculate(
                periodStartInclusive = 0L,
                periodEndInclusive = Long.MAX_VALUE,
                asOfInclusive = Long.MAX_VALUE,
                accounts = database.accountDao().getAll(),
                entries = database.journalEntryDao().getAll(),
                lines = database.journalLineDao().getAll()
            )
            val income = statements.incomeStatements.single { it.currencyCode == "SYP" }
            assertDecimal("70", income.revenues.single { it.accountCode == "4100" }.amount.toPlainString())
            assertDecimal("30", income.expenses.single { it.accountCode == "7000" }.amount.toPlainString())
            assertDecimal(summary.netProfit.toPlainString(), income.netIncome.toPlainString())
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
