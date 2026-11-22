package com.goodgirl.accounting.domain

import java.math.BigDecimal

data class NormalizedPartyPayment(
    val amount: String,
    val currencyCode: String,
    val exchangeRate: String,
    val partyKind: String
)

object PartyPaymentRules {
    fun normalize(
        partyKind: String,
        amount: String,
        currencyCode: String,
        exchangeRate: String,
        outstandingBalance: String
    ): NormalizedPartyPayment {
        val kind = PartyInputRules.normalizeKind(partyKind)
        val cleanAmount = ProductInputRules.normalizeNonNegativeDecimal(amount, "مبلغ الدفعة")
        require(cleanAmount.toBigDecimal() > BigDecimal.ZERO) { "مبلغ الدفعة يجب أن يكون أكبر من صفر" }

        val currency = currencyCode.trim().uppercase()
        require(currency == "SYP" || currency == "USD") { "عملة الدفعة غير مدعومة" }
        val available = ProductInputRules.normalizeNonNegativeDecimal(outstandingBalance, "الرصيد المستحق").toBigDecimal()
        require(cleanAmount.toBigDecimal() <= available) { "مبلغ الدفعة أكبر من الرصيد المستحق بهذه العملة" }

        val rate = ProductInputRules.normalizeNonNegativeDecimal(exchangeRate, "سعر الصرف")
        require(rate.toBigDecimal() > BigDecimal.ZERO) { "سعر الصرف يجب أن يكون أكبر من صفر" }
        if (currency == "SYP") {
            require(rate.toBigDecimal().compareTo(BigDecimal.ONE) == 0) {
                "سعر صرف الليرة السورية يجب أن يكون 1"
            }
        }
        return NormalizedPartyPayment(cleanAmount, currency, rate, kind)
    }
}
