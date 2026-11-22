package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.ProductUnitEntity
import com.goodgirl.accounting.domain.ProductUnitInputRules
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ProductUnitRepository(private val database: AccountingDatabase) {
    fun observeUnits(productId: String): Flow<List<ProductUnitEntity>> =
        database.productUnitDao().observeForProduct(productId)

    suspend fun addAlternateUnit(productId: String, name: String, baseFactor: String) {
        val cleanName = ProductUnitInputRules.normalizeName(name)
        val cleanFactor = ProductUnitInputRules.normalizeFactor(baseFactor)
        database.withTransaction {
            val product = database.productDao().findById(productId)
                ?: throw IllegalArgumentException("المنتج غير موجود")
            require(product.isActive) { "لا يمكن إضافة وحدة لمنتج مؤرشف" }
            require(cleanName != product.baseUnit) { "اسم وحدة الأساس مستخدم بالفعل" }
            require(database.productUnitDao().findByName(productId, cleanName) == null) {
                "هذه الوحدة مسجلة لهذا المنتج بالفعل"
            }
            database.productUnitDao().insert(
                ProductUnitEntity(
                    id = UUID.randomUUID().toString(),
                    productId = productId,
                    unitName = cleanName,
                    baseFactor = cleanFactor,
                    isBaseUnit = false
                )
            )
        }
    }
}
