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

    @Test
    fun cartRejectsBlankInvalidAndNonPositiveQuantities() {
        assertThrows(IllegalArgumentException::class.java) {
            PointOfSaleCartRules.validateCart(
                listOf(PointOfSaleCartLineInput("", "5", "10"))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PointOfSaleCartRules.validateCart(
                listOf(PointOfSaleCartLineInput("0", "5", "10"))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PointOfSaleCartRules.validateCart(
                listOf(PointOfSaleCartLineInput("abc", "5", "10"))
            )
        }
    }

    @Test
    fun cartRejectsQuantityAboveStockAndInvalidPrice() {
        assertThrows(IllegalArgumentException::class.java) {
            PointOfSaleCartRules.validateCart(
                listOf(PointOfSaleCartLineInput("2.5", "5", "2"))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PointOfSaleCartRules.validateCart(
                listOf(PointOfSaleCartLineInput("1", "-1", "2"))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PointOfSaleCartRules.validateCart(
                listOf(PointOfSaleCartLineInput("1", "bad", "2"))
            )
        }
    }

    @Test
    fun cartAcceptsValidFractionalQuantityAndDecimalPrice() {
        PointOfSaleCartRules.validateCart(
            listOf(PointOfSaleCartLineInput("1.5", "2.75", "3"))
        )
    }

    @Test
    fun estimatedGrossProfitUsesCurrentUnitCost() {
        assertEquals(
            java.math.BigDecimal("3"),
            PointOfSaleCartRules.estimateGrossProfit("1.5", "5", "3")
        )
        assertEquals(
            java.math.BigDecimal("-2"),
            PointOfSaleCartRules.estimateGrossProfit("2", "2", "3")
        )
    }

    @Test
    fun estimatedGrossProfitIsUnavailableWhenCostOrInputIsMissing() {
        assertEquals(null, PointOfSaleCartRules.estimateGrossProfit("1", "5", "0"))
        assertEquals(null, PointOfSaleCartRules.estimateGrossProfit("", "5", "3"))
        assertEquals(null, PointOfSaleCartRules.estimateGrossProfit("1", "bad", "3"))
    }
}
