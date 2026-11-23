package com.goodgirl.accounting.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.goodgirl.accounting.domain.StockMovementReconciliation
import java.math.BigDecimal
import java.util.UUID

const val ACCOUNTING_DATABASE_VERSION = 6

private data class MigrationCurrencyBalances(
    var cash: BigDecimal = BigDecimal.ZERO,
    var receivables: BigDecimal = BigDecimal.ZERO,
    var payables: BigDecimal = BigDecimal.ZERO,
    var inventory: BigDecimal = BigDecimal.ZERO,
    var exchangeRate: String = "1",
    var latestRateAt: Long = Long.MIN_VALUE
)

private data class MigrationOpeningLine(
    val accountId: String,
    val debit: BigDecimal,
    val credit: BigDecimal,
    val memo: String
)

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

        /**
         * Adds the chart of accounts and journal storage, then carries forward
         * each existing currency balance as one balanced opening journal.
         * Historical source documents remain untouched; the migration records
         * the current cash, party balances, and inventory at the upgrade date.
         */
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

                createSystemAccounts(database, System.currentTimeMillis())
                migrateExistingBalancesToOpeningJournals(database)
            }

            private fun createSystemAccounts(database: SupportSQLiteDatabase, createdAt: Long) {
                val defaults = listOf(
                    arrayOf("SYS-1000", "1000", "الصندوق", "ASSET", "DEBIT"),
                    arrayOf("SYS-1100", "1100", "ذمم العملاء", "ASSET", "DEBIT"),
                    arrayOf("SYS-1200", "1200", "المخزون", "ASSET", "DEBIT"),
                    arrayOf("SYS-2000", "2000", "ذمم الموردين", "LIABILITY", "CREDIT"),
                    arrayOf("SYS-3000", "3000", "رأس المال", "EQUITY", "CREDIT"),
                    arrayOf("SYS-4000", "4000", "المبيعات", "REVENUE", "CREDIT"),
                    arrayOf("SYS-5000", "5000", "تكلفة البضاعة المباعة", "EXPENSE", "DEBIT"),
                    arrayOf("SYS-6000", "6000", "إيرادات أخرى", "REVENUE", "CREDIT"),
                    arrayOf("SYS-7000", "7000", "مصروفات تشغيلية", "EXPENSE", "DEBIT")
                )
                defaults.forEach { account ->
                    database.execSQL(
                        "INSERT OR IGNORE INTO accounts " +
                            "(id, code, name, accountType, normalSide, isSystem, isActive, createdAt) " +
                            "VALUES (?, ?, ?, ?, ?, 1, 1, ?)",
                        arrayOf(account[0], account[1], account[2], account[3], account[4], createdAt)
                    )
                }
            }

            private fun migrateExistingBalancesToOpeningJournals(database: SupportSQLiteDatabase) {
                val balances = linkedMapOf<String, MigrationCurrencyBalances>()
                database.query(
                    "SELECT entryType, amount, currencyCode, exchangeRateSnapshot, occurredAt " +
                        "FROM ledger_entries ORDER BY occurredAt, id"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val entryType = cursor.getString(0)
                        val amount = BigDecimal(cursor.getString(1))
                        val currency = cursor.getString(2)
                        val rate = cursor.getString(3)
                        val occurredAt = cursor.getLong(4)
                        require(currency == "SYP" || currency == "USD") {
                            "عملة قديمة غير مدعومة أثناء ترحيل الأرصدة: $currency"
                        }
                        require(amount.compareTo(BigDecimal.ZERO) >= 0) {
                            "مبلغ حركة قديمة غير صالح أثناء ترحيل الأرصدة"
                        }
                        val totals = balances.getOrPut(currency) { MigrationCurrencyBalances() }
                        when (entryType) {
                            "CASH_IN" -> totals.cash = totals.cash.add(amount)
                            "CASH_OUT" -> totals.cash = totals.cash.subtract(amount)
                            "RECEIVABLE" -> totals.receivables = totals.receivables.add(amount)
                            "RECEIVABLE_PAYMENT" -> totals.receivables = totals.receivables.subtract(amount)
                            "PAYABLE" -> totals.payables = totals.payables.add(amount)
                            "PAYABLE_PAYMENT" -> totals.payables = totals.payables.subtract(amount)
                        }
                        if (currency == "USD" && occurredAt >= totals.latestRateAt) {
                            val parsedRate = BigDecimal(rate)
                            require(parsedRate > BigDecimal.ZERO) {
                                "سعر صرف قديم غير صالح أثناء ترحيل الأرصدة"
                            }
                            totals.exchangeRate = parsedRate.stripTrailingZeros().toPlainString()
                            totals.latestRateAt = occurredAt
                        }
                    }
                }

                database.query(
                    "SELECT stockQuantity, purchaseCost, priceCurrencyCode FROM products ORDER BY id"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val quantity = BigDecimal(cursor.getString(0))
                        val unitCost = BigDecimal(cursor.getString(1))
                        val currency = cursor.getString(2)
                        require(currency == "SYP" || currency == "USD") {
                            "عملة مخزون قديمة غير مدعومة أثناء ترحيل الأرصدة: $currency"
                        }
                        require(quantity.compareTo(BigDecimal.ZERO) >= 0 && unitCost.compareTo(BigDecimal.ZERO) >= 0) {
                            "قيمة مخزون قديمة غير صالحة أثناء ترحيل الأرصدة"
                        }
                        val totals = balances.getOrPut(currency) { MigrationCurrencyBalances() }
                        totals.inventory = totals.inventory.add(quantity.multiply(unitCost))
                    }
                }

                val migrationTime = System.currentTimeMillis()
                balances.toSortedMap().forEach { (currency, totals) ->
                    val lines = mutableListOf<MigrationOpeningLine>()
                    addAssetBalance(lines, "SYS-1000", totals.cash, "رصيد الصندوق المرحّل")
                    addAssetBalance(lines, "SYS-1100", totals.receivables, "صافي ذمم العملاء المرحّل")
                    addAssetBalance(lines, "SYS-1200", totals.inventory, "قيمة المخزون المرحّلة")
                    addLiabilityBalance(lines, "SYS-2000", totals.payables, "صافي ذمم الموردين المرحّل")

                    if (lines.isEmpty()) return@forEach

                    val debitTotal = lines.fold(BigDecimal.ZERO) { sum, line -> sum.add(line.debit) }
                    val creditTotal = lines.fold(BigDecimal.ZERO) { sum, line -> sum.add(line.credit) }
                    val difference = debitTotal.subtract(creditTotal)
                    if (difference > BigDecimal.ZERO) {
                        lines += MigrationOpeningLine(
                            accountId = "SYS-3000",
                            debit = BigDecimal.ZERO,
                            credit = difference,
                            memo = "مقابل أرصدة الفترة السابقة"
                        )
                    } else if (difference < BigDecimal.ZERO) {
                        lines += MigrationOpeningLine(
                            accountId = "SYS-3000",
                            debit = difference.abs(),
                            credit = BigDecimal.ZERO,
                            memo = "مقابل أرصدة الفترة السابقة"
                        )
                    }

                    val finalDebits = lines.fold(BigDecimal.ZERO) { sum, line -> sum.add(line.debit) }
                    val finalCredits = lines.fold(BigDecimal.ZERO) { sum, line -> sum.add(line.credit) }
                    check(finalDebits.compareTo(finalCredits) == 0) {
                        "تعذر موازنة قيد الأرصدة الافتتاحية للعملة $currency"
                    }

                    val sourceId = "V5_TO_V6_$currency"
                    val entryId = UUID.randomUUID().toString()
                    val exchangeRate = if (currency == "SYP") "1" else totals.exchangeRate
                    database.execSQL(
                        "INSERT INTO journal_entries " +
                            "(id, journalNumber, sourceType, sourceId, currencyCode, exchangeRateSnapshot, " +
                            "occurredAt, description, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(
                            entryId,
                            "AUTO-V5-V6-OPENING-$currency",
                            "MIGRATION_OPENING",
                            sourceId,
                            currency,
                            exchangeRate,
                            migrationTime,
                            "ملخص أرصدة افتتاحية مرحّل من الإصدار السابق - $currency",
                            migrationTime
                        )
                    )

                    lines.forEachIndexed { index, line ->
                        database.execSQL(
                            "INSERT INTO journal_lines " +
                                "(id, journalEntryId, lineNumber, accountId, debit, credit, memo) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(
                                UUID.randomUUID().toString(),
                                entryId,
                                index + 1,
                                line.accountId,
                                line.debit.stripTrailingZeros().toPlainString(),
                                line.credit.stripTrailingZeros().toPlainString(),
                                line.memo
                            )
                        )
                    }
                }
            }

            private fun addAssetBalance(
                lines: MutableList<MigrationOpeningLine>,
                accountId: String,
                balance: BigDecimal,
                memo: String
            ) {
                when {
                    balance > BigDecimal.ZERO -> lines += MigrationOpeningLine(accountId, balance, BigDecimal.ZERO, memo)
                    balance < BigDecimal.ZERO -> lines += MigrationOpeningLine(accountId, BigDecimal.ZERO, balance.abs(), memo)
                }
            }

            private fun addLiabilityBalance(
                lines: MutableList<MigrationOpeningLine>,
                accountId: String,
                balance: BigDecimal,
                memo: String
            ) {
                when {
                    balance > BigDecimal.ZERO -> lines += MigrationOpeningLine(accountId, BigDecimal.ZERO, balance, memo)
                    balance < BigDecimal.ZERO -> lines += MigrationOpeningLine(accountId, balance.abs(), BigDecimal.ZERO, memo)
                }
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
