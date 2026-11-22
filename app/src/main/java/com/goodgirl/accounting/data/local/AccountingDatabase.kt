package com.goodgirl.accounting.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

const val ACCOUNTING_DATABASE_VERSION = 4

@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        ProductUnitEntity::class,
        PartyEntity::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        StockMovementEntity::class,
        LedgerEntryEntity::class
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

        @Volatile
        private var INSTANCE: AccountingDatabase? = null

        fun getInstance(context: Context): AccountingDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AccountingDatabase::class.java,
                    "good_girl_accounting.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
            }
    }
}
