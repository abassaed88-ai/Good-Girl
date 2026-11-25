package com.goodgirl.accounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PartyPaymentRulesTest {
    @Test
    fun normalizesCustomerReceipt() {
        val result = PartyPaymentRules.normalize("CUSTOMER", "25.00", "usd", "15000", "50")
        assertEquals("25", result.amount)
        assertEquals("USD", result.currencyCode)
        assertEquals("CUSTOMER", result.partyKind)
    }

    @Test
    fun normalizesSupplierPaymentInSyrianPound() {
        val result = PartyPaymentRules.normalize("SUPPLIER", "1000", "syp", "1", "1500")
        assertEquals("1000", result.amount)
        assertEquals("SUPPLIER", result.partyKind)
    }

    @Test
    fun rejectsPaymentLargerThanOutstandingBalanceInThatCurrency() {
        assertThrows(IllegalArgumentException::class.java) {
            PartyPaymentRules.normalize("CUSTOMER", "10", "SYP", "1", "0")
        }
    }

    @Test
    fun rejectsZeroPayment() {
        assertThrows(IllegalArgumentException::class.java) {
            PartyPaymentRules.normalize("SUPPLIER", "0", "USD", "15000", "100")
        }
    }

    @Test
    fun requiresSyrianPoundExchangeRateToBeOne() {
        assertThrows(IllegalArgumentException::class.java) {
            PartyPaymentRules.normalize("CUSTOMER", "10", "SYP", "15000", "100")
        }
    }

    @Test
    fun allowsSettlementInEitherCurrencyWhenThatCurrencyHasBalance() {
        assertEquals(
            "USD",
            PartyPaymentRules.normalize("CUSTOMER", "2", "USD", "15000", "10").currencyCode
        )
        assertEquals(
            "SYP",
            PartyPaymentRules.normalize("CUSTOMER", "2000", "SYP", "1", "5000").currencyCode
        )
    }
}
