package com.goodgirl.accounting.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        PartyEntity::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        StockMovementEntity::class,
        LedgerEntryEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AccountingDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
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

        @Volatile
        private var INSTANCE: AccountingDatabase? = null

        fun getInstance(context: Context): AccountingDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AccountingDatabase::class.java,
                    "good_girl_accounting.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
