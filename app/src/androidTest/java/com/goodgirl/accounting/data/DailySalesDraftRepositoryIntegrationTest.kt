package com.goodgirl.accounting.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.DailySalesDraftEntity
import com.goodgirl.accounting.data.local.DailySalesDraftLineEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailySalesDraftRepositoryIntegrationTest {

    @Test
    fun editorAndMultiplePendingRowsSurviveDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "daily-sales-draft-reopen-test.db"
        context.deleteDatabase(databaseName)
        try {
            val firstDatabase = Room.databaseBuilder(
                context,
                AccountingDatabase::class.java,
                databaseName
            ).build()
            val header = sampleHeader()
            val lines = listOf(sampleLine("draft-line-1", 0), sampleLine("draft-line-2", 1))
            DailySalesDraftRepository(firstDatabase).saveSnapshot(header, lines)
            firstDatabase.close()

            val reopened = Room.databaseBuilder(
                context,
                AccountingDatabase::class.java,
                databaseName
            ).build()
            try {
                val restored = DailySalesDraftRepository(reopened).loadSnapshot()
                assertEquals(header.selectedDay, restored.header!!.selectedDay)
                assertEquals("USD", restored.header.currencyCode)
                assertEquals("مادة لم يكتمل إدخالها", restored.header.selectedProductNameSnapshot)
                assertEquals("1.75", restored.header.quantity)
                assertEquals(2, restored.lines.size)
                assertEquals("draft-line-1", restored.lines[0].id)
                assertEquals("draft-line-2", restored.lines[1].id)
                assertEquals("12345", restored.header.paidAmount)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun alreadyPostedDraftLineIsRecognizedAndRemovedWithoutPostingAgain() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val productRepository = ProductRepository(database)
            val invoiceRepository = InvoiceRepository(database)
            productRepository.addProduct(
                sku = "DRAFT-IDEMPOTENT",
                name = "مادة اختبار المسودة",
                unit = "قطعة",
                purchaseCost = "2",
                salePrice = "5",
                openingStock = "5",
                note = "اختبار منع التكرار"
            )
            val product = database.productDao().getAll().single()
            val line = sampleLine(
                id = "00000000-0000-0000-0000-000000000001",
                lineOrder = 0,
                productId = product.id,
                productName = product.name,
                unitName = product.baseUnit,
                currencyCode = "SYP",
                exchangeRate = "1",
                quantity = "1",
                unitFactor = "1",
                unitPrice = "5",
                discount = "0",
                paidAmount = "5"
            )
            val invoiceNumber = DailySalesDraftRepository.invoiceNumberFor(line.id)

            // Simulate process death after the accounting transaction succeeded,
            // but before the draft row was removed from Room.
            invoiceRepository.postInvoice(
                invoiceNumber = invoiceNumber,
                kind = "SALE",
                partyId = null,
                currencyCode = line.currencyCode,
                exchangeRateSnapshot = line.exchangeRate,
                lines = listOf(
                    InvoiceLineDraft(
                        productId = line.productId,
                        quantity = line.quantity,
                        unitPrice = line.unitPrice,
                        discount = line.discount,
                        unitName = line.unitName
                    )
                ),
                paidAmount = line.paidAmount,
                issuedAt = line.issuedAt,
                note = line.note
            )
            val quantityAfterOriginalPost = database.productDao().findById(product.id)!!.stockQuantity
            DailySalesDraftRepository(database).saveSnapshot(sampleHeader(), listOf(line))

            DailySalesDraftRepository(database).postPendingLine(line)

            assertEquals(1, database.invoiceDao().getAllInvoices().count { it.invoiceNumber == invoiceNumber })
            assertEquals(quantityAfterOriginalPost, database.productDao().findById(product.id)!!.stockQuantity)
            assertNull(DailySalesDraftRepository(database).loadSnapshot().lines.firstOrNull { it.id == line.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun partialPostingRemovesCompletedRowAndRetainsOnlyFailedRows() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            ProductRepository(database).addProduct(
                sku = "DRAFT-PARTIAL-001",
                name = "مادة صالحة للترحيل",
                unit = "قطعة",
                purchaseCost = "2",
                salePrice = "5",
                openingStock = "5",
                note = "اختبار الترحيل الجزئي"
            )
            val product = database.productDao().getAll().single()
            val completedLine = sampleLine(
                id = "00000000-0000-0000-0000-000000000003",
                lineOrder = 0,
                productId = product.id,
                productName = product.name,
                unitName = product.baseUnit,
                currencyCode = "SYP",
                exchangeRate = "1",
                quantity = "1",
                unitFactor = "1",
                unitPrice = "5",
                discount = "0",
                paidAmount = "5"
            )
            val failedLine = sampleLine(
                id = "00000000-0000-0000-0000-000000000004",
                lineOrder = 1,
                productId = "missing-product-after-first-row"
            )
            val repository = DailySalesDraftRepository(database)
            repository.saveSnapshot(sampleHeader(), listOf(completedLine, failedLine))

            repository.postPendingLine(completedLine)
            val failure = runCatching { repository.postPendingLine(failedLine) }.exceptionOrNull()
            val remaining = repository.loadSnapshot().lines

            assertNotNull(failure)
            assertEquals(listOf(failedLine.id), remaining.map { it.id })
            assertEquals(1, database.invoiceDao().getAllInvoices().size)
            assertEquals(0, database.productDao().findById(product.id)!!.stockQuantity.toBigDecimal().compareTo(java.math.BigDecimal("4")))
        } finally {
            database.close()
        }
    }

    @Test
    fun failedPostingKeepsThePendingLineForCorrectionAndRetry() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val line = sampleLine(
                id = "00000000-0000-0000-0000-000000000002",
                lineOrder = 0,
                productId = "missing-product-id"
            )
            val repository = DailySalesDraftRepository(database)
            repository.saveSnapshot(sampleHeader(), listOf(line))

            val failure = runCatching { repository.postPendingLine(line) }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals(1, repository.loadSnapshot().lines.size)
            assertEquals(0, database.invoiceDao().getAllInvoices().size)
        } finally {
            database.close()
        }
    }

    private fun sampleHeader() = DailySalesDraftEntity(
        selectedDay = 1_798_000_000_000L,
        selectedProductId = "archived-product-id",
        selectedProductNameSnapshot = "مادة لم يكتمل إدخالها",
        selectedUnitName = "علبة",
        productSearchQuery = "مادة",
        selectedProductCategoryId = "archived-category-id",
        quantity = "1.75",
        salePrice = "20",
        lineDiscount = "1",
        currencyCode = "USD",
        exchangeRate = "15000",
        paidAmount = "12345",
        paidAmountTouched = true,
        selectedPartyId = "archived-customer-id",
        note = "مسودة غير مكتملة",
        updatedAt = 1_798_000_000_123L
    )

    private fun sampleLine(
        id: String,
        lineOrder: Int,
        productId: String = "archived-product-id",
        productName: String = "مادة محفوظة",
        unitName: String = "علبة",
        currencyCode: String = "USD",
        exchangeRate: String = "15000",
        quantity: String = "1",
        unitFactor: String = "1",
        unitPrice: String = "20",
        discount: String = "0",
        paidAmount: String = "20"
    ) = DailySalesDraftLineEntity(
        id = id,
        lineOrder = lineOrder,
        productId = productId,
        productNameSnapshot = productName,
        quantity = quantity,
        unitName = unitName,
        unitFactor = unitFactor,
        unitPrice = unitPrice,
        discount = discount,
        currencyCode = currencyCode,
        exchangeRate = exchangeRate,
        paidAmount = paidAmount,
        customerId = null,
        note = "سطر مسودة",
        issuedAt = 1_798_000_000_123L
    )

    private fun newInMemoryDatabase(): AccountingDatabase {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, AccountingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}
