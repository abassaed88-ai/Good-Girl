package com.goodgirl.accounting.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.goodgirl.accounting.domain.StockMovementReconciliation
import com.goodgirl.accounting.domain.HistoricalStockCostBaseline
import com.goodgirl.accounting.domain.HistoricalStockCostBaselineReconstructor
import com.goodgirl.accounting.domain.HistoricalStockCostCalculator
import com.goodgirl.accounting.domain.HistoricalStockCostCurrentState
import com.goodgirl.accounting.domain.HistoricalStockCostEvent
import java.math.BigDecimal
import java.util.UUID

const val ACCOUNTING_DATABASE_VERSION = 9

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
        ProductBarcodeEntity::class,
        ProductUnitEntity::class,
        PartyEntity::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        StockMovementEntity::class,
        StockCostBaselineEntity::class,
        LedgerEntryEntity::class,
        AccountEntity::class,
        JournalEntryEntity::class,
        JournalLineEntity::class,
        ExchangeRateHistoryEntity::class,
        ExchangeRateAuditEntity::class,
        ExchangeRateCorrectionItemEntity::class
    ],
    version = ACCOUNTING_DATABASE_VERSION,
    exportSchema = true
)
abstract class AccountingDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun productBarcodeDao(): ProductBarcodeDao
    abstract fun productUnitDao(): ProductUnitDao
    abstract fun categoryDao(): CategoryDao
    abstract fun partyDao(): PartyDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun stockCostBaselineDao(): StockCostBaselineDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun accountDao(): AccountDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun journalLineDao(): JournalLineDao
    abstract fun exchangeRateHistoryDao(): ExchangeRateHistoryDao
    abstract fun exchangeRateAuditDao(): ExchangeRateAuditDao
    abstract fun exchangeRateCorrectionItemDao(): ExchangeRateCorrectionItemDao

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


        /**
         * Adds durable exchange-rate history and its audit trail. It deliberately
         * does not update any invoice, ledger, journal, stock, or balance amounts.
         */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS exchange_rate_history (" +
                        "id TEXT NOT NULL, currencyPair TEXT NOT NULL, rate TEXT NOT NULL, " +
                        "effectiveAt INTEGER NOT NULL, recordedAt INTEGER NOT NULL, " +
                        "source TEXT NOT NULL, isArchived INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_exchange_rate_history_currencyPair_effectiveAt " +
                        "ON exchange_rate_history(currencyPair, effectiveAt)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_exchange_rate_history_recordedAt " +
                        "ON exchange_rate_history(recordedAt)"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS exchange_rate_audits (" +
                        "id TEXT NOT NULL, historyId TEXT NOT NULL, eventType TEXT NOT NULL, " +
                        "oldRate TEXT NOT NULL, newRate TEXT NOT NULL, reason TEXT NOT NULL, " +
                        "correctedAt INTEGER NOT NULL, affectedInvoiceCount INTEGER NOT NULL, " +
                        "affectedLedgerCount INTEGER NOT NULL, affectedJournalCount INTEGER NOT NULL, " +
                        "financialAmountsChanged INTEGER NOT NULL, PRIMARY KEY(id), " +
                        "FOREIGN KEY(historyId) REFERENCES exchange_rate_history(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_exchange_rate_audits_historyId " +
                        "ON exchange_rate_audits(historyId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_exchange_rate_audits_correctedAt " +
                        "ON exchange_rate_audits(correctedAt)"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS exchange_rate_correction_items (" +
                        "id TEXT NOT NULL, auditId TEXT NOT NULL, recordType TEXT NOT NULL, " +
                        "recordId TEXT NOT NULL, sourceType TEXT NOT NULL, sourceId TEXT NOT NULL, " +
                        "oldRate TEXT NOT NULL, newRate TEXT NOT NULL, PRIMARY KEY(id), " +
                        "FOREIGN KEY(auditId) REFERENCES exchange_rate_audits(id) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_exchange_rate_correction_items_auditId " +
                        "ON exchange_rate_correction_items(auditId)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_exchange_rate_correction_items_auditId_recordType_recordId " +
                        "ON exchange_rate_correction_items(auditId, recordType, recordId)"
                )
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS product_barcodes (" +
                        "id TEXT NOT NULL, productId TEXT NOT NULL, barcodeValue TEXT NOT NULL, " +
                        "format TEXT NOT NULL, origin TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_product_barcodes_productId ON product_barcodes(productId)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_product_barcodes_barcodeValue ON product_barcodes(barcodeValue)"
                )
            }
        }

        /**
         * Stores a reliable recording timestamp for each movement and establishes
         * a conservative per-product cost baseline. Where the ledger-opening
         * boundary and later movement history reconcile, the baseline is
         * reconstructed. Otherwise the current balance becomes an explicitly
         * limited fallback boundary; no missing historical cost is invented.
         */
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val migrationAt = System.currentTimeMillis()
                database.execSQL(
                    "ALTER TABLE stock_movements ADD COLUMN recordedAt INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "UPDATE stock_movements SET recordedAt = COALESCE(" +
                        "(SELECT invoices.createdAt FROM invoice_lines " +
                        "INNER JOIN invoices ON invoices.id = invoice_lines.invoiceId " +
                        "WHERE invoice_lines.id = stock_movements.sourceId LIMIT 1), occurredAt)"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS stock_cost_baselines (" +
                        "productId TEXT NOT NULL, quantity TEXT NOT NULL, averageCost TEXT NOT NULL, " +
                        "currencyCode TEXT NOT NULL, effectiveAt INTEGER NOT NULL, " +
                        "recordedAt INTEGER NOT NULL, source TEXT NOT NULL, PRIMARY KEY(productId))"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_stock_cost_baselines_effectiveAt " +
                        "ON stock_cost_baselines(effectiveAt)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_stock_cost_baselines_source " +
                        "ON stock_cost_baselines(source)"
                )

                val accountingBoundaryAt = database.query(
                    "SELECT MAX(occurredAt) FROM journal_entries " +
                        "WHERE sourceType = 'MIGRATION_OPENING' AND sourceId GLOB 'V5_TO_V6_*'"
                ).use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
                }

                database.query(
                    "SELECT id, stockQuantity, purchaseCost, priceCurrencyCode, createdAt " +
                        "FROM products ORDER BY id"
                ).use { products ->
                    while (products.moveToNext()) {
                        val productId = products.getString(0)
                        val currentQuantity = products.getString(1)
                        val currentCost = products.getString(2)
                        val currencyCode = products.getString(3).trim().uppercase()
                        val createdAt = products.getLong(4)
                        require(currencyCode == "SYP" || currencyCode == "USD") {
                            "عملة المنتج غير مدعومة أثناء إنشاء نقطة بداية كلفة المخزون"
                        }
                        val quantityValue = BigDecimal(currentQuantity)
                        val costValue = BigDecimal(currentCost)
                        require(quantityValue >= BigDecimal.ZERO && costValue >= BigDecimal.ZERO) {
                            "رصيد أو كلفة المنتج غير صالحين أثناء ترحيل سجل كلفة المخزون"
                        }

                        val allEvents = mutableListOf<HistoricalStockCostEvent>()
                        val previousCurrencies = mutableMapOf<String, String>()
                        var latestOccurredAt = createdAt
                        var latestRecordedAt = createdAt
                        database.query(
                            "SELECT id, sourceType, quantityDelta, unitCostSnapshot, currencyCode, " +
                                "occurredAt, recordedAt, note FROM stock_movements " +
                                "WHERE productId = ? ORDER BY occurredAt, recordedAt, id",
                            arrayOf(productId)
                        ).use { movements ->
                            while (movements.moveToNext()) {
                                val id = movements.getString(0)
                                val sourceType = movements.getString(1)
                                val quantityDelta = movements.getString(2)
                                val unitCost = movements.getString(3)
                                val eventCurrency = movements.getString(4)
                                val occurredAt = movements.getLong(5)
                                val recordedAt = movements.getLong(6)
                                val note = movements.getString(7)
                                latestOccurredAt = maxOf(latestOccurredAt, occurredAt)
                                latestRecordedAt = maxOf(latestRecordedAt, recordedAt)
                                allEvents += HistoricalStockCostEvent(
                                    id = id,
                                    sourceType = sourceType,
                                    quantityDelta = quantityDelta,
                                    unitCostSnapshot = unitCost,
                                    currencyCode = eventCurrency,
                                    occurredAt = occurredAt,
                                    recordedAt = recordedAt
                                )
                                if (sourceType.trim().uppercase() == "PRODUCT_CURRENCY_CORRECTION") {
                                    val oldCurrency = Regex(
                                        """من\s+(SYP|USD)\s+إلى\s+(SYP|USD)"""
                                    ).find(note)?.groupValues?.get(1)
                                    if (oldCurrency != null) previousCurrencies[id] = oldCurrency
                                }
                            }
                        }

                        var baseline: HistoricalStockCostBaseline? = null
                        var source = "CURRENT_SNAPSHOT_FALLBACK"
                        val hasPreBoundaryFutureEvent = accountingBoundaryAt != null &&
                            allEvents.any {
                                it.recordedAt <= accountingBoundaryAt && it.occurredAt > accountingBoundaryAt
                            }
                        if (accountingBoundaryAt != null && accountingBoundaryAt > 0L &&
                            !hasPreBoundaryFutureEvent
                        ) {
                            if (createdAt > accountingBoundaryAt) {
                                val zeroOpening = HistoricalStockCostBaseline(
                                    productId = productId,
                                    quantity = "0",
                                    averageCost = "0",
                                    currencyCode = currencyCode,
                                    effectiveAt = createdAt,
                                    recordedAt = (createdAt - 1L).coerceAtLeast(0L)
                                )
                                val replay = runCatching {
                                    HistoricalStockCostCalculator.recalculate(
                                        zeroOpening,
                                        allEvents.filter { it.recordedAt > accountingBoundaryAt }
                                    )
                                }.getOrNull()
                                if (replay != null &&
                                    BigDecimal(replay.endingQuantity).compareTo(quantityValue) == 0 &&
                                    replay.currencyCode == currencyCode &&
                                    (quantityValue.compareTo(BigDecimal.ZERO) == 0 ||
                                        BigDecimal(replay.endingAverageCost).subtract(costValue).abs()
                                            <= BigDecimal("0.00000001"))
                                ) {
                                    baseline = zeroOpening
                                    source = "PRODUCT_ZERO_OPENING"
                                }
                            } else {
                                baseline = runCatching {
                                    HistoricalStockCostBaselineReconstructor.reconstruct(
                                        current = HistoricalStockCostCurrentState(
                                            productId = productId,
                                            quantity = currentQuantity,
                                            averageCost = currentCost,
                                            currencyCode = currencyCode
                                        ),
                                        accountingBoundaryAt = accountingBoundaryAt,
                                        postBoundaryEvents = allEvents.filter {
                                            it.recordedAt > accountingBoundaryAt
                                        },
                                        previousCurrencyByCorrectionId = previousCurrencies
                                    )
                                }.getOrNull()
                                if (baseline != null) source = "ACCOUNTING_RECONSTRUCTED"
                            }
                        }

                        if (baseline == null) {
                            baseline = HistoricalStockCostBaseline(
                                productId = productId,
                                quantity = currentQuantity,
                                averageCost = currentCost,
                                currencyCode = currencyCode,
                                effectiveAt = maxOf(migrationAt, latestOccurredAt),
                                recordedAt = maxOf(migrationAt, latestRecordedAt)
                            )
                            source = "CURRENT_SNAPSHOT_FALLBACK"
                        }

                        database.execSQL(
                            "INSERT INTO stock_cost_baselines " +
                                "(productId, quantity, averageCost, currencyCode, effectiveAt, recordedAt, source) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(
                                baseline.productId,
                                baseline.quantity,
                                baseline.averageCost,
                                baseline.currencyCode,
                                baseline.effectiveAt,
                                baseline.recordedAt,
                                source
                            )
                        )
                    }
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build().also { INSTANCE = it }
            }
    }
}
