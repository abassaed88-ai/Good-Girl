package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.InvoiceEntity
import java.math.BigDecimal

data class PartyInvoiceAllocationResult(
    val updatedInvoices: List<InvoiceEntity>,
    val allocatedAmount: BigDecimal,
    val unallocatedAmount: BigDecimal
)

/**
 * Allocates an on-account receipt/payment to posted invoices in chronological order.
 * Any remainder may settle an opening balance rather than an invoice.
 */
object PartyInvoiceAllocationRules {
    fun allocate(
        amount: BigDecimal,
        invoices: List<InvoiceEntity>
    ): PartyInvoiceAllocationResult {
        require(amount > BigDecimal.ZERO) { "مبلغ التوزيع يجب أن يكون أكبر من صفر" }
        var remainingToAllocate = amount
        var allocated = BigDecimal.ZERO

        val updated = invoices
            .filter { it.status == "POSTED" }
            .sortedWith(compareBy<InvoiceEntity> { it.issuedAt }.thenBy { it.createdAt }.thenBy { it.invoiceNumber })
            .mapNotNull { invoice ->
                if (remainingToAllocate.compareTo(BigDecimal.ZERO) <= 0) {
                    return@mapNotNull null
                }
                val invoiceRemaining = invoice.remaining.toBigDecimal()
                require(invoiceRemaining >= BigDecimal.ZERO) {
                    "الفاتورة " + invoice.invoiceNumber + " تحتوي على متبقٍ غير صالح"
                }
                if (invoiceRemaining.compareTo(BigDecimal.ZERO) == 0) {
                    return@mapNotNull null
                }

                val applied = invoiceRemaining.min(remainingToAllocate)
                val newPaid = invoice.paid.toBigDecimal().add(applied)
                val newRemaining = invoiceRemaining.subtract(applied)
                remainingToAllocate = remainingToAllocate.subtract(applied)
                allocated = allocated.add(applied)

                invoice.copy(
                    paid = newPaid.stripTrailingZeros().toPlainString(),
                    remaining = newRemaining.stripTrailingZeros().toPlainString()
                )
            }

        return PartyInvoiceAllocationResult(
            updatedInvoices = updated,
            allocatedAmount = allocated,
            unallocatedAmount = remainingToAllocate
        )
    }
}