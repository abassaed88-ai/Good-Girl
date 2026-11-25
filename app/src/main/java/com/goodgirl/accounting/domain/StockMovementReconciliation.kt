package com.goodgirl.accounting.domain

import java.math.BigDecimal

/**
 * Calculates the one-time stock movement needed to reconcile a legacy
 * product balance with the movements already present in its audit history.
 */
object StockMovementReconciliation {
    fun calculateAdjustment(currentBalance: String, movementDeltas: List<String>): String {
        val current = decimal(currentBalance, "الرصيد الحالي")
        val movementTotal = movementDeltas.fold(BigDecimal.ZERO) { total, value ->
            total.add(decimal(value, "كمية حركة المخزون"))
        }
        return current.subtract(movementTotal).stripTrailingZeros().toPlainString()
    }

    private fun decimal(value: String, label: String): BigDecimal =
        try {
            BigDecimal(value)
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException("$label غير صالح", error)
        }
}
