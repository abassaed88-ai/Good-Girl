package com.goodgirl.accounting.domain

import java.math.BigDecimal
import java.math.RoundingMode

object ProductUnitInputRules {
    fun normalizeName(value: String): String {
        val name = value.trim()
        require(name.isNotEmpty()) { "اسم الوحدة مطلوب" }
        require(name.length <= 40) { "اسم الوحدة طويل جداً" }
        return name
    }

    fun normalizeFactor(value: String): String {
        val factor = try {
            BigDecimal(value.trim())
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("معامل التحويل غير صحيح")
        }
        require(factor > BigDecimal.ZERO) { "معامل التحويل يجب أن يكون أكبر من صفر" }
        return factor.stripTrailingZeros().toPlainString()
    }

    fun toBaseQuantity(quantity: String, factor: String): String {
        val cleanQuantity = InvoicePostingRules.normalizePositiveQuantity(quantity).toBigDecimal()
        val cleanFactor = normalizeFactor(factor).toBigDecimal()
        return cleanQuantity.multiply(cleanFactor).stripTrailingZeros().toPlainString()
    }

    fun toBaseUnitCost(unitPrice: String, factor: String): String {
        val cleanPrice = ProductInputRules.normalizeNonNegativeDecimal(unitPrice, "سعر الوحدة").toBigDecimal()
        val cleanFactor = normalizeFactor(factor).toBigDecimal()
        return cleanPrice.divide(cleanFactor, 8, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()
    }
}
