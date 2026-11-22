package com.goodgirl.accounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InvoicePaymentRulesTest {
    @Test
    fun recordsPartialPayment() {
        val result = InvoicePaymentRules.calculate("80.50", "19.50", "20")
        assertEquals("20", result.payment.stripTrailingZeros().toPlainString())
        assertEquals("39.5", result.newPaid.stripTrailingZeros().toPlainString())
        assertEquals("60.5", result.newRemaining.stripTrailingZeros().toPlainString())
    }

    @Test
    fun recordsFinalPayment() {
        val result = InvoicePaymentRules.calculate("25", "75", "25")
        assertEquals("100", result.newPaid.stripTrailingZeros().toPlainString())
        assertEquals("0", result.newRemaining.stripTrailingZeros().toPlainString())
    }

    @Test
    fun rejectsPaymentAboveRemaining() {
        assertThrows(IllegalArgumentException::class.java) {
            InvoicePaymentRules.calculate("10", "90", "10.01")
        }
    }

    @Test
    fun rejectsZeroPayment() {
        assertThrows(IllegalArgumentException::class.java) {
            InvoicePaymentRules.calculate("10", "90", "0")
        }
    }
}
