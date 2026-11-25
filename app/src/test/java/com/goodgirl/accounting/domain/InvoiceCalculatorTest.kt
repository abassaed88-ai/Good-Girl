package com.goodgirl.accounting.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InvoiceCalculatorTest {
    @Test
    fun calculatesPaidAndRemainingAmounts() {
        val result = InvoiceCalculator.calculate(
            subtotal = "150.75".toBigDecimal(),
            discount = "10.25".toBigDecimal(),
            paid = "50.00".toBigDecimal()
        )

        assertEquals("140.50", result.total.toPlainString())
        assertEquals("90.50", result.remaining.toPlainString())
        assertEquals("0", result.change.toPlainString())
    }

    @Test
    fun calculatesCustomerChangeWithoutNegativeBalance() {
        val result = InvoiceCalculator.calculate(
            subtotal = "100".toBigDecimal(),
            discount = "0".toBigDecimal(),
            paid = "125".toBigDecimal()
        )

        assertEquals("0", result.remaining.toPlainString())
        assertEquals("25", result.change.toPlainString())
    }

    @Test
    fun rejectsDiscountGreaterThanSubtotal() {
        assertThrows(IllegalArgumentException::class.java) {
            InvoiceCalculator.calculate(
                subtotal = BigDecimal("20"),
                discount = BigDecimal("21"),
                paid = BigDecimal.ZERO
            )
        }
    }
}
