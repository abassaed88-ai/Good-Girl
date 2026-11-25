package com.goodgirl.accounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProductUnitInputRulesTest {
    @Test
    fun convertsBoxQuantityToBasePieces() {
        assertEquals("24", ProductUnitInputRules.toBaseQuantity("2", "12"))
    }

    @Test
    fun convertsPackPriceToBaseUnitCost() {
        assertEquals("2.5", ProductUnitInputRules.toBaseUnitCost("30", "12"))
    }

    @Test
    fun rejectsZeroOrNegativeConversionFactor() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductUnitInputRules.normalizeFactor("0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProductUnitInputRules.normalizeFactor("-3")
        }
    }

    @Test
    fun rejectsBlankUnitName() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductUnitInputRules.normalizeName(" ")
        }
    }
}
