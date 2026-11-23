package com.goodgirl.accounting.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Monetary and quantity values are stored as decimal strings, never Double,
 * so historical invoice values remain exact.
 */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val note: String = "",
    val createdAt: Long
)

@Entity(
    tableName = "products",
    indices = [Index("name"), Index("sku"), Index("categoryId")]
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val sku: String = "",
    val name: String,
    val categoryId: String? = null,
    val baseUnit: String = "قطعة",
    val purchaseCost: String = "0",
    val salePrice: String = "0",
    @ColumnInfo(defaultValue = "'SYP'") val priceCurrencyCode: String = "SYP",
    val stockQuantity: String = "0",
    val lowStockThreshold: String = "0",
    val note: String = "",
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "parties", indices = [Index("name"), Index("kind")])
data class PartyEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** CUSTOMER or SUPPLIER */
    val kind: String,
    val phone: String = "",
    val address: String = "",
    val openingBalance: String = "0",
    val currencyCode: String = "SYP",
    @ColumnInfo(defaultValue = "'1'") val openingExchangeRate: String = "1",
    val note: String = "",
    val createdAt: Long
)

@Entity(
    tableName = "invoices",
    indices = [Index(value = ["invoiceNumber"], unique = true), Index("partyId"), Index("issuedAt")]
)
data class InvoiceEntity(
    @PrimaryKey val id: String,
    val invoiceNumber: String,
    /** SALE or PURCHASE */
    val kind: String,
    val partyId: String? = null,
    val currencyCode: String,
    val exchangeRateSnapshot: String,
    val subtotal: String,
    val discount: String,
    val total: String,
    val paid: String,
    val remaining: String,
    /** DRAFT, POSTED, or VOID */
    val status: String = "DRAFT",
    val issuedAt: Long,
    val note: String = "",
    val createdAt: Long
)

@Entity(
    tableName = "invoice_lines",
    indices = [Index("invoiceId"), Index("productId")]
)
data class InvoiceLineEntity(
    @PrimaryKey val id: String,
    val invoiceId: String,
    val productId: String? = null,
    val productNameSnapshot: String,
    val unitSnapshot: String,
    val quantity: String,
    val unitCostSnapshot: String,
    val unitPriceSnapshot: String,
    val discount: String = "0",
    val lineTotal: String,
    @ColumnInfo(defaultValue = "'1'") val unitFactorSnapshot: String = "1",
    @ColumnInfo(defaultValue = "'1'") val baseQuantity: String = "1"
)

@Entity(
    tableName = "product_units",
    indices = [Index("productId"), Index(value = ["productId", "unitName"], unique = true)]
)
data class ProductUnitEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val unitName: String,
    val baseFactor: String,
    val isBaseUnit: Boolean = false
)

@Entity(
    tableName = "stock_movements",
    indices = [Index("productId"), Index(value = ["sourceType", "sourceId"])]
)
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val productId: String,
    /** Signed quantity: positive for stock-in, negative for stock-out. */
    val quantityDelta: String,
    val unitCostSnapshot: String,
    @ColumnInfo(defaultValue = "'SYP'") val currencyCode: String = "SYP",
    val sourceType: String,
    val sourceId: String,
    val occurredAt: Long,
    val note: String = ""
)

@Entity(
    tableName = "ledger_entries",
    indices = [Index("partyId"), Index(value = ["sourceType", "sourceId"]), Index("occurredAt")]
)
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val partyId: String? = null,
    /** CASH_IN, CASH_OUT, RECEIVABLE, PAYABLE, INCOME, or EXPENSE */
    val entryType: String,
    /** Positive amount; entryType defines its accounting meaning. */
    val amount: String,
    val currencyCode: String,
    val exchangeRateSnapshot: String,
    val sourceType: String,
    val sourceId: String,
    val occurredAt: Long,
    val note: String = ""
)


@Entity(
    tableName = "accounts",
    indices = [Index(value = ["code"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    /** ASSET, LIABILITY, EQUITY, REVENUE, or EXPENSE. */
    val accountType: String,
    /** DEBIT or CREDIT. */
    val normalSide: String,
    val isSystem: Boolean,
    val isActive: Boolean,
    val createdAt: Long
)

@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["journalNumber"], unique = true),
        Index(value = ["sourceType", "sourceId"], unique = true),
        Index(value = ["occurredAt"])
    ]
)
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val journalNumber: String,
    val sourceType: String,
    val sourceId: String,
    val currencyCode: String,
    val exchangeRateSnapshot: String,
    val occurredAt: Long,
    val description: String,
    val createdAt: Long
)

@Entity(
    tableName = "journal_lines",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalEntryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["journalEntryId"]),
        Index(value = ["accountId"]),
        Index(value = ["journalEntryId", "lineNumber"], unique = true)
    ]
)
data class JournalLineEntity(
    @PrimaryKey val id: String,
    val journalEntryId: String,
    val lineNumber: Int,
    val accountId: String,
    val debit: String,
    val credit: String,
    val memo: String
)
