package com.goodgirl.accounting.data

import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.data.local.PartyEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.data.local.ProductBarcodeEntity
import com.goodgirl.accounting.data.local.ProductUnitEntity
import com.goodgirl.accounting.data.local.StockMovementEntity
import com.goodgirl.accounting.data.local.StockCostBaselineEntity
import com.goodgirl.accounting.data.local.ExchangeRateHistoryEntity
import com.goodgirl.accounting.data.local.ExchangeRateAuditEntity
import com.goodgirl.accounting.data.local.ExchangeRateCorrectionItemEntity
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupIntegrityValidatorTest {
    private val empty = emptyList<com.goodgirl.accounting.data.local.CategoryEntity>()

    private fun validate(
        products: List<ProductEntity> = emptyList(),
        barcodes: List<ProductBarcodeEntity> = emptyList(),
        units: List<ProductUnitEntity> = emptyList(),
        parties: List<PartyEntity> = emptyList(),
        invoices: List<InvoiceEntity> = emptyList(),
        lines: List<InvoiceLineEntity> = emptyList(),
        movements: List<StockMovementEntity> = emptyList(),
        entries: List<LedgerEntryEntity> = emptyList(),
        accounts: List<AccountEntity> = emptyList(),
        journalEntries: List<JournalEntryEntity> = emptyList(),
        journalLines: List<JournalLineEntity> = emptyList(),
        rates: List<ExchangeRateHistoryEntity> = emptyList(),
        rateAudits: List<ExchangeRateAuditEntity> = emptyList(),
        correctionItems: List<ExchangeRateCorrectionItemEntity> = emptyList(),
        stockCostBaselines: List<StockCostBaselineEntity> = emptyList()
    ) = BackupIntegrityValidator.validate(
        categories = empty,
        products = products,
        productBarcodes = barcodes,
        units = units,
        parties = parties,
        invoices = invoices,
        invoiceLines = lines,
        movements = movements,
        ledgerEntries = entries,
        accounts = accounts,
        journalEntries = journalEntries,
        journalLines = journalLines,
        exchangeRateHistory = rates,
        exchangeRateAudits = rateAudits,
        exchangeRateCorrectionItems = correctionItems,
        stockCostBaselines = stockCostBaselines
    )

    private fun product() = ProductEntity(
        id = "p1",
        name = "كريم",
        baseUnit = "قطعة",
        purchaseCost = "3",
        salePrice = "50",
        priceCurrencyCode = "SYP",
        stockQuantity = "4",
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun baseUnit() = ProductUnitEntity(
        id = "u1",
        productId = "p1",
        unitName = "قطعة",
        baseFactor = "1",
        isBaseUnit = true
    )

    private fun customer() = PartyEntity(
        id = "c1",
        name = "عميل",
        kind = "CUSTOMER",
        createdAt = 1L
    )

    private fun saleInvoice(partyId: String? = null) = InvoiceEntity(
        id = "i1",
        invoiceNumber = "S-1",
        kind = "SALE",
        partyId = partyId,
        currencyCode = "SYP",
        exchangeRateSnapshot = "1",
        subtotal = "100",
        discount = "0",
        total = "100",
        paid = "100",
        remaining = "0",
        status = "POSTED",
        issuedAt = 1L,
        createdAt = 1L
    )

    private fun saleLine() = InvoiceLineEntity(
        id = "l1",
        invoiceId = "i1",
        productId = "p1",
        productNameSnapshot = "كريم",
        unitSnapshot = "علبة",
        quantity = "2",
        unitCostSnapshot = "3",
        unitPriceSnapshot = "50",
        discount = "0",
        lineTotal = "100",
        unitFactorSnapshot = "2",
        baseQuantity = "4"
    )

    @Test
    fun acceptsUniqueBarcodeLinkedToAnExistingProduct() {
        validate(
            products = listOf(product()),
            barcodes = listOf(ProductBarcodeEntity(
                id = "barcode-1",
                productId = "p1",
                barcodeValue = "6291234567890",
                format = "EAN_13",
                origin = "ORIGINAL",
                createdAt = 10L
            )),
            units = listOf(baseUnit()),
            movements = listOf(
                StockMovementEntity(
                    id = "m-opening", productId = "p1", quantityDelta = "4",
                    unitCostSnapshot = "3", currencyCode = "SYP",
                    sourceType = "OPENING_BALANCE", sourceId = "p1", occurredAt = 1L
                )
            )
        )
    }

    @Test
    fun rejectsDuplicateBarcodeValuesAcrossProducts() {
        val secondProduct = product().copy(id = "p2", name = "شامبو", stockQuantity = "0")
        assertThrows(IllegalArgumentException::class.java) {
            validate(
                products = listOf(product(), secondProduct),
                barcodes = listOf(
                    ProductBarcodeEntity("b1", "p1", "123456789012", "CODE_128", "INTERNAL", 1L),
                    ProductBarcodeEntity("b2", "p2", "123456789012", "CODE_128", "INTERNAL", 2L)
                ),
                units = listOf(baseUnit(), ProductUnitEntity("u2", "p2", "قطعة", "1", true)),
                movements = listOf(
                    StockMovementEntity("m1", "p1", "4", "3", "SYP", "OPENING_BALANCE", "p1", 1L)
                )
            )
        }
    }

    @Test
    fun rejectsBarcodeLinkedToMissingProduct() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(
                barcodes = listOf(ProductBarcodeEntity(
                    "barcode-orphan", "missing", "12345678", "EAN_8", "ORIGINAL", 1L
                ))
            )
        }
    }

    @Test
    fun acceptsAnEmptyBackup() {
        validate()
    }

    @Test
    fun acceptsConsistentProductUnitInvoiceAndMovementRecords() {
        validate(
            products = listOf(product()),
            units = listOf(baseUnit(), ProductUnitEntity("u2", "p1", "علبة", "2")),
            invoices = listOf(saleInvoice()),
            lines = listOf(saleLine()),
            movements = listOf(
                StockMovementEntity(
                    id = "m-opening", productId = "p1", quantityDelta = "6",
                    unitCostSnapshot = "3", currencyCode = "SYP",
                    sourceType = "OPENING_BALANCE", sourceId = "p1", occurredAt = 1L
                ),
                StockMovementEntity(
                    id = "m1", productId = "p1", quantityDelta = "-2",
                    unitCostSnapshot = "3", currencyCode = "SYP",
                    sourceType = "SALE_INVOICE", sourceId = "l1", occurredAt = 2L
                )
            )
        )
    }

    @Test
    fun rejectsProductWithoutItsBaseUnit() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(products = listOf(product()))
        }
    }

    @Test
    fun rejectsProductBalanceThatDoesNotMatchMovementHistory() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(
                products = listOf(product()),
                units = listOf(baseUnit()),
                movements = listOf(
                    StockMovementEntity(
                        id = "m-opening", productId = "p1", quantityDelta = "3",
                        unitCostSnapshot = "3", currencyCode = "SYP",
                        sourceType = "OPENING_BALANCE", sourceId = "p1", occurredAt = 1L
                    )
                )
            )
        }
    }
    @Test
    fun rejectsZeroMovementEvenWhenDecimalHasScale() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(
                products = listOf(product()),
                units = listOf(baseUnit()),
                movements = listOf(
                    StockMovementEntity(
                        id = "m-zero",
                        productId = "p1",
                        quantityDelta = "0.0",
                        unitCostSnapshot = "3",
                        currencyCode = "SYP",
                        sourceType = "MANUAL_ADJUSTMENT",
                        sourceId = "m-zero",
                        occurredAt = 1L
                    )
                )
            )
        }
    }

    @Test
    fun rejectsMovementPointingToMissingProduct() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(
                movements = listOf(
                    StockMovementEntity(
                        id = "m1",
                        productId = "missing",
                        quantityDelta = "1",
                        unitCostSnapshot = "3",
                        currencyCode = "SYP",
                        sourceType = "MANUAL_ADJUSTMENT",
                        sourceId = "m1",
                        occurredAt = 1L
                    )
                )
            )
        }
    }

    @Test
    fun rejectsInvoiceLinkedToWrongPartyKind() {
        assertThrows(IllegalArgumentException::class.java) {
            val supplier = customer().copy(kind = "SUPPLIER")
            validate(parties = listOf(supplier), invoices = listOf(saleInvoice("c1")))
        }
    }

    @Test
    fun rejectsInvoiceLineWithIncorrectBaseQuantity() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(
                products = listOf(product()),
                units = listOf(baseUnit()),
                invoices = listOf(saleInvoice()),
                lines = listOf(saleLine().copy(baseQuantity = "3"))
            )
        }
    }

    @Test
    fun rejectsLedgerEntryPointingToMissingParty() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(
                entries = listOf(
                    LedgerEntryEntity(
                        id = "e1",
                        partyId = "missing",
                        entryType = "RECEIVABLE",
                        amount = "10",
                        currencyCode = "SYP",
                        exchangeRateSnapshot = "1",
                        sourceType = "TEST",
                        sourceId = "1",
                        occurredAt = 1L
                    )
                )
            )
        }
    }

    @Test
    fun rejectsPostedInvoiceWithoutLines() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(invoices = listOf(saleInvoice()))
        }
    }

    @Test
    fun rejectsInvoiceHeaderThatDoesNotMatchItsLineTotals() {
        assertThrows(IllegalArgumentException::class.java) {
            val inconsistent = saleInvoice().copy(
                subtotal = "110",
                discount = "10",
                total = "100"
            )
            validate(
                products = listOf(product()),
                units = listOf(baseUnit()),
                invoices = listOf(inconsistent),
                lines = listOf(saleLine())
            )
        }
    }

    @Test
    fun rejectsCreditInvoiceWithoutPartyAccount() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(invoices = listOf(saleInvoice().copy(paid = "90", remaining = "10")))
        }
    }

    @Test
    fun rejectsInvoiceWhosePaidAndRemainingDoNotMatchTotal() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(invoices = listOf(saleInvoice().copy(remaining = "1")))
        }
    }


    private fun journalAccounts() = listOf(
        AccountEntity("a-cash", "1000", "الصندوق", "ASSET", "DEBIT", true, true, 1L),
        AccountEntity("a-sales", "4000", "المبيعات", "REVENUE", "CREDIT", true, true, 1L)
    )

    private fun journalHeader() = JournalEntryEntity(
        id = "j1", journalNumber = "JE-1", sourceType = "SALE_INVOICE",
        sourceId = "invoice-1", currencyCode = "SYP", exchangeRateSnapshot = "1",
        occurredAt = 1L, description = "قيد بيع", createdAt = 1L
    )

    private fun journalLines(debit: String = "100", credit: String = "100") = listOf(
        JournalLineEntity("jl1", "j1", 1, "a-cash", debit, "0", ""),
        JournalLineEntity("jl2", "j1", 2, "a-sales", "0", credit, "")
    )

    @Test
    fun acceptsBalancedJournalWithValidAccounts() {
        validate(accounts = journalAccounts(), journalEntries = listOf(journalHeader()), journalLines = journalLines())
    }

    @Test
    fun rejectsUnbalancedJournal() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(accounts = journalAccounts(), journalEntries = listOf(journalHeader()),
                journalLines = journalLines(debit = "100", credit = "99.99"))
        }
    }

    @Test
    fun rejectsJournalLineLinkedToMissingAccount() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(
                accounts = journalAccounts(),
                journalEntries = listOf(journalHeader()),
                journalLines = listOf(
                    JournalLineEntity("jl1", "j1", 1, "missing", "10", "0", ""),
                    JournalLineEntity("jl2", "j1", 2, "a-sales", "0", "10", "")
                )
            )
        }
    }


    @Test
    fun acceptsExchangeRateCorrectionWithCompleteAuditAndAffectedRecordSnapshot() {
        val rate = ExchangeRateHistoryEntity(
            id = "rate-1", currencyPair = "USD/SYP", rate = "15100",
            effectiveAt = 100L, recordedAt = 100L, source = "USER"
        )
        val audit = ExchangeRateAuditEntity(
            id = "audit-1", historyId = "rate-1", eventType = "RATE_CORRECTION",
            oldRate = "15000", newRate = "15100", reason = "تصحيح سعر مدخل بالخطأ",
            correctedAt = 200L, affectedInvoiceCount = 1
        )
        val item = ExchangeRateCorrectionItemEntity(
            id = "item-1", auditId = "audit-1", recordType = "INVOICE",
            recordId = "invoice-1", sourceType = "SALE_INVOICE", sourceId = "invoice-1",
            oldRate = "15000", newRate = "15100"
        )
        validate(rates = listOf(rate), rateAudits = listOf(audit), correctionItems = listOf(item))
    }

    @Test
    fun rejectsExchangeRateAuditWithoutItsHistoryRecord() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(
                rateAudits = listOf(
                    ExchangeRateAuditEntity(
                        id = "audit-1", historyId = "missing", eventType = "RATE_SET",
                        oldRate = "15000", newRate = "15100", reason = "تغيير سعر",
                        correctedAt = 200L
                    )
                )
            )
        }
    }

    @Test
    fun rejectsCorrectionAuditWhenAffectedCountsDoNotMatchItems() {
        assertThrows(IllegalArgumentException::class.java) {
            val rate = ExchangeRateHistoryEntity(
                id = "rate-1", currencyPair = "USD/SYP", rate = "15100",
                effectiveAt = 100L, recordedAt = 100L, source = "USER"
            )
            val audit = ExchangeRateAuditEntity(
                id = "audit-1", historyId = "rate-1", eventType = "RATE_CORRECTION",
                oldRate = "15000", newRate = "15100", reason = "تصحيح",
                correctedAt = 200L, affectedInvoiceCount = 2
            )
            validate(rates = listOf(rate), rateAudits = listOf(audit))
        }
    }

    @Test
    fun acceptsACompleteTrustedStockCostBaselineSet() {
        validate(
            products = listOf(product()),
            units = listOf(baseUnit()),
            movements = listOf(
                StockMovementEntity(
                    id = "m-opening", productId = "p1", quantityDelta = "4",
                    unitCostSnapshot = "3", currencyCode = "SYP",
                    sourceType = "OPENING_BALANCE", sourceId = "p1", occurredAt = 1L
                )
            ),
            stockCostBaselines = listOf(
                StockCostBaselineEntity(
                    productId = "p1",
                    quantity = "0",
                    averageCost = "0",
                    currencyCode = "SYP",
                    effectiveAt = 1L,
                    recordedAt = 1L,
                    source = "PRODUCT_ZERO_OPENING"
                )
            )
        )
    }

    @Test
    fun rejectsStockCostBaselineForMissingProduct() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(
                stockCostBaselines = listOf(
                    StockCostBaselineEntity(
                        productId = "missing",
                        quantity = "0",
                        averageCost = "0",
                        currencyCode = "SYP",
                        effectiveAt = 1L,
                        recordedAt = 1L,
                        source = "PRODUCT_ZERO_OPENING"
                    )
                )
            )
        }
    }

    @Test
    fun rejectsUnsupportedStockCostBaselineSource() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(
                products = listOf(product()),
                units = listOf(baseUnit()),
                stockCostBaselines = listOf(
                    StockCostBaselineEntity(
                        productId = "p1",
                        quantity = "4",
                        averageCost = "3",
                        currencyCode = "SYP",
                        effectiveAt = 1L,
                        recordedAt = 1L,
                        source = "UNVERIFIED"
                    )
                )
            )
        }
    }

    @Test
    fun acceptsZeroQuantityCurrencyCorrectionBoundaryInBackup() {
        validate(
            products = listOf(product().copy(priceCurrencyCode = "USD")),
            units = listOf(baseUnit()),
            movements = listOf(
                StockMovementEntity(
                    id = "m-opening", productId = "p1", quantityDelta = "4",
                    unitCostSnapshot = "3", currencyCode = "SYP",
                    sourceType = "OPENING_BALANCE", sourceId = "p1", occurredAt = 1L
                ),
                StockMovementEntity(
                    id = "m-currency-fix", productId = "p1", quantityDelta = "0",
                    unitCostSnapshot = "3", currencyCode = "USD",
                    sourceType = "PRODUCT_CURRENCY_CORRECTION", sourceId = "fix-1",
                    occurredAt = 2L
                )
            ),
            stockCostBaselines = listOf(
                StockCostBaselineEntity(
                    productId = "p1",
                    quantity = "0",
                    averageCost = "0",
                    currencyCode = "SYP",
                    effectiveAt = 1L,
                    recordedAt = 1L,
                    source = "PRODUCT_ZERO_OPENING"
                )
            )
        )
    }

}
