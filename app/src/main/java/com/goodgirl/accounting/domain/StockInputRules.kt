package com.goodgirl.accounting.domain

import java.math.BigDecimal

data class StockAdjustmentCostResult(
    val movementUnitCost: String,
    val resultingAverageCost: String
)

object StockInputRules {
    /**
     * A stock-in adjustment contributes its entered unit cost to weighted average cost.
     * A stock-out adjustment is valued at the current average cost and never changes it.
     */
    fun calculateAdjustmentCost(
        currentQuantity: String,
        currentAverageCost: String,
        quantityDelta: String,
        incomingUnitCost: String
    ): StockAdjustmentCostResult {
        val delta = normalizeDelta(quantityDelta).toBigDecimal()
        val averageCost = ProductInputRules.normalizeNonNegativeDecimal(
            currentAverageCost, "متوسط الكلفة الحالي"
        )
        if (delta < BigDecimal.ZERO) {
            return StockAdjustmentCostResult(
                movementUnitCost = averageCost,
                resultingAverageCost = averageCost
            )
        }

        val cleanIncomingCost = ProductInputRules.normalizeNonNegativeDecimal(
            incomingUnitCost, "كلفة الوحدة المضافة"
        )
        val newAverage = InvoicePostingRules.calculateWeightedAverageCost(
            currentQuantity = currentQuantity,
            currentUnitCost = averageCost,
            incomingQuantity = delta.stripTrailingZeros().toPlainString(),
            incomingUnitCost = cleanIncomingCost
        )
        return StockAdjustmentCostResult(
            movementUnitCost = cleanIncomingCost,
            resultingAverageCost = newAverage
        )
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
