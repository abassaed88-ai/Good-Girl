package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodClosingRulesTest {
    private val accounts = listOf(
        AccountEntity("sales", "4000", "المبيعات", "REVENUE", "CREDIT", true, true, 1),
        AccountEntity("expense", "7000", "مصروف", "EXPENSE", "DEBIT", true, true, 1),
        AccountEntity("equity", "3000", "الأرباح المحتجزة", "EQUITY", "CREDIT", true, true, 1),
        AccountEntity("cash", "1000", "الصندوق", "ASSET", "DEBIT", true, true, 1)
    )
    private fun entry(id: String, currency: String, at: Long, source: String = "TEST") =
        JournalEntryEntity(id, id, source, id, currency, "1", at, id, at)
    private fun line(id: String, entryId: String, account: String, debit: String, credit: String) =
        JournalLineEntity(id, entryId, 1, account, debit, credit, "")

    private val entries = listOf(
        entry("old-sale", "SYP", 5),
        entry("sale", "SYP", 10),
        entry("expense", "SYP", 11),
        entry("future-sale", "SYP", 30),
        entry("usd-sale", "USD", 10)
    )
    private val lines = listOf(
        line("1", "old-sale", "cash", "50", "0"),
        line("2", "old-sale", "sales", "0", "50"),
        line("3", "sale", "cash", "100", "0"),
        line("4", "sale", "sales", "0", "100"),
        line("5", "expense", "expense", "30", "0"),
        line("6", "expense", "cash", "0", "30"),
        line("7", "future-sale", "cash", "20", "0"),
        line("8", "future-sale", "sales", "0", "20"),
        line("9", "usd-sale", "cash", "10", "0"),
        line("10", "usd-sale", "sales", "0", "10")
    )

    @Test fun createsBalancedCloseOnlyForSelectedPeriod() {
        val draft = PeriodClosingRules.build(10, 20, "SYP", accounts, entries, lines, "equity")
        assertEquals(BigDecimal("70"), draft.netIncome)
        assertEquals(BigDecimal("100"), draft.lines.fold(BigDecimal.ZERO) { a, l -> a.add(l.debit) })
        assertEquals(BigDecimal("100"), draft.lines.fold(BigDecimal.ZERO) { a, l -> a.add(l.credit) })
        assertEquals(3, draft.lines.size)
    }

    @Test fun createsBalancedCloseForNetLoss() {
        val lossLines = listOf(
            line("1", "sale", "cash", "80", "0"),
            line("2", "sale", "sales", "0", "80"),
            line("3", "expense", "expense", "100", "0"),
            line("4", "expense", "cash", "0", "100")
        )
        val lossEntries = listOf(entry("sale", "SYP", 10), entry("expense", "SYP", 11))
        val draft = PeriodClosingRules.build(10, 20, "SYP", accounts, lossEntries, lossLines, "equity")
        assertEquals(BigDecimal("-20"), draft.netIncome)
        assertEquals(BigDecimal("100"), draft.lines.fold(BigDecimal.ZERO) { a, l -> a.add(l.debit) })
        assertEquals(BigDecimal("100"), draft.lines.fold(BigDecimal.ZERO) { a, l -> a.add(l.credit) })
    }

    @Test fun excludesOtherCurrenciesAndAlreadyClosedJournals() {
        val withOldClose = entries + entry("previous-close", "SYP", 10, "PERIOD_CLOSE")
        val linesWithClose = lines + listOf(
            line("11", "previous-close", "sales", "50", "0"),
            line("12", "previous-close", "equity", "0", "50")
        )
        val draft = PeriodClosingRules.build(10, 20, "SYP", accounts, withOldClose, linesWithClose, "equity")
        assertEquals(BigDecimal("70"), draft.netIncome)
    }
}