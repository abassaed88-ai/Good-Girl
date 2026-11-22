package com.goodgirl.accounting.domain

import java.math.BigDecimal

object ProductInputRules {
    fun normalizeNonNegativeDecimal(value: String, fieldName: String): String {
        val parsed = try {
            BigDecimal(value.trim())
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("قيمة $fieldName غير صحيحة")
        }
        require(parsed >= BigDecimal.ZERO) { "لا يمكن أن تكون قيمة $fieldName سالبة" }
        return parsed.stripTrailingZeros().toPlainString()
    }

    fun requireName(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "اسم المنتج مطلوب" }
        require(normalized.length <= 120) { "اسم المنتج طويل جداً" }
        return normalized
    }
}
