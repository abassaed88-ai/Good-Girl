package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.LedgerEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyBalanceCalculatorTest {
    private fun entry(type: String, amount: String, currency: String = "SYP") =
        LedgerEntryEntity(
            id = type + amount + currency,
            partyId = "party-1",
            entryType = type,
            amount = amount,
            currencyCode = currency,
            exchangeRateSnapshot = "1",
            sourceType = "TEST",
            sourceId = "source",
            occurredAt = 1L
        )

    @Test
    fun calculatesCustomerReceivablesAfterPayments() {
        val balances = PartyBalanceCalculator.calculate(
            "CUSTOMER",
            listOf(entry("RECEIVABLE", "100"), entry("RECEIVABLE_PAYMENT", "35"))
        )
        assertEquals("65", balances["SYP"]?.stripTrailingZeros()?.toPlainString())
    }

    @Test
    fun calculatesSupplierPayablesAfterPayments() {
        val balances = PartyBalanceCalculator.calculate(
            "SUPPLIER",
            listOf(entry("PAYABLE", "80"), entry("PAYABLE_PAYMENT", "20"))
        )
        assertEquals("60", balances["SYP"]?.stripTrailingZeros()?.toPlainString())
    }

    @Test
    fun keepsCurrenciesSeparate() {
        val balances = PartyBalanceCalculator.calculate(
            "CUSTOMER",
            listOf(entry("RECEIVABLE", "100", "SYP"), entry("RECEIVABLE", "4", "USD"))
        )
        assertEquals(setOf("SYP", "USD"), balances.keys)
    }

    @Test
    fun ignoresCashCounterEntriesInPartyBalance() {
        val balances = PartyBalanceCalculator.calculate(
            "CUSTOMER",
            listOf(entry("RECEIVABLE", "100"), entry("CASH_IN", "20"))
        )
        assertEquals("100", balances["SYP"]?.stripTrailingZeros()?.toPlainString())
        assertTrue(balances.size == 1)
    }
}
