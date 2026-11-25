package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.ProductBarcodeEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.domain.BarcodeInputRules
import java.security.SecureRandom
import java.util.UUID

class ProductBarcodeRepository(
    private val database: AccountingDatabase,
    private val random: SecureRandom = SecureRandom()
) {
    suspend fun registerOriginalBarcode(
        productId: String,
        value: String,
        format: String
    ): ProductBarcodeEntity = database.withTransaction {
        requireProduct(productId)
        val normalized = BarcodeInputRules.normalize(value, format)
        require(database.productBarcodeDao().findByValue(normalized) == null) {
            "رقم الباركود مستخدم لمنتج آخر"
        }
        val barcode = ProductBarcodeEntity(
            id = UUID.randomUUID().toString(),
            productId = productId,
            barcodeValue = normalized,
            format = format,
            origin = "ORIGINAL",
            createdAt = System.currentTimeMillis()
        )
        database.productBarcodeDao().insert(barcode)
        barcode
    }

    suspend fun createInternalBarcode(productId: String): ProductBarcodeEntity =
        database.withTransaction {
            requireProduct(productId)
            database.productBarcodeDao().getForProduct(productId)
                .firstOrNull { it.origin == "INTERNAL" }
                ?.let { return@withTransaction it }

            var attempts = 0
            while (attempts < MAX_GENERATION_ATTEMPTS) {
                attempts++
                val suffix = buildString(12) {
                    repeat(12) { append(('0'.code + random.nextInt(10)).toChar()) }
                }
                val value = BarcodeInputRules.internalCode(suffix)
                if (database.productBarcodeDao().findByValue(value) == null) {
                    val barcode = ProductBarcodeEntity(
                        id = UUID.randomUUID().toString(),
                        productId = productId,
                        barcodeValue = value,
                        format = "CODE_128",
                        origin = "INTERNAL",
                        createdAt = System.currentTimeMillis()
                    )
                    database.productBarcodeDao().insert(barcode)
                    return@withTransaction barcode
                }
            }
            throw IllegalStateException("تعذر إنشاء رقم باركود فريد؛ حاول مرة أخرى")
        }

    suspend fun findProductByBarcode(value: String): ProductEntity? {
        val normalized = value.trim()
        if (normalized.isEmpty()) return null
        val barcode = database.productBarcodeDao().findByValue(normalized) ?: return null
        return database.productDao().findById(barcode.productId)
    }

    private suspend fun requireProduct(productId: String) {
        require(database.productDao().findById(productId) != null) {
            "المنتج المحدد غير موجود"
        }
    }

    private companion object {
        const val MAX_GENERATION_ATTEMPTS = 10
    }
}
