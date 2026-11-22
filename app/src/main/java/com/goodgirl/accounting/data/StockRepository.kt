package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.StockMovementEntity
import com.goodgirl.accounting.domain.StockInputRules
import java.util.UUID

class StockRepository(private val database: AccountingDatabase) {
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
