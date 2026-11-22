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
