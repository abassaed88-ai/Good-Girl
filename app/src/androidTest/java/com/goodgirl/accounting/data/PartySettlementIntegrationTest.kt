package com.goodgirl.accounting.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.domain.FinancialReportCalculator
import com.goodgirl.accounting.domain.PartyBalanceCalculator
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the separation between party debt settlement, cash movement,
 * and profit-and-loss classification on a real in-memory Room database.
 */
@RunWith(AndroidJUnit4::class)
class PartySettlementIntegrationTest {

    @Test
    fun openingBalanceCreditInvoiceReceiptsAndOtherIncomeKeepBalancesConsistent() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val partyRepository = PartyRepository(database)
            val productRepository = ProductRepository(database)
            val invoiceRepository = InvoiceRepository(database)
            val partyPaymentRepository = PartyPaymentRepository(database)
            val cashRepository = CashTransactionRepository(database)

            partyRepository.saveParty(
                id = null,
                kind = "CUSTOMER",
                name = "عميل تكامل",
                phone = "",
                address = "",
                note = "اختبار رصيد وتحصيل",
                openingBalance = "10",
                currencyCode = "SYP",
                openingExchangeRate = "1"
            )
            val customer = database.partyDao().getAll().single()

            productRepository.addProduct(
                sku = "PARTY-001",
                name = "منتج تحصيل",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "10",
                note = "اختبار تكامل"
            )
            val product = database.productDao().getAll().single()

            val invoice = invoiceRepository.postInvoice(
                invoiceNumber = "PARTY-SALE-001",
                kind = "SALE",
                partyId = customer.id,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "2", "15")),
                paidAmount = "0",
                issuedAt = 1_000L
            )

            assertDecimal(
                "40",
                PartyBalanceCalculator.calculate(
                    "CUSTOMER",
                    database.ledgerDao().getForParty(customer.id)
                ).getValue("SYP").toPlainString()
            )

            partyPaymentRepository.recordPayment(
                partyId = customer.id,
                amount = "15",
                currencyCode = "SYP",
                exchangeRate = "1",
                note = "دفعة أولى"
            )

            var updatedInvoice = database.invoiceDao().findById(invoice.invoiceId)
            assertNotNull(updatedInvoice)
            assertDecimal("15", updatedInvoice!!.paid)
            assertDecimal("15", updatedInvoice.remaining)
            assertDecimal(
                "25",
                PartyBalanceCalculator.calculate(
                    "CUSTOMER",
                    database.ledgerDao().getForParty(customer.id)
                ).getValue("SYP").toPlainString()
            )

            invoiceRepository.recordPayment(
                invoiceId = invoice.invoiceId,
                amount = "5",
                note = "دفعة مباشرة على الفاتورة",
                occurredAt = 2_000L
            )

            updatedInvoice = database.invoiceDao().findById(invoice.invoiceId)
            assertNotNull(updatedInvoice)
            assertDecimal("20", updatedInvoice!!.paid)
            assertDecimal("10", updatedInvoice.remaining)
            assertDecimal(
                "20",
                PartyBalanceCalculator.calculate(
                    "CUSTOMER",
                    database.ledgerDao().getForParty(customer.id)
                ).getValue("SYP").toPlainString()
            )

            cashRepository.recordTransaction(
                kind = "INCOME",
                amount = "7",
                currencyCode = "SYP",
                exchangeRate = "1",
                description = "إيراد آخر"
            )
            cashRepository.recordTransaction(
                kind = "EXPENSE",
                amount = "2.5",
                currencyCode = "SYP",
                exchangeRate = "1",
                description = "مصروف تشغيلي"
            )

            val entries = database.ledgerDao().getAll()
            assertDecimal(
                "20",
                entries.filter { it.entryType == "RECEIVABLE_PAYMENT" }
                    .fold(BigDecimal.ZERO) { sum, entry -> sum + entry.amount.toBigDecimal() }
                    .toPlainString()
            )
            assertDecimal(
                "27",
                entries.filter { it.entryType == "CASH_IN" }
                    .fold(BigDecimal.ZERO) { sum, entry -> sum + entry.amount.toBigDecimal() }
                    .toPlainString()
            )
            assertDecimal(
                "2.5",
                entries.filter { it.entryType == "CASH_OUT" }
                    .fold(BigDecimal.ZERO) { sum, entry -> sum + entry.amount.toBigDecimal() }
                    .toPlainString()
            )

            val report = FinancialReportCalculator.calculate(
                fromInclusive = 0L,
                toInclusive = Long.MAX_VALUE,
                invoices = database.invoiceDao().getAllInvoices(),
                lines = database.invoiceDao().getAllLines(),
                ledgerEntries = entries
            )
            val summary = report.currencySummaries.single { it.currencyCode == "SYP" }
            assertDecimal("30", summary.sales.toPlainString())
            assertDecimal("20", summary.costOfGoodsSold.toPlainString())
            assertDecimal("10", summary.grossProfit.toPlainString())
            assertDecimal("7", summary.otherIncome.toPlainString())
            assertDecimal("2.5", summary.expenses.toPlainString())
            assertDecimal("14.5", summary.netProfit.toPlainString())
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
