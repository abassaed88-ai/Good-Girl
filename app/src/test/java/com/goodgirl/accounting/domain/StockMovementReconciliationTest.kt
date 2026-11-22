package com.goodgirl.accounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StockMovementReconciliationTest {
    @Test
    fun returnsZeroWhenMovementHistoryAlreadyMatchesBalance() {
        assertEquals(
            "0",
            StockMovementReconciliation.calculateAdjustment("7.5", listOf("10", "-2.5"))
        )
    }

    @Test
    fun restoresOpeningBalanceMissingFromLegacyMovementHistory() {
        assertEquals(
            "6.25",
            StockMovementReconciliation.calculateAdjustment("8.75", listOf("2.5"))
        )
    }

    @Test
    fun recordsNegativeCorrectionWhenLegacyMovementsExceedCurrentBalance() {
        assertEquals(
            "-3.125",
            StockMovementReconciliation.calculateAdjustment("1.875", listOf("5"))
        )
    }

    @Test
    fun preservesExactDecimalPrecision() {
        assertEquals(
            "0.00000003",
            StockMovementReconciliation.calculateAdjustment(
                "0.30000003",
                listOf("0.1", "0.2")
            )
        )
    }

    @Test
    fun rejectsMalformedStoredValues() {
        assertThrows(IllegalArgumentException::class.java) {
            StockMovementReconciliation.calculateAdjustment("bad", listOf("1"))
        }
    }
}
