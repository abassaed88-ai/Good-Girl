package com.goodgirl.accounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InvoicePostingRulesTest {
    @Test
    fun calculatesLineTotalUsingDecimalArithmetic() {
        assertEquals("27.5", InvoicePostingRules.calculateLineTotal("2.5", "12", "2.5"))
    }

    @Test
    fun rejectsZeroQuantity() {
        assertThrows(IllegalArgumentException::class.java) {
            InvoicePostingRules.normalizePositiveQuantity("0")
        }
    }

    @Test
    fun rejectsLineDiscountAboveGrossAmount() {
        assertThrows(IllegalArgumentException::class.java) {
            InvoicePostingRules.calculateLineTotal("1", "10", "11")
        }
    }

    @Test
    fun calculatesWeightedAverageCost() {
        assertEquals(
            "13.33333333",
            InvoicePostingRules.calculateWeightedAverageCost("10", "10", "5", "20")
        )
    }

    @Test
    fun weightedAverageCostStartsFromIncomingCostWhenStockWasZero() {
        assertEquals(
            "8.5",
            InvoicePostingRules.calculateWeightedAverageCost("0", "10", "2", "8.5")
        )
    }
}
