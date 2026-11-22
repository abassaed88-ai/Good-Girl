package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.data.local.StockMovementEntity
import com.goodgirl.accounting.domain.ProductInputRules
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ProductRepository(private val database: AccountingDatabase) {
    fun observeProducts(): Flow<List<ProductEntity>> =
        database.productDao().observeActiveProducts()

    suspend fun addProduct(
        sku: String,
        name: String,
        unit: String,
        purchaseCost: String,
        salePrice: String,
        openingStock: String,
        note: String
    ) {
        val now = System.currentTimeMillis()
        val productId = UUID.randomUUID().toString()
        val cleanName = ProductInputRules.requireName(name)
        val cleanUnit = unit.trim().ifEmpty { "قطعة" }
        val cleanCost = ProductInputRules.normalizeNonNegativeDecimal(purchaseCost, "كلفة الشراء")
        val cleanPrice = ProductInputRules.normalizeNonNegativeDecimal(salePrice, "سعر البيع")
        val cleanStock = ProductInputRules.normalizeNonNegativeDecimal(openingStock, "الرصيد الافتتاحي")
        val cleanSku = sku.trim()

        database.withTransaction {
            database.productDao().insert(
                ProductEntity(
                    id = productId,
                    sku = cleanSku,
                    name = cleanName,
                    baseUnit = cleanUnit,
                    purchaseCost = cleanCost,
                    salePrice = cleanPrice,
                    stockQuantity = cleanStock,
                    note = note.trim(),
                    createdAt = now,
                    updatedAt = now
                )
            )

            if (cleanStock.toBigDecimal() > java.math.BigDecimal.ZERO) {
                database.stockMovementDao().insert(
                    StockMovementEntity(
                        id = UUID.randomUUID().toString(),
                        productId = productId,
                        quantityDelta = cleanStock,
                        unitCostSnapshot = cleanCost,
                        sourceType = "OPENING_BALANCE",
                        sourceId = productId,
                        occurredAt = now,
                        note = "رصيد افتتاحي عند إنشاء المنتج"
                    )
                )
            }
        }
    }

    suspend fun updateProduct(
        id: String,
        sku: String,
        name: String,
        unit: String,
        purchaseCost: String,
        salePrice: String,
        note: String
    ): Boolean {
        val cleanName = ProductInputRules.requireName(name)
        val cleanUnit = unit.trim().ifEmpty { "قطعة" }
        val cleanCost = ProductInputRules.normalizeNonNegativeDecimal(purchaseCost, "كلفة الشراء")
        val cleanPrice = ProductInputRules.normalizeNonNegativeDecimal(salePrice, "سعر البيع")
        var updated = false

        database.withTransaction {
            val current = database.productDao().findById(id)
            if (current != null && current.isActive) {
                database.productDao().update(
                    current.copy(
                        sku = sku.trim(),
                        name = cleanName,
                        baseUnit = cleanUnit,
                        purchaseCost = cleanCost,
                        salePrice = cleanPrice,
                        note = note.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                updated = true
            }
        }
        return updated
    }

    suspend fun archiveProduct(id: String) {
        database.productDao().archive(id, System.currentTimeMillis())
    }
}
