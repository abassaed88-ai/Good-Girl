package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeneralLedgerFinancialStatementsCalculatorTest {
    private val accounts = listOf(
        AccountEntity("cash", "1000", "الصندوق", "ASSET", "DEBIT", true, true, 1L),
        AccountEntity("stock", "1200", "المخزون", "ASSET", "DEBIT", true, true, 1L),
        AccountEntity("capital", "3000", "رأس المال", "EQUITY", "CREDIT", true, true, 1L),
        AccountEntity("sales", "4000", "المبيعات", "REVENUE", "CREDIT", true, true, 1L),
        AccountEntity("cogs", "5000", "تكلفة البضاعة", "EXPENSE", "DEBIT", true, true, 1L),
        AccountEntity("expense", "7000", "مصروف تشغيلي", "EXPENSE", "DEBIT", true, true, 1L)
    )

    private fun entry(id: String, currency: String, at: Long) = JournalEntryEntity(
        id = id,
        journalNumber = id,
        sourceType = "TEST",
        sourceId = id,
        currencyCode = currency,
        exchangeRateSnapshot = "1",
        occurredAt = at,
        description = id,
        createdAt = at
    )

    private fun line(id: String, entry: String, account: String, debit: String, credit: String) =
        JournalLineEntity(id, entry, 1, account, debit, credit, "")

    private val entries = listOf(
        entry("opening-syp", "SYP", 10L),
        entry("opening-usd", "USD", 15L),
        entry("purchase", "SYP", 20L),
        entry("sale", "SYP", 30L),
        entry("cogs", "SYP", 30L),
        entry("expense", "SYP", 40L)
    )

    private val lines = listOf(
        line("l1", "opening-syp", "cash", "1000", "0"),
        line("l2", "opening-syp", "capital", "0", "1000"),
        line("l3", "opening-usd", "cash", "100", "0"),
        line("l4", "opening-usd", "capital", "0", "100"),
        line("l5", "purchase", "stock", "200", "0"),
        line("l6", "purchase", "cash", "0", "200"),
        line("l7", "sale", "cash", "150", "0"),
        line("l8", "sale", "sales", "0", "150"),
        line("l9", "cogs", "cogs", "60", "0"),
        line("l10", "cogs", "stock", "0", "60"),
        line("l11", "expense", "expense", "10", "0"),
        line("l12", "expense", "cash", "0", "10")
    )

    @Test
    fun buildsBalanceSheetAndPeriodIncomeStatementFromGeneralJournal() {
        val result = GeneralLedgerFinancialStatementsCalculator.calculate(
            periodStartInclusive = 30L,
            periodEndInclusive = 40L,
            asOfInclusive = 40L,
            accounts = accounts,
            entries = entries,
            lines = lines
        )

        val syp = result.balanceSheets.single { it.currencyCode == "SYP" }
        assertEquals(BigDecimal("1080"), syp.totalAssets)
        assertEquals(BigDecimal("1000"), syp.totalEquity)
        assertEquals(BigDecimal("80"), syp.retainedEarnings)
        assertEquals(BigDecimal("1080"), syp.totalLiabilitiesAndEquity)

        val usd = result.balanceSheets.single { it.currencyCode == "USD" }
        assertEquals(BigDecimal("100"), usd.totalAssets)
        assertEquals(BigDecimal("100"), usd.totalLiabilitiesAndEquity)

        val income = result.incomeStatements.single { it.currencyCode == "SYP" }
        assertEquals(BigDecimal("150"), income.totalRevenue)
        assertEquals(BigDecimal("70"), income.totalExpenses)
        assertEquals(BigDecimal("80"), income.netIncome)
        assertEquals(BigDecimal.ZERO, incomeStatementsForUsd(result).netIncome)
    }

    @Test
    fun excludesTransactionsOutsideSelectedIncomePeriodButKeepsThemInBalanceSheet() {
        val result = GeneralLedgerFinancialStatementsCalculator.calculate(
            periodStartInclusive = 30L,
            periodEndInclusive = 40L,
            asOfInclusive = 40L,
            accounts = accounts,
            entries = entries,
            lines = lines
        )
        val income = result.incomeStatements.single { it.currencyCode == "SYP" }
        assertEquals(BigDecimal("150"), income.totalRevenue)
        assertEquals(BigDecimal("80"), result.balanceSheets.single { it.currencyCode == "SYP" }.retainedEarnings)
    }

    @Test
    fun rejectsUnbalancedJournalAndInvalidPeriod() {
        val broken = lines.dropLast(1)
        assertThrows(IllegalArgumentException::class.java) {
            GeneralLedgerFinancialStatementsCalculator.calculate(
                30L, 40L, 40L, accounts, entries, broken
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeneralLedgerFinancialStatementsCalculator.calculate(
                50L, 40L, 40L, accounts, entries, lines
            )
        }
    }

    private fun incomeStatementsForUsd(result: GeneralLedgerFinancialStatements) =
        result.incomeStatements.single { it.currencyCode == "USD" }
}
