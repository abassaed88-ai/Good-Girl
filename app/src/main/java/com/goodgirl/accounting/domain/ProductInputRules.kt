package com.goodgirl.accounting.domain

import java.math.BigDecimal

object ProductInputRules {
    fun normalizeCurrencyCode(value: String): String {
        val currency = value.trim().uppercase()
        require(currency == "SYP" || currency == "USD") { "عملة المنتج غير مدعومة" }
        return currency
    }

    fun normalizeNonNegativeDecimal(value: String, fieldName: String): String {
        val parsed = try {
            BigDecimal(value.trim())
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("قيمة $fieldName غير صحيحة")
        }
        require(parsed >= BigDecimal.ZERO) { "لا يمكن أن تكون قيمة $fieldName سالبة" }
        return parsed.stripTrailingZeros().toPlainString()
    }

    fun requirePurchaseCostChangeIsTracked(
        currentQuantity: String,
        currentCost: String,
        requestedCost: String
    ): String {
        val quantity = normalizeNonNegativeDecimal(currentQuantity, "الرصيد الحالي").toBigDecimal()
        val previous = normalizeNonNegativeDecimal(currentCost, "كلفة الشراء الحالية")
        val requested = normalizeNonNegativeDecimal(requestedCost, "كلفة الشراء").toBigDecimal()
        require(quantity.compareTo(BigDecimal.ZERO) == 0 || requested.compareTo(previous.toBigDecimal()) == 0) {
            "لا يمكن تعديل متوسط كلفة المخزون يدوياً مع وجود رصيد؛ سجّل فاتورة شراء أو حركة إضافة مخزون لتوثيق الكلفة الجديدة"
        }
        return requested.stripTrailingZeros().toPlainString()
    }

    fun isLowStock(quantity: String, threshold: String): Boolean {
        val current = normalizeNonNegativeDecimal(quantity, "الرصيد").toBigDecimal()
        val limit = normalizeNonNegativeDecimal(threshold, "حد تنبيه المخزون").toBigDecimal()
        return limit > java.math.BigDecimal.ZERO && current <= limit
    }

    fun requireName(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "اسم المنتج مطلوب" }
        require(normalized.length <= 120) { "اسم المنتج طويل جداً" }
        return normalized
    }
}
