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
class CashTransactionJournalIntegrationTest {

    @Test
    fun manualIncomeAndExpensePostBalancedJournalsWithoutDoubleCountingReports() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val repository = CashTransactionRepository(database)
            val income = repository.recordTransaction(
                kind = "INCOME",
                amount = "12.750",
                currencyCode = "USD",
                exchangeRate = "15000",
                description = "إيراد خدمة"
            )
            val expense = repository.recordTransaction(
                kind = "EXPENSE",
                amount = "3.50",
                currencyCode = "SYP",
                exchangeRate = "1",
                description = "مصروف تشغيل"
            )

            val journals = database.journalEntryDao().getAll()
            assertEquals(2, journals.size)
            val incomeJournal = journals.single { it.sourceType == "MANUAL_CASH_INCOME" }
            val expenseJournal = journals.single { it.sourceType == "MANUAL_CASH_EXPENSE" }
            assertEquals(income.transactionId, incomeJournal.sourceId)
            assertEquals(expense.transactionId, expenseJournal.sourceId)
            assertEquals("USD", incomeJournal.currencyCode)
            assertEquals("15000", incomeJournal.exchangeRateSnapshot)
            assertEquals("SYP", expenseJournal.currencyCode)
            assertEquals("1", expenseJournal.exchangeRateSnapshot)

            val accountNames = database.accountDao().getAll().associate { it.id to it.code }
            val incomeLines = database.journalLineDao().getForEntry(incomeJournal.id)
            assertEquals(2, incomeLines.size)
            assertEquals("1000", accountNames.getValue(incomeLines.single { it.debit != "0" }.accountId))
            assertEquals("6000", accountNames.getValue(incomeLines.single { it.credit != "0" }.accountId))
            assertDecimal("12.75", incomeLines.single { it.debit != "0" }.debit)
            assertDecimal("12.75", incomeLines.single { it.credit != "0" }.credit)

            val expenseLines = database.journalLineDao().getForEntry(expenseJournal.id)
            assertEquals(2, expenseLines.size)
            assertEquals("7000", accountNames.getValue(expenseLines.single { it.debit != "0" }.accountId))
            assertEquals("1000", accountNames.getValue(expenseLines.single { it.credit != "0" }.accountId))
            assertDecimal("3.5", expenseLines.single { it.debit != "0" }.debit)
            assertDecimal("3.5", expenseLines.single { it.credit != "0" }.credit)

            journals.forEach { journal ->
                val lines = database.journalLineDao().getForEntry(journal.id)
                val debit = lines.fold(BigDecimal.ZERO) { sum, line -> sum + line.debit.toBigDecimal() }
                val credit = lines.fold(BigDecimal.ZERO) { sum, line -> sum + line.credit.toBigDecimal() }
                assertEquals(0, debit.compareTo(credit))
                assertEquals(1, lines.count { it.debit != "0" })
                assertEquals(1, lines.count { it.credit != "0" })
            }

            val ledger = database.ledgerDao().getAll()
            assertEquals(4, ledger.size)
            assertEquals(1, ledger.count { it.entryType == "CASH_IN" })
            assertEquals(1, ledger.count { it.entryType == "CASH_OUT" })

            val report = FinancialReportCalculator.calculate(
                fromInclusive = 0L,
                toInclusive = Long.MAX_VALUE,
                invoices = emptyList(),
                lines = emptyList(),
                ledgerEntries = ledger
            )
            assertDecimal(
                "12.75",
                report.currencySummaries.single { it.currencyCode == "USD" }.otherIncome.toPlainString()
            )
            assertDecimal(
                "3.5",
                report.currencySummaries.single { it.currencyCode == "SYP" }.expenses.toPlainString()
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
