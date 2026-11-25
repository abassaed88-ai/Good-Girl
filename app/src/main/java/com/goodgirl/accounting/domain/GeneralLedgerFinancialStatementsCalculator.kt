package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import java.math.BigDecimal

data class StatementAccountAmount(
    val accountId: String,
    val accountCode: String,
    val accountName: String,
    val amount: BigDecimal
)

data class CurrencyBalanceSheet(
    val currencyCode: String,
    val assets: List<StatementAccountAmount>,
    val liabilities: List<StatementAccountAmount>,
    val equity: List<StatementAccountAmount>,
    val totalAssets: BigDecimal,
    val totalLiabilities: BigDecimal,
    val totalEquity: BigDecimal,
    /** Accumulated revenue less expenses through the balance-sheet date. */
    val retainedEarnings: BigDecimal,
    val totalLiabilitiesAndEquity: BigDecimal
)

data class CurrencyIncomeStatement(
    val currencyCode: String,
    val revenues: List<StatementAccountAmount>,
    val expenses: List<StatementAccountAmount>,
    val totalRevenue: BigDecimal,
    val totalExpenses: BigDecimal,
    val netIncome: BigDecimal
)

data class GeneralLedgerFinancialStatements(
    val periodStartInclusive: Long,
    val periodEndInclusive: Long,
    val asOfInclusive: Long,
    val balanceSheets: List<CurrencyBalanceSheet>,
    val incomeStatements: List<CurrencyIncomeStatement>
)

/**
 * Produces financial statements exclusively from posted general-journal rows.
 * Amounts remain separated by currency; this calculator never converts them.
 *
 * Before a formal close, accumulated revenue less expenses is presented as
 * retained earnings. After a PERIOD_CLOSE journal, those balances are cleared
 * and the closing amount remains in the selected equity account. Closing
 * journals are excluded from the period income-statement activity totals.
 */
