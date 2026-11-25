package com.goodgirl.accounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JournalEntryRulesTest {
    private fun balancedLines() = listOf(
        JournalLineDraft("cash", debit = "60.00"),
        JournalLineDraft("receivables", debit = "40"),
        JournalLineDraft("sales", credit = "100.000")
    )

    @Test
    fun acceptsBalancedSingleCurrencyJournalAndNormalizesExactDecimals() {
        val result = JournalEntryRules.normalize("syp", "1.000", balancedLines())
        assertEquals("SYP", result.currencyCode)
        assertEquals("1", result.exchangeRateSnapshot)
        assertEquals(listOf("60", "40", "0"), result.lines.map { it.debit })
        assertEquals(listOf("0", "0", "100"), result.lines.map { it.credit })
    }

    @Test
    fun rejectsUnbalancedJournal() {
        assertThrows(IllegalArgumentException::class.java) {
            JournalEntryRules.normalize(
                "SYP",
                "1",
                listOf(JournalLineDraft("cash", debit = "100"), JournalLineDraft("sales", credit = "99.99"))
            )
        }
    }

    @Test
    fun rejectsLineWithBothDebitAndCredit() {
        assertThrows(IllegalArgumentException::class.java) {
            JournalEntryRules.normalize(
                "SYP",
                "1",
                listOf(JournalLineDraft("cash", debit = "5", credit = "5"), JournalLineDraft("sales", credit = "5"))
            )
        }
    }

    @Test
    fun rejectsNegativeAndMalformedAmounts() {
        assertThrows(IllegalArgumentException::class.java) {
            JournalEntryRules.normalize(
                "SYP",
                "1",
                listOf(JournalLineDraft("cash", debit = "-1"), JournalLineDraft("sales", credit = "-1"))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            JournalEntryRules.normalize(
                "SYP",
                "1",
                listOf(JournalLineDraft("cash", debit = "bad"), JournalLineDraft("sales", credit = "1"))
            )
        }
    }

    @Test
    fun rejectsUnsupportedCurrencyAndNonPositiveExchangeRate() {
        assertThrows(IllegalArgumentException::class.java) {
            JournalEntryRules.normalize("EUR", "1", balancedLines())
        }
        assertThrows(IllegalArgumentException::class.java) {
            JournalEntryRules.normalize("USD", "0", balancedLines())
        }
    }

    @Test
    fun rejectsFewerThanTwoLinesAndEmptyAccount() {
        assertThrows(IllegalArgumentException::class.java) {
            JournalEntryRules.normalize("SYP", "1", listOf(JournalLineDraft("cash", debit = "1")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            JournalEntryRules.normalize(
                "SYP",
                "1",
                listOf(JournalLineDraft(" ", debit = "1"), JournalLineDraft("sales", credit = "1"))
            )
        }
    }
}
