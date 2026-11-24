package com.goodgirl.accounting.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HistoricalStockCostBaselineReconstructorTest {

    @Test
    fun reconstructsOpeningBalanceByReversingLaterPurchaseAndSale() {
        val baseline = HistoricalStockCostBaselineReconstructor.reconstruct(
            current = HistoricalStockCostCurrentState(
                productId = "product-1",
                quantity = "7",
                averageCost = "13.6",
                currencyCode = "SYP"
            ),
            accountingBoundaryAt = 100,
            postBoundaryEvents = listOf(
                event("purchase", "PURCHASE_INVOICE", "2", "20", "SYP", 120, 121),
                event("sale", "SALE_INVOICE", "-3", "13.6", "SYP", 150, 151)
            )
        )

        assertEquals("product-1", baseline.productId)
        assertDecimal("8", baseline.quantity)
        assertDecimal("12", baseline.averageCost)
        assertEquals("SYP", baseline.currencyCode)
        assertEquals(100L, baseline.effectiveAt)
        assertEquals(100L, baseline.recordedAt)
    }

    @Test
    fun reconstructsAcrossExplicitCurrencyCorrection() {
        val baseline = HistoricalStockCostBaselineReconstructor.reconstruct(
            current = HistoricalStockCostCurrentState(
                productId = "product-2",
                quantity = "2",
                averageCost = "13.33333333",
                currencyCode = "USD"
            ),
            accountingBoundaryAt = 100,
            postBoundaryEvents = listOf(
                event("currency-fix", "PRODUCT_CURRENCY_CORRECTION", "0", "10", "USD", 120, 121),
                event("purchase-usd", "PURCHASE_INVOICE", "1", "20", "USD", 130, 131),
                event("sale-usd", "SALE_INVOICE", "-1", "13.33333333", "USD", 140, 141)
            ),
            previousCurrencyByCorrectionId = mapOf("currency-fix" to "SYP")
        )

        assertDecimal("2", baseline.quantity)
        assertDecimal("10", baseline.averageCost)
        assertEquals("SYP", baseline.currencyCode)
    }

    @Test
    fun rejectsPostBoundaryRecordedMovementWithEarlierBusinessDate() {
        assertThrows(IllegalArgumentException::class.java) {
            HistoricalStockCostBaselineReconstructor.reconstruct(
                current = HistoricalStockCostCurrentState("product-3", "1", "10", "SYP"),
                accountingBoundaryAt = 100,
                postBoundaryEvents = listOf(
                    event("backdated-before-opening", "PURCHASE_INVOICE", "1", "10", "SYP", 99, 120)
                )
            )
        }
    }

    @Test
    fun rejectsMovementRecordedBeforeAccountingBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            HistoricalStockCostBaselineReconstructor.reconstruct(
                current = HistoricalStockCostCurrentState("product-4", "2", "10", "SYP"),
                accountingBoundaryAt = 100,
                postBoundaryEvents = listOf(
                    event("already-in-opening", "PURCHASE_INVOICE", "1", "10", "SYP", 120, 99)
                )
            )
        }
    }

    @Test
    fun rejectsCurrencyCorrectionWithoutAuditedPreviousCurrency() {
        assertThrows(IllegalArgumentException::class.java) {
            HistoricalStockCostBaselineReconstructor.reconstruct(
                current = HistoricalStockCostCurrentState("product-5", "1", "10", "USD"),
                accountingBoundaryAt = 100,
                postBoundaryEvents = listOf(
                    event("currency-fix", "PRODUCT_CURRENCY_CORRECTION", "0", "10", "USD", 120, 121)
                )
            )
        }
    }

    @Test
    fun rejectsWhenForwardReplayDoesNotMatchCurrentProductBalance() {
        assertThrows(IllegalArgumentException::class.java) {
            HistoricalStockCostBaselineReconstructor.reconstruct(
                current = HistoricalStockCostCurrentState("product-6", "8", "13.6", "SYP"),
                accountingBoundaryAt = 100,
                postBoundaryEvents = listOf(
                    event("purchase", "PURCHASE_INVOICE", "2", "20", "SYP", 120, 121),
                    event("sale", "SALE_INVOICE", "-3", "13.6", "SYP", 150, 151)
                )
            )
        }
    }

    @Test
    fun rejectsLegacyReconciliationAfterAccountingBoundaryAndRequiresFallback() {
        assertThrows(IllegalArgumentException::class.java) {
            HistoricalStockCostBaselineReconstructor.reconstruct(
                current = HistoricalStockCostCurrentState("product-7", "2", "10", "SYP"),
                accountingBoundaryAt = 100,
                postBoundaryEvents = listOf(
                    event("legacy-adjustment", "MIGRATION_RECONCILIATION", "1", "10", "SYP", 120, 121)
                )
            )
        }
    }

    private fun event(
        id: String,
        sourceType: String,
        quantityDelta: String,
        unitCost: String,
        currency: String,
        occurredAt: Long,
        recordedAt: Long
    ) = HistoricalStockCostEvent(
        id = id,
        sourceType = sourceType,
        quantityDelta = quantityDelta,
        unitCostSnapshot = unitCost,
        currencyCode = currency,
        occurredAt = occurredAt,
        recordedAt = recordedAt
    )

    private fun assertDecimal(expected: String, actual: String) {
        assertEquals(0, BigDecimal(expected).compareTo(BigDecimal(actual)))
    }
}
