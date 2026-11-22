package com.goodgirl.accounting.data

import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.data.local.PartyEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.data.local.ProductUnitEntity
import com.goodgirl.accounting.data.local.StockMovementEntity
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupIntegrityValidatorTest {
    private val empty = emptyList<com.goodgirl.accounting.data.local.CategoryEntity>()

    private fun validate(
        products: List<ProductEntity> = emptyList(),
        units: List<ProductUnitEntity> = emptyList(),
        parties: List<PartyEntity> = emptyList(),
        invoices: List<InvoiceEntity> = emptyList(),
        lines: List<InvoiceLineEntity> = emptyList(),
        movements: List<StockMovementEntity> = emptyList(),
        entries: List<LedgerEntryEntity> = emptyList()
    ) = BackupIntegrityValidator.validate(
        categories = empty,
        products = products,
        units = units,
        parties = parties,
        invoices = invoices,
        invoiceLines = lines,
        movements = movements,
        ledgerEntries = entries
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
                    id = "m1",
                    productId = "p1",
                    quantityDelta = "-2",
                    unitCostSnapshot = "3",
                    currencyCode = "SYP",
                    sourceType = "SALE_INVOICE",
                    sourceId = "l1",
                    occurredAt = 2L
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
    fun rejectsInvoiceWhosePaidAndRemainingDoNotMatchTotal() {
        assertThrows(IllegalArgumentException::class.java) {
            validate(invoices = listOf(saleInvoice().copy(remaining = "1")))
        }
    }
}
