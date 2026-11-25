package com.goodgirl.accounting.domain

import java.math.BigDecimal

/**
 * Pure calculation for invoice totals. Values stay decimal-safe and are never
 * converted through Double. Currency and exchange-rate snapshots belong to the
 * persisted invoice, not to this calculation.
 */
data class InvoiceAmounts(
    val subtotal: BigDecimal,
    val discount: BigDecimal,
    val total: BigDecimal,
    val paid: BigDecimal,
    val remaining: BigDecimal,
    val change: BigDecimal
)

object InvoiceCalculator {
    fun calculate(
        subtotal: BigDecimal,
        discount: BigDecimal,
        paid: BigDecimal
    ): InvoiceAmounts {
        require(subtotal >= BigDecimal.ZERO) { "Subtotal cannot be negative" }
        require(discount >= BigDecimal.ZERO) { "Discount cannot be negative" }
        require(discount <= subtotal) { "Discount cannot exceed subtotal" }
        require(paid >= BigDecimal.ZERO) { "Paid amount cannot be negative" }

        val total = subtotal.subtract(discount)
        val remaining = total.subtract(paid).max(BigDecimal.ZERO)
        val change = paid.subtract(total).max(BigDecimal.ZERO)

        return InvoiceAmounts(
            subtotal = subtotal,
            discount = discount,
            total = total,
            paid = paid,
            remaining = remaining,
            change = change
        )
    }
}
