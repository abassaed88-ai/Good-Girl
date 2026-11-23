package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import java.math.BigDecimal

data class AccountLedgerMovement(
    val journalEntryId: String,
    val journalNumber: String,
    val occurredAt: Long,
    val description: String,
    val currencyCode: String,
    val debit: BigDecimal,
    val credit: BigDecimal,
    val memo: String,
    /** Positive values are on the account's normal side; negative values are contra balances. */
    val runningBalance: BigDecimal,
    val balanceSide: String
)

data class AccountLedgerCurrencySummary(
    val currencyCode: String,
    val totalDebit: BigDecimal,
    val totalCredit: BigDecimal,
    val closingBalance: BigDecimal,
    val closingSide: String
)

data class AccountLedgerStatement(
    val accountId: String,
    val movements: List<AccountLedgerMovement>,
    val currencySummaries: List<AccountLedgerCurrencySummary>
)

/**
 * Builds an account ledger from the immutable general journal.
 * Each currency is accumulated independently and no conversion is performed.
 */
object AccountLedgerCalculator {
    fun calculate(
        account: AccountEntity,
        entries: List<JournalEntryEntity>,
        lines: List<JournalLineEntity>,
        asOfInclusive: Long = Long.MAX_VALUE
    ): AccountLedgerStatement {
        require(asOfInclusive >= 0L) { "تاريخ كشف الحساب غير صالح" }
        require(account.normalSide == "DEBIT" || account.normalSide == "CREDIT") {
            "الطبيعة المحاسبية للحساب غير صالحة"
        }
        require(entries.map { it.id }.toSet().size == entries.size) {
            "توجد معرّفات مكررة في قيود اليومية"
        }
        require(lines.map { it.id }.toSet().size == lines.size) {
            "توجد معرّفات مكررة في أسطر قيود اليومية"
        }

        val entriesById = entries.associateBy { it.id }
        val relevant = lines.asSequence()
            .filter { it.accountId == account.id }
            .mapNotNull { line ->
                val entry = entriesById[line.journalEntryId]
                    ?: throw IllegalArgumentException("يوجد سطر حساب بلا قيد يومية مرتبط")
                if (entry.occurredAt > asOfInclusive) return@mapNotNull null

                require(entry.currencyCode == "SYP" || entry.currencyCode == "USD") {
                    "عملة قيد اليومية غير مدعومة"
                }
                val debit = amount(line.debit, "مدين")
                val credit = amount(line.credit, "دائن")
                require((debit.signum() > 0) xor (credit.signum() > 0)) {
                    "سطر كشف الحساب يجب أن يحتوي على مبلغ مدين أو دائن فقط"
                }
                Triple(entry, line, debit to credit)
            }
            .sortedWith(
                compareBy<Triple<JournalEntryEntity, JournalLineEntity, Pair<BigDecimal, BigDecimal>>> { it.first.occurredAt }
                    .thenBy { it.first.journalNumber }
                    .thenBy { it.second.lineNumber }
            )
            .toList()

        val movements = mutableListOf<AccountLedgerMovement>()
        val summaries = relevant.groupBy { it.first.currencyCode }
            .toSortedMap()
            .map { (currency, rows) ->
                var runningBalance = BigDecimal.ZERO
                var totalDebit = BigDecimal.ZERO
                var totalCredit = BigDecimal.ZERO

                rows.forEach { (entry, line, amounts) ->
                    val debit = amounts.first
                    val credit = amounts.second
                    totalDebit = totalDebit.add(debit)
                    totalCredit = totalCredit.add(credit)
                    val delta = if (account.normalSide == "DEBIT") debit.subtract(credit) else credit.subtract(debit)
                    runningBalance = runningBalance.add(delta)
                    movements += AccountLedgerMovement(
                        journalEntryId = entry.id,
                        journalNumber = entry.journalNumber,
                        occurredAt = entry.occurredAt,
                        description = entry.description,
                        currencyCode = currency,
                        debit = debit,
                        credit = credit,
                        memo = line.memo,
                        runningBalance = runningBalance,
                        balanceSide = sideForBalance(runningBalance, account.normalSide)
                    )
                }

                AccountLedgerCurrencySummary(
                    currencyCode = currency,
                    totalDebit = totalDebit,
                    totalCredit = totalCredit,
                    closingBalance = runningBalance.abs(),
                    closingSide = sideForBalance(runningBalance, account.normalSide)
                )
            }

        val orderedMovements = movements.sortedWith(
            compareBy<AccountLedgerMovement> { it.occurredAt }
                .thenBy { it.journalNumber }
                .thenBy { it.currencyCode }
        )
        return AccountLedgerStatement(account.id, orderedMovements, summaries)
    }

    private fun sideForBalance(balance: BigDecimal, normalSide: String): String {
        if (balance.signum() == 0) return normalSide
        if (balance.signum() > 0) return normalSide
        return if (normalSide == "DEBIT") "CREDIT" else "DEBIT"
    }

    private fun amount(raw: String, label: String): BigDecimal {
        val value = try {
            BigDecimal(raw)
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException("قيمة $label في كشف الحساب غير صالحة", error)
        }
        require(value.signum() >= 0) { "قيمة $label في كشف الحساب لا يجوز أن تكون سالبة" }
        return value
    }
}
