package com.goodgirl.accounting.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY id")
    suspend fun getAll(): List<ProductEntity>

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(products: List<ProductEntity>)

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name COLLATE NOCASE")
    fun observeActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    fun observeProduct(id: String): Flow<ProductEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: ProductEntity)

    @Update
    suspend fun update(product: ProductEntity)

    @Query("UPDATE products SET isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: String, updatedAt: Long)

    @Query("UPDATE products SET stockQuantity = :quantity, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStock(id: String, quantity: String, updatedAt: Long)
}

@Dao
interface ProductUnitDao {
    @Query("SELECT * FROM product_units ORDER BY id")
    suspend fun getAll(): List<ProductUnitEntity>

    @Query("DELETE FROM product_units")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(units: List<ProductUnitEntity>)

    @Query("SELECT * FROM product_units WHERE productId = :productId ORDER BY isBaseUnit DESC, unitName COLLATE NOCASE")
    fun observeForProduct(productId: String): Flow<List<ProductUnitEntity>>

    @Query("SELECT * FROM product_units WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ProductUnitEntity?

    @Query("SELECT * FROM product_units WHERE productId = :productId AND unitName = :unitName LIMIT 1")
    suspend fun findByName(productId: String, unitName: String): ProductUnitEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(unit: ProductUnitEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun getAll(): List<CategoryEntity>

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)
}

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties ORDER BY id")
    suspend fun getAll(): List<PartyEntity>

    @Query("DELETE FROM parties")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(parties: List<PartyEntity>)

    @Query("SELECT * FROM parties WHERE kind = :kind ORDER BY name COLLATE NOCASE")
    fun observeByKind(kind: String): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PartyEntity?

    @Query("SELECT * FROM parties WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PartyEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(party: PartyEntity)

    @Update
    suspend fun update(party: PartyEntity)
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY id")
    suspend fun getAllInvoices(): List<InvoiceEntity>

    @Query("SELECT * FROM invoice_lines ORDER BY id")
    suspend fun getAllLines(): List<InvoiceLineEntity>

    @Query("DELETE FROM invoice_lines")
    suspend fun deleteAllLines()

    @Query("DELETE FROM invoices")
    suspend fun deleteAllInvoices()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllInvoices(invoices: List<InvoiceEntity>)

    @Query("SELECT * FROM invoices ORDER BY issuedAt DESC")
    fun observeAll(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :invoiceId ORDER BY id")
    suspend fun getLines(invoiceId: String): List<InvoiceLineEntity>

    @Query("SELECT * FROM invoice_lines ORDER BY id")
    fun observeAllLines(): Flow<List<InvoiceLineEntity>>

    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE partyId = :partyId AND kind = :kind AND currencyCode = :currencyCode AND status = 'POSTED' ORDER BY issuedAt ASC, createdAt ASC")
    suspend fun getPostedForPartyAndCurrency(
        partyId: String,
        kind: String,
        currencyCode: String
    ): List<InvoiceEntity>

    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvoice(invoice: InvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<InvoiceLineEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllLines(lines: List<InvoiceLineEntity>)
}

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements ORDER BY id")
    suspend fun getAll(): List<StockMovementEntity>

    @Query("DELETE FROM stock_movements")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(movements: List<StockMovementEntity>)

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY occurredAt DESC, recordedAt DESC, id DESC")
    fun observeForProduct(productId: String): Flow<List<StockMovementEntity>>

    @Query("SELECT MAX(recordedAt) FROM stock_movements WHERE productId = :productId")
    suspend fun getLatestRecordedAt(productId: String): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(movement: StockMovementEntity)
}

@Dao
interface StockCostBaselineDao {
    @Query("SELECT * FROM stock_cost_baselines ORDER BY productId")
    suspend fun getAll(): List<StockCostBaselineEntity>

    @Query("SELECT * FROM stock_cost_baselines WHERE productId = :productId LIMIT 1")
    suspend fun findByProductId(productId: String): StockCostBaselineEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(baseline: StockCostBaselineEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(baselines: List<StockCostBaselineEntity>)

    @Query("DELETE FROM stock_cost_baselines")
    suspend fun deleteAll()
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_entries ORDER BY id")
    suspend fun getAll(): List<LedgerEntryEntity>

    @Query("DELETE FROM ledger_entries")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<LedgerEntryEntity>)

    @Query("SELECT * FROM ledger_entries ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE partyId = :partyId ORDER BY occurredAt DESC")
    fun observeForParty(partyId: String): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE partyId = :partyId ORDER BY occurredAt DESC")
    suspend fun getForParty(partyId: String): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): LedgerEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: LedgerEntryEntity)

    @Update
    suspend fun update(entry: LedgerEntryEntity)
}


@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY code")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY code")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(accounts: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)
}

@Dao
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries ORDER BY occurredAt DESC, journalNumber DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries ORDER BY occurredAt, journalNumber")
    suspend fun getAll(): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE journalNumber = :journalNumber LIMIT 1")
    suspend fun findByNumber(journalNumber: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE sourceType = :sourceType AND sourceId = :sourceId LIMIT 1")
    suspend fun findBySource(sourceType: String, sourceId: String): JournalEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: JournalEntryEntity)

    @Update
    suspend fun update(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<JournalEntryEntity>)

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAll()
}

