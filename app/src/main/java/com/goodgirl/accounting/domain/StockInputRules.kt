package com.goodgirl.accounting.domain

import java.math.BigDecimal

data class StockAdjustmentCostResult(
    val movementUnitCost: String
)

object StockInputRules {
    /**
     * Manual stock adjustments and stocktake differences are valued at the
     * product's current purchase cost (the latest posted purchase cost).
     * These movements never change that cost reference. Only a purchase invoice
     * is allowed to establish a new current purchase cost.
     */
    fun calculateAdjustmentCost(
        currentPurchaseCost: String,
        quantityDelta: String
    ): StockAdjustmentCostResult {
        normalizeDelta(quantityDelta)
        val currentCost = ProductInputRules.normalizeNonNegativeDecimal(
            currentPurchaseCost, "كلفة الشراء الحالية"
        )
        return StockAdjustmentCostResult(movementUnitCost = currentCost)
    }
    fun normalizeDelta(value: String): String {
        val parsed = try {
            BigDecimal(value.trim())
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("كمية الحركة غير صحيحة")
        }
        require(parsed.compareTo(BigDecimal.ZERO) != 0) { "كمية الحركة لا يمكن أن تكون صفراً" }
        return parsed.stripTrailingZeros().toPlainString()
    }

    fun calculateCountDelta(currentBalance: String, countedQuantity: String): String {
        val current = ProductInputRules.normalizeNonNegativeDecimal(
            currentBalance, "الرصيد المسجل"
        ).toBigDecimal()
        val counted = ProductInputRules.normalizeNonNegativeDecimal(
            countedQuantity, "الكمية الفعلية بعد الجرد"
        ).toBigDecimal()
        return counted.subtract(current).stripTrailingZeros().toPlainString()
    }

    fun calculateNewBalance(currentBalance: String, delta: String): String {
        val current = ProductInputRules.normalizeNonNegativeDecimal(currentBalance, "الرصيد الحالي").toBigDecimal()
        val movement = normalizeDelta(delta).toBigDecimal()
        val updated = current.add(movement)
        require(updated >= BigDecimal.ZERO) { "لا يمكن أن يصبح رصيد المنتج سالباً" }
        return updated.stripTrailingZeros().toPlainString()
    }
}
