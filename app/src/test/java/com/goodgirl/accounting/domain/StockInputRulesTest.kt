package com.goodgirl.accounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StockInputRulesTest {
    @Test
    fun increasesBalanceWithExactDecimals() {
        assertEquals("12.75", StockInputRules.calculateNewBalance("10.25", "2.5"))
    }

    @Test
    fun decreasesBalanceWhenSufficient() {
        assertEquals("3.5", StockInputRules.calculateNewBalance("5", "-1.5"))
    }

    @Test
    fun manualStockInUsesLatestPurchaseCostWithoutChangingIt() {
        val result = StockInputRules.calculateAdjustmentCost(
            currentPurchaseCost = "20",
            quantityDelta = "5"
        )
        assertEquals("20", result.movementUnitCost)
    }

    @Test
    fun stocktakeGainUsesLatestPurchaseCostWithoutChangingIt() {
        val result = StockInputRules.calculateAdjustmentCost(
            currentPurchaseCost = "12.5",
            quantityDelta = "3"
        )
        assertEquals("12.5", result.movementUnitCost)
    }

    @Test
    fun stockOutUsesCurrentAverageCostWithoutChangingIt() {
        val result = StockInputRules.calculateAdjustmentCost(
            currentPurchaseCost = "12.5",
            quantityDelta = "-3"
        )
        assertEquals("12.5", result.movementUnitCost)
    }

    @Test
    fun stocktakeDeltaAddsWhenActualQuantityIsHigher() {
        assertEquals("3", StockInputRules.calculateCountDelta("7", "10"))
    }

    @Test
    fun stocktakeDeltaRemovesWhenActualQuantityIsLower() {
        assertEquals("-2.5", StockInputRules.calculateCountDelta("7.5", "5"))
    }

    @Test
    fun stocktakeDeltaIsZeroWhenQuantitiesMatch() {
        assertEquals("0", StockInputRules.calculateCountDelta("5", "5"))
    }

    @Test
    fun rejectsNegativeCountedQuantity() {
        assertThrows(IllegalArgumentException::class.java) {
            StockInputRules.calculateCountDelta("5", "-1")
        }
    }

    @Test
    fun rejectsNegativeResultingBalance() {
        assertThrows(IllegalArgumentException::class.java) {
            StockInputRules.calculateNewBalance("2", "-3")
        }
    }

    @Test
    fun rejectsZeroMovement() {
        assertThrows(IllegalArgumentException::class.java) {
            StockInputRules.normalizeDelta("0")
        }
    }

    @Test
    fun rejectsMalformedMovement() {
        assertThrows(IllegalArgumentException::class.java) {
            StockInputRules.normalizeDelta("3kg")
        }
    }
}
