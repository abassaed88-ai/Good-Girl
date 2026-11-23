package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import java.math.BigDecimal

data class CashFlowActivityTotals(
    val section: String,
    val cashInflows: BigDecimal,
    val cashOutflows: BigDecimal,
    val netCashFlow: BigDecimal,
    val transactionCount: Int
)

data class CurrencyCashFlowStatement(
    val currencyCode: String,
    val openingCashBalance: BigDecimal,
    val activities: List<CashFlowActivityTotals>,
    val netCashChange: BigDecimal,
    val closingCashBalance: BigDecimal,
    val actualClosingCashBalance: BigDecimal,
    val reconciliationDifference: BigDecimal,
    val unclassifiedTransactionCount: Int
)

data class CashFlowStatement(
    val requestedStartInclusive: Long,
    val effectiveStartInclusive: Long,
    val periodEndInclusive: Long,
    val asOfInclusive: Long,
    val migrationBoundaryInclusive: Long?,
    val coverageAvailable: Boolean,
    val historyTruncated: Boolean,
    val statements: List<CurrencyCashFlowStatement>
)

/**
 * Builds a direct cash-flow statement from posted general-journal lines.
 *
 * Only explicitly recognized source types are classified as operating cash
 * flows. Any other cash movement is preserved under UNCLASSIFIED rather than
 * silently assigned to an operating, investing, or financing activity.
 * Amounts are never converted or combined across currencies.
 *
 * MIGRATION_OPENING represents a balance snapshot from an older database.
 * Transactions before that snapshot are not reconstructable as historical
 * flows. When a requested period predates the snapshot, the statement is
 * transparently shortened to start at the migration boundary.
 */
object CashFlowStatementCalculator {
    const val OPERATING = "OPERATING"
    const val INVESTING = "INVESTING"
    const val FINANCING = "FINANCING"
    const val UNCLASSIFIED = "UNCLASSIFIED"

    private const val MIGRATION_OPENING = "MIGRATION_OPENING"

