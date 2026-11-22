package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.InvoiceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PartyInvoiceAllocationRulesTest {
    private fun invoice(
        id: String,
        issuedAt: Long,
        paid: String,
        remaining: String,
        status: String = "POSTED"
    ) = InvoiceEntity(
        id = id,
        invoiceNumber = id,
        kind = "SALE",
        partyId = "customer-1",
        currencyCode = "USD",
        exchangeRateSnapshot = "15000",
        subtotal = (paid.toBigDecimal() + remaining.toBigDecimal()).toPlainString(),
        discount = "0",
        total = (paid.toBigDecimal() + remaining.toBigDecimal()).toPlainString(),
        paid = paid,
        remaining = remaining,
        status = status,
        issuedAt = issuedAt,
        createdAt = issuedAt
    )

    @Test
    fun allocatesAcrossOldestInvoicesFirst() {
        val newer = invoice("newer", 200L, "0", "40")
        val older = invoice("older", 100L, "10", "50")

        val result = PartyInvoiceAllocationRules.allocate(
            BigDecimal("70"),
            listOf(newer, older)
        )

        assertEquals(2, result.updatedInvoices.size)
        assertEquals("60", result.updatedInvoices[0].paid)
        assertEquals("0", result.updatedInvoices[0].remaining)
        assertEquals("30", result.updatedInvoices[1].paid)
        assertEquals("20", result.updatedInvoices[1].remaining)
        assertEquals(0, result.allocatedAmount.compareTo(BigDecimal("70")))
        assertEquals(0, result.unallocatedAmount.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun leavesExcessPaymentForOpeningBalanceAfterInvoicesAreSettled() {
        val onlyInvoice = invoice("invoice-1", 100L, "0", "25")

        val result = PartyInvoiceAllocationRules.allocate(
            BigDecimal("40"),
            listOf(onlyInvoice)
        )

        assertEquals("25", result.updatedInvoices.single().paid)
        assertEquals("0", result.updatedInvoices.single().remaining)
        assertEquals(0, result.unallocatedAmount.compareTo(BigDecimal("15")))
    }

    @Test
    fun doesNotChangeDraftOrAlreadyPaidInvoices() {
        val draft = invoice("draft", 50L, "0", "20", status = "DRAFT")
        val paid = invoice("paid", 60L, "20", "0")

        val result = PartyInvoiceAllocationRules.allocate(
            BigDecimal("10"),
            listOf(draft, paid)
        )

        assertTrue(result.updatedInvoices.isEmpty())
        assertEquals(0, result.allocatedAmount.compareTo(BigDecimal.ZERO))
        assertEquals(0, result.unallocatedAmount.compareTo(BigDecimal("10")))
    }

    @Test
    fun rejectsZeroOrNegativeAllocation() {
        assertTrue(runCatching {
            PartyInvoiceAllocationRules.allocate(BigDecimal.ZERO, emptyList())
        }.isFailure)
        assertTrue(runCatching {
            PartyInvoiceAllocationRules.allocate(BigDecimal("-1"), emptyList())
        }.isFailure)
    }
}
