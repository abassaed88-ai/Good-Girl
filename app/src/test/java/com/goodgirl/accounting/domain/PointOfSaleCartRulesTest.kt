package com.goodgirl.accounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PointOfSaleCartRulesTest {
    @Test
    fun repeatedScanIncrementsQuantityUntilStockIsReached() {
        assertEquals("1", PointOfSaleCartRules.nextScannedQuantity("0", "3"))
        assertEquals("2", PointOfSaleCartRules.nextScannedQuantity("1", "3"))
        assertEquals("3", PointOfSaleCartRules.nextScannedQuantity("2", "3"))
        assertThrows(IllegalArgumentException::class.java) {
            PointOfSaleCartRules.nextScannedQuantity("3", "3")
        }
    }

    @Test
    fun partialPaymentRequiresCustomerAccount() {
        assertThrows(IllegalArgumentException::class.java) {
            PointOfSaleCartRules.validatePayment("20", "12", hasCustomer = false)
        }
        PointOfSaleCartRules.validatePayment("20", "12", hasCustomer = true)
        PointOfSaleCartRules.validatePayment("20", "20", hasCustomer = false)
    }

    @Test
    fun overpaymentIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PointOfSaleCartRules.validatePayment("20", "21", hasCustomer = true)
        }
    }
}
