package com.goodgirl.accounting.domain

import java.math.BigDecimal

object StockInputRules {
    fun normalizeDelta(value: String): String {
        val parsed = try {
            BigDecimal(value.trim())
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("كمية الحركة غير صحيحة")
        }
        require(parsed.compareTo(BigDecimal.ZERO) != 0) { "كمية الحركة لا يمكن أن تكون صفراً" }
        return parsed.stripTrailingZeros().toPlainString()
    }

    fun calculateNewBalance(currentBalance: String, delta: String): String {
        val current = ProductInputRules.normalizeNonNegativeDecimal(currentBalance, "الرصيد الحالي").toBigDecimal()
        val movement = normalizeDelta(delta).toBigDecimal()
        val updated = current.add(movement)
        require(updated >= BigDecimal.ZERO) { "لا يمكن أن يصبح رصيد المنتج سالباً" }
        return updated.stripTrailingZeros().toPlainString()
    }
}
