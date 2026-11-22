package com.goodgirl.accounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProductInputRulesTest {
    @Test
    fun trimsName() {
        assertEquals("كريم مرطب", ProductInputRules.requireName("  كريم مرطب  "))
    }

    @Test
    fun rejectsBlankName() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductInputRules.requireName("   ")
        }
    }

    @Test
    fun normalizesDecimalWithoutFloatingPoint() {
        assertEquals("1250.75", ProductInputRules.normalizeNonNegativeDecimal(" 1250.7500 ", "السعر"))
    }

    @Test
    fun rejectsNegativeStock() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductInputRules.normalizeNonNegativeDecimal("-2", "الرصيد")
        }
    }

    @Test
    fun detectsLowStockOnlyWhenThresholdIsSet() {
        assertEquals(true, ProductInputRules.isLowStock("3", "5"))
        assertEquals(true, ProductInputRules.isLowStock("5", "5"))
        assertEquals(false, ProductInputRules.isLowStock("6", "5"))
        assertEquals(false, ProductInputRules.isLowStock("0", "0"))
    }

    @Test
    fun acceptsSupportedCurrencies() {
        assertEquals("SYP", ProductInputRules.normalizeCurrencyCode("syp"))
        assertEquals("USD", ProductInputRules.normalizeCurrencyCode("USD"))
    }

    @Test
    fun rejectsUnsupportedCurrency() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductInputRules.normalizeCurrencyCode("EUR")
        }
    }

    @Test
    fun rejectsMalformedAmount() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductInputRules.normalizeNonNegativeDecimal("12x", "السعر")
        }
    }
}
