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
    @ColumnInfo(defaultValue = "0") val recordedAt: Long = occurredAt,
    val note: String = ""
)

@Entity(
    tableName = "stock_cost_baselines",
    indices = [Index("effectiveAt"), Index("source")]
)
data class StockCostBaselineEntity(
    @PrimaryKey val productId: String,
    val quantity: String,
    val averageCost: String,
    val currencyCode: String,
    val effectiveAt: Long,
    val recordedAt: Long,
    /** ACCOUNTING_RECONSTRUCTED, PRODUCT_ZERO_OPENING, or CURRENT_SNAPSHOT_FALLBACK. */
    val source: String
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


@Entity(tableName = "exchange_rate_history", indices = [
    Index(value = ["currencyPair", "effectiveAt"]), Index("recordedAt")
])
data class ExchangeRateHistoryEntity(
    @PrimaryKey val id: String,
    val currencyPair: String,
    val rate: String,
    val effectiveAt: Long,
    val recordedAt: Long,
    val source: String,
    val isArchived: Boolean = false
)

@Entity(tableName = "exchange_rate_audits", foreignKeys = [
    ForeignKey(
        entity = ExchangeRateHistoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["historyId"],
        onDelete = ForeignKey.RESTRICT
    )
], indices = [Index("historyId"), Index("correctedAt")])
data class ExchangeRateAuditEntity(
    @PrimaryKey val id: String,
    val historyId: String,
    /** RATE_IMPORTED, RATE_SET, RATE_CORRECTION, or RATE_ARCHIVED. */
    val eventType: String,
    val oldRate: String,
    val newRate: String,
    val reason: String,
    val correctedAt: Long,
    val affectedInvoiceCount: Int = 0,
    val affectedLedgerCount: Int = 0,
    val affectedJournalCount: Int = 0,
    val financialAmountsChanged: Boolean = false
)

@Entity(tableName = "exchange_rate_correction_items", foreignKeys = [
    ForeignKey(
        entity = ExchangeRateAuditEntity::class,
        parentColumns = ["id"],
        childColumns = ["auditId"],
        onDelete = ForeignKey.CASCADE
    )
], indices = [
    Index("auditId"),
    Index(value = ["auditId", "recordType", "recordId"], unique = true)
])
data class ExchangeRateCorrectionItemEntity(
    @PrimaryKey val id: String,
    val auditId: String,
    /** INVOICE, LEDGER, or JOURNAL. */
    val recordType: String,
    val recordId: String,
    val sourceType: String,
    val sourceId: String,
    val oldRate: String,
    val newRate: String
)

@Entity(
    tableName = "product_barcodes",
    indices = [Index("productId"), Index(value = ["barcodeValue"], unique = true)]
)
data class ProductBarcodeEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val barcodeValue: String,
    /** EAN_13, EAN_8, UPC_A, or CODE_128. */
    val format: String,
    /** ORIGINAL for a supplier barcode, INTERNAL for a code created in Good Girl. */
    val origin: String,
    val createdAt: Long
)


@Entity(tableName = "stock_cost_recalculation_audits", indices = [Index("createdAt"), Index("productId")])
data class StockCostRecalculationAuditEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val triggerType: String,
    val triggerSourceId: String,
    val reason: String,
    val createdAt: Long,
    val currencyCode: String,
    val oldEndingQuantity: String,
    val newEndingQuantity: String,
    val oldEndingAverageCost: String,
    val newEndingAverageCost: String,
    val affectedStockOutCount: Int
)

@Entity(tableName = "stock_cost_recalculation_items", foreignKeys = [
    ForeignKey(entity = StockCostRecalculationAuditEntity::class, parentColumns = ["id"], childColumns = ["auditId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("auditId"), Index(value = ["auditId", "movementId"], unique = true), Index("movementId")])
data class StockCostRecalculationItemEntity(
    @PrimaryKey val id: String,
    val auditId: String,
    val movementId: String,
    val sourceType: String,
    val sourceId: String,
    val invoiceId: String,
    val currencyCode: String,
    val quantity: String,
    val oldUnitCost: String,
    val newUnitCost: String,
    val oldExtendedCost: String,
    val newExtendedCost: String
)


/**
 * Durable editor state for the single active daily-sales draft. Product and
 * customer IDs are snapshots only, so archiving master data cannot erase work.
 */
@Entity(tableName = "daily_sales_drafts")
data class DailySalesDraftEntity(
    @PrimaryKey val id: String = "active",
    val selectedDay: Long,
    val selectedProductId: String? = null,
    val selectedProductNameSnapshot: String = "",
    val selectedUnitName: String? = null,
    val productSearchQuery: String = "",
    val selectedProductCategoryId: String? = null,
    val quantity: String = "1",
    val salePrice: String = "0",
    val lineDiscount: String = "0",
    val currencyCode: String = "SYP",
    val exchangeRate: String = "1",
    val paidAmount: String = "",
    val paidAmountTouched: Boolean = false,
    val selectedPartyId: String? = null,
    val note: String = "",
    val updatedAt: Long
)

@Entity(
    tableName = "daily_sales_draft_lines",
    foreignKeys = [
        ForeignKey(
            entity = DailySalesDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("draftId"),
        Index(value = ["draftId", "lineOrder"], unique = true)
    ]
)
data class DailySalesDraftLineEntity(
    @PrimaryKey val id: String,
    val draftId: String = "active",
    val lineOrder: Int,
    val productId: String,
    val productNameSnapshot: String,
    val quantity: String,
    val unitName: String,
    val unitFactor: String,
    val unitPrice: String,
    val discount: String,
    val currencyCode: String,
    val exchangeRate: String,
    val paidAmount: String,
    val customerId: String?,
    val note: String,
    val issuedAt: Long,
    val purchaseCostSnapshot: String? = null
)
