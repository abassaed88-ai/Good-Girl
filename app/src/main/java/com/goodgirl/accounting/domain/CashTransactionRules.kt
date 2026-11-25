package com.goodgirl.accounting.domain

import java.math.BigDecimal

data class NormalizedCashTransaction(
    val amount: String,
    val currencyCode: String,
    val exchangeRate: String
)

object CashTransactionRules {
    fun normalize(amount: String, currencyCode: String, exchangeRate: String): NormalizedCashTransaction {
        val cleanAmount = ProductInputRules.normalizeNonNegativeDecimal(amount, "المبلغ")
        require(cleanAmount.toBigDecimal() > BigDecimal.ZERO) { "المبلغ يجب أن يكون أكبر من صفر" }
        val cleanCurrency = currencyCode.trim().uppercase()
        require(cleanCurrency == "SYP" || cleanCurrency == "USD") { "العملة غير مدعومة" }
        val cleanRate = ProductInputRules.normalizeNonNegativeDecimal(exchangeRate, "سعر الصرف")
        require(cleanRate.toBigDecimal() > BigDecimal.ZERO) { "سعر الصرف يجب أن يكون أكبر من صفر" }
        if (cleanCurrency == "SYP") {
            require(cleanRate.toBigDecimal().compareTo(BigDecimal.ONE) == 0) {
                "سعر صرف الليرة السورية يجب أن يكون 1"
            }
        }
        return NormalizedCashTransaction(cleanAmount, cleanCurrency, cleanRate)
    }
}
