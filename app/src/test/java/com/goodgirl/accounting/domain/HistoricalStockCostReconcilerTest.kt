package com.goodgirl.accounting.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalStockCostReconcilerTest {

    @Test
    fun backdatedPurchaseProducesOnlyTheChangedSaleCost() {
        val baseline = baseline()
        val events = listOf(
            event("sale-line-1", "SALE_INVOICE", "-2", "10", 30, 30),
            event("purchase-line-1", "PURCHASE_INVOICE", "4", "20", 20, 40)
        )

        val plan = HistoricalStockCostReconciler.plan(
            baseline = baseline,
            events = events
        )

        assertDecimal("12.85714286", plan.stockOutUnitCosts.getValue("sale-line-1"))
        assertDecimal("12", plan.endingQuantity)
        assertDecimal("12.85714286", plan.endingAverageCost)
        assertEquals(1, plan.changes.size)
        val change = plan.changes.single()
        assertEquals("sale-line-1", change.movementId)
        assertDecimal("10", change.oldUnitCost)
        assertDecimal("12.85714286", change.newUnitCost)
        assertDecimal("20", change.oldExtendedCost)
        assertDecimal("25.71428572", change.newExtendedCost)
    }

    @Test
    fun secondReplayIsIdempotentWhenLatestEffectiveCostWasAlreadyRecorded() {
        val baseline = baseline()
        val events = listOf(
            event("sale-line-1", "SALE_INVOICE", "-2", "10", 30, 30),
            event("purchase-line-1", "PURCHASE_INVOICE", "4", "20", 20, 40)
        )
        val first = HistoricalStockCostReconciler.plan(baseline, events)
        val second = HistoricalStockCostReconciler.plan(
            baseline = baseline,
            events = events,
            currentlyEffectiveUnitCosts = first.stockOutUnitCosts
        )

        assertTrue(second.changes.isEmpty())
        assertEquals(first.stockOutUnitCosts, second.stockOutUnitCosts)
    }

    @Test
    fun unchangedHistoricalCostDoesNotCreateAdjustment() {
        val plan = HistoricalStockCostReconciler.plan(
            baseline = baseline(),
            events = listOf(event("sale-line-1", "SALE_INVOICE", "-2", "10", 20, 20)),
            currentlyEffectiveUnitCosts = mapOf("sale-line-1" to "10.00000000")
        )

        assertTrue(plan.changes.isEmpty())
        assertDecimal("10", plan.stockOutUnitCosts.getValue("sale-line-1"))
    }

    private fun baseline() = HistoricalStockCostBaseline(
        productId = "product-1",
        quantity = "10",
        averageCost = "10",
        currencyCode = "SYP",
        effectiveAt = 10,
        recordedAt = 10
    )

    private fun event(
        id: String,
        sourceType: String,
        quantityDelta: String,
        unitCost: String,
        occurredAt: Long,
        recordedAt: Long
    ) = HistoricalStockCostEvent(
        id = id,
        sourceType = sourceType,
        quantityDelta = quantityDelta,
        unitCostSnapshot = unitCost,
        currencyCode = "SYP",
        occurredAt = occurredAt,
        recordedAt = recordedAt
    )

    private fun assertDecimal(expected: String, actual: String) {
        assertEquals(0, BigDecimal(expected).compareTo(BigDecimal(actual)))
    }
}
