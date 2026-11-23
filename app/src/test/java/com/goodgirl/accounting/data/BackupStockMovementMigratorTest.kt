package com.goodgirl.accounting.data

import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.data.local.StockMovementEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupStockMovementMigratorTest {
    private fun product(stock: String = "5") = ProductEntity(
        id = "p1",
        name = "منتج",
        baseUnit = "قطعة",
        purchaseCost = "3",
        salePrice = "6",
        priceCurrencyCode = "SYP",
        stockQuantity = stock,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun movement(quantity: String = "2") = StockMovementEntity(
        id = "m1",
        productId = "p1",
        quantityDelta = quantity,
        unitCostSnapshot = "3",
        currencyCode = "SYP",
        sourceType = "OPENING_BALANCE",
        sourceId = "p1",
        occurredAt = 1L,
        note = ""
    )

    @Test
    fun upgradesVersionFourBackupByAddingOnlyMissingStockDifference() {
        val original = movement()
        val result = BackupStockMovementMigrator.migrateToCurrentVersion(
            databaseVersion = 4,
            products = listOf(product(stock = "5")),
            movements = listOf(original),
            occurredAt = 99L
        )

        assertEquals(2, result.size)
        assertSame(original, result.first())
        val adjustment = result.last()
        assertEquals("3", adjustment.quantityDelta)
        assertEquals("MIGRATION_RECONCILIATION", adjustment.sourceType)
        assertEquals("backup-migration-v6-p1", adjustment.id)
        assertEquals(99L, adjustment.occurredAt)
    }

    @Test
    fun doesNotAddMovementWhenVersionFourHistoryAlreadyMatchesBalance() {
        val original = movement(quantity = "5")
        val result = BackupStockMovementMigrator.migrateToCurrentVersion(
            databaseVersion = 4,
            products = listOf(product(stock = "5")),
            movements = listOf(original),
            occurredAt = 99L
        )

        assertEquals(listOf(original), result)
    }

    @Test
    fun neverSilentlyReconcilesVersionFiveBackup() {
        val original = movement(quantity = "2")
        val result = BackupStockMovementMigrator.migrateToCurrentVersion(
            databaseVersion = 5,
            products = listOf(product(stock = "5")),
            movements = listOf(original),
            occurredAt = 99L
        )

        assertSame(original, result.single())
    }

    @Test
    fun versionSixBackupDoesNotReconcileStockHistory() {
        val original = movement(quantity = "2")
        val result = BackupStockMovementMigrator.migrateToCurrentVersion(
            databaseVersion = 6,
            products = listOf(product(stock = "5")),
            movements = listOf(original),
            occurredAt = 99L
        )
        assertSame(original, result.single())
    }

    @Test
    fun rejectsBackupFromUnsupportedDatabaseVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupStockMovementMigrator.migrateToCurrentVersion(
                databaseVersion = 3,
                products = listOf(product()),
                movements = emptyList(),
                occurredAt = 99L
            )
        }
    }
}
