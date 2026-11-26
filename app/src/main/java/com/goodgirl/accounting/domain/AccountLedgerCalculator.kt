package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import java.math.BigDecimal

/**
 * Builds an account statement from posted journal lines.
 * Balances are kept separate by currency; no implicit FX conversion is applied.
 */
object AccountLedgerCalculator {
    data class Movement(
        val journalEntryId: String,
        val journalNumber: String,
        val description: String,
        val occurredAt: Long,
        val currencyCode: String,
        val debit: BigDecimal,
        val credit: BigDecimal,
        val memo: String,
        /** Signed balance: debit minus credit. */
        val runningBalance: BigDecimal
    ) {
        val balanceSide: String
            get() = if (runningBalance.signum() < 0) "CREDIT" else "DEBIT"
    }

    data class CurrencySummary(
        val currencyCode: String,
        val totalDebit: BigDecimal,
        val totalCredit: BigDecimal,
        val closingBalance: BigDecimal,
        val closingSide: String
    )

    data class Statement(
        val movements: List<Movement>,
        val currencySummaries: List<CurrencySummary>
    )

    fun calculate(
        account: AccountEntity,
        entries: List<JournalEntryEntity>,
        lines: List<JournalLineEntity>
    ): Statement {
        val entriesById = entries.associateBy { it.id }
        val relevant = lines.asSequence()
            .filter { it.accountId == account.id }
            .mapNotNull { line -> entriesById[line.journalEntryId]?.let { entry -> entry to line } }
            .sortedWith(compareBy<Pair<JournalEntryEntity, JournalLineEntity>> { it.first.occurredAt }
                .thenBy { it.first.journalNumber }
                .thenBy { it.second.lineNumber })
            .toList()

        val runningByCurrency = linkedMapOf<String, BigDecimal>()
        val debitByCurrency = linkedMapOf<String, BigDecimal>()
        val creditByCurrency = linkedMapOf<String, BigDecimal>()
        val movements = relevant.map { (entry, line) ->
            val debit = line.debit.toBigDecimalOrZero()
            val credit = line.credit.toBigDecimalOrZero()
            require(debit.signum() >= 0 && credit.signum() >= 0) { "قيم المدين والدائن لا يمكن أن تكون سالبة" }
            require(debit.signum() == 0 || credit.signum() == 0) { "لا يجوز أن يجتمع المدين والدائن في السطر نفسه" }

            val currency = entry.currencyCode
            val running = (runningByCurrency[currency] ?: BigDecimal.ZERO)
                .add(debit)
                .subtract(credit)
            runningByCurrency[currency] = running
            debitByCurrency[currency] = (debitByCurrency[currency] ?: BigDecimal.ZERO).add(debit)
            creditByCurrency[currency] = (creditByCurrency[currency] ?: BigDecimal.ZERO).add(credit)

            Movement(
                journalEntryId = entry.id,
                journalNumber = entry.journalNumber,
                description = entry.description,
                occurredAt = entry.occurredAt,
                currencyCode = currency,
                debit = debit,
                credit = credit,
                memo = line.memo,
                runningBalance = running
            )
        }

        val summaries = runningByCurrency.keys.sorted().map { currency ->
            val signed = runningByCurrency.getValue(currency)
            val debit = debitByCurrency[currency] ?: BigDecimal.ZERO
            val credit = creditByCurrency[currency] ?: BigDecimal.ZERO
            CurrencySummary(
                currencyCode = currency,
                totalDebit = debit,
                totalCredit = credit,
                closingBalance = signed.abs(),
                closingSide = if (signed.signum() < 0) "CREDIT" else "DEBIT"
            )
        }
        return Statement(movements, summaries)
    }

    private fun String.toBigDecimalOrZero(): BigDecimal =
        if (isBlank()) BigDecimal.ZERO else toBigDecimal()
}
