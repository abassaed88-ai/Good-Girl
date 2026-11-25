package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import java.math.BigDecimal

data class PeriodCloseLineDraft(val accountId: String, val debit: BigDecimal, val credit: BigDecimal)
data class PeriodCloseDraft(val currencyCode: String, val periodEndInclusive: Long, val lines: List<PeriodCloseLineDraft>, val netIncome: BigDecimal)

/** Builds, but does not persist, a single-currency closing journal for revenue and expense accounts. */
object PeriodClosingRules {
    fun build(
        periodEndInclusive: Long,
        currencyCode: String,
        accounts: List<AccountEntity>,
        entries: List<JournalEntryEntity>,
        lines: List<JournalLineEntity>,
        retainedEarningsAccountId: String
    ): PeriodCloseDraft {
        require(periodEndInclusive >= 0) { "تاريخ الإغلاق غير صالح" }
        val currency = currencyCode.trim().uppercase()
        require(currency == "SYP" || currency == "USD") { "عملة الإغلاق غير مدعومة" }
        require(accounts.map { it.id }.toSet().size == accounts.size) { "معرّفات الحسابات مكررة" }
        val byId = accounts.associateBy { it.id }
        val equity = byId[retainedEarningsAccountId] ?: error("حساب الأرباح المحتجزة غير موجود")
        require(equity.accountType == "EQUITY" && equity.isActive) { "حساب الأرباح المحتجزة يجب أن يكون حساب حقوق ملكية فعالاً" }
        val included = entries.filter { it.currencyCode == currency && it.occurredAt <= periodEndInclusive }
        val entryIds = included.map { it.id }.toSet()
        val linesByEntry = lines.filter { it.journalEntryId in entryIds }.groupBy { it.journalEntryId }
        val netByAccount = mutableMapOf<String, Pair<BigDecimal, BigDecimal>>()
        included.forEach { entry ->
            val entryLines = linesByEntry[entry.id].orEmpty()
            require(entryLines.size >= 2) { "القيد ${entry.journalNumber} غير مكتمل" }
            var debits = BigDecimal.ZERO; var credits = BigDecimal.ZERO
            entryLines.forEach { line ->
                val account = byId[line.accountId] ?: error("حساب قيد غير موجود")
                val debit = decimal(line.debit); val credit = decimal(line.credit)
                require((debit.signum() > 0) xor (credit.signum() > 0)) { "سطر القيد غير صالح" }
                debits = debits.add(debit); credits = credits.add(credit)
                val old = netByAccount[line.accountId] ?: (BigDecimal.ZERO to BigDecimal.ZERO)
                netByAccount[line.accountId] = old.first.add(debit) to old.second.add(credit)
            }
            require(debits.compareTo(credits) == 0) { "القيد ${entry.journalNumber} غير متوازن" }
        }
        val closing = mutableListOf<PeriodCloseLineDraft>()
        var revenueTotal = BigDecimal.ZERO
        var expenseTotal = BigDecimal.ZERO
        accounts.filter { it.accountType == "REVENUE" || it.accountType == "EXPENSE" }.forEach { account ->
            val totals = netByAccount[account.id] ?: return@forEach
            val balance = if (account.accountType == "REVENUE") totals.second.subtract(totals.first) else totals.first.subtract(totals.second)
            if (balance.signum() <= 0) return@forEach
            if (account.accountType == "REVENUE") {
                revenueTotal = revenueTotal.add(balance)
                closing.add(PeriodCloseLineDraft(account.id, balance, BigDecimal.ZERO))
            } else {
                expenseTotal = expenseTotal.add(balance)
                closing.add(PeriodCloseLineDraft(account.id, BigDecimal.ZERO, balance))
            }
        }
        val net = revenueTotal.subtract(expenseTotal)
        if (net.signum() > 0) closing.add(PeriodCloseLineDraft(equity.id, BigDecimal.ZERO, net))
        else if (net.signum() < 0) closing.add(PeriodCloseLineDraft(equity.id, net.negate(), BigDecimal.ZERO))
        require(closing.isNotEmpty()) { "لا توجد أرصدة إيرادات أو مصروفات لإغلاقها" }
        require(closing.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.debit) }.compareTo(
            closing.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.credit) }) == 0) { "مسودة قيد الإغلاق غير متوازنة" }
        return PeriodCloseDraft(currency, periodEndInclusive, closing, net)
    }
    private fun decimal(value: String): BigDecimal = try { BigDecimal(value) } catch (e: NumberFormatException) { throw IllegalArgumentException("قيمة قيد غير رقمية", e) }
}