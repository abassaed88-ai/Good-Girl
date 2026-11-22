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
        unitCost: String,
        note: String
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
                    currentQuantity = product.stockQuantity,
                    currentAverageCost = product.purchaseCost,
                    quantityDelta = delta,
                    incomingUnitCost = unitCost
                )
                val newBalance = StockInputRules.calculateNewBalance(product.stockQuantity, delta)
                database.productDao().update(
                    product.copy(
                        stockQuantity = newBalance,
                        purchaseCost = costResult.resultingAverageCost,
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
                        note = note.trim()
                    )
                )
                result = StocktakeResult(delta, newBalance)
            }
        }
        return result ?: error("تعذر حفظ نتيجة الجرد")
    }

    suspend fun adjustStock(
        productId: String,
        quantityDelta: String,
        unitCost: String,
        note: String
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
                currentQuantity = product.stockQuantity,
                currentAverageCost = product.purchaseCost,
                quantityDelta = cleanDelta,
                incomingUnitCost = unitCost
            )
            resultingBalance = StockInputRules.calculateNewBalance(product.stockQuantity, cleanDelta)
            database.productDao().update(
                product.copy(
                    stockQuantity = resultingBalance,
                    purchaseCost = costResult.resultingAverageCost,
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
                    note = note.trim()
                )
            )
        }
        return resultingBalance
    }
}