    fun calculate(
        periodStartInclusive: Long,
        periodEndInclusive: Long,
        asOfInclusive: Long,
        cashAccountIds: Set<String>,
        entries: List<JournalEntryEntity>,
        lines: List<JournalLineEntity>
    ): CashFlowStatement {
        require(periodStartInclusive >= 0L) { "بداية فترة التدفقات النقدية غير صالحة" }
        require(periodEndInclusive >= periodStartInclusive) { "نطاق فترة التدفقات النقدية غير صالح" }
        require(asOfInclusive >= periodEndInclusive) { "تاريخ احتساب التدفقات النقدية لا يجوز أن يسبق نهاية الفترة" }
        require(cashAccountIds.isNotEmpty() && cashAccountIds.none { it.isBlank() }) {
            "يجب تحديد حساب نقدي واحد على الأقل"
        }
        require(entries.map { it.id }.toSet().size == entries.size) {
            "توجد معرّفات مكررة في قيود اليومية"
        }
        require(lines.map { it.id }.toSet().size == lines.size) {
            "توجد معرّفات مكررة في أسطر قيود اليومية"
        }

        val entryIds = entries.map { it.id }.toSet()
        val linesByEntry = lines.groupBy { line ->
            require(line.journalEntryId in entryIds) {
                "يوجد سطر قيد لا يرتبط بقيد يومية موجود"
            }
            line.journalEntryId
        }
        val cutoff = minOf(periodEndInclusive, asOfInclusive)
        val eligibleEntries = entries.filter { it.occurredAt <= cutoff }
        // Look across the full database snapshot, not just the requested period:
        // otherwise a report ending before migration could be mistaken for a
        // complete historical statement with a zero opening balance.
        val migrationBoundary = entries
            .filter { it.sourceType == MIGRATION_OPENING && it.occurredAt <= asOfInclusive }
            .minOfOrNull { it.occurredAt }

        if (migrationBoundary != null && periodEndInclusive < migrationBoundary) {
            return CashFlowStatement(
                requestedStartInclusive = periodStartInclusive,
                effectiveStartInclusive = periodStartInclusive,
                periodEndInclusive = periodEndInclusive,
                asOfInclusive = asOfInclusive,
                migrationBoundaryInclusive = migrationBoundary,
                coverageAvailable = false,
                historyTruncated = false,
                statements = emptyList()
            )
        }

        val effectiveStart = if (migrationBoundary != null && periodStartInclusive < migrationBoundary) {
            migrationBoundary
        } else {
            periodStartInclusive
        }
        val historyTruncated = effectiveStart > periodStartInclusive

        val openingEntries = eligibleEntries.filter { entry ->
            entry.occurredAt < effectiveStart ||
                (entry.sourceType == MIGRATION_OPENING && entry.occurredAt <= effectiveStart)
        }
        val periodEntries = eligibleEntries.filter { entry ->
            entry.occurredAt >= effectiveStart && entry.sourceType != MIGRATION_OPENING
        }

        val openingByCurrency = linkedMapOf<String, BigDecimal>()
        openingEntries.forEach { entry ->
            val netCash = cashMovement(entry, linesByEntry[entry.id].orEmpty(), cashAccountIds)
            if (netCash.compareTo(BigDecimal.ZERO) != 0) {
                openingByCurrency[entry.currencyCode] =
                    (openingByCurrency[entry.currencyCode] ?: BigDecimal.ZERO).add(netCash)
            }
        }

        val flowTotals = linkedMapOf<Pair<String, String>, MutableActivity>()
        periodEntries.forEach { entry ->
            val netCash = cashMovement(entry, linesByEntry[entry.id].orEmpty(), cashAccountIds)
            if (netCash.compareTo(BigDecimal.ZERO) == 0) return@forEach

            val section = classify(entry.sourceType)
            val bucket = flowTotals.getOrPut(entry.currencyCode to section) { MutableActivity() }
            if (netCash.signum() > 0) {
                bucket.inflows = bucket.inflows.add(netCash)
            } else {
                bucket.outflows = bucket.outflows.add(netCash.negate())
            }
            bucket.transactionCount += 1
        }

        val actualClosingByCurrency = linkedMapOf<String, BigDecimal>()
        eligibleEntries.forEach { entry ->
            val netCash = cashMovement(entry, linesByEntry[entry.id].orEmpty(), cashAccountIds)
            if (netCash.compareTo(BigDecimal.ZERO) != 0) {
                actualClosingByCurrency[entry.currencyCode] =
                    (actualClosingByCurrency[entry.currencyCode] ?: BigDecimal.ZERO).add(netCash)
            }
        }

        val currencies = (
            openingByCurrency.keys +
                flowTotals.keys.map { it.first } +
                actualClosingByCurrency.keys
            ).toSortedSet()

        val statements = currencies.map { currency ->
            val activities = listOf(OPERATING, INVESTING, FINANCING, UNCLASSIFIED).map { section ->
                val totals = flowTotals[currency to section] ?: MutableActivity()
                CashFlowActivityTotals(
                    section = section,
                    cashInflows = totals.inflows,
                    cashOutflows = totals.outflows,
                    netCashFlow = totals.inflows.subtract(totals.outflows),
                    transactionCount = totals.transactionCount
                )
            }
            val opening = openingByCurrency[currency] ?: BigDecimal.ZERO
            val netChange = activities.fold(BigDecimal.ZERO) { sum, activity ->
                sum.add(activity.netCashFlow)
            }
            val closing = opening.add(netChange)
            val actualClosing = actualClosingByCurrency[currency] ?: BigDecimal.ZERO

            CurrencyCashFlowStatement(
                currencyCode = currency,
                openingCashBalance = opening,
                activities = activities,
                netCashChange = netChange,
                closingCashBalance = closing,
                actualClosingCashBalance = actualClosing,
                reconciliationDifference = actualClosing.subtract(closing),
                unclassifiedTransactionCount = activities
                    .first { it.section == UNCLASSIFIED }.transactionCount
            )
        }

        return CashFlowStatement(
            requestedStartInclusive = periodStartInclusive,
            effectiveStartInclusive = effectiveStart,
            periodEndInclusive = periodEndInclusive,
            asOfInclusive = asOfInclusive,
            migrationBoundaryInclusive = migrationBoundary,
            coverageAvailable = true,
            historyTruncated = historyTruncated,
            statements = statements
        )
    }

    private data class MutableActivity(
        var inflows: BigDecimal = BigDecimal.ZERO,
        var outflows: BigDecimal = BigDecimal.ZERO,
        var transactionCount: Int = 0
    )

    private fun classify(sourceType: String): String = when (sourceType.trim().uppercase()) {
        "SALE_INVOICE",
        "PURCHASE_INVOICE",
        "CUSTOMER_RECEIPT",
        "SUPPLIER_PAYMENT",
        "MANUAL_CASH_INCOME",
        "MANUAL_CASH_EXPENSE" -> OPERATING
        else -> UNCLASSIFIED
    }

    private fun cashMovement(
        entry: JournalEntryEntity,
        entryLines: List<JournalLineEntity>,
        cashAccountIds: Set<String>
    ): BigDecimal {
        require(entry.currencyCode.isNotBlank()) { "عملة قيد اليومية غير محددة" }
        var debitTotal = BigDecimal.ZERO
        var creditTotal = BigDecimal.ZERO

        entryLines.filter { it.accountId in cashAccountIds }.forEach { line ->
            val debit = amount(line.debit, "مدين الصندوق")
            val credit = amount(line.credit, "دائن الصندوق")
            require((debit.signum() == 0) != (credit.signum() == 0)) {
                "سطر الصندوق يجب أن يحتوي على مبلغ في جهة واحدة فقط"
            }
            debitTotal = debitTotal.add(debit)
            creditTotal = creditTotal.add(credit)
        }
        return debitTotal.subtract(creditTotal)
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
}
