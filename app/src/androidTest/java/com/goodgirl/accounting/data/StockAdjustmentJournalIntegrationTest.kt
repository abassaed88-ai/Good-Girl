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
