package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.LedgerEntryEntity
import java.math.BigDecimal

object PartyBalanceCalculator {
    fun calculate(kind: String, entries: List<LedgerEntryEntity>): Map<String, BigDecimal> {
        val cleanKind = PartyInputRules.normalizeKind(kind)
        val balances = mutableMapOf<String, BigDecimal>()

        entries.forEach { entry ->
            val effect = when {
                cleanKind == "CUSTOMER" && entry.entryType == "RECEIVABLE" ->
                    entry.amount.toBigDecimal()
                cleanKind == "CUSTOMER" && entry.entryType == "RECEIVABLE_PAYMENT" ->
                    entry.amount.toBigDecimal().negate()
                cleanKind == "SUPPLIER" && entry.entryType == "PAYABLE" ->
                    entry.amount.toBigDecimal()
                cleanKind == "SUPPLIER" && entry.entryType == "PAYABLE_PAYMENT" ->
                    entry.amount.toBigDecimal().negate()
                else -> BigDecimal.ZERO
            }
            if (effect.compareTo(BigDecimal.ZERO) != 0) {
                balances[entry.currencyCode] =
                    (balances[entry.currencyCode] ?: BigDecimal.ZERO).add(effect)
            }
        }

        return balances.filterValues { it.compareTo(BigDecimal.ZERO) != 0 }
    }
}
