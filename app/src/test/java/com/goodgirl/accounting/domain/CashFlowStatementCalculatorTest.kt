package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CashFlowStatementCalculatorTest {
    private fun entry(id: String, sourceType: String, currency: String = "SYP", at: Long) =
        JournalEntryEntity(
            id = id,
            journalNumber = id,
            sourceType = sourceType,
            sourceId = id,
            currencyCode = currency,
            exchangeRateSnapshot = "1",
            occurredAt = at,
            description = id,
            createdAt = at
        )

    private fun line(id: String, entryId: String, accountId: String, debit: String, credit: String) =
        JournalLineEntity(id, entryId, 1, accountId, debit, credit, "")

    private val entries = listOf(
        entry("opening-syp", "MIGRATION_OPENING", at = 10L),
        entry("opening-usd", "MIGRATION_OPENING", currency = "USD", at = 10L),
        entry("sale", "SALE_INVOICE", at = 30L),
        entry("purchase", "PURCHASE_INVOICE", at = 31L),
        entry("receipt", "CUSTOMER_RECEIPT", at = 32L),
        entry("supplier-payment", "SUPPLIER_PAYMENT", at = 33L),
        entry("manual-income", "MANUAL_CASH_INCOME", at = 34L),
        entry("manual-expense", "MANUAL_CASH_EXPENSE", at = 35L),
        entry("manual-journal", "MANUAL_JOURNAL", at = 36L),
        entry("usd-sale", "SALE_INVOICE", currency = "USD", at = 37L)
    )

    private val lines = listOf(
        line("l1", "opening-syp", "SYS-1000", "1000", "0"),
        line("l2", "opening-syp", "SYS-3000", "0", "1000"),
        line("l3", "opening-usd", "SYS-1000", "100", "0"),
        line("l4", "opening-usd", "SYS-3000", "0", "100"),
        line("l5", "sale", "SYS-1000", "150", "0"),
        line("l6", "sale", "SYS-4000", "0", "150"),
        line("l7", "purchase", "SYS-1200", "50", "0"),
        line("l8", "purchase", "SYS-1000", "0", "50"),
        line("l9", "receipt", "SYS-1000", "40", "0"),
        line("l10", "receipt", "SYS-1100", "0", "40"),
        line("l11", "supplier-payment", "SYS-2000", "15", "0"),
        line("l12", "supplier-payment", "SYS-1000", "0", "15"),
        line("l13", "manual-income", "SYS-1000", "20", "0"),
        line("l14", "manual-income", "SYS-6000", "0", "20"),
        line("l15", "manual-expense", "SYS-7000", "10", "0"),
        line("l16", "manual-expense", "SYS-1000", "0", "10"),
        line("l17", "manual-journal", "SYS-1000", "5", "0"),
        line("l18", "manual-journal", "SYS-2000", "0", "5"),
        line("l19", "usd-sale", "SYS-1000", "25", "0"),
        line("l20", "usd-sale", "SYS-4000", "0", "25")
    )

    @Test
    fun separatesCurrenciesAndClassifiesRecognizedCashMovements() {
        val statement = CashFlowStatementCalculator.calculate(
            periodStartInclusive = 30L,
            periodEndInclusive = 40L,
            asOfInclusive = 40L,
            cashAccountIds = setOf("SYS-1000"),
            entries = entries,
            lines = lines
        )

        assertTrue(statement.coverageAvailable)
        assertFalse(statement.historyTruncated)

        val syp = statement.statements.single { it.currencyCode == "SYP" }
        assertEquals(BigDecimal("1000"), syp.openingCashBalance)
        val operating = syp.activities.single { it.section == CashFlowStatementCalculator.OPERATING }
        assertEquals(BigDecimal("210"), operating.cashInflows)
        assertEquals(BigDecimal("75"), operating.cashOutflows)
        assertEquals(BigDecimal("135"), operating.netCashFlow)

        val unclassified = syp.activities.single { it.section == CashFlowStatementCalculator.UNCLASSIFIED }
        assertEquals(BigDecimal("5"), unclassified.cashInflows)
        assertEquals(1, syp.unclassifiedTransactionCount)
        assertEquals(BigDecimal("140"), syp.netCashChange)
        assertEquals(BigDecimal("1140"), syp.closingCashBalance)
        assertEquals(BigDecimal("1140"), syp.actualClosingCashBalance)
        assertEquals(BigDecimal.ZERO, syp.reconciliationDifference)

        val usd = statement.statements.single { it.currencyCode == "USD" }
        assertEquals(BigDecimal("100"), usd.openingCashBalance)
        assertEquals(BigDecimal("25"), usd.netCashChange)
        assertEquals(BigDecimal("125"), usd.closingCashBalance)
    }

    @Test
    fun shortensPeriodToMigrationBoundaryWhenOlderCashHistoryCannotBeReconstructed() {
        val statement = CashFlowStatementCalculator.calculate(
            periodStartInclusive = 5L,
            periodEndInclusive = 40L,
            asOfInclusive = 40L,
            cashAccountIds = setOf("SYS-1000"),
            entries = entries,
            lines = lines
        )

        assertTrue(statement.coverageAvailable)
        assertTrue(statement.historyTruncated)
        assertEquals(10L, statement.effectiveStartInclusive)
        val syp = statement.statements.single { it.currencyCode == "SYP" }
        assertEquals(BigDecimal("1000"), syp.openingCashBalance)
        assertEquals(BigDecimal("140"), syp.netCashChange)
        assertEquals(BigDecimal("1140"), syp.closingCashBalance)
    }

    @Test
    fun refusesToProduceHistoricalStatementBeforeLegacyOpeningSnapshot() {
        val statement = CashFlowStatementCalculator.calculate(
            periodStartInclusive = 1L,
            periodEndInclusive = 9L,
            asOfInclusive = 40L,
            cashAccountIds = setOf("SYS-1000"),
            entries = entries,
            lines = lines
        )

        assertFalse(statement.coverageAvailable)
        assertTrue(statement.statements.isEmpty())
        assertEquals(10L, statement.migrationBoundaryInclusive)
    }

    @Test
    fun rejectsInvalidCashLineAmountsAndOrphanJournalLines() {
        val invalidCashLine = line("bad", "sale", "SYS-1000", "-1", "0")
        assertThrows(IllegalArgumentException::class.java) {
            CashFlowStatementCalculator.calculate(
                30L, 40L, 40L, setOf("SYS-1000"), entries, lines + invalidCashLine
            )
        }

        val orphan = line("orphan", "missing-entry", "SYS-1000", "1", "0")
        assertThrows(IllegalArgumentException::class.java) {
            CashFlowStatementCalculator.calculate(
                30L, 40L, 40L, setOf("SYS-1000"), entries, lines + orphan
            )
        }
    }
}
