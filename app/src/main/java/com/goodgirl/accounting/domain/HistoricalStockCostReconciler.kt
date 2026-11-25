package com.goodgirl.accounting.domain

import java.math.BigDecimal

/**
 * A stock-out movement whose effective weighted-average cost changes after
 * replaying the trusted inventory history.
 *
 * The original movement and invoice snapshots are never modified. Callers
 * persist these differences as append-only audit rows and balanced journals.
 */
data class HistoricalStockCostChange(
    val movementId: String,
    val sourceType: String,
    val sourceId: String,
    val currencyCode: String,
    val quantity: String,
    val oldUnitCost: String,
    val newUnitCost: String,
    val oldExtendedCost: String,
    val newExtendedCost: String
)

/**
 * Pure calculation output used by the transactional repository layer.
 * It contains the reconciled ending inventory and only the stock-out costs
 * that differ from the currently effective values.
 */
data class HistoricalStockCostReconciliationPlan(
    val endingQuantity: String,
    val endingAverageCost: String,
    val currencyCode: String?,
    val stockOutUnitCosts: Map<String, String>,
    val changes: List<HistoricalStockCostChange>
)

object HistoricalStockCostReconciler {
    fun plan(
        baseline: HistoricalStockCostBaseline,
        events: List<HistoricalStockCostEvent>,
        currentlyEffectiveUnitCosts: Map<String, String> = emptyMap()
    ): HistoricalStockCostReconciliationPlan {
        val replay = HistoricalStockCostCalculator.recalculate(baseline, events)
        val eventsById = events.associateBy { it.id }
        val changes = replay.stockOutUnitCosts.mapNotNull { (movementId, newCostText) ->
            val event = eventsById[movementId]
                ?: throw IllegalStateException("نتيجة إعادة الاحتساب تشير إلى حركة غير موجودة")
            val oldCostText = currentlyEffectiveUnitCosts[movementId] ?: event.unitCostSnapshot
            val oldCost = decimal(oldCostText, "الكلفة الفعلية السابقة")
            val newCost = decimal(newCostText, "الكلفة التاريخية الجديدة")
            if (oldCost.compareTo(newCost) == 0) {
                null
            } else {
                val quantity = decimal(event.quantityDelta, "كمية حركة الصرف").abs()
                val oldExtended = quantity.multiply(oldCost)
                val newExtended = quantity.multiply(newCost)
                HistoricalStockCostChange(
                    movementId = movementId,
                    sourceType = event.sourceType,
                    sourceId = event.id.let { eventSourceId ->
                        // sourceId is supplied by the stock movement's source;
                        // HistoricalStockCostEvent.id is the movement identity.
                        eventSourceId
                    },
                    currencyCode = event.currencyCode,
                    quantity = plain(quantity),
                    oldUnitCost = plain(oldCost),
                    newUnitCost = plain(newCost),
                    oldExtendedCost = plain(oldExtended),
                    newExtendedCost = plain(newExtended)
                )
            }
        }.sortedBy { it.movementId }

        return HistoricalStockCostReconciliationPlan(
            endingQuantity = replay.endingQuantity,
            endingAverageCost = replay.endingAverageCost,
            currencyCode = replay.currencyCode,
            stockOutUnitCosts = replay.stockOutUnitCosts,
            changes = changes
        )
    }

    private fun decimal(value: String, label: String): BigDecimal =
        try {
            BigDecimal(value.trim())
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException("$label غير صالحة", error)
        }

    private fun plain(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString()
}
