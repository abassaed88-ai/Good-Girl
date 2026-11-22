package com.goodgirl.accounting

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun basicArithmeticIsAvailableForDomainTests() {
        val invoiceTotal = 1250L + 750L
        val paidAmount = 1000L
        val remaining = invoiceTotal - paidAmount

        assertEquals(2000L, invoiceTotal)
        assertEquals(1000L, remaining)
    }
}
