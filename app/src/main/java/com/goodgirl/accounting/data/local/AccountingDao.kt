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

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY occurredAt DESC")
    fun observeForProduct(productId: String): Flow<List<StockMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(movement: StockMovementEntity)
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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: LedgerEntryEntity)
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
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)
}

@Dao
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries ORDER BY occurredAt DESC, journalNumber DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE journalNumber = :journalNumber LIMIT 1")
    suspend fun findByNumber(journalNumber: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE sourceType = :sourceType AND sourceId = :sourceId LIMIT 1")
    suspend fun findBySource(sourceType: String, sourceId: String): JournalEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: JournalEntryEntity)
}

@Dao
interface JournalLineDao {
    @Query("SELECT * FROM journal_lines WHERE journalEntryId = :journalEntryId ORDER BY lineNumber")
    suspend fun getForEntry(journalEntryId: String): List<JournalLineEntity>

    @Query("SELECT * FROM journal_lines WHERE journalEntryId = :journalEntryId ORDER BY lineNumber")
    fun observeForEntry(journalEntryId: String): Flow<List<JournalLineEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(lines: List<JournalLineEntity>)
}
