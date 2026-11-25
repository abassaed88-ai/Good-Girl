package com.goodgirl.accounting.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class ProductCurrencyCorrectionIntegrationTest {

    @Test
    fun correctionRevaluesRemainingStockButKeepsHistoricalInvoicesAndFutureCurrencyConsistent() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            val invoices = InvoiceRepository(database)

            products.addProduct(
                sku = "CURRENCY-001",
                name = "مادة تصحيح العملة",
                unit = "قطعة",
                purchaseCost = "10",
                salePrice = "15",
                openingStock = "5",
                note = "اختبار تصحيح عملة",
                priceCurrencyCode = "SYP",
                openingExchangeRate = "1"
            )
            val originalProduct = database.productDao().getAll().single()
            val baselineAt = database.stockCostBaselineDao().findByProductId(originalProduct.id)!!.effectiveAt

            val oldSale = invoices.postInvoice(
                invoiceNumber = "CURRENCY-SALE-SYP",
                kind = "SALE",
                partyId = null,
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                lines = listOf(InvoiceLineDraft(originalProduct.id, "1", "15")),
                paidAmount = "15",
                issuedAt = baselineAt + 1_000L
            )
            val oldInvoiceBefore = database.invoiceDao().findById(oldSale.invoiceId)!!
            val oldLineBefore = database.invoiceDao().getLines(oldSale.invoiceId).single()
            assertDecimal("4", database.productDao().findById(originalProduct.id)!!.stockQuantity)

            assertTrue(
                products.updateProduct(
                    id = originalProduct.id,
                    sku = originalProduct.sku,
                    name = originalProduct.name,
                    unit = originalProduct.baseUnit,
                    purchaseCost = originalProduct.purchaseCost,
                    salePrice = originalProduct.salePrice,
                    note = originalProduct.note,
                    priceCurrencyCode = "USD",
                    categoryId = originalProduct.categoryId,
                    lowStockThreshold = originalProduct.lowStockThreshold,
                    currencyCorrectionReason = "تم إدخال السعر بالدولار مع اختيار الليرة السورية بالخطأ",
                    currencyCorrectionExchangeRate = "15500"
                )
            )

            val correctedProduct = database.productDao().findById(originalProduct.id)!!
            assertEquals("USD", correctedProduct.priceCurrencyCode)
            assertDecimal("4", correctedProduct.stockQuantity)
            assertDecimal("10", correctedProduct.purchaseCost)
            assertDecimal("15", correctedProduct.salePrice)

            val movements = database.stockMovementDao().getAll()
            val correctionMovement = movements.single { it.sourceType == "PRODUCT_CURRENCY_CORRECTION" }
            assertEquals(originalProduct.id, correctionMovement.productId)
            assertEquals("0", correctionMovement.quantityDelta)
            assertEquals("USD", correctionMovement.currencyCode)
            assertTrue(correctionMovement.note.contains("SYP"))
            assertTrue(correctionMovement.note.contains("USD"))
            assertTrue(correctionMovement.note.contains("تم إدخال السعر"))

            val oldInvoiceAfter = database.invoiceDao().findById(oldSale.invoiceId)!!
            val oldLineAfter = database.invoiceDao().getLines(oldSale.invoiceId).single()
            assertEquals(oldInvoiceBefore, oldInvoiceAfter)
            assertEquals(oldLineBefore, oldLineAfter)
            assertEquals("SYP", oldInvoiceAfter.currencyCode)
            assertDecimal("10", oldLineAfter.unitCostSnapshot)
            assertDecimal("15", oldLineAfter.unitPriceSnapshot)

            val correctionJournals = database.journalEntryDao().getAll()
                .filter { it.sourceType.startsWith("PRODUCT_CURRENCY_CORRECTION_") }
            assertEquals(2, correctionJournals.size)
            val accountsById = database.accountDao().getAll().associateBy { it.id }
            val oldCurrencyJournal = correctionJournals.single { it.sourceType == "PRODUCT_CURRENCY_CORRECTION_OLD" }
            val newCurrencyJournal = correctionJournals.single { it.sourceType == "PRODUCT_CURRENCY_CORRECTION_NEW" }
            assertEquals(correctionMovement.id, oldCurrencyJournal.sourceId)
            assertEquals(correctionMovement.id, newCurrencyJournal.sourceId)
            assertEquals("SYP", oldCurrencyJournal.currencyCode)
            assertEquals("1", oldCurrencyJournal.exchangeRateSnapshot)
            assertEquals("USD", newCurrencyJournal.currencyCode)
            assertEquals("15500", newCurrencyJournal.exchangeRateSnapshot)

            val oldLines = database.journalLineDao().getForEntry(oldCurrencyJournal.id)
            assertDecimal("40", oldLines.single { accountsById[it.accountId]!!.code == "3100" }.debit)
            assertDecimal("40", oldLines.single { accountsById[it.accountId]!!.code == "1200" }.credit)

            val newLines = database.journalLineDao().getForEntry(newCurrencyJournal.id)
            assertDecimal("40", newLines.single { accountsById[it.accountId]!!.code == "1200" }.debit)
            assertDecimal("40", newLines.single { accountsById[it.accountId]!!.code == "3100" }.credit)

            database.journalEntryDao().getAll().forEach { entry ->
                val lines = database.journalLineDao().getForEntry(entry.id)
                val debit = lines.fold(BigDecimal.ZERO) { total, line -> total + line.debit.toBigDecimal() }
                val credit = lines.fold(BigDecimal.ZERO) { total, line -> total + line.credit.toBigDecimal() }
                assertEquals("Journal ${entry.journalNumber} must balance", 0, debit.compareTo(credit))
            }

            val newSale = invoices.postInvoice(
                invoiceNumber = "CURRENCY-SALE-USD",
                kind = "SALE",
                partyId = null,
                currencyCode = "USD",
                exchangeRateSnapshot = "15500",
                lines = listOf(InvoiceLineDraft(originalProduct.id, "1", "15")),
                paidAmount = "15",
                issuedAt = correctionMovement.occurredAt + 1_000L
            )
            val newInvoice = database.invoiceDao().findById(newSale.invoiceId)!!
            val newLine = database.invoiceDao().getLines(newSale.invoiceId).single()
            assertEquals("USD", newInvoice.currencyCode)
            assertDecimal("10", newLine.unitCostSnapshot)
            assertDecimal("15", newLine.unitPriceSnapshot)
            assertDecimal("3", database.productDao().findById(originalProduct.id)!!.stockQuantity)
        } finally {
            database.close()
        }
    }

    @Test
    fun currencyChangeRequiresAnAuditReasonAndRollsBackWithoutOne() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            products.addProduct(
                sku = "CURRENCY-002",
                name = "مادة تحتاج سبب تصحيح",
                unit = "قطعة",
                purchaseCost = "5",
                salePrice = "9",
                openingStock = "2",
                note = "اختبار سبب التصحيح",
                priceCurrencyCode = "SYP"
            )
            val product = database.productDao().getAll().single()

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    products.updateProduct(
                        id = product.id,
                        sku = product.sku,
                        name = product.name,
                        unit = product.baseUnit,
                        purchaseCost = product.purchaseCost,
                        salePrice = product.salePrice,
                        note = product.note,
                        priceCurrencyCode = "USD",
                        categoryId = product.categoryId,
                        lowStockThreshold = product.lowStockThreshold,
                        currencyCorrectionReason = "  ",
                        currencyCorrectionExchangeRate = "15000"
                    )
                }
            }

            val unchanged = database.productDao().findById(product.id)!!
            assertEquals("SYP", unchanged.priceCurrencyCode)
            assertDecimal("2", unchanged.stockQuantity)
            assertEquals(1, database.stockMovementDao().getAll().size)
            assertTrue(database.journalEntryDao().getAll().none { it.sourceType.startsWith("PRODUCT_CURRENCY_CORRECTION_") })
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
