package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodClosingRulesTest {
    private val accounts = listOf(
        AccountEntity("sales", "4000", "Sales", "REVENUE", "CREDIT", true, true, 1),
        AccountEntity("expense", "7000", "Expense", "EXPENSE", "DEBIT", true, true, 1),
        AccountEntity("equity", "3000", "Retained earnings", "EQUITY", "CREDIT", true, true, 1),
        AccountEntity("cash", "1000", "Cash", "ASSET", "DEBIT", true, true, 1)
    )
    private val entries = listOf(
        JournalEntryEntity("sale", "1", "TEST", "1", "SYP", "1", 10, "Sale", 10),
        JournalEntryEntity("expense", "2", "TEST", "2", "SYP", "1", 11, "Expense", 11)
    )
    private val lines = listOf(
        JournalLineEntity("1", "sale", 1, "cash", "100", "0", ""),
        JournalLineEntity("2", "sale", 2, "sales", "0", "100", ""),
        JournalLineEntity("3", "expense", 1, "expense", "30", "0", ""),
        JournalLineEntity("4", "expense", 2, "cash", "0", "30", "")
    )
    @Test fun createsBalancedCloseForProfitAndLoss() {
        val draft = PeriodClosingRules.build(11, "SYP", accounts, entries, lines, "equity")
        assertEquals(BigDecimal("70"), draft.netIncome)
        assertEquals(BigDecimal("100"), draft.lines.fold(BigDecimal.ZERO) { a, l -> a.add(l.debit) })
        assertEquals(BigDecimal("130"), draft.lines.fold(BigDecimal.ZERO) { a, l -> a.add(l.credit) })
    }
    @Test fun excludesOtherCurrenciesAndFutureEntries() {
        val draft = PeriodClosingRules.build(10, "SYP", accounts, entries, lines, "equity")
        assertEquals(BigDecimal("100"), draft.netIncome)
    }
}