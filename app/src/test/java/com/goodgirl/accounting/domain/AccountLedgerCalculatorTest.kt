package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountLedgerCalculatorTest {
    private fun account(id: String = "cash", side: String = "DEBIT") = AccountEntity(
        id = id,
        code = if (id == "cash") "1000" else "2000",
        name = if (id == "cash") "الصندوق" else "ذمم الموردين",
        accountType = if (side == "DEBIT") "ASSET" else "LIABILITY",
        normalSide = side,
        isSystem = true,
        isActive = true,
        createdAt = 1L
    )

    private fun entry(id: String, currency: String, at: Long) = JournalEntryEntity(
        id = id,
        journalNumber = "JE-$id",
        sourceType = "TEST",
        sourceId = id,
        currencyCode = currency,
        exchangeRateSnapshot = if (currency == "USD") "15000" else "1",
        occurredAt = at,
        description = "حركة اختبار",
        createdAt = at
    )

    private fun line(
        id: String,
        journalId: String,
        accountId: String,
        debit: String = "0",
        credit: String = "0"
    ) = JournalLineEntity(id, journalId, 1, accountId, debit, credit, "بيان اختبار")

    @Test
    fun calculatesRunningBalancesSeparatelyForEachCurrency() {
        val entries = listOf(
            entry("sale", "SYP", 1L),
            entry("expense", "SYP", 2L),
            entry("receipt", "USD", 3L)
        )
        val lines = listOf(
            line("1", "sale", "cash", debit = "100"),
            line("2", "expense", "cash", credit = "25"),
            line("3", "receipt", "cash", debit = "10")
        )

        val statement = AccountLedgerCalculator.calculate(account(), entries, lines)

        assertEquals(3, statement.movements.size)
        val syp = statement.currencySummaries.single { it.currencyCode == "SYP" }
        assertDecimal("100", syp.totalDebit)
        assertDecimal("25", syp.totalCredit)
        assertDecimal("75", syp.closingBalance)
        assertEquals("DEBIT", syp.closingSide)

        val usd = statement.currencySummaries.single { it.currencyCode == "USD" }
        assertDecimal("10", usd.closingBalance)
        assertEquals("DEBIT", usd.closingSide)
        assertDecimal("100", statement.movements.single { it.journalEntryId == "sale" }.runningBalance)
        assertDecimal("75", statement.movements.single { it.journalEntryId == "expense" }.runningBalance)
    }

    @Test
    fun computesCreditNormalBalanceAndContraBalanceCorrectly() {
        val entries = listOf(entry("bill", "SYP", 1L), entry("payment", "SYP", 2L))
        val lines = listOf(
            line("1", "bill", "payables", credit = "50"),
            line("2", "payment", "payables", debit = "20")
        )

        val summary = AccountLedgerCalculator.calculate(account("payables", "CREDIT"), entries, lines)
            .currencySummaries.single()

        assertDecimal("30", summary.closingBalance)
        assertEquals("CREDIT", summary.closingSide)
    }

    @Test
    fun excludesFutureRowsFromAsOfStatement() {
        val entries = listOf(entry("old", "SYP", 1L), entry("future", "SYP", 10L))
        val lines = listOf(
            line("1", "old", "cash", debit = "5"),
            line("2", "future", "cash", debit = "90")
        )

        val statement = AccountLedgerCalculator.calculate(account(), entries, lines, asOfInclusive = 1L)

        assertEquals(1, statement.movements.size)
        assertDecimal("5", statement.currencySummaries.single().closingBalance)
    }

    @Test
    fun rejectsOrphanLinesAndInvalidAmounts() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountLedgerCalculator.calculate(
                account(),
                emptyList(),
                listOf(line("1", "missing", "cash", debit = "1"))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountLedgerCalculator.calculate(
                account(),
                listOf(entry("bad", "SYP", 1L)),
                listOf(line("1", "bad", "cash", debit = "-1"))
            )
        }
    }

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }
}
