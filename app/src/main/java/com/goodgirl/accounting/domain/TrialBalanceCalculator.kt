package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import java.math.BigDecimal

data class TrialBalanceRow(
    val accountId: String,
    val accountCode: String,
    val accountName: String,
    val accountType: String,
    val currencyCode: String,
    val totalDebit: BigDecimal,
    val totalCredit: BigDecimal,
    val debitBalance: BigDecimal,
    val creditBalance: BigDecimal
)

data class TrialBalanceCurrencySummary(
    val currencyCode: String,
    val totalDebits: BigDecimal,
    val totalCredits: BigDecimal,
    val debitBalances: BigDecimal,
    val creditBalances: BigDecimal
)

data class TrialBalance(
    val asOfInclusive: Long,
    val rows: List<TrialBalanceRow>,
    val currencySummaries: List<TrialBalanceCurrencySummary>
)

/**
 * Calculates a trial balance from posted general-journal rows.
 *
 * Currency amounts are deliberately kept in separate groups. Exchange-rate
 * snapshots are retained on journal headers, but this calculator never
 * converts or adds SYP and USD amounts together.
 */
object TrialBalanceCalculator {
    fun calculate(
        asOfInclusive: Long,
        accounts: List<AccountEntity>,
        entries: List<JournalEntryEntity>,
        lines: List<JournalLineEntity>
    ): TrialBalance {
        require(asOfInclusive >= 0L) { "تاريخ ميزان المراجعة غير صالح" }
        require(accounts.map { it.id }.toSet().size == accounts.size) {
            "توجد معرّفات مكررة في دليل الحسابات"
        }
        require(entries.map { it.id }.toSet().size == entries.size) {
            "توجد معرّفات مكررة في قيود اليومية"
        }
        require(lines.map { it.id }.toSet().size == lines.size) {
            "توجد معرّفات مكررة في أسطر قيود اليومية"
        }

        val accountsById = accounts.associateBy { it.id }
        val entriesById = entries.associateBy { it.id }
        val linesByEntry = lines.groupBy { line ->
            require(entriesById.containsKey(line.journalEntryId)) {
                "يوجد سطر قيد لا يرتبط بقيد يومية موجود"
            }
            line.journalEntryId
        }

        val totals = linkedMapOf<Pair<String, String>, MutableList<BigDecimal>>()
        val includedEntries = entries.filter { it.occurredAt <= asOfInclusive }

        includedEntries.forEach { entry ->
            val entryLines = linesByEntry[entry.id].orEmpty()
            require(entryLines.size >= 2) {
                "قيد اليومية ${entry.journalNumber} لا يحتوي على سطرين محاسبيين على الأقل"
            }

            var entryDebit = BigDecimal.ZERO
            var entryCredit = BigDecimal.ZERO
            entryLines.forEach { line ->
                val account = accountsById[line.accountId]
                    ?: throw IllegalArgumentException("أحد أسطر القيد مرتبط بحساب غير موجود")
                val debit = amount(line.debit, "مدين القيد ${entry.journalNumber}")
                val credit = amount(line.credit, "دائن القيد ${entry.journalNumber}")
                require(debit.signum() == 0 || credit.signum() == 0) {
                    "لا يجوز أن يحتوي سطر القيد على مبلغ مدين ودائن معاً"
                }
                require(debit.signum() > 0 || credit.signum() > 0) {
                    "لا يجوز أن يكون سطر القيد صفراً من الجهتين"
                }

                entryDebit = entryDebit.add(debit)
                entryCredit = entryCredit.add(credit)
                val key = account.id to entry.currencyCode
                val bucket = totals.getOrPut(key) {
                    mutableListOf(BigDecimal.ZERO, BigDecimal.ZERO)
                }
                bucket[0] = bucket[0].add(debit)
                bucket[1] = bucket[1].add(credit)
            }
            require(entryDebit.compareTo(entryCredit) == 0) {
                "قيد اليومية ${entry.journalNumber} غير متوازن"
            }
        }

        val rows = totals.map { (key, values) ->
            val account = accountsById.getValue(key.first)
            val net = values[0].subtract(values[1])
            TrialBalanceRow(
                accountId = account.id,
                accountCode = account.code,
                accountName = account.name,
                accountType = account.accountType,
                currencyCode = key.second,
                totalDebit = values[0],
                totalCredit = values[1],
                debitBalance = net.max(BigDecimal.ZERO),
                creditBalance = net.negate().max(BigDecimal.ZERO)
            )
        }.sortedWith(compareBy<TrialBalanceRow> { it.currencyCode }.thenBy { it.accountCode })

        val summaries = rows.groupBy { it.currencyCode }.map { (currency, currencyRows) ->
            TrialBalanceCurrencySummary(
                currencyCode = currency,
                totalDebits = sum(currencyRows) { it.totalDebit },
                totalCredits = sum(currencyRows) { it.totalCredit },
                debitBalances = sum(currencyRows) { it.debitBalance },
                creditBalances = sum(currencyRows) { it.creditBalance }
            )
        }.sortedBy { it.currencyCode }

        summaries.forEach { summary ->
            require(summary.totalDebits.compareTo(summary.totalCredits) == 0) {
                "إجمالي الحركات المدينة والدائنة غير متوازن للعملة ${summary.currencyCode}"
            }
            require(summary.debitBalances.compareTo(summary.creditBalances) == 0) {
                "أرصدة ميزان المراجعة غير متوازنة للعملة ${summary.currencyCode}"
            }
        }

        return TrialBalance(
            asOfInclusive = asOfInclusive,
            rows = rows,
            currencySummaries = summaries
        )
    }

    private fun amount(raw: String, label: String): BigDecimal {
        val value = try {
            BigDecimal(raw)
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException("$label يحتوي على قيمة غير رقمية", error)
        }
        require(value.signum() >= 0) { "$label لا يجوز أن يكون سالباً" }
        return value
    }

    private fun sum(
        rows: List<TrialBalanceRow>,
        selector: (TrialBalanceRow) -> BigDecimal
    ): BigDecimal = rows.fold(BigDecimal.ZERO) { total, row -> total.add(selector(row)) }
}
