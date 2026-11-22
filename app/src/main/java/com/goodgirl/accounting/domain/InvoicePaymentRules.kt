package com.goodgirl.accounting.domain

import java.math.BigDecimal

data class InvoicePaymentAmounts(
    val payment: BigDecimal,
    val newPaid: BigDecimal,
    val newRemaining: BigDecimal
)

object InvoicePaymentRules {
    fun calculate(remaining: String, alreadyPaid: String, payment: String): InvoicePaymentAmounts {
        val cleanRemaining = ProductInputRules.normalizeNonNegativeDecimal(remaining, "المتبقي").toBigDecimal()
        val cleanPaid = ProductInputRules.normalizeNonNegativeDecimal(alreadyPaid, "المدفوع سابقاً").toBigDecimal()
        val cleanPayment = ProductInputRules.normalizeNonNegativeDecimal(payment, "قيمة الدفعة").toBigDecimal()
        require(cleanPayment > BigDecimal.ZERO) { "قيمة الدفعة يجب أن تكون أكبر من صفر" }
        require(cleanPayment <= cleanRemaining) { "قيمة الدفعة أكبر من المبلغ المتبقي" }
        val newPaid = cleanPaid.add(cleanPayment)
        val newRemaining = cleanRemaining.subtract(cleanPayment)
        return InvoicePaymentAmounts(cleanPayment, newPaid, newRemaining)
    }
}
