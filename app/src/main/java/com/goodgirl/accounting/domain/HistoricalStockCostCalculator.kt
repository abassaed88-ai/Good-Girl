package com.goodgirl.accounting.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One physical inventory event, ordered by business time and then by the time
 * it was recorded. recordedAt makes same-day backdated invoices deterministic.
 */
data class HistoricalStockCostEvent(
    val id: String,
    val sourceType: String,
    val quantityDelta: String,
    val unitCostSnapshot: String,
    val currencyCode: String,
    val occurredAt: Long,
    val recordedAt: Long
)

data class HistoricalStockCostResult(
    val endingQuantity: String,
    val endingAverageCost: String,
    val currencyCode: String?,
    /** The weighted-average unit cost applied to each stock-out movement. */
    val stockOutUnitCosts: Map<String, String>
)

/**
 * Replays stock movements in chronological order using a moving weighted
 * average. The saved snapshot on a stock-out is deliberately not trusted:
 * it is the result of the replay that matters after a backdated event arrives.
 *
 * Currency changes are explicit zero-quantity PRODUCT_CURRENCY_CORRECTION
 * events. Audit-only zero-quantity events do not affect the running balance.
 */
object HistoricalStockCostCalculator {
    private val ZERO = BigDecimal.ZERO
    private const val SCALE = 8
    private const val CURRENCY_CORRECTION = "PRODUCT_CURRENCY_CORRECTION"
    private const val COST_RECALCULATION_AUDIT = "STOCK_COST_RECALCULATION_AUDIT"

    fun recalculate(events: List<HistoricalStockCostEvent>): HistoricalStockCostResult {
        require(events.map { it.id }.toSet().size == events.size) {
            "معرّفات حركات المخزون يجب أن تكون فريدة"
        }

        var quantity = ZERO
        var averageCost = ZERO
        var currency: String? = null
        val stockOutCosts = linkedMapOf<String, String>()

        events.sortedWith(
            compareBy<HistoricalStockCostEvent> { it.occurredAt }
                .thenBy { it.recordedAt }
                .thenBy { it.id }
        ).forEach { event ->
            require(event.id.isNotBlank()) { "معرّف حركة المخزون مطلوب" }
            val eventCurrency = event.currencyCode.trim().uppercase()
            require(eventCurrency == "SYP" || eventCurrency == "USD") {
                "عملة حركة المخزون غير مدعومة: ${event.currencyCode}"
            }
            val delta = decimal(event.quantityDelta, "كمية حركة المخزون")
            val unitCost = nonNegative(event.unitCostSnapshot, "كلفة حركة المخزون")
            val sourceType = event.sourceType.trim().uppercase()

            if (sourceType == COST_RECALCULATION_AUDIT) return@forEach

            if (sourceType == CURRENCY_CORRECTION) {
                require(delta.compareTo(ZERO) == 0) {
                    "حركة تصحيح العملة لا يجوز أن تغيّر كمية المخزون"
                }
                currency = eventCurrency
                averageCost = unitCost
                return@forEach
            }

            if (delta.compareTo(ZERO) == 0) return@forEach

            if (delta > ZERO) {
                if (quantity.compareTo(ZERO) == 0) {
                    currency = eventCurrency
                    averageCost = unitCost
                } else {
                    require(currency == eventCurrency) {
                        "لا يمكن دمج مخزون بعملتين مختلفتين قبل تسجيل تصحيح العملة"
                    }
                    val newQuantity = quantity.add(delta)
                    val carryingValue = quantity.multiply(averageCost)
                        .add(delta.multiply(unitCost))
                    averageCost = carryingValue.divide(newQuantity, SCALE, RoundingMode.HALF_UP)
                }
                quantity = quantity.add(delta)
            } else {
                require(currency == eventCurrency) {
                    "عملة حركة الصرف لا تطابق عملة المخزون المتاحة"
                }
                val outgoing = delta.abs()
                require(quantity >= outgoing) {
                    "توجد حركة صرف بتاريخ أسبق من توافر الكمية الكافية للمخزون"
                }
                stockOutCosts[event.id] = averageCost.stripTrailingZeros().toPlainString()
                quantity = quantity.subtract(outgoing)
            }
        }

        return HistoricalStockCostResult(
            endingQuantity = quantity.stripTrailingZeros().toPlainString(),
            endingAverageCost = averageCost.stripTrailingZeros().toPlainString(),
            currencyCode = currency,
            stockOutUnitCosts = stockOutCosts
        )
    }

    private fun nonNegative(value: String, label: String): BigDecimal {
        val parsed = decimal(value, label)
        require(parsed >= ZERO) { "$label لا يمكن أن تكون سالبة" }
        return parsed
    }

    private fun decimal(value: String, label: String): BigDecimal =
        try {
            BigDecimal(value.trim())
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException("$label غير صالحة", error)
        }
}
