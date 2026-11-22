package com.goodgirl.accounting.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.math.BigDecimal
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the 4 -> 5 migration against a real SQLite database with the
 * legacy v4 products and stock_movements table definitions.
 *
 * This deliberately tests the migration body directly. Room schema validation
 * remains a separate check and must not be inferred from this test.
 */
@RunWith(AndroidJUnit4::class)
class AccountingDatabaseMigrationTest {

    @get:Rule
    val roomMigrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AccountingDatabase::class.java
    )

    @Test
    fun roomMigrationValidatesCompleteVersionFiveSchemaAndPreservesHistory() {
        val databaseName = "room-migration-v4-v5.db"
        val legacy = roomMigrationHelper.createDatabase(databaseName, 4)
        try {
            insertProduct(legacy, "room-product", "8.75", "12.50", "USD")
            insertMovement(legacy, "room-old-movement", "room-product", "2.5")
        } finally {
            legacy.close()
        }

        val migrated = roomMigrationHelper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            AccountingDatabase.MIGRATION_4_5
        )
        try {
            assertDecimalEquals("8.75", movementTotal(migrated, "room-product"))
            assertEquals("6.25", adjustmentFor(migrated, "room-product"))
            assertEquals("2.5", quantityForMovement(migrated, "room-old-movement"))
            assertEquals(1, databaseCount(migrated, "products"))
            assertEquals(2, databaseCount(migrated, "stock_movements"))
        } finally {
            migrated.close()
            InstrumentationRegistry.getInstrumentation()
                .targetContext.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrationReconcilesBalancesWithoutChangingExistingMovementRows() {
        withVersionFourDatabase { database ->
            insertProduct(database, "p-match", "7.5", "4.00", "SYP")
            insertProduct(database, "p-positive", "8.75", "12.50", "USD")
            insertProduct(database, "p-negative", "1.875", "3.25", "SYP")
            insertProduct(database, "p-precision", "0.30000003", "1.00", "SYP")
            insertProduct(database, "p-zero", "0", "0", "SYP")

            insertMovement(database, "old-match-in", "p-match", "10")
            insertMovement(database, "old-match-out", "p-match", "-2.5")
            insertMovement(database, "old-positive", "p-positive", "2.5")
            insertMovement(database, "old-negative", "p-negative", "5")
            insertMovement(database, "old-precision-a", "p-precision", "0.1")
            insertMovement(database, "old-precision-b", "p-precision", "0.2")

            val originalRows = listOf(
                "old-match-in" to "10",
                "old-match-out" to "-2.5",
                "old-positive" to "2.5",
                "old-negative" to "5",
                "old-precision-a" to "0.1",
                "old-precision-b" to "0.2"
            )

            database.beginTransaction()
            try {
                AccountingDatabase.MIGRATION_4_5.migrate(database)
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }

            assertNull(adjustmentFor(database, "p-match"))
            assertEquals("6.25", adjustmentFor(database, "p-positive"))
            assertEquals("-3.125", adjustmentFor(database, "p-negative"))
            assertEquals("0.00000003", adjustmentFor(database, "p-precision"))
            assertNull(adjustmentFor(database, "p-zero"))

            assertDecimalEquals("7.5", movementTotal(database, "p-match"))
            assertDecimalEquals("8.75", movementTotal(database, "p-positive"))
            assertDecimalEquals("1.875", movementTotal(database, "p-negative"))
            assertDecimalEquals("0.30000003", movementTotal(database, "p-precision"))
            assertDecimalEquals("0", movementTotal(database, "p-zero"))

            originalRows.forEach { (id, quantity) ->
                assertEquals(quantity, quantityForMovement(database, id))
            }

            assertEquals(3, countReconciliationMovements(database))

            val reconciliation = database.query(
                "SELECT unitCostSnapshot, currencyCode, sourceType, sourceId, occurredAt, note " +
                    "FROM stock_movements WHERE id = ?",
                arrayOf("migration-v5-p-positive")
            )
            reconciliation.use {
                assertTrue(it.moveToFirst())
                assertEquals("12.50", it.getString(0))
                assertEquals("USD", it.getString(1))
                assertEquals("MIGRATION_RECONCILIATION", it.getString(2))
                assertEquals("p-positive", it.getString(3))
                assertTrue(it.getLong(4) > 0L)
                assertTrue(it.getString(5).contains("تسوية"))
                assertFalse(it.moveToNext())
            }
        }
    }

    @Test
    fun failedMigrationCanBeRolledBackWithoutLeavingPartialAdjustments() {
        withVersionFourDatabase { database ->
            // The first product sorts before the invalid one. Its adjustment
            // must roll back when the later product contains an invalid balance.
            insertProduct(database, "a-valid", "5", "2", "SYP")
            insertProduct(database, "z-invalid", "not-a-number", "2", "SYP")

            database.beginTransaction()
            var failed = false
            try {
                AccountingDatabase.MIGRATION_4_5.migrate(database)
                database.setTransactionSuccessful()
            } catch (_: IllegalArgumentException) {
                failed = true
            } finally {
                database.endTransaction()
            }

            assertTrue("The malformed balance should fail migration", failed)
            assertEquals(0, countReconciliationMovements(database))
            assertEquals(0, databaseCount(database, "stock_movements"))
        }
    }

    private fun withVersionFourDatabase(block: (SupportSQLiteDatabase) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-v4-${UUID.randomUUID()}.db"
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(VersionFourCallback())
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)

        try {
            block(helper.writableDatabase)
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun insertProduct(
        database: SupportSQLiteDatabase,
        id: String,
        stockQuantity: String,
        purchaseCost: String,
        currencyCode: String
    ) {
        database.execSQL(
            "INSERT INTO products " +
                "(id, sku, name, categoryId, baseUnit, purchaseCost, salePrice, " +
                "priceCurrencyCode, stockQuantity, lowStockThreshold, note, isActive, createdAt, updatedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                id, "", "اختبار $id", null, "قطعة", purchaseCost, "0", currencyCode,
                stockQuantity, "0", "", 1, 1L, 1L
            )
        )
    }

    private fun insertMovement(
        database: SupportSQLiteDatabase,
        id: String,
        productId: String,
        quantityDelta: String
    ) {
        database.execSQL(
            "INSERT INTO stock_movements " +
                "(id, productId, quantityDelta, unitCostSnapshot, currencyCode, " +
                "sourceType, sourceId, occurredAt, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(id, productId, quantityDelta, "2.00", "SYP", "LEGACY_TEST", id, 1L, "قديم")
        )
    }

    private fun adjustmentFor(database: SupportSQLiteDatabase, productId: String): String? {
        database.query(
            "SELECT quantityDelta FROM stock_movements " +
                "WHERE sourceType = 'MIGRATION_RECONCILIATION' AND productId = ?",
            arrayOf(productId)
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun quantityForMovement(database: SupportSQLiteDatabase, id: String): String {
        database.query(
            "SELECT quantityDelta FROM stock_movements WHERE id = ?",
            arrayOf(id)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getString(0)
        }
    }

    private fun movementTotal(database: SupportSQLiteDatabase, productId: String): BigDecimal {
        var total = BigDecimal.ZERO
        database.query(
            "SELECT quantityDelta FROM stock_movements WHERE productId = ?",
            arrayOf(productId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                total = total.add(BigDecimal(cursor.getString(0)))
            }
        }
        return total
    }

    private fun assertDecimalEquals(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    private fun countReconciliationMovements(database: SupportSQLiteDatabase): Int =
        databaseCount(
            database,
            "stock_movements",
            "sourceType = 'MIGRATION_RECONCILIATION'"
        )

    private fun databaseCount(
        database: SupportSQLiteDatabase,
        table: String,
        where: String? = null
    ): Int {
        val query = if (where == null) {
            "SELECT COUNT(*) FROM $table"
        } else {
            "SELECT COUNT(*) FROM $table WHERE $where"
        }
        database.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private class VersionFourCallback : SupportSQLiteOpenHelper.Callback(4) {
        override fun onCreate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE products (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "sku TEXT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "categoryId TEXT, " +
                    "baseUnit TEXT NOT NULL, " +
                    "purchaseCost TEXT NOT NULL, " +
                    "salePrice TEXT NOT NULL, " +
                    "priceCurrencyCode TEXT NOT NULL DEFAULT 'SYP', " +
                    "stockQuantity TEXT NOT NULL, " +
                    "lowStockThreshold TEXT NOT NULL, " +
                    "note TEXT NOT NULL, " +
                    "isActive INTEGER NOT NULL, " +
                    "createdAt INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL)"
            )
            database.execSQL("CREATE INDEX index_products_name ON products(name)")
            database.execSQL("CREATE INDEX index_products_sku ON products(sku)")
            database.execSQL("CREATE INDEX index_products_categoryId ON products(categoryId)")
            database.execSQL(
                "CREATE TABLE stock_movements (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "productId TEXT NOT NULL, " +
                    "quantityDelta TEXT NOT NULL, " +
                    "unitCostSnapshot TEXT NOT NULL, " +
                    "currencyCode TEXT NOT NULL DEFAULT 'SYP', " +
                    "sourceType TEXT NOT NULL, " +
                    "sourceId TEXT NOT NULL, " +
                    "occurredAt INTEGER NOT NULL, " +
                    "note TEXT NOT NULL)"
            )
            database.execSQL(
                "CREATE INDEX index_stock_movements_productId ON stock_movements(productId)"
            )
            database.execSQL(
                "CREATE INDEX index_stock_movements_sourceType_sourceId " +
                    "ON stock_movements(sourceType, sourceId)"
            )
        }

        override fun onUpgrade(
            database: SupportSQLiteDatabase,
            oldVersion: Int,
            newVersion: Int
        ) {
            throw IllegalStateException("Unexpected upgrade from $oldVersion to $newVersion")
        }
    }
}
