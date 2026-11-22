package com.goodgirl.accounting.domain

import java.math.BigDecimal
import java.math.RoundingMode

object InvoicePostingRules {
    fun normalizePositiveQuantity(value: String): String {
        val quantity = parse(value, "الكمية")
        require(quantity > BigDecimal.ZERO) { "يجب أن تكون الكمية أكبر من صفر" }
        return quantity.stripTrailingZeros().toPlainString()
    }

    fun normalizeNonNegativeAmount(value: String, fieldName: String): String =
        ProductInputRules.normalizeNonNegativeDecimal(value, fieldName)

    fun calculateLineTotal(quantity: String, unitPrice: String, discount: String): String {
        val q = normalizePositiveQuantity(quantity).toBigDecimal()
        val price = normalizeNonNegativeAmount(unitPrice, "سعر الوحدة").toBigDecimal()
        val lineDiscount = normalizeNonNegativeAmount(discount, "خصم السطر").toBigDecimal()
        val gross = q.multiply(price)
        require(lineDiscount <= gross) { "خصم السطر أكبر من قيمة السطر" }
        return gross.subtract(lineDiscount).stripTrailingZeros().toPlainString()
    }

    fun calculateWeightedAverageCost(
        currentQuantity: String,
        currentUnitCost: String,
        incomingQuantity: String,
        incomingUnitCost: String
    ): String {
        val oldQuantity = normalizeNonNegativeAmount(currentQuantity, "الرصيد الحالي").toBigDecimal()
        val oldCost = normalizeNonNegativeAmount(currentUnitCost, "الكلفة الحالية").toBigDecimal()
        val addedQuantity = normalizePositiveQuantity(incomingQuantity).toBigDecimal()
        val addedCost = normalizeNonNegativeAmount(incomingUnitCost, "كلفة الشراء الجديدة").toBigDecimal()
        val totalQuantity = oldQuantity.add(addedQuantity)
        val totalCost = oldQuantity.multiply(oldCost).add(addedQuantity.multiply(addedCost))
        return totalCost.divide(totalQuantity, 8, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun parse(value: String, fieldName: String): BigDecimal {
        return try {
            BigDecimal(value.trim())
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("قيمة $fieldName غير صحيحة")
        }
    }
}
