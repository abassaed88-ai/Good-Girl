package com.goodgirl.accounting.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.domain.FinancialReportCalculator
import com.goodgirl.accounting.domain.GeneralLedgerFinancialStatementsCalculator
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises invoice posting against a real in-memory SQLite/Room database.
 * It protects the latest-purchase-cost rule: a later purchase reprices
 * remaining stock, but must not rewrite any already-posted sale snapshot.
 */
@RunWith(AndroidJUnit4::class)
class AccountingFlowIntegrationTest {

    @Test
    fun latestPurchaseCostRevaluesRemainingStockWithoutRewritingHistoricalSaleCostOrProfit() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)
            products.addProduct(
                sku = "FLOW-001",
                name = "مادة اختبار",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "10",
                note = "اختبار تكلفة أحدث فاتورة"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            invoices.postInvoice(
                invoiceNumber = "FLOW-SALE-1",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "2", "15")),
                paidAmount = "30",
                issuedAt = baselineAt + 1_000L
            )
            val firstSale = database.invoiceDao().getAllInvoices().single()
            val firstSaleLine = database.invoiceDao().getLines(firstSale.id).single()
            assertDecimal("10", firstSaleLine.unitCostSnapshot)
            assertDecimal("8", database.productDao().findById(product.id)!!.stockQuantity)

            val firstSaleJournal = database.journalEntryDao().findBySource("SALE_INVOICE", firstSale.id)!!
            val firstSaleJournalLinesBefore = database.journalLineDao().getForEntry(firstSaleJournal.id)

            val purchase = invoices.postInvoice(
                invoiceNumber = "FLOW-PURCHASE-1",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "4", "20")),
                paidAmount = "80",
                issuedAt = baselineAt + 2_000L
            )
            val afterPurchase = database.productDao().findById(product.id)!!
            assertDecimal("20", afterPurchase.purchaseCost)
            assertDecimal("12", afterPurchase.stockQuantity)
            assertDecimal("10", database.invoiceDao().getLines(firstSale.id).single().unitCostSnapshot)

            val purchaseInvoice = database.invoiceDao().findById(purchase.invoiceId)!!
            val purchaseJournal = database.journalEntryDao()
                .findBySource("PURCHASE_INVOICE", purchaseInvoice.id)!!
            val purchaseJournalLines = database.journalLineDao().getForEntry(purchaseJournal.id)
            val accountsById = database.accountDao().getAll().associateBy { it.id }
            val inventoryPurchaseDebits = purchaseJournalLines
                .filter { accountsById[it.accountId]!!.code == "1200" }
                .fold(BigDecimal.ZERO) { total, line -> total.add(line.debit.toBigDecimal()) }
            assertDecimal("80", inventoryPurchaseDebits.toPlainString())
            val revaluationJournal = database.journalEntryDao().findBySource(
                "PURCHASE_INVENTORY_REVALUATION",
                purchaseInvoice.id
            )!!
            assertTrue(revaluationJournal.occurredAt >= purchaseInvoice.createdAt)
            val revaluationLines = database.journalLineDao().getForEntry(revaluationJournal.id)
            assertDecimal(
                "80",
                revaluationLines.single { accountsById[it.accountId]!!.code == "1200" }.debit
            )
            assertDecimal(
                "80",
                revaluationLines.single { accountsById[it.accountId]!!.code == "4100" }.credit
            )

            invoices.postInvoice(
                invoiceNumber = "FLOW-SALE-2",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "1", "15")),
                paidAmount = "15",
                issuedAt = baselineAt + 3_000L
            )
            val sales = database.invoiceDao().getAllInvoices()
                .filter { it.kind == "SALE" }
                .sortedBy { it.issuedAt }
            val saleLines = sales.flatMap { database.invoiceDao().getLines(it.id) }
            assertEquals(2, sales.size)
            assertEquals(2, saleLines.size)
            assertDecimal("10", saleLines[0].unitCostSnapshot)
            assertDecimal("20", saleLines[1].unitCostSnapshot)
            assertDecimal("11", database.productDao().findById(product.id)!!.stockQuantity)
            assertEquals(
                firstSaleJournalLinesBefore,
                database.journalLineDao().getForEntry(firstSaleJournal.id)
            )

            val report = FinancialReportCalculator.calculate(
                fromInclusive = 0L,
                toInclusive = Long.MAX_VALUE,
                invoices = database.invoiceDao().getAllInvoices(),
                lines = database.invoiceDao().getAllLines(),
                ledgerEntries = database.ledgerDao().getAll()
            )
            val summary = report.currencySummaries.single { it.currencyCode == "SYP" }
            assertDecimal("45", summary.sales.toPlainString())
            assertDecimal("40", summary.costOfGoodsSold.toPlainString())
            assertDecimal("5", summary.grossProfit.toPlainString())
            assertDecimal("80", summary.otherIncome.toPlainString())
            assertDecimal("85", summary.netProfit.toPlainString())
            assertDecimal("5", report.products.single().profit.toPlainString())

            val statements = GeneralLedgerFinancialStatementsCalculator.calculate(
                periodStartInclusive = 0L,
                periodEndInclusive = Long.MAX_VALUE,
                asOfInclusive = Long.MAX_VALUE,
                accounts = database.accountDao().getAll(),
                entries = database.journalEntryDao().getAll(),
                lines = database.journalLineDao().getAll()
            )
            val balanceSheet = statements.balanceSheets.single { it.currencyCode == "SYP" }
            assertDecimal("220", balanceSheet.assets.single { it.accountCode == "1200" }.amount.toPlainString())
            assertDecimal(
                "80",
                statements.incomeStatements.single { it.currencyCode == "SYP" }
                    .revenues.single { it.accountCode == "4100" }.amount.toPlainString()
            )
            assertDecimal(
                balanceSheet.totalAssets.toPlainString(),
                balanceSheet.totalLiabilitiesAndEquity.toPlainString()
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun repeatedPurchasesReplaceCurrentCostAndPostDownwardRevaluationSeparately() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)
            products.addProduct(
                sku = "FLOW-REPEAT-001",
                name = "مادة تغير سعرها",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "5",
                note = "اختبار تكرار تحديث التكلفة"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            invoices.postInvoice(
                invoiceNumber = "FLOW-REPEAT-PURCHASE-1",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "2", "20")),
                paidAmount = "40",
                issuedAt = baselineAt + 1_000L
            )
            assertDecimal("20", database.productDao().findById(product.id)!!.purchaseCost)
            assertDecimal("7", database.productDao().findById(product.id)!!.stockQuantity)

            val secondPurchase = invoices.postInvoice(
                invoiceNumber = "FLOW-REPEAT-PURCHASE-2",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "1", "12")),
                paidAmount = "12",
                issuedAt = baselineAt + 2_000L
            )
            val updated = database.productDao().findById(product.id)!!
            assertDecimal("12", updated.purchaseCost)
            assertDecimal("8", updated.stockQuantity)

            val invoice = database.invoiceDao().findById(secondPurchase.invoiceId)!!
            val journal = database.journalEntryDao().findBySource("PURCHASE_INVOICE", invoice.id)!!
            val revaluationJournal = database.journalEntryDao().findBySource(
                "PURCHASE_INVENTORY_REVALUATION",
                invoice.id
            )!!
            val lines = database.journalLineDao().getForEntry(revaluationJournal.id)
            val accounts = database.accountDao().getAll().associateBy { it.id }
            assertDecimal("56", lines.single { accounts[it.accountId]!!.code == "7100" }.debit)
            assertDecimal("56", lines.single { accounts[it.accountId]!!.code == "1200" && it.credit != "0" }.credit)

            val statements = GeneralLedgerFinancialStatementsCalculator.calculate(
                periodStartInclusive = 0L,
                periodEndInclusive = Long.MAX_VALUE,
                asOfInclusive = Long.MAX_VALUE,
                accounts = database.accountDao().getAll(),
                entries = database.journalEntryDao().getAll(),
                lines = database.journalLineDao().getAll()
            )
            val balanceSheet = statements.balanceSheets.single { it.currencyCode == "SYP" }
            assertDecimal("96", balanceSheet.assets.single { it.accountCode == "1200" }.amount.toPlainString())
            assertDecimal(
                balanceSheet.totalAssets.toPlainString(),
                balanceSheet.totalLiabilitiesAndEquity.toPlainString()
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun repeatedProductLinesUseLastLineCostAndCreateOneRevaluation() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)
            products.addProduct(
                sku = "FLOW-MULTI-001",
                name = "مادة مكررة بالفاتورة",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "5",
                note = "اختبار تكرار السطر"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            val purchase = invoices.postInvoice(
                invoiceNumber = "FLOW-MULTI-PURCHASE",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(
                    InvoiceLineDraft(product.id, "2", "20"),
                    InvoiceLineDraft(product.id, "1", "30")
                ),
                paidAmount = "70",
                issuedAt = baselineAt + 1_000L
            )
            val updated = database.productDao().findById(product.id)!!
            assertDecimal("30", updated.purchaseCost)
            assertDecimal("8", updated.stockQuantity)

            val invoice = database.invoiceDao().findById(purchase.invoiceId)!!
            val revaluationJournal = database.journalEntryDao().findBySource(
                "PURCHASE_INVENTORY_REVALUATION",
                invoice.id
            )!!
            val revaluationLines = database.journalLineDao().getForEntry(revaluationJournal.id)
            val accounts = database.accountDao().getAll().associateBy { it.id }
            assertDecimal("120", revaluationLines.single { accounts[it.accountId]!!.code == "4100" }.credit)
            assertEquals(1, revaluationLines.count { accounts[it.accountId]!!.code == "4100" })
        } finally {
            database.close()
        }
    }

    @Test
    fun revaluationUsesExactInvoiceValueWhenUnitCostRequiresRounding() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)
            products.addProduct(
                sku = "FLOW-ROUND-001",
                name = "مادة تكلفة كسرية",
                unit = "قطعة",
                purchaseCost = "1",
                salePrice = "5",
                openingStock = "1",
                note = "اختبار تقريب تكلفة الوحدة"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt
            val purchase = invoices.postInvoice(
                invoiceNumber = "FLOW-ROUND-PURCHASE",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(
                    InvoiceLineDraft(product.id, "3", "3.333333333333333333")
                ),
                paidAmount = "9.999999999999999999",
                issuedAt = baselineAt + 1_000L
            )

            val current = database.productDao().findById(product.id)!!
            assertDecimal("4", current.stockQuantity)
            assertDecimal("3.33333333", current.purchaseCost)

            val invoice = database.invoiceDao().findById(purchase.invoiceId)!!
            val purchaseJournal = database.journalEntryDao()
                .findBySource("PURCHASE_INVOICE", invoice.id)!!
            val purchaseLines = database.journalLineDao().getForEntry(purchaseJournal.id)
            val revaluation = database.journalEntryDao().findBySource(
                "PURCHASE_INVENTORY_REVALUATION",
                invoice.id
            )!!
            val revaluationLines = database.journalLineDao().getForEntry(revaluation.id)
            val accounts = database.accountDao().getAll().associateBy { it.id }
            assertDecimal(
                "9.999999999999999999",
                purchaseLines.single { accounts[it.accountId]!!.code == "1200" }.debit
            )
            assertDecimal(
                "2.333333320000000001",
                revaluationLines.single { accounts[it.accountId]!!.code == "1200" }.debit
            )

            val statements = GeneralLedgerFinancialStatementsCalculator.calculate(
                periodStartInclusive = 0L,
                periodEndInclusive = Long.MAX_VALUE,
                asOfInclusive = Long.MAX_VALUE,
                accounts = database.accountDao().getAll(),
                entries = database.journalEntryDao().getAll(),
                lines = database.journalLineDao().getAll()
            )
            val balanceSheet = statements.balanceSheets.single { it.currencyCode == "SYP" }
            assertDecimal(
                "13.33333332",
                balanceSheet.assets.single { it.accountCode == "1200" }.amount.toPlainString()
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun purchaseAtZeroStockUpdatesCurrentCostWithoutRevaluationGainOrLoss() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)
            products.addProduct(
                sku = "FLOW-ZERO-001",
                name = "مادة نفد مخزونها",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "0",
                note = "اختبار شراء بعد نفاد المخزون"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            val purchase = invoices.postInvoice(
                invoiceNumber = "FLOW-ZERO-PURCHASE",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "3", "3.333333333333333333")),
                paidAmount = "9.999999999999999999",
                issuedAt = baselineAt + 1_000L
            )
            val updated = database.productDao().findById(product.id)!!
            assertDecimal("3.33333333", updated.purchaseCost)
            assertDecimal("3", updated.stockQuantity)

            val invoice = database.invoiceDao().findById(purchase.invoiceId)!!
            val journal = database.journalEntryDao().findBySource("PURCHASE_INVOICE", invoice.id)!!
            val lines = database.journalLineDao().getForEntry(journal.id)
            val accounts = database.accountDao().getAll().associateBy { it.id }
            assertDecimal(
                "9.999999999999999999",
                lines.single { accounts[it.accountId]!!.code == "1200" }.debit
            )
            assertTrue(lines.none { accounts[it.accountId]!!.code == "4100" || accounts[it.accountId]!!.code == "7100" })
            assertTrue(
                database.journalEntryDao().findBySource("PURCHASE_INVENTORY_REVALUATION", invoice.id) == null
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun changingSalePriceAffectsFutureSalesWithoutRewritingHistoryOrStock() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)

            products.addProduct(
                sku = "PRICE-001",
                name = "مادة تعديل السعر",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "10",
                note = "اختبار تحديث سعر البيع"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            invoices.postInvoice(
                invoiceNumber = "PRICE-SALE-1",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "1", "15")),
                paidAmount = "15",
                issuedAt = baselineAt + 1_000L
            )
            val firstInvoice = database.invoiceDao().getAllInvoices().single()

            assertTrue(products.updateSalePrice(product.id, "17.50"))

            val updatedProduct = database.productDao().findById(product.id)!!
            assertDecimal("17.5", updatedProduct.salePrice)
            assertDecimal("10", updatedProduct.purchaseCost)
            assertDecimal("9", updatedProduct.stockQuantity)

            val historicalLine = database.invoiceDao().getLines(firstInvoice.id).single()
            assertDecimal("15", historicalLine.unitPriceSnapshot)
            assertDecimal("10", historicalLine.unitCostSnapshot)

            invoices.postInvoice(
                invoiceNumber = "PRICE-SALE-2",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "1", updatedProduct.salePrice)),
                paidAmount = "17.5",
                issuedAt = baselineAt + 2_000L
            )

            val saleInvoices = database.invoiceDao().getAllInvoices().sortedBy { it.issuedAt }
            assertEquals(2, saleInvoices.size)
            val firstSaleLine = database.invoiceDao().getLines(saleInvoices[0].id).single()
            val secondSaleLine = database.invoiceDao().getLines(saleInvoices[1].id).single()
            assertDecimal("15", firstSaleLine.unitPriceSnapshot)
            assertDecimal("17.5", secondSaleLine.unitPriceSnapshot)
            assertDecimal("8", database.productDao().findById(product.id)!!.stockQuantity)
        } finally {
            database.close()
        }
    }

    @Test
    fun rejectedSaleRollsBackWithoutChangingStockOrCreatingInvoice() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)
            products.addProduct(
                sku = "FLOW-002",
                name = "مخزون محدود",
                unit = "قطعة",
                purchaseCost = "4",
                salePrice = "7",
                openingStock = "2",
                note = "اختبار رفض البيع"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    invoices.postInvoice(
                        invoiceNumber = "FLOW-SALE-INVALID",
                        kind = "SALE",
                        partyId = null,
                        currencyCode = "SYP",
                        exchangeRateSnapshot = "1",
                        lines = listOf(InvoiceLineDraft(product.id, "3", "7")),
                        paidAmount = "21",
                        issuedAt = baselineAt + 4_000L
                    )
                }
            }

            assertDecimal("2", database.productDao().findById(product.id)!!.stockQuantity)
            assertTrue(database.invoiceDao().getAllInvoices().isEmpty())
            assertEquals(1, database.stockMovementDao().getAll().size)
        } finally {
            database.close()
        }
    }


    @Test
    fun saleAndPurchaseInvoicesCreateBalancedAutomaticJournalEntries() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)
            products.addProduct(
                sku = "JOURNAL-001",
                name = "مادة القيود",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "5",
                note = "اختبار ترحيل القيود"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt

            val sale = invoices.postInvoice(
                invoiceNumber = "JOURNAL-SALE-1",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "2", "15")),
                paidAmount = "30",
                issuedAt = baselineAt + 10_000L
            )
            val purchase = invoices.postInvoice(
                invoiceNumber = "JOURNAL-PURCHASE-1",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "2", "20")),
                paidAmount = "40",
                issuedAt = baselineAt + 20_000L
            )

            val entries = database.journalEntryDao().getAll()
            assertEquals(4, entries.size)
            assertEquals(
                setOf(
                    "PRODUCT_OPENING_STOCK",
                    "SALE_INVOICE",
                    "PURCHASE_INVOICE",
                    "PURCHASE_INVENTORY_REVALUATION"
                ),
                entries.map { it.sourceType }.toSet()
            )
            val accounts = database.accountDao().getAll().associateBy { it.id }
            val openingEntry = entries.single { it.sourceType == "PRODUCT_OPENING_STOCK" }
            assertEquals(product.id, openingEntry.sourceId)
            val openingLines = database.journalLineDao().getForEntry(openingEntry.id)
            assertEquals(2, openingLines.size)
            assertDecimal("50", openingLines.single { accounts[it.accountId]!!.code == "1200" }.debit)
            assertDecimal("50", openingLines.single { accounts[it.accountId]!!.code == "3000" }.credit)
            assertEquals(setOf(sale.invoiceId, purchase.invoiceId), entries.filter { it.sourceType != "PRODUCT_OPENING_STOCK" }.map { it.sourceId }.toSet())
            val saleEntry = entries.single { it.sourceId == sale.invoiceId }
            val saleLines = database.journalLineDao().getForEntry(saleEntry.id)
            assertEquals(4, saleLines.size)
            assertDecimal("30", saleLines.single { accounts[it.accountId]!!.code == "1000" }.debit)
            assertDecimal("30", saleLines.single { accounts[it.accountId]!!.code == "4000" }.credit)
            assertDecimal("20", saleLines.single { accounts[it.accountId]!!.code == "5000" }.debit)
            assertDecimal("20", saleLines.single { accounts[it.accountId]!!.code == "1200" }.credit)

            val purchaseEntry = entries.single {
                it.sourceType == "PURCHASE_INVOICE" && it.sourceId == purchase.invoiceId
            }
            val purchaseLines = database.journalLineDao().getForEntry(purchaseEntry.id)
            assertEquals(2, purchaseLines.size)
            assertDecimal("40", purchaseLines.single { accounts[it.accountId]!!.code == "1200" }.debit)
            assertDecimal("40", purchaseLines.single { accounts[it.accountId]!!.code == "1000" }.credit)

            entries.forEach { entry ->
                val lines = database.journalLineDao().getForEntry(entry.id)
                val debit = lines.fold(BigDecimal.ZERO) { sum, line -> sum.add(BigDecimal(line.debit)) }
                val credit = lines.fold(BigDecimal.ZERO) { sum, line -> sum.add(BigDecimal(line.credit)) }
                assertEquals(0, debit.compareTo(credit))
                assertEquals("SYP", entry.currencyCode)
                assertEquals("1", entry.exchangeRateSnapshot)
            }
        } finally {
            database.close()
        }
    }


    @Test
    fun backdatedPurchaseChangesCurrentStockCostButNeverRewritesPostedSaleCostOrProfit() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)
            products.addProduct(
                sku = "BACKDATE-001",
                name = "مادة فاتورة مؤرخة بأثر رجعي",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "10",
                note = "اختبار حماية تكلفة البيع السابقة"
            )
            val product = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(product.id)!!.effectiveAt
            val saleAt = baselineAt + 3_000L
            val backdatedPurchaseAt = baselineAt - 1_000L

            val sale = invoices.postInvoice(
                invoiceNumber = "BACKDATE-SALE-1",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "2", "15")),
                paidAmount = "30",
                issuedAt = saleAt
            )
            val originalSaleLine = database.invoiceDao().getLines(sale.invoiceId).single()
            assertDecimal("10", originalSaleLine.unitCostSnapshot)
            val originalSaleJournal = database.journalEntryDao()
                .findBySource("SALE_INVOICE", sale.invoiceId)!!
            val originalSaleJournalLines = database.journalLineDao().getForEntry(originalSaleJournal.id)

            val purchase = invoices.postInvoice(
                invoiceNumber = "BACKDATE-PURCHASE-1",
                kind = "PURCHASE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(product.id, "4", "20")),
                paidAmount = "80",
                issuedAt = backdatedPurchaseAt
            )

            val updatedProduct = database.productDao().findById(product.id)!!
            assertDecimal("20", updatedProduct.purchaseCost)
            assertDecimal("12", updatedProduct.stockQuantity)

            // Historical sale and its posted COGS journal stay immutable.
            assertDecimal(
                "10",
                database.invoiceDao().getLines(sale.invoiceId).single().unitCostSnapshot
            )
            assertEquals(
                originalSaleJournalLines,
                database.journalLineDao().getForEntry(originalSaleJournal.id)
            )
            assertTrue(
                database.stockCostRecalculationAuditDao().getAll().isEmpty()
            )
            assertTrue(
                database.journalEntryDao().getAll().none {
                    it.sourceType == "STOCK_COST_RECALCULATION"
                }
            )

            val purchaseInvoice = database.invoiceDao().findById(purchase.invoiceId)!!
            val revaluation = database.journalEntryDao().findBySource(
                "PURCHASE_INVENTORY_REVALUATION",
                purchase.invoiceId
            )!!
            assertTrue(revaluation.occurredAt >= purchaseInvoice.createdAt)
            assertTrue(revaluation.occurredAt > backdatedPurchaseAt)
            val accounts = database.accountDao().getAll().associateBy { it.id }
            val revaluationLines = database.journalLineDao().getForEntry(revaluation.id)
            assertDecimal(
                "80",
                revaluationLines.single { accounts[it.accountId]!!.code == "1200" }.debit
            )
            assertDecimal(
                "80",
                revaluationLines.single { accounts[it.accountId]!!.code == "4100" }.credit
            )

            val report = FinancialReportCalculator.calculate(
                fromInclusive = 0L,
                toInclusive = Long.MAX_VALUE,
                invoices = database.invoiceDao().getAllInvoices(),
                lines = database.invoiceDao().getAllLines(),
                ledgerEntries = database.ledgerDao().getAll()
            )
            val saleSummary = report.currencySummaries.single { it.currencyCode == "SYP" }
            assertDecimal("30", saleSummary.sales.toPlainString())
            assertDecimal("20", saleSummary.costOfGoodsSold.toPlainString())
            assertDecimal("10", saleSummary.grossProfit.toPlainString())
            assertDecimal("80", saleSummary.otherIncome.toPlainString())
            assertDecimal("90", saleSummary.netProfit.toPlainString())
            assertDecimal("10", report.products.single().profit.toPlainString())

            val statements = GeneralLedgerFinancialStatementsCalculator.calculate(
                periodStartInclusive = 0L,
                periodEndInclusive = Long.MAX_VALUE,
                asOfInclusive = Long.MAX_VALUE,
                accounts = database.accountDao().getAll(),
                entries = database.journalEntryDao().getAll(),
                lines = database.journalLineDao().getAll()
            )
            val income = statements.incomeStatements.single { it.currencyCode == "SYP" }
            assertDecimal("30", income.revenues.single { it.accountCode == "4000" }.amount.toPlainString())
            assertDecimal("80", income.revenues.single { it.accountCode == "4100" }.amount.toPlainString())
            assertDecimal("20", income.expenses.single { it.accountCode == "5000" }.amount.toPlainString())
        } finally {
            database.close()
        }
    }

    private fun newInMemoryDatabase(): AccountingDatabase {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, AccountingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun assertDecimal(expected: String, actual: String) {
        assertEquals(0, BigDecimal(expected).compareTo(BigDecimal(actual)))
    }
}
