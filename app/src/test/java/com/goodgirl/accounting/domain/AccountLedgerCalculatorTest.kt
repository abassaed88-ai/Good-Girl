package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountLedgerCalculatorTest {
    private val account = AccountEntity(
        id = "cash", code = "1001", name = "Cash", accountType = "ASSET",
        normalSide = "DEBIT", isSystem = true, isActive = true, createdAt = 1L
    )

    private fun entry(id: String, number: String, currency: String, at: Long) =
        JournalEntryEntity(
            id = id, journalNumber = number, sourceType = "MANUAL_JOURNAL",
            sourceId = id, currencyCode = currency, exchangeRateSnapshot = "1",
            occurredAt = at, description = number, createdAt = at
        )

    private fun line(id: String, entryId: String, number: Int, debit: String, credit: String) =
        JournalLineEntity(
            id = id, journalEntryId = entryId, lineNumber = number, accountId = account.id,
            debit = debit, credit = credit, memo = ""
        )

    @Test
    fun calculatesRunningBalanceAndTotalsInChronologicalOrder() {
        val entries = listOf(
            entry("second", "J-002", "SYP", 200L),
            entry("first", "J-001", "SYP", 100L)
        )
        val lines = listOf(
            line("l2", "second", 1, "0", "30"),
            line("l1", "first", 1, "100", "0")
        )

        val result = AccountLedgerCalculator.calculate(account, entries, lines)

        assertEquals(listOf("J-001", "J-002"), result.movements.map { it.journalNumber })
        assertEquals(BigDecimal("100"), result.movements[0].runningBalance)
        assertEquals(BigDecimal("70"), result.movements[1].runningBalance)
        assertEquals(BigDecimal("100"), result.currencySummaries.single().totalDebit)
        assertEquals(BigDecimal("30"), result.currencySummaries.single().totalCredit)
        assertEquals(BigDecimal("70"), result.currencySummaries.single().closingBalance)
        assertEquals("DEBIT", result.currencySummaries.single().closingSide)
    }

    @Test
    fun keepsCurrenciesSeparateAndReportsCreditClosingBalance() {
        val entries = listOf(
            entry("usd", "J-USD", "USD", 100L),
            entry("syp", "J-SYP", "SYP", 200L)
        )
        val lines = listOf(
            line("usd-line", "usd", 1, "0", "12.50"),
            line("syp-line", "syp", 1, "500", "0")
        )

        val result = AccountLedgerCalculator.calculate(account, entries, lines)

        assertEquals(listOf("SYP", "USD"), result.currencySummaries.map { it.currencyCode })
        val usd = result.currencySummaries.first { it.currencyCode == "USD" }
        assertEquals(BigDecimal("12.50"), usd.closingBalance)
        assertEquals("CREDIT", usd.closingSide)
        val syp = result.currencySummaries.first { it.currencyCode == "SYP" }
        assertEquals(BigDecimal("500"), syp.closingBalance)
        assertEquals("DEBIT", syp.closingSide)
    }
}
