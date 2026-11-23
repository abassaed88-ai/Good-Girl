package com.goodgirl.accounting.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.goodgirl.accounting.domain.StockMovementReconciliation
import java.math.BigDecimal

const val ACCOUNTING_DATABASE_VERSION = 6

@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        ProductUnitEntity::class,
        PartyEntity::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        StockMovementEntity::class,
        LedgerEntryEntity::class,
        AccountEntity::class,
        JournalEntryEntity::class,
        JournalLineEntity::class
    ],
    version = ACCOUNTING_DATABASE_VERSION,
    exportSchema = true
)
abstract class AccountingDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun productUnitDao(): ProductUnitDao
    abstract fun categoryDao(): CategoryDao
    abstract fun partyDao(): PartyDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun accountDao(): AccountDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun journalLineDao(): JournalLineDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE parties ADD COLUMN openingExchangeRate TEXT NOT NULL DEFAULT '1'"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE products ADD COLUMN priceCurrencyCode TEXT NOT NULL DEFAULT 'SYP'"
                )
                database.execSQL(
                    "ALTER TABLE stock_movements ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'SYP'"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS product_units (id TEXT NOT NULL, productId TEXT NOT NULL, unitName TEXT NOT NULL, baseFactor TEXT NOT NULL, isBaseUnit INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_product_units_productId ON product_units(productId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_product_units_productId_unitName ON product_units(productId, unitName)")
                database.execSQL("ALTER TABLE invoice_lines ADD COLUMN unitFactorSnapshot TEXT NOT NULL DEFAULT '1'")
                database.execSQL("ALTER TABLE invoice_lines ADD COLUMN baseQuantity TEXT NOT NULL DEFAULT '1'")
                database.execSQL("UPDATE invoice_lines SET baseQuantity = quantity")
                database.execSQL(
                    "INSERT INTO product_units(id, productId, unitName, baseFactor, isBaseUnit) SELECT 'base-' || id, id, baseUnit, '1', 1 FROM products"
                )
            }
        }

        /**
         * Older installations could have product balances that were not fully
         * represented in stock_movements. Add only the exact difference, so the
         * existing movement history and current on-hand quantity reconcile.
         */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.query(
                    "SELECT id, stockQuantity, purchaseCost, priceCurrencyCode FROM products ORDER BY id"
                ).use { products ->
                    while (products.moveToNext()) {
                        val productId = products.getString(0)
                        val currentBalance = products.getString(1)
                        val purchaseCost = products.getString(2)
                        val currencyCode = products.getString(3)
                        val movementDeltas = mutableListOf<String>()

                        database.query(
                            "SELECT quantityDelta FROM stock_movements WHERE productId = ?",
                            arrayOf(productId)
                        ).use { movements ->
                            while (movements.moveToNext()) {
                                movementDeltas += movements.getString(0)
                            }
                        }

                        val adjustment = StockMovementReconciliation.calculateAdjustment(
                            currentBalance,
                            movementDeltas
                        )
                        if (BigDecimal(adjustment).compareTo(BigDecimal.ZERO) != 0) {
                            database.execSQL(
                                "INSERT INTO stock_movements " +
                                    "(id, productId, quantityDelta, unitCostSnapshot, currencyCode, " +
                                    "sourceType, sourceId, occurredAt, note) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                arrayOf(
                                    "migration-v5-$productId",
                                    productId,
                                    adjustment,
                                    purchaseCost,
                                    currencyCode,
                                    "MIGRATION_RECONCILIATION",
                                    productId,
                                    System.currentTimeMillis(),
                                    "تسوية رصيد تاريخي عند تحديث قاعدة البيانات"
                                )
                            )
                        }
                    }
                }
            }
        }

        /** Adds the chart of accounts and balanced general-journal storage. */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS accounts (" +
                        "id TEXT NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, " +
                        "accountType TEXT NOT NULL, normalSide TEXT NOT NULL, " +
                        "isSystem INTEGER NOT NULL, isActive INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_accounts_code ON accounts(code)"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS journal_entries (" +
                        "id TEXT NOT NULL, journalNumber TEXT NOT NULL, sourceType TEXT NOT NULL, " +
                        "sourceId TEXT NOT NULL, currencyCode TEXT NOT NULL, " +
                        "exchangeRateSnapshot TEXT NOT NULL, occurredAt INTEGER NOT NULL, " +
                        "description TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_journal_entries_journalNumber " +
                        "ON journal_entries(journalNumber)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_journal_entries_sourceType_sourceId " +
                        "ON journal_entries(sourceType, sourceId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_journal_entries_occurredAt " +
                        "ON journal_entries(occurredAt)"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS journal_lines (" +
                        "id TEXT NOT NULL, journalEntryId TEXT NOT NULL, lineNumber INTEGER NOT NULL, " +
                        "accountId TEXT NOT NULL, debit TEXT NOT NULL, credit TEXT NOT NULL, " +
                        "memo TEXT NOT NULL, PRIMARY KEY(id), " +
                        "FOREIGN KEY(journalEntryId) REFERENCES journal_entries(id) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(accountId) REFERENCES accounts(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_journal_lines_journalEntryId " +
                        "ON journal_lines(journalEntryId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_journal_lines_accountId ON journal_lines(accountId)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_journal_lines_journalEntryId_lineNumber " +
                        "ON journal_lines(journalEntryId, lineNumber)"
                )
            }
        }

        @Volatile
        private var INSTANCE: AccountingDatabase? = null

        fun getInstance(context: Context): AccountingDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AccountingDatabase::class.java,
                    "good_girl_accounting.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { INSTANCE = it }
            }
    }
}