object GeneralLedgerFinancialStatementsCalculator {
    fun calculate(
        periodStartInclusive: Long,
        periodEndInclusive: Long,
        asOfInclusive: Long,
        accounts: List<AccountEntity>,
        entries: List<JournalEntryEntity>,
        lines: List<JournalLineEntity>
    ): GeneralLedgerFinancialStatements {
        require(periodStartInclusive >= 0L) { "بداية فترة التقرير غير صالحة" }
        require(periodEndInclusive >= periodStartInclusive) { "نطاق فترة التقرير غير صالح" }
        require(asOfInclusive >= periodEndInclusive) { "تاريخ الميزانية لا يجوز أن يسبق نهاية فترة التقرير" }
        require(accounts.map { it.id }.toSet().size == accounts.size) { "توجد معرّفات حسابات مكررة" }
        require(entries.map { it.id }.toSet().size == entries.size) { "توجد معرّفات قيود مكررة" }
        require(lines.map { it.id }.toSet().size == lines.size) { "توجد معرّفات أسطر قيود مكررة" }

        val accountById = accounts.associateBy { it.id }
        val entryById = entries.associateBy { it.id }
        val linesByEntry = lines.groupBy { line ->
            require(entryById.containsKey(line.journalEntryId)) { "يوجد سطر قيد دون قيد يومية مرتبط" }
            line.journalEntryId
        }
        val includedEntries = entries.filter { it.occurredAt <= asOfInclusive }
        val reversedCloseIds = includedEntries.asSequence()
            .filter { it.sourceType == "PERIOD_CLOSE_REVERSAL" }
            .map { it.sourceId }
            .toSet()
        val balances = linkedMapOf<Triple<String, String, String>, MutableList<BigDecimal>>()
        val periodFlows = linkedMapOf<Triple<String, String, String>, MutableList<BigDecimal>>()

        includedEntries.forEach { entry ->
            val entryLines = linesByEntry[entry.id].orEmpty()
            require(entryLines.size >= 2) { "قيد اليومية ${entry.journalNumber} لا يحتوي على سطرين على الأقل" }
            var debitTotal = BigDecimal.ZERO
            var creditTotal = BigDecimal.ZERO

            entryLines.forEach { line ->
                val account = accountById[line.accountId]
                    ?: throw IllegalArgumentException("يوجد سطر قيد مرتبط بحساب غير موجود")
                val debit = amount(line.debit, "المدين")
                val credit = amount(line.credit, "الدائن")
                require((debit.signum() == 0) != (credit.signum() == 0)) {
                    "يجب أن يكون أحد طرفي سطر القيد صفراً فقط"
                }
                debitTotal = debitTotal.add(debit)
                creditTotal = creditTotal.add(credit)

                val key = Triple(account.id, account.accountType, entry.currencyCode)
                val balance = balances.getOrPut(key) { mutableListOf(BigDecimal.ZERO, BigDecimal.ZERO) }
                balance[0] = balance[0].add(debit)
                balance[1] = balance[1].add(credit)

                if (
                    entry.occurredAt in periodStartInclusive..periodEndInclusive &&
                    entry.sourceType != "PERIOD_CLOSE" &&
                    entry.sourceType != "PERIOD_CLOSE_REVERSAL"
                ) {
                    val flow = periodFlows.getOrPut(key) { mutableListOf(BigDecimal.ZERO, BigDecimal.ZERO) }
                    flow[0] = flow[0].add(debit)
                    flow[1] = flow[1].add(credit)
                }
            }
            require(debitTotal.compareTo(creditTotal) == 0) {
                "قيد اليومية ${entry.journalNumber} غير متوازن"
            }
        }

        val currencies = (balances.keys.map { it.third } + periodFlows.keys.map { it.third }).toSortedSet()
        val balanceSheets = currencies.map { currency ->
            val rows = balances.filterKeys { it.third == currency }.map { (key, totals) ->
                val account = accountById.getValue(key.first)
                StatementAccountAmount(
                    accountId = account.id,
                    accountCode = account.code,
                    accountName = account.name,
                    amount = statementAmount(account.accountType, totals[0], totals[1])
                )
            }.filter { it.amount.compareTo(BigDecimal.ZERO) != 0 }
            val assets = rows.filter { accountType(it.accountId, accounts) == "ASSET" }
            val liabilities = rows.filter { accountType(it.accountId, accounts) == "LIABILITY" }
            val equity = rows.filter { accountType(it.accountId, accounts) == "EQUITY" }
            val revenueAndExpense = balances.filterKeys { it.third == currency }.map { (key, totals) ->
                val type = key.second
                when (type) {
                    "REVENUE" -> totals[1].subtract(totals[0])
                    // Expenses reduce accumulated earnings; they must be subtracted.
                    "EXPENSE" -> totals[1].subtract(totals[0])
                    else -> BigDecimal.ZERO
                }
            }.fold(BigDecimal.ZERO, BigDecimal::add)
            val closedEarnings = includedEntries
                .filter {
                    it.currencyCode == currency &&
                        it.sourceType == "PERIOD_CLOSE" &&
                        it.id !in reversedCloseIds
                }
                .fold(BigDecimal.ZERO) { total, entry ->
                    val equityMovement = linesByEntry[entry.id].orEmpty().fold(BigDecimal.ZERO) { subtotal, line ->
                        val account = accountById.getValue(line.accountId)
                        if (account.accountType == "EQUITY") {
                            subtotal.add(BigDecimal(line.credit)).subtract(BigDecimal(line.debit))
                        } else {
                            subtotal
                        }
                    }
                    total.add(equityMovement)
                }
            val cumulativeRetainedEarnings = closedEarnings.add(revenueAndExpense)
            val totalAssets = sum(assets)
            val totalLiabilities = sum(liabilities)
            val totalEquity = sum(equity)
            // Closed earnings are already part of equity; only unclosed P&L
            // balances are added here to preserve the balance-sheet equation.
            val totalLiabilitiesAndEquity = totalLiabilities.add(totalEquity).add(revenueAndExpense)
            CurrencyBalanceSheet(
                currencyCode = currency,
                assets = assets.sortedBy { it.accountCode },
                liabilities = liabilities.sortedBy { it.accountCode },
                equity = equity.sortedBy { it.accountCode },
                totalAssets = totalAssets,
                totalLiabilities = totalLiabilities,
                totalEquity = totalEquity,
                retainedEarnings = cumulativeRetainedEarnings,
                totalLiabilitiesAndEquity = totalLiabilitiesAndEquity
            )
        }

        val incomeStatements = currencies.map { currency ->
            val rows = periodFlows.filterKeys { it.third == currency }.map { (key, totals) ->
                val account = accountById.getValue(key.first)
                StatementAccountAmount(
                    accountId = account.id,
                    accountCode = account.code,
                    accountName = account.name,
                    amount = statementAmount(account.accountType, totals[0], totals[1])
                )
            }
            val revenues = rows.filter { accountType(it.accountId, accounts) == "REVENUE" }
                .filter { it.amount.compareTo(BigDecimal.ZERO) != 0 }.sortedBy { it.accountCode }
            val expenses = rows.filter { accountType(it.accountId, accounts) == "EXPENSE" }
                .filter { it.amount.compareTo(BigDecimal.ZERO) != 0 }.sortedBy { it.accountCode }
            val totalRevenue = sum(revenues)
            val totalExpenses = sum(expenses)
            CurrencyIncomeStatement(
                currencyCode = currency,
                revenues = revenues,
                expenses = expenses,
                totalRevenue = totalRevenue,
                totalExpenses = totalExpenses,
                netIncome = totalRevenue.subtract(totalExpenses)
            )
        }

        return GeneralLedgerFinancialStatements(
            periodStartInclusive = periodStartInclusive,
            periodEndInclusive = periodEndInclusive,
            asOfInclusive = asOfInclusive,
            balanceSheets = balanceSheets,
            incomeStatements = incomeStatements
        )
    }

    private fun statementAmount(type: String, debit: BigDecimal, credit: BigDecimal): BigDecimal =
        when (type) {
            "ASSET", "EXPENSE" -> debit.subtract(credit)
            "LIABILITY", "EQUITY", "REVENUE" -> credit.subtract(debit)
            else -> throw IllegalArgumentException("نوع حساب غير مدعوم في القوائم المالية: $type")
        }

    private fun accountType(id: String, accounts: List<AccountEntity>): String =
        accounts.firstOrNull { it.id == id }?.accountType
            ?: throw IllegalArgumentException("الحساب غير موجود")

    private fun amount(raw: String, label: String): BigDecimal {
        val value = try { BigDecimal(raw) } catch (error: NumberFormatException) {
            throw IllegalArgumentException("قيمة $label غير رقمية", error)
        }
        require(value.signum() >= 0) { "قيمة $label لا يجوز أن تكون سالبة" }
        return value
    }

    private fun sum(rows: List<StatementAccountAmount>): BigDecimal =
        rows.fold(BigDecimal.ZERO) { total, row -> total.add(row.amount) }
}
