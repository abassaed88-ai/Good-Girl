package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.data.local.ProductUnitEntity
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
        note: String,
        priceCurrencyCode: String = "SYP",
        categoryId: String? = null,
        lowStockThreshold: String = "0",
        openingExchangeRate: String = "1"
    ) {
        val now = System.currentTimeMillis()
        val productId = UUID.randomUUID().toString()
        val cleanName = ProductInputRules.requireName(name)
        val cleanUnit = unit.trim().ifEmpty { "قطعة" }
        val cleanCost = ProductInputRules.normalizeNonNegativeDecimal(purchaseCost, "كلفة الشراء")
        val cleanPrice = ProductInputRules.normalizeNonNegativeDecimal(salePrice, "سعر البيع")
        val cleanStock = ProductInputRules.normalizeNonNegativeDecimal(openingStock, "الرصيد الافتتاحي")
        val cleanThreshold = ProductInputRules.normalizeNonNegativeDecimal(lowStockThreshold, "حد تنبيه المخزون")
        val cleanSku = sku.trim()
        val cleanCurrency = ProductInputRules.normalizeCurrencyCode(priceCurrencyCode)

        database.withTransaction {
            if (categoryId != null) {
                require(database.categoryDao().findById(categoryId) != null) { "التصنيف المحدد غير موجود" }
            }
            database.productDao().insert(
                ProductEntity(
                    id = productId,
                    sku = cleanSku,
                    name = cleanName,
                    baseUnit = cleanUnit,
                    purchaseCost = cleanCost,
                    salePrice = cleanPrice,
                    priceCurrencyCode = cleanCurrency,
                    categoryId = categoryId,
                    stockQuantity = cleanStock,
                    lowStockThreshold = cleanThreshold,
                    note = note.trim(),
                    createdAt = now,
                    updatedAt = now
                )
            )

            database.productUnitDao().insert(
                ProductUnitEntity(
                    id = UUID.randomUUID().toString(),
                    productId = productId,
                    unitName = cleanUnit,
                    baseFactor = "1",
                    isBaseUnit = true
                )
            )

            if (cleanStock.toBigDecimal() > java.math.BigDecimal.ZERO) {
                database.stockMovementDao().insert(
                    StockMovementEntity(
                        id = UUID.randomUUID().toString(),
                        productId = productId,
                        quantityDelta = cleanStock,
                        unitCostSnapshot = cleanCost,
                        currencyCode = cleanCurrency,
                        sourceType = "OPENING_BALANCE",
                        sourceId = productId,
                        occurredAt = now,
                        note = "رصيد افتتاحي عند إنشاء المنتج"
                    )
                )
                ProductOpeningStockJournalPoster.post(
                    database = database,
                    productId = productId,
                    quantity = cleanStock,
                    unitCost = cleanCost,
                    currencyCode = cleanCurrency,
                    exchangeRateSnapshot = openingExchangeRate,
                    occurredAt = now
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
        note: String,
        priceCurrencyCode: String = "SYP",
        categoryId: String? = null,
        lowStockThreshold: String = "0"
    ): Boolean {
        val cleanName = ProductInputRules.requireName(name)
        val cleanUnit = unit.trim().ifEmpty { "قطعة" }
        val cleanCost = ProductInputRules.normalizeNonNegativeDecimal(purchaseCost, "كلفة الشراء")
        val cleanPrice = ProductInputRules.normalizeNonNegativeDecimal(salePrice, "سعر البيع")
        val cleanCurrency = ProductInputRules.normalizeCurrencyCode(priceCurrencyCode)
        val cleanThreshold = ProductInputRules.normalizeNonNegativeDecimal(lowStockThreshold, "حد تنبيه المخزون")
        var updated = false

        database.withTransaction {
            if (categoryId != null) {
                require(database.categoryDao().findById(categoryId) != null) { "التصنيف المحدد غير موجود" }
            }
            val current = database.productDao().findById(id)
            if (current != null && current.isActive) {
                val currentStock = current.stockQuantity.toBigDecimal()
                val trackedPurchaseCost = ProductInputRules.requirePurchaseCostChangeIsTracked(
                    currentQuantity = current.stockQuantity,
                    currentCost = current.purchaseCost,
                    requestedCost = cleanCost
                )
                require(currentStock.compareTo(java.math.BigDecimal.ZERO) == 0 || cleanCurrency == current.priceCurrencyCode) {
                    "لا يمكن تغيير عملة المنتج ما دام لديه رصيد في المخزون"
                }
                require(cleanUnit == current.baseUnit) {
                    "وحدة الأساس ثابتة بعد إنشاء المنتج. أضف وحدات تحويل إضافية من شاشة الوحدات."
                }
                database.productDao().update(
                    current.copy(
                        sku = sku.trim(),
                        name = cleanName,
                        baseUnit = cleanUnit,
                        purchaseCost = trackedPurchaseCost,
                        salePrice = cleanPrice,
                        priceCurrencyCode = cleanCurrency,
                        categoryId = categoryId,
                        lowStockThreshold = cleanThreshold,
                        note = note.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                updated = true
            }
        }
        return updated
    }

    /**
     * Updates only the default sale price for future sales.
     * Existing invoice lines keep their saved unit-price snapshots.
     */
    suspend fun updateSalePrice(id: String, salePrice: String): Boolean {
        val cleanPrice = ProductInputRules.normalizeNonNegativeDecimal(salePrice, "سعر البيع")
        var updated = false

        database.withTransaction {
            val current = database.productDao().findById(id)
            if (current != null && current.isActive) {
                database.productDao().update(
                    current.copy(
                        salePrice = cleanPrice,
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
