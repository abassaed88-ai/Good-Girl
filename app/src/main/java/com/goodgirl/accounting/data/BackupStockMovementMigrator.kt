package com.goodgirl.accounting.data

import com.goodgirl.accounting.data.local.ACCOUNTING_DATABASE_VERSION
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.data.local.StockMovementEntity
import com.goodgirl.accounting.domain.StockMovementReconciliation
import java.math.BigDecimal

/**
 * Upgrades the stock audit trail embedded in a version-4 JSON backup to the
 * version-5 invariant: each product balance equals the sum of its movements.
 *
 * Version-4 installations may contain legacy balance edits that were not
 * written to stock_movements. The database migration uses the same rule:
 * preserve the saved product balance and append only the missing difference.
 */
object BackupStockMovementMigrator {
    fun migrateToCurrentVersion(
        databaseVersion: Int,
        products: List<ProductEntity>,
        movements: List<StockMovementEntity>,
        occurredAt: Long
    ): List<StockMovementEntity> {
        val previousVersion = ACCOUNTING_DATABASE_VERSION - 1
        require(databaseVersion in previousVersion..ACCOUNTING_DATABASE_VERSION) {
            "إصدار قاعدة البيانات في النسخة الاحتياطية غير مدعوم"
        }
        if (databaseVersion == ACCOUNTING_DATABASE_VERSION) return movements

        val deltasByProduct = movements.groupBy { it.productId }
            .mapValues { (_, rows) -> rows.map { it.quantityDelta } }
        val existingIds = movements.map { it.id }.toHashSet()

        val adjustments = products.mapNotNull { product ->
            val adjustment = StockMovementReconciliation.calculateAdjustment(
                currentBalance = product.stockQuantity,
                movementDeltas = deltasByProduct[product.id].orEmpty()
            )
            if (BigDecimal(adjustment).compareTo(BigDecimal.ZERO) == 0) {
                null
            } else {
                val movementId = "backup-migration-v${ACCOUNTING_DATABASE_VERSION}-${product.id}"
                require(movementId !in existingIds) {
                    "تعذّر ترقية سجل المخزون بسبب تعارض معرّف تسوية قديم"
                }
                StockMovementEntity(
                    id = movementId,
                    productId = product.id,
                    quantityDelta = adjustment,
                    unitCostSnapshot = product.purchaseCost,
                    currencyCode = product.priceCurrencyCode,
                    sourceType = "MIGRATION_RECONCILIATION",
                    sourceId = product.id,
                    occurredAt = occurredAt,
                    note = "تسوية رصيد تاريخية عند ترقية نسخة احتياطية من الإصدار 4"
                )
            }
        }
        return movements + adjustments
    }
}
