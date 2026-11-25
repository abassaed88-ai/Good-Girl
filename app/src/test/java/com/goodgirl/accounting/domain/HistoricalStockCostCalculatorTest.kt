package com.goodgirl.accounting.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HistoricalStockCostCalculatorTest {

    @Test
    fun replaysOpeningSalePurchaseSaleUsingWeightedAverage() {
        val result = HistoricalStockCostCalculator.recalculate(
            listOf(
                event("opening", "OPENING_BALANCE", "10", "10", "SYP", 1, 1),
                event("sale-1", "SALE_INVOICE", "-2", "10", "SYP", 2, 2),
                event("purchase", "PURCHASE_INVOICE", "4", "20", "SYP", 3, 3),
                event("sale-2", "SALE_INVOICE", "-1", "13.33333333", "SYP", 4, 4)
            )
        )

        assertDecimal("10", result.stockOutUnitCosts.getValue("sale-1"))
        assertDecimal("13.33333333", result.stockOutUnitCosts.getValue("sale-2"))
        assertDecimal("11", result.endingQuantity)
        assertDecimal("13.33333333", result.endingAverageCost)
        assertEquals("SYP", result.currencyCode)
    }

    @Test
    fun backdatedPurchaseRecalculatesLaterSaleCost() {
        val result = HistoricalStockCostCalculator.recalculate(
            listOf(
                event("opening", "OPENING_BALANCE", "10", "10", "SYP", 1, 1),
                event("sale-before-purchase", "SALE_INVOICE", "-2", "10", "SYP", 20, 20),
                event("purchase-1", "PURCHASE_INVOICE", "4", "20", "SYP", 30, 30),
                event("sale-after-purchase", "SALE_INVOICE", "-1", "13.33333333", "SYP", 40, 40),
                // Recorded later, but its business date is between the sale and purchase.
                event("backdated-purchase", "PURCHASE_INVOICE", "4", "30", "SYP", 25, 50)
            )
        )

        assertDecimal("10", result.stockOutUnitCosts.getValue("sale-before-purchase"))
        assertDecimal("17.5", result.stockOutUnitCosts.getValue("sale-after-purchase"))
        assertDecimal("15", result.endingQuantity)
        assertDecimal("17.5", result.endingAverageCost)
    }

    @Test
    fun backdatedSaleChangesWeightedAverageForLaterPurchasesAndSales() {
        val result = HistoricalStockCostCalculator.recalculate(
            listOf(
                event("opening", "OPENING_BALANCE", "10", "10", "SYP", 1, 1),
                event("existing-sale", "SALE_INVOICE", "-2", "10", "SYP", 20, 20),
                event("purchase", "PURCHASE_INVOICE", "4", "20", "SYP", 30, 30),
                event("later-sale", "SALE_INVOICE", "-1", "13.33333333", "SYP", 40, 40),
                event("backdated-sale", "SALE_INVOICE", "-2", "10", "SYP", 10, 50)
            )
        )

        assertDecimal("10", result.stockOutUnitCosts.getValue("backdated-sale"))
        assertDecimal("10", result.stockOutUnitCosts.getValue("existing-sale"))
        assertDecimal("14", result.stockOutUnitCosts.getValue("later-sale"))
        assertDecimal("9", result.endingQuantity)
        assertDecimal("14", result.endingAverageCost)
    }

    @Test
    fun recordedAtOrdersSameDayBackdatedEventsDeterministically() {
        val result = HistoricalStockCostCalculator.recalculate(
            listOf(
                event("opening", "OPENING_BALANCE", "1", "10", "SYP", 1, 1),
                event("sale", "SALE_INVOICE", "-1", "10", "SYP", 2, 30),
                event("purchase", "PURCHASE_INVOICE", "1", "20", "SYP", 2, 20)
            )
        )

        assertDecimal("15", result.stockOutUnitCosts.getValue("sale"))
        assertDecimal("1", result.endingQuantity)
        assertDecimal("15", result.endingAverageCost)
    }

    @Test
    fun rejectsHistoricalSaleBeforeEnoughStockExists() {
        assertThrows(IllegalArgumentException::class.java) {
            HistoricalStockCostCalculator.recalculate(
                listOf(
                    event("opening", "OPENING_BALANCE", "1", "10", "SYP", 20, 20),
                    event("backdated-sale", "SALE_INVOICE", "-2", "10", "SYP", 10, 30)
                )
            )
        }
    }

    @Test
    fun currencyCorrectionStartsAnExplicitNewInventoryCurrencySegment() {
        val result = HistoricalStockCostCalculator.recalculate(
            listOf(
                event("opening", "OPENING_BALANCE", "2", "10", "SYP", 1, 1),
                event("currency-fix", "PRODUCT_CURRENCY_CORRECTION", "0", "12", "USD", 2, 2),
                event("sale-usd", "SALE_INVOICE", "-1", "12", "USD", 3, 3)
            )
        )

        assertDecimal("12", result.stockOutUnitCosts.getValue("sale-usd"))
        assertDecimal("1", result.endingQuantity)
        assertDecimal("12", result.endingAverageCost)
        assertEquals("USD", result.currencyCode)
    }

    @Test
    fun rejectsMixedCurrencyStockWithoutExplicitCorrection() {
        assertThrows(IllegalArgumentException::class.java) {
            HistoricalStockCostCalculator.recalculate(
                listOf(
                    event("opening", "OPENING_BALANCE", "2", "10", "SYP", 1, 1),
                    event("purchase-usd", "PURCHASE_INVOICE", "1", "10", "USD", 2, 2)
                )
            )
        }
    }

    @Test
    fun ignoresCostRecalculationAuditRowsAndRejectsDuplicateIds() {
        val auditResult = HistoricalStockCostCalculator.recalculate(
            listOf(
                event("opening", "OPENING_BALANCE", "2", "10", "SYP", 1, 1),
                event("audit", "STOCK_COST_RECALCULATION_AUDIT", "0", "99", "USD", 2, 2)
            )
        )
        assertDecimal("2", auditResult.endingQuantity)
        assertDecimal("10", auditResult.endingAverageCost)
        assertEquals("SYP", auditResult.currencyCode)

        assertThrows(IllegalArgumentException::class.java) {
            HistoricalStockCostCalculator.recalculate(
                listOf(
                    event("same", "OPENING_BALANCE", "1", "10", "SYP", 1, 1),
                    event("same", "PURCHASE_INVOICE", "1", "20", "SYP", 2, 2)
                )
            )
        }
    }

    @Test
    fun replaysEventsFromTrustedBaselineWithoutReplayingLegacyRows() {
        val baseline = HistoricalStockCostBaseline(
            productId = "product-1",
            quantity = "8",
            averageCost = "12",
            currencyCode = "SYP",
            effectiveAt = 100,
            recordedAt = 200
        )
        val result = HistoricalStockCostCalculator.recalculate(
            baseline,
            listOf(
                event("purchase-after-baseline", "PURCHASE_INVOICE", "2", "20", "SYP", 120, 210),
                event("sale-after-purchase", "SALE_INVOICE", "-3", "12", "SYP", 150, 220)
            )
        )

        assertDecimal("7", result.endingQuantity)
        assertDecimal("13.6", result.endingAverageCost)
        assertDecimal("13.6", result.stockOutUnitCosts.getValue("sale-after-purchase"))
        assertEquals("SYP", result.currencyCode)
    }

    @Test
    fun rejectsBackdatedMovementBeforeTrustedBaseline() {
        val baseline = HistoricalStockCostBaseline(
            productId = "product-1",
            quantity = "5",
            averageCost = "10",
            currencyCode = "SYP",
            effectiveAt = 100,
            recordedAt = 200
        )

        assertThrows(IllegalArgumentException::class.java) {
            HistoricalStockCostCalculator.recalculate(
                baseline,
                listOf(event("too-old", "PURCHASE_INVOICE", "1", "8", "SYP", 99, 300))
            )
        }
    }

    @Test
    fun rejectsMovementAlreadyIncludedAtExactBaselineBoundary() {
        val baseline = HistoricalStockCostBaseline(
            productId = "product-1",
            quantity = "5",
            averageCost = "10",
            currencyCode = "SYP",
            effectiveAt = 100,
            recordedAt = 200
        )

        assertThrows(IllegalArgumentException::class.java) {
            HistoricalStockCostCalculator.recalculate(
                baseline,
                listOf(event("already-included", "PURCHASE_INVOICE", "1", "8", "SYP", 100, 200))
            )
        }
    }

    @Test
    fun allowsSameBusinessTimestampOnlyWhenRecordedAfterBaselineSnapshot() {
        val baseline = HistoricalStockCostBaseline(
            productId = "product-1",
            quantity = "1",
            averageCost = "10",
            currencyCode = "SYP",
            effectiveAt = 100,
            recordedAt = 200
        )
        val result = HistoricalStockCostCalculator.recalculate(
            baseline,
            listOf(event("same-time-later-record", "PURCHASE_INVOICE", "1", "20", "SYP", 100, 201))
        )

        assertDecimal("2", result.endingQuantity)
        assertDecimal("15", result.endingAverageCost)
    }

    @Test
    fun rejectsMovementRecordedBeforeBaselineEvenWhenItsBusinessDateIsLater() {
        val baseline = HistoricalStockCostBaseline(
            productId = "product-1",
            quantity = "5",
            averageCost = "10",
            currencyCode = "SYP",
            effectiveAt = 100,
            recordedAt = 200
        )

        assertThrows(IllegalArgumentException::class.java) {
            HistoricalStockCostCalculator.recalculate(
                baseline,
                listOf(event("known-before-baseline", "PURCHASE_INVOICE", "1", "8", "SYP", 150, 199))
            )
        }
    }

    @Test
    fun rejectsCurrencyMismatchWhenInventoryIsTemporarilyEmpty() {
        val baseline = HistoricalStockCostBaseline(
            productId = "product-1",
            quantity = "1",
            averageCost = "10",
            currencyCode = "SYP",
            effectiveAt = 100,
            recordedAt = 200
        )

        assertThrows(IllegalArgumentException::class.java) {
            HistoricalStockCostCalculator.recalculate(
                baseline,
                listOf(
                    event("sale-to-zero", "SALE_INVOICE", "-1", "10", "SYP", 110, 210),
                    event("wrong-currency-purchase", "PURCHASE_INVOICE", "1", "10", "USD", 120, 220)
                )
            )
        }
    }

    @Test
    fun rejectsInvalidTrustedBaseline() {
        val baseline = HistoricalStockCostBaseline(
            productId = "product-1",
            quantity = "-1",
            averageCost = "10",
            currencyCode = "SYP",
            effectiveAt = 100,
            recordedAt = 200
        )

        assertThrows(IllegalArgumentException::class.java) {
            HistoricalStockCostCalculator.recalculate(baseline, emptyList())
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
