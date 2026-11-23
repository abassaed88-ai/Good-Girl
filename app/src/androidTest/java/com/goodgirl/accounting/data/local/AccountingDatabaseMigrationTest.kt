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
 * Tests the 4 -> 5 stock reconciliation against SQLite and Room's exported
 * v4/v5 schemas. The Room helper test validates the complete resulting schema;
 * focused SQLite cases cover precision, signed corrections, and rollback.
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

    @Test
    fun roomMigrationCreatesVersionSixJournalTablesAndPostsBalancedCurrencyOpeningSummaries() {
        val databaseName = "room-migration-v5-v6.db"
        val legacy = roomMigrationHelper.createDatabase(databaseName, 5)
        try {
            insertProduct(legacy, "legacy-product", "4.25", "3.00", "SYP")
            insertLegacyLedger(legacy, "cash-in", "CASH_IN", "100", "SYP", "1", 100L)
            insertLegacyLedger(legacy, "cash-out", "CASH_OUT", "25", "SYP", "1", 200L)
            insertLegacyLedger(legacy, "supplier-debt", "PAYABLE", "20", "SYP", "1", 300L)
            insertLegacyLedger(legacy, "customer-debt", "RECEIVABLE", "40", "USD", "15000", 400L)
            insertLegacyLedger(legacy, "customer-paid", "RECEIVABLE_PAYMENT", "10", "USD", "15250", 500L)
        } finally {
            legacy.close()
        }

        val migrated = roomMigrationHelper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            AccountingDatabase.MIGRATION_5_6
        )
        try {
            assertEquals(1, databaseCount(migrated, "products"))
            assertEquals(9, databaseCount(migrated, "accounts"))
            assertEquals(2, databaseCount(migrated, "journal_entries"))
            assertEquals(6, databaseCount(migrated, "journal_lines"))

            migrated.query("SELECT stockQuantity FROM products WHERE id = 'legacy-product'").use {
                assertTrue(it.moveToFirst())
                assertEquals("4.25", it.getString(0))
            }

            val currencyJournals = mutableMapOf<String, String>()
            migrated.query(
                "SELECT id, currencyCode, exchangeRateSnapshot, sourceType FROM journal_entries"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val entryId = cursor.getString(0)
                    val currency = cursor.getString(1)
                    val rate = cursor.getString(2)
                    val sourceType = cursor.getString(3)
                    currencyJournals[currency] = entryId
                    assertEquals("MIGRATION_OPENING", sourceType)
                    if (currency == "SYP") assertEquals("1", rate)
                    if (currency == "USD") assertEquals("15250", rate)
                }
            }
            assertEquals(setOf("SYP", "USD"), currencyJournals.keys)

            val accountCodes = mutableMapOf<String, String>()
            migrated.query("SELECT id, code FROM accounts").use { cursor ->
                while (cursor.moveToNext()) accountCodes[cursor.getString(0)] = cursor.getString(1)
            }

            val sypLines = linesForCurrencyJournal(migrated, currencyJournals.getValue("SYP"))
            assertEquals(4, sypLines.size)
            assertDecimal("75", sypLines.single { accountCodes.getValue(it.first) == "1000" }.second)
            assertDecimal("12.75", sypLines.single { accountCodes.getValue(it.first) == "1200" }.second)
            assertDecimal("20", sypLines.single { accountCodes.getValue(it.first) == "2000" }.third)
            assertDecimal("67.75", sypLines.single { accountCodes.getValue(it.first) == "3000" }.third)
            assertBalanced(sypLines)

            val usdLines = linesForCurrencyJournal(migrated, currencyJournals.getValue("USD"))
            assertEquals(2, usdLines.size)
            assertDecimal("30", usdLines.single { accountCodes.getValue(it.first) == "1100" }.second)
            assertDecimal("30", usdLines.single { accountCodes.getValue(it.first) == "3000" }.third)
            assertBalanced(usdLines)

            // The migration summarizes current balances per currency; it never
            // converts or adds USD to SYP.
            assertEquals(0, accountCodes.size - 9)
        } finally {
            migrated.close()
            InstrumentationRegistry.getInstrumentation()
                .targetContext.deleteDatabase(databaseName)
        }
    }

    @Test
    fun roomMigrationCreatesVersionSevenExchangeRateHistoryAndAuditTables() {
        val databaseName = "room-migration-v6-v7.db"
        val legacy = roomMigrationHelper.createDatabase(databaseName, 6)
        legacy.close()

        val migrated = roomMigrationHelper.runMigrationsAndValidate(
            databaseName,
            7,
            true,
            AccountingDatabase.MIGRATION_6_7
        )
        try {
            assertEquals(0, databaseCount(migrated, "exchange_rate_history"))
            assertEquals(0, databaseCount(migrated, "exchange_rate_audits"))
            assertEquals(0, databaseCount(migrated, "exchange_rate_correction_items"))

            migrated.execSQL(
                "INSERT INTO exchange_rate_history " +
                    "(id, currencyPair, rate, effectiveAt, recordedAt, source, isArchived) " +
                    "VALUES ('rate-1', 'USD/SYP', '15100', 100, 100, 'USER', 0)"
            )
            migrated.execSQL(
                "INSERT INTO exchange_rate_audits " +
                    "(id, historyId, eventType, oldRate, newRate, reason, correctedAt, " +
                    "affectedInvoiceCount, affectedLedgerCount, affectedJournalCount, financialAmountsChanged) " +
                    "VALUES ('audit-1', 'rate-1', 'RATE_CORRECTION', '15000', '15100', 'تصحيح', 200, 1, 0, 0, 0)"
            )
            migrated.execSQL(
                "INSERT INTO exchange_rate_correction_items " +
                    "(id, auditId, recordType, recordId, sourceType, sourceId, oldRate, newRate) " +
                    "VALUES ('item-1', 'audit-1', 'INVOICE', 'invoice-1', 'SALE_INVOICE', 'invoice-1', '15000', '15100')"
            )
            assertEquals(1, databaseCount(migrated, "exchange_rate_history"))
            assertEquals(1, databaseCount(migrated, "exchange_rate_audits"))
            assertEquals(1, databaseCount(migrated, "exchange_rate_correction_items"))
        } finally {
            migrated.close()
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
        }
    }

    private fun insertLegacyLedger(
        database: SupportSQLiteDatabase,
        id: String,
        entryType: String,
        amount: String,
        currencyCode: String,
        exchangeRate: String,
        occurredAt: Long
    ) {
        database.execSQL(
            "INSERT INTO ledger_entries " +
                "(id, partyId, entryType, amount, currencyCode, exchangeRateSnapshot, " +
                "sourceType, sourceId, occurredAt, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(id, null, entryType, amount, currencyCode, exchangeRate, "LEGACY_TEST", id, occurredAt, "سجل قديم")
        )
    }

    private data class MigrationJournalLine(
        val first: String,
        val second: String,
        val third: String
    )

    private fun linesForCurrencyJournal(
        database: SupportSQLiteDatabase,
        entryId: String
    ): List<MigrationJournalLine> {
        val rows = mutableListOf<MigrationJournalLine>()
        database.query(
            "SELECT accountId, debit, credit FROM journal_lines WHERE journalEntryId = ?",
            arrayOf(entryId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += MigrationJournalLine(cursor.getString(0), cursor.getString(1), cursor.getString(2))
            }
        }
        return rows
    }

    private fun assertBalanced(lines: List<MigrationJournalLine>) {
        val debits = lines.fold(BigDecimal.ZERO) { sum, line -> sum + BigDecimal(line.second) }
        val credits = lines.fold(BigDecimal.ZERO) { sum, line -> sum + BigDecimal(line.third) }
        assertEquals(0, debits.compareTo(credits))
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

    private fun assertDecimal(expected: String, actual: String) {
        assertEquals(0, BigDecimal(expected).compareTo(BigDecimal(actual)))
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
