package com.goodgirl.accounting.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InvoiceDiscountAllocatorTest {
    @Test
    fun allocatesDiscountProportionallyAndExactly() {
        val result = InvoiceDiscountAllocator.allocate(
            listOf(BigDecimal("100"), BigDecimal("50"), BigDecimal("0")),
            BigDecimal("15")
        )
        assertEquals(listOf(BigDecimal("10.00000000"), BigDecimal("5"), BigDecimal.ZERO), result)
        assertEquals(BigDecimal("15.00000000"), result.fold(BigDecimal.ZERO, BigDecimal::add))
    }

    @Test
    fun assignsRoundingRemainderToLastPositiveLine() {
        val result = InvoiceDiscountAllocator.allocate(
            listOf(BigDecimal("1"), BigDecimal("1"), BigDecimal("1")),
            BigDecimal("1")
        )
        assertEquals(0, result.fold(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal("1")))
    }

    @Test
    fun zeroValueLinesReceiveNoDiscount() {
        assertEquals(
            listOf(BigDecimal.ZERO, BigDecimal.ZERO),
            InvoiceDiscountAllocator.allocate(listOf(BigDecimal.ZERO, BigDecimal.ZERO), BigDecimal.ZERO)
        )
    }

    @Test
    fun rejectsDiscountLargerThanNetLines() {
        assertThrows(IllegalArgumentException::class.java) {
            InvoiceDiscountAllocator.allocate(listOf(BigDecimal("10")), BigDecimal("11"))
        }
    }
}
