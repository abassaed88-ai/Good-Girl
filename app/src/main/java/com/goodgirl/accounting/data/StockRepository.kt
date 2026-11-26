package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.StockMovementEntity
import com.goodgirl.accounting.domain.StockInputRules
import java.util.UUID

data class StocktakeResult(
    val adjustmentDelta: String,
    val resultingBalance: String
)

class StockRepository(private val database: AccountingDatabase) {
    suspend fun reconcileStocktake(
        productId: String,
        countedQuantity: String,
        note: String,
        exchangeRateSnapshot: String = "1"
    ): StocktakeResult {
        val cleanCount = com.goodgirl.accounting.domain.ProductInputRules
            .normalizeNonNegativeDecimal(countedQuantity, "الكمية الفعلية")
        val movementId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        var result: StocktakeResult? = null

        database.withTransaction {
            val product = database.productDao().findById(productId)
                ?: throw IllegalArgumentException("المنتج غير موجود")
            require(product.isActive) { "لا يمكن جرد مخزون منتج مؤرشف" }

            // Read the latest book balance and calculate the difference inside the same
            // transaction, so concurrent sales/purchases cannot make the count stale.
            val delta = StockInputRules.calculateCountDelta(product.stockQuantity, cleanCount)
            if (delta == "0") {
                result = StocktakeResult("0", product.stockQuantity)
            } else {
                val costResult = StockInputRules.calculateAdjustmentCost(
                    currentPurchaseCost = product.purchaseCost,
                    quantityDelta = delta
                )
                val newBalance = StockInputRules.calculateNewBalance(product.stockQuantity, delta)
                val recordedAt = nextMovementRecordedAt(productId, now)
                // Keep the latest purchase-cost reference unchanged; this movement
                // only changes quantity and is valued at the saved current cost.
                database.productDao().update(
                    product.copy(
                        stockQuantity = newBalance,
                        updatedAt = now
                    )
                )
                database.stockMovementDao().insert(
                    StockMovementEntity(
                        id = movementId,
                        productId = productId,
                        quantityDelta = delta,
                        unitCostSnapshot = costResult.movementUnitCost,
                        currencyCode = product.priceCurrencyCode,
                        sourceType = "STOCKTAKE_ADJUSTMENT",
                        sourceId = movementId,
                        occurredAt = now,
                        recordedAt = recordedAt,
                        note = note.trim()
                    )
                )
                val adjustmentValue = delta.toBigDecimal().abs()
                    .multiply(costResult.movementUnitCost.toBigDecimal())
                StockAdjustmentJournalPoster.post(
                    database = database,
                    movementId = movementId,
                    quantityDelta = delta,
                    amount = adjustmentValue.stripTrailingZeros().toPlainString(),
                    currencyCode = product.priceCurrencyCode,
                    exchangeRateSnapshot = exchangeRateSnapshot,
                    occurredAt = now,
                    description = note.trim().ifEmpty { "تسوية جرد مخزون" },
                    sourcePrefix = "STOCKTAKE"
                )
                result = StocktakeResult(delta, newBalance)
            }
        }
        return result ?: error("تعذر حفظ نتيجة الجرد")
    }

    suspend fun adjustStock(
        productId: String,
        quantityDelta: String,
        note: String,
        exchangeRateSnapshot: String = "1"
    ): String {
        val cleanDelta = StockInputRules.normalizeDelta(quantityDelta)
        val movementId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        var resultingBalance = ""

        database.withTransaction {
            val product = database.productDao().findById(productId)
                ?: throw IllegalArgumentException("المنتج غير موجود")
            require(product.isActive) { "لا يمكن تعديل مخزون منتج مؤرشف" }

            val costResult = StockInputRules.calculateAdjustmentCost(
                currentPurchaseCost = product.purchaseCost,
                quantityDelta = cleanDelta
            )
            resultingBalance = StockInputRules.calculateNewBalance(product.stockQuantity, cleanDelta)
            val recordedAt = nextMovementRecordedAt(productId, now)
            // Manual stock movements never establish a new purchase cost.
            // Only posting a purchase invoice may update product.purchaseCost.
            database.productDao().update(
                product.copy(
                    stockQuantity = resultingBalance,
                    updatedAt = now
                )
            )
            database.stockMovementDao().insert(
                StockMovementEntity(
                    id = movementId,
                    productId = productId,
                    quantityDelta = cleanDelta,
                    unitCostSnapshot = costResult.movementUnitCost,
                    currencyCode = product.priceCurrencyCode,
                    sourceType = "MANUAL_ADJUSTMENT",
                    sourceId = movementId,
                    occurredAt = now,
                    recordedAt = recordedAt,
                    note = note.trim()
                )
            )
            val adjustmentValue = cleanDelta.toBigDecimal().abs()
                .multiply(costResult.movementUnitCost.toBigDecimal())
            StockAdjustmentJournalPoster.post(
                database = database,
                movementId = movementId,
                quantityDelta = cleanDelta,
                amount = adjustmentValue.stripTrailingZeros().toPlainString(),
                currencyCode = product.priceCurrencyCode,
                exchangeRateSnapshot = exchangeRateSnapshot,
                occurredAt = now,
                description = note.trim().ifEmpty { "تسوية مخزون يدوية" },
                sourcePrefix = "MANUAL"
            )
        }
        return resultingBalance
    }

    private suspend fun nextMovementRecordedAt(productId: String, requestedAt: Long): Long {
        val latestMovement = database.stockMovementDao().getLatestRecordedAt(productId)
        val baselineRecordedAt = database.stockCostBaselineDao().findByProductId(productId)?.recordedAt
        val previous = listOfNotNull(latestMovement, baselineRecordedAt).maxOrNull()
        return if (previous == null) requestedAt else maxOf(requestedAt, Math.addExact(previous, 1L))
    }

}
