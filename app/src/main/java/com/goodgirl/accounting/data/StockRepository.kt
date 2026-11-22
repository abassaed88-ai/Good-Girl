package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.StockMovementEntity
import com.goodgirl.accounting.domain.ProductInputRules
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
        val cleanCost = ProductInputRules.normalizeNonNegativeDecimal(unitCost, "كلفة الوحدة")
        val movementId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        var resultingBalance = ""

        database.withTransaction {
            val product = database.productDao().findById(productId)
                ?: throw IllegalArgumentException("المنتج غير موجود")
            require(product.isActive) { "لا يمكن تعديل مخزون منتج مؤرشف" }

            resultingBalance = StockInputRules.calculateNewBalance(product.stockQuantity, cleanDelta)
            database.productDao().updateStock(productId, resultingBalance, now)
            database.stockMovementDao().insert(
                StockMovementEntity(
                    id = movementId,
                    productId = productId,
                    quantityDelta = cleanDelta,
                    unitCostSnapshot = cleanCost,
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
