package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialBalanceCalculatorTest {

    private val accounts = listOf(
        AccountEntity("cash", "1000", "الصندوق", "ASSET", "DEBIT", true, true, 1L),
        AccountEntity("inventory", "1200", "المخزون", "ASSET", "DEBIT", true, true, 1L),
        AccountEntity("sales", "4000", "المبيعات", "REVENUE", "CREDIT", true, true, 1L),
        AccountEntity("cogs", "5000", "تكلفة البضاعة المباعة", "EXPENSE", "DEBIT", true, true, 1L)
    )

    private fun entry(id: String, currency: String, occurredAt: Long) = JournalEntryEntity(
        id = id,
        journalNumber = "JE-$id",
        sourceType = "TEST",
        sourceId = id,
        currencyCode = currency,
        exchangeRateSnapshot = if (currency == "USD") "15000" else "1",
        occurredAt = occurredAt,
        description = "اختبار ميزان المراجعة",
        createdAt = occurredAt
    )

    private fun line(
        id: String,
        journalId: String,
        accountId: String,
        debit: String = "0",
        credit: String = "0",
        lineNumber: Int = 1
    ) = JournalLineEntity(id, journalId, lineNumber, accountId, debit, credit, "")

    @Test
    fun aggregatesBalancedBalancesByAccountAndCurrencyAndExcludesFutureEntries() {
        val entries = listOf(
            entry("sale-syp", "SYP", 1L),
            entry("cost-syp", "SYP", 2L),
            entry("sale-usd", "USD", 3L),
            entry("future-syp", "SYP", 10L)
        )
        val lines = listOf(
            line("1", "sale-syp", "cash", debit = "100", lineNumber = 1),
            line("2", "sale-syp", "sales", credit = "100", lineNumber = 2),
            line("3", "cost-syp", "cogs", debit = "35", lineNumber = 1),
            line("4", "cost-syp", "inventory", credit = "35", lineNumber = 2),
            line("5", "sale-usd", "cash", debit = "10", lineNumber = 1),
            line("6", "sale-usd", "sales", credit = "10", lineNumber = 2),
            line("7", "future-syp", "cash", debit = "500", lineNumber = 1),
            line("8", "future-syp", "sales", credit = "500", lineNumber = 2)
        )

        val result = TrialBalanceCalculator.calculate(3L, accounts, entries, lines)

        assertEquals(6, result.rows.size)
        val cashSyp = result.rows.single { it.accountId == "cash" && it.currencyCode == "SYP" }
        assertDecimal("100", cashSyp.totalDebit)
        assertDecimal("0", cashSyp.totalCredit)
        assertDecimal("100", cashSyp.debitBalance)
        assertDecimal("0", cashSyp.creditBalance)

        val salesSyp = result.rows.single { it.accountId == "sales" && it.currencyCode == "SYP" }
        assertDecimal("0", salesSyp.totalDebit)
        assertDecimal("100", salesSyp.totalCredit)
        assertDecimal("0", salesSyp.debitBalance)
        assertDecimal("100", salesSyp.creditBalance)

        val cashUsd = result.rows.single { it.accountId == "cash" && it.currencyCode == "USD" }
        assertDecimal("10", cashUsd.debitBalance)

        val syp = result.currencySummaries.single { it.currencyCode == "SYP" }
        assertDecimal("135", syp.totalDebits)
        assertDecimal("135", syp.totalCredits)
        assertDecimal("135", syp.debitBalances)
        assertDecimal("135", syp.creditBalances)

        val usd = result.currencySummaries.single { it.currencyCode == "USD" }
        assertDecimal("10", usd.totalDebits)
        assertDecimal("10", usd.totalCredits)
        assertDecimal("10", usd.debitBalances)
        assertDecimal("10", usd.creditBalances)
        assertTrue(result.rows.none { it.totalDebit == BigDecimal("500") })
    }

    @Test
    fun netsDebitAndCreditMovementsWithinTheSameAccount() {
        val entry = entry("mixed", "SYP", 1L)
        val lines = listOf(
            line("1", "mixed", "cash", debit = "90", lineNumber = 1),
            line("2", "mixed", "cash", credit = "25", lineNumber = 2),
            line("3", "mixed", "sales", credit = "65", lineNumber = 3)
        )

        val result = TrialBalanceCalculator.calculate(1L, accounts, listOf(entry), lines)
        val cash = result.rows.single { it.accountId == "cash" }

        assertDecimal("90", cash.totalDebit)
        assertDecimal("25", cash.totalCredit)
        assertDecimal("65", cash.debitBalance)
        assertDecimal("0", cash.creditBalance)
    }

    @Test
    fun rejectsAnUnbalancedJournalInsteadOfProducingMisleadingBalances() {
        val entry = entry("unbalanced", "SYP", 1L)
        val lines = listOf(
            line("1", "unbalanced", "cash", debit = "100", lineNumber = 1),
            line("2", "unbalanced", "sales", credit = "99", lineNumber = 2)
        )

        assertThrows(IllegalArgumentException::class.java) {
            TrialBalanceCalculator.calculate(1L, accounts, listOf(entry), lines)
        }
    }

    @Test
    fun rejectsJournalLinesWithMissingAccountOrHeader() {
        val entry = entry("test", "SYP", 1L)
        assertThrows(IllegalArgumentException::class.java) {
            TrialBalanceCalculator.calculate(
                1L,
                accounts,
                listOf(entry),
                listOf(
                    line("1", "test", "missing", debit = "1", lineNumber = 1),
                    line("2", "test", "sales", credit = "1", lineNumber = 2)
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TrialBalanceCalculator.calculate(
                1L,
                accounts,
                listOf(entry),
                listOf(
                    line("1", "missing-header", "cash", debit = "1"),
                    line("2", "test", "sales", credit = "1", lineNumber = 2)
                )
            )
        }
    }

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    private fun assertDecimal(expected: String, actual: String) {
        assertEquals(0, BigDecimal(expected).compareTo(BigDecimal(actual)))
    }
}
