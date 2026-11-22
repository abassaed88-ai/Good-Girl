package com.goodgirl.accounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CashTransactionRulesTest {
    @Test
    fun normalizesValidDollarTransaction() {
        val result = CashTransactionRules.normalize("150.5000", "usd", "14500")
        assertEquals("150.5", result.amount)
        assertEquals("USD", result.currencyCode)
        assertEquals("14500", result.exchangeRate)
    }

    @Test
    fun rejectsZeroOrNegativeAmount() {
        assertThrows(IllegalArgumentException::class.java) {
            CashTransactionRules.normalize("0", "SYP", "1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CashTransactionRules.normalize("-5", "SYP", "1")
        }
    }

    @Test
    fun rejectsInvalidCurrencyAndRate() {
        assertThrows(IllegalArgumentException::class.java) {
            CashTransactionRules.normalize("10", "EUR", "1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CashTransactionRules.normalize("10", "USD", "0")
        }
    }

    @Test
    fun requiresSyrianPoundRateToBeOne() {
        assertThrows(IllegalArgumentException::class.java) {
            CashTransactionRules.normalize("10", "SYP", "15000")
        }
    }
}
