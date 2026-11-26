package com.goodgirl.accounting.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.domain.FinancialReportCalculator
import com.goodgirl.accounting.domain.CashFlowStatementCalculator
import com.goodgirl.accounting.domain.TrialBalanceCalculator
import com.goodgirl.accounting.domain.GeneralLedgerFinancialStatementsCalculator
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reconciles the invoice-based profit report with the posted general journal.
 *
 * Purchases must increase inventory, not operating expenses. Sale COGS must
 * use each invoice line's historical cost snapshot. Manual income and expense
 * entries must appear once in both report paths, without counting cash rows
 * as additional income or expenses.
 */
@RunWith(AndroidJUnit4::class)
class FinancialReportJournalReconciliationIntegrationTest {

    @Test
    fun salesCostAndNetProfitReconcileWithGeneralJournalByCurrency() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)

            products.addProduct(
                sku = "RECON-001",
                name = "مادة المطابقة",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "10",
                note = "اختبار مطابقة التقارير"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            invoices.postInvoice(
                invoiceNumber = "RECON-SALE-1",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "2", "15")),
                paidAmount = "30",
                issuedAt = baselineAt + 1_000L
            )
            invoices.postInvoice(
                invoiceNumber = "RECON-PURCHASE-1",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "4", "20")),
                paidAmount = "80",
                issuedAt = baselineAt + 2_000L
            )
            invoices.postInvoice(
                invoiceNumber = "RECON-SALE-2",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "1", "15")),
                paidAmount = "15",
                issuedAt = baselineAt + 3_000L
            )

            val cash = CashTransactionRepository(database)
            cash.recordTransaction(
                kind = "INCOME",
                amount = "12.75",
                currencyCode = "SYP",
                exchangeRate = "1",
                description = "إيراد آخر للمطابقة"
            )
            cash.recordTransaction(
                kind = "EXPENSE",
                amount = "3.50",
                currencyCode = "SYP",
                exchangeRate = "1",
                description = "مصروف تشغيلي للمطابقة"
            )

            val invoicesSnapshot = database.invoiceDao().getAllInvoices()
            val report = FinancialReportCalculator.calculate(
                fromInclusive = 0L,
                toInclusive = Long.MAX_VALUE,
                invoices = invoicesSnapshot,
                lines = database.invoiceDao().getAllLines(),
                ledgerEntries = database.ledgerDao().getAll()
            )
            val salesSummary = report.currencySummaries.single { it.currencyCode == "SYP" }
            val productSummary = report.products.single()

            val statements = GeneralLedgerFinancialStatementsCalculator.calculate(
                periodStartInclusive = 0L,
                periodEndInclusive = Long.MAX_VALUE,
                asOfInclusive = Long.MAX_VALUE,
                accounts = database.accountDao().getAll(),
                entries = database.journalEntryDao().getAll(),
                lines = database.journalLineDao().getAll()
            )
            val incomeStatement = statements.incomeStatements.single { it.currencyCode == "SYP" }

            // The purchase increases stock; it is not an immediate P&L expense.
            assertMoney("80", salesSummary.purchases)
            assertMoney("45", salesSummary.sales)
            assertMoney("33.33333333", salesSummary.costOfGoodsSold)
            assertMoney("11.66666667", salesSummary.grossProfit)
            assertMoney("12.75", salesSummary.otherIncome)
            assertMoney("3.5", salesSummary.expenses)
            assertMoney("20.91666667", salesSummary.netProfit)
            assertMoney("11.66666667", productSummary.profit)

            // Match sales, cost of goods, other income and operating expenses
            // to the corresponding general-ledger account balances.
            assertMoney(
                "45",
                incomeStatement.revenues.single { it.accountCode == "4000" }.amount
            )
            assertMoney(
                "12.75",
                incomeStatement.revenues.single { it.accountCode == "6000" }.amount
            )
            assertMoney(
                "33.33333333",
                incomeStatement.expenses.single { it.accountCode == "5000" }.amount
            )
            assertMoney(
                "3.5",
                incomeStatement.expenses.single { it.accountCode == "7000" }.amount
            )
            assertMoney("57.75", incomeStatement.totalRevenue)
            assertMoney("36.83333333", incomeStatement.totalExpenses)
            assertMoney(salesSummary.netProfit.toPlainString(), incomeStatement.netIncome)

            val balanceSheet = statements.balanceSheets.single { it.currencyCode == "SYP" }
            assertEquals(
                0,
                balanceSheet.totalAssets.compareTo(balanceSheet.totalLiabilitiesAndEquity)
            )

            // The cash-flow closing balance must equal the cash account balance
            // calculated independently from the complete general journal.
            val entriesSnapshot = database.journalEntryDao().getAll()
            val linesSnapshot = database.journalLineDao().getAll()
            val accountsSnapshot = database.accountDao().getAll()
            val trialBalance = TrialBalanceCalculator.calculate(
                asOfInclusive = Long.MAX_VALUE,
                accounts = accountsSnapshot,
                entries = entriesSnapshot,
                lines = linesSnapshot
            )
            val cashFlow = CashFlowStatementCalculator.calculate(
                periodStartInclusive = 0L,
                periodEndInclusive = Long.MAX_VALUE,
                asOfInclusive = Long.MAX_VALUE,
                cashAccountIds = setOf("SYS-1000"),
                entries = entriesSnapshot,
                lines = linesSnapshot
            )
            val cashStatement = cashFlow.statements.single { it.currencyCode == "SYP" }
            val cashAccount = trialBalance.rows.single {
                it.accountCode == "1000" && it.currencyCode == "SYP"
            }
            val ledgerCashBalance = cashAccount.debitBalance.subtract(cashAccount.creditBalance)
            assertMoney(ledgerCashBalance.toPlainString(), cashStatement.actualClosingCashBalance)
            assertMoney(ledgerCashBalance.toPlainString(), cashStatement.closingCashBalance)
            assertMoney("0", cashStatement.reconciliationDifference)
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

    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertEquals(
            "Expected $expected but was ${actual.toPlainString()}",
            0,
            BigDecimal(expected).compareTo(actual)
        )
    }
}
