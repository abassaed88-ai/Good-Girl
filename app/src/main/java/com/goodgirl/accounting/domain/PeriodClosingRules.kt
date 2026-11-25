package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import java.math.BigDecimal

data class PeriodCloseLineDraft(
    val accountId: String,
    val debit: BigDecimal,
    val credit: BigDecimal,
    val memo: String
)
data class PeriodCloseDraft(
    val currencyCode: String,
    val periodStartInclusive: Long,
    val periodEndInclusive: Long,
    val lines: List<PeriodCloseLineDraft>,
    val netIncome: BigDecimal
)

/** Builds a balanced, single-currency closing journal for one explicit period. */
object PeriodClosingRules {
    fun build(
        periodStartInclusive: Long,
        periodEndInclusive: Long,
        currencyCode: String,
        accounts: List<AccountEntity>,
        entries: List<JournalEntryEntity>,
        lines: List<JournalLineEntity>,
        retainedEarningsAccountId: String
    ): PeriodCloseDraft {
        require(periodStartInclusive >= 0L) { "بداية فترة الإغلاق غير صالحة" }
        require(periodEndInclusive >= periodStartInclusive) { "نطاق فترة الإغلاق غير صالح" }
        val currency = currencyCode.trim().uppercase()
        require(currency == "SYP" || currency == "USD") { "عملة الإغلاق غير مدعومة" }
        require(accounts.map { it.id }.toSet().size == accounts.size) { "معرّفات الحسابات مكررة" }
        require(entries.map { it.id }.toSet().size == entries.size) { "معرّفات قيود اليومية مكررة" }
        require(lines.map { it.id }.toSet().size == lines.size) { "معرّفات أسطر اليومية مكررة" }

        val byId = accounts.associateBy { it.id }
        val equity = byId[retainedEarningsAccountId]
            ?: throw IllegalArgumentException("حساب الأرباح المحتجزة غير موجود")
        require(equity.accountType == "EQUITY" && equity.isActive) {
            "حساب الأرباح المحتجزة يجب أن يكون حساب حقوق ملكية فعالاً"
        }

        val included = entries.filter {
            it.currencyCode == currency &&
                it.occurredAt in periodStartInclusive..periodEndInclusive &&
                it.sourceType != "PERIOD_CLOSE"
        }
        val entryIds = included.map { it.id }.toSet()
        val linesByEntry = lines.filter { it.journalEntryId in entryIds }.groupBy { it.journalEntryId }
        val netByAccount = mutableMapOf<String, BigDecimal>()

        included.forEach { entry ->
            val entryLines = linesByEntry[entry.id].orEmpty()
            require(entryLines.size >= 2) { "القيد ${entry.journalNumber} غير مكتمل" }
            var debits = BigDecimal.ZERO
            var credits = BigDecimal.ZERO
            entryLines.forEach { line ->
                val account = byId[line.accountId]
                    ?: throw IllegalArgumentException("حساب قيد غير موجود")
                val debit = decimal(line.debit)
                val credit = decimal(line.credit)
                require((debit.signum() > 0) xor (credit.signum() > 0)) { "سطر القيد غير صالح" }
                debits = debits.add(debit)
                credits = credits.add(credit)
                val normalBalanceChange = when (account.accountType) {
                    "ASSET", "EXPENSE" -> debit.subtract(credit)
                    "LIABILITY", "EQUITY", "REVENUE" -> credit.subtract(debit)
                    else -> throw IllegalArgumentException("نوع حساب غير مدعوم: ${account.accountType}")
                }
                netByAccount[line.accountId] = (netByAccount[line.accountId] ?: BigDecimal.ZERO)
                    .add(normalBalanceChange)
            }
            require(debits.compareTo(credits) == 0) { "القيد ${entry.journalNumber} غير متوازن" }
        }

        val closing = mutableListOf<PeriodCloseLineDraft>()
        var netIncome = BigDecimal.ZERO
        accounts.filter { it.accountType == "REVENUE" || it.accountType == "EXPENSE" }
            .sortedBy { it.code }
            .forEach { account ->
                val balance = netByAccount[account.id] ?: BigDecimal.ZERO
                if (balance.signum() == 0) return@forEach
                if (account.accountType == "REVENUE") netIncome = netIncome.add(balance)
                else netIncome = netIncome.subtract(balance)

                val closeDebit = if (account.accountType == "REVENUE") balance else balance.negate()
                val closeCredit = closeDebit.negate()
                closing += PeriodCloseLineDraft(
                    accountId = account.id,
                    debit = closeDebit.max(BigDecimal.ZERO),
                    credit = closeCredit.max(BigDecimal.ZERO),
                    memo = "إقفال رصيد ${account.name}"
                )
            }

        require(closing.isNotEmpty()) { "لا توجد أرصدة إيرادات أو مصروفات ضمن الفترة لإغلاقها" }
        if (netIncome.signum() > 0) {
            closing += PeriodCloseLineDraft(equity.id, BigDecimal.ZERO, netIncome, "ترحيل صافي ربح الفترة")
        } else if (netIncome.signum() < 0) {
            closing += PeriodCloseLineDraft(equity.id, netIncome.negate(), BigDecimal.ZERO, "ترحيل صافي خسارة الفترة")
        }

        val debitTotal = closing.fold(BigDecimal.ZERO) { total, line -> total.add(line.debit) }
        val creditTotal = closing.fold(BigDecimal.ZERO) { total, line -> total.add(line.credit) }
        require(debitTotal.compareTo(creditTotal) == 0) { "مسودة قيد الإغلاق غير متوازنة" }
        return PeriodCloseDraft(currency, periodStartInclusive, periodEndInclusive, closing, netIncome)
    }

    private fun decimal(value: String): BigDecimal = try {
        BigDecimal(value).also { require(it.signum() >= 0) { "لا يجوز أن يكون مبلغ القيد سالباً" } }
    } catch (error: NumberFormatException) {
        throw IllegalArgumentException("قيمة قيد غير رقمية", error)
    }
}