@Dao
interface JournalLineDao {
    @Query("SELECT * FROM journal_lines WHERE journalEntryId = :journalEntryId ORDER BY lineNumber")
    suspend fun getForEntry(journalEntryId: String): List<JournalLineEntity>

    @Query("SELECT * FROM journal_lines ORDER BY journalEntryId, lineNumber")
    suspend fun getAll(): List<JournalLineEntity>

    @Query("SELECT * FROM journal_lines ORDER BY journalEntryId, lineNumber")
    fun observeAll(): Flow<List<JournalLineEntity>>

    @Query("SELECT * FROM journal_lines WHERE journalEntryId = :journalEntryId ORDER BY lineNumber")
    fun observeForEntry(journalEntryId: String): Flow<List<JournalLineEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(lines: List<JournalLineEntity>)

    @Query("DELETE FROM journal_lines")
    suspend fun deleteAll()
}


@Dao
interface ExchangeRateHistoryDao {
    @Query("SELECT * FROM exchange_rate_history WHERE isArchived = 0 ORDER BY effectiveAt DESC, recordedAt DESC")
    fun observeAll(): Flow<List<ExchangeRateHistoryEntity>>

    @Query("SELECT * FROM exchange_rate_history ORDER BY effectiveAt, recordedAt")
    suspend fun getAll(): List<ExchangeRateHistoryEntity>

    @Query("SELECT * FROM exchange_rate_history WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ExchangeRateHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: ExchangeRateHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<ExchangeRateHistoryEntity>)

    @Update
    suspend fun update(entry: ExchangeRateHistoryEntity)

    @Query("UPDATE exchange_rate_history SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: String)

    @Query("DELETE FROM exchange_rate_history")
    suspend fun deleteAll()
}

@Dao
interface ExchangeRateAuditDao {
    @Query("SELECT * FROM exchange_rate_audits ORDER BY correctedAt DESC, id DESC")
    fun observeAll(): Flow<List<ExchangeRateAuditEntity>>

    @Query("SELECT * FROM exchange_rate_audits ORDER BY correctedAt DESC, id DESC")
    suspend fun getAll(): List<ExchangeRateAuditEntity>

    @Query("SELECT * FROM exchange_rate_audits WHERE historyId = :historyId ORDER BY correctedAt DESC")
    suspend fun getForHistory(historyId: String): List<ExchangeRateAuditEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: ExchangeRateAuditEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<ExchangeRateAuditEntity>)

    @Query("DELETE FROM exchange_rate_audits")
    suspend fun deleteAll()
}

@Dao
interface ExchangeRateCorrectionItemDao {
    @Query("SELECT * FROM exchange_rate_correction_items ORDER BY auditId, recordType, recordId")
    suspend fun getAll(): List<ExchangeRateCorrectionItemEntity>

    @Query("SELECT * FROM exchange_rate_correction_items WHERE auditId = :auditId ORDER BY recordType, recordId")
    suspend fun getForAudit(auditId: String): List<ExchangeRateCorrectionItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<ExchangeRateCorrectionItemEntity>)

    @Query("DELETE FROM exchange_rate_correction_items")
    suspend fun deleteAll()
}

@Dao
interface ProductBarcodeDao {
    @Query("SELECT * FROM product_barcodes ORDER BY productId, createdAt")
    suspend fun getAll(): List<ProductBarcodeEntity>

    @Query("SELECT * FROM product_barcodes WHERE productId = :productId ORDER BY createdAt")
    suspend fun getForProduct(productId: String): List<ProductBarcodeEntity>

    @Query("SELECT * FROM product_barcodes WHERE barcodeValue = :barcodeValue LIMIT 1")
    suspend fun findByValue(barcodeValue: String): ProductBarcodeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(barcode: ProductBarcodeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(barcodes: List<ProductBarcodeEntity>)

    @Query("DELETE FROM product_barcodes")
    suspend fun deleteAll()
}


@Dao
interface StockCostRecalculationAuditDao {
    @Query("SELECT * FROM stock_cost_recalculation_audits ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<StockCostRecalculationAuditEntity>>
    @Query("SELECT * FROM stock_cost_recalculation_audits ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<StockCostRecalculationAuditEntity>
    @Query("SELECT * FROM stock_cost_recalculation_audits WHERE productId = :productId ORDER BY createdAt DESC")
    suspend fun getForProduct(productId: String): List<StockCostRecalculationAuditEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(audits: List<StockCostRecalculationAuditEntity>)
    @Query("DELETE FROM stock_cost_recalculation_audits")
    suspend fun deleteAll()
}

@Dao
interface StockCostRecalculationItemDao {
    @Query("SELECT * FROM stock_cost_recalculation_items ORDER BY auditId, movementId")
    fun observeAll(): Flow<List<StockCostRecalculationItemEntity>>
    @Query("SELECT * FROM stock_cost_recalculation_items ORDER BY auditId, movementId")
    suspend fun getAll(): List<StockCostRecalculationItemEntity>
    @Query("SELECT * FROM stock_cost_recalculation_items WHERE auditId = :auditId ORDER BY movementId")
    suspend fun getForAudit(auditId: String): List<StockCostRecalculationItemEntity>
    @Query("SELECT * FROM stock_cost_recalculation_items WHERE movementId = :movementId ORDER BY auditId DESC")
    suspend fun getForMovement(movementId: String): List<StockCostRecalculationItemEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<StockCostRecalculationItemEntity>)
    @Query("DELETE FROM stock_cost_recalculation_items")
    suspend fun deleteAll()
}
