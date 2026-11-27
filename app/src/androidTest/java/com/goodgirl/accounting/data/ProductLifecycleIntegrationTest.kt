package com.goodgirl.accounting.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductLifecycleIntegrationTest {

    @Test
    fun productWithoutAccountingHistoryIsDeletedWithSetupRows() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val productId = ProductRepository(database).addProduct(
                sku = "LIFECYCLE-DELETE",
                name = "منتج بلا سجل",
                unit = "قطعة",
                purchaseCost = "4",
                salePrice = "8",
                openingStock = "0",
                note = "اختبار حذف منتج جديد",
                initialBarcode = "4006381333931",
                initialBarcodeFormat = "EAN_13"
            )

            assertEquals(
                ProductRemovalResult.DELETED,
                ProductRepository(database).removeOrArchiveProduct(productId)
            )

            assertNull(database.productDao().findById(productId))
            assertTrue(database.productBarcodeDao().getForProduct(productId).isEmpty())
            assertTrue(database.productUnitDao().getAll().isEmpty())
            assertTrue(database.stockCostBaselineDao().getAll().isEmpty())
            assertTrue(database.stockMovementDao().getAll().isEmpty())
            assertTrue(database.journalEntryDao().getAll().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun productWithStockHistoryIsArchivedAndCanBeRestored() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val repository = ProductRepository(database)
            val productId = repository.addProduct(
                sku = "LIFECYCLE-ARCHIVE",
                name = "منتج له رصيد افتتاحي",
                unit = "قطعة",
                purchaseCost = "5",
                salePrice = "9",
                openingStock = "3",
                note = "اختبار حفظ تاريخ المخزون"
            )
            val originalProduct = database.productDao().findById(productId)!!
            val originalMovement = database.stockMovementDao().getAll().single()
            val originalJournal = database.journalEntryDao().getAll().single()

            assertEquals(
                ProductRemovalResult.ARCHIVED,
                repository.removeOrArchiveProduct(productId)
            )

            val archived = database.productDao().findById(productId)
            assertNotNull(archived)
            assertFalse(archived!!.isActive)
            assertEquals(originalProduct.stockQuantity, archived.stockQuantity)
            assertEquals(originalMovement, database.stockMovementDao().getAll().single())
            assertEquals(originalJournal, database.journalEntryDao().getAll().single())
            assertTrue(database.productDao().observeActiveProducts().first().none { it.id == productId })
            assertTrue(database.productDao().observeArchivedProducts().first().any { it.id == productId })

            assertTrue(repository.restoreProduct(productId))
            assertTrue(database.productDao().findById(productId)!!.isActive)
            assertTrue(database.productDao().observeActiveProducts().first().any { it.id == productId })
            assertTrue(database.productDao().observeArchivedProducts().first().none { it.id == productId })
            assertFalse(repository.restoreProduct("missing-product"))
        } finally {
            database.close()
        }
    }

    @Test
    fun draftInvoiceLineIsEnoughToPreventPhysicalDeletion() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val repository = ProductRepository(database)
            val productId = repository.addProduct(
                sku = "LIFECYCLE-DRAFT",
                name = "منتج في مسودة فاتورة",
                unit = "قطعة",
                purchaseCost = "2",
                salePrice = "3",
                openingStock = "0",
                note = "اختبار حماية مسودة الفاتورة"
            )
            val now = System.currentTimeMillis()
            val invoiceId = "draft-lifecycle-invoice"
            database.invoiceDao().insertInvoice(
                InvoiceEntity(
                    id = invoiceId,
                    invoiceNumber = "DRAFT-LIFECYCLE-001",
                    kind = "SALE",
                    currencyCode = "SYP",
                    exchangeRateSnapshot = "1",
                    subtotal = "3",
                    discount = "0",
                    total = "3",
                    paid = "0",
                    remaining = "3",
                    status = "DRAFT",
                    issuedAt = now,
                    note = "مسودة مرتبطة بالمنتج",
                    createdAt = now
                )
            )
            database.invoiceDao().insertLines(
                listOf(
                    InvoiceLineEntity(
                        id = "draft-lifecycle-line",
                        invoiceId = invoiceId,
                        productId = productId,
                        productNameSnapshot = "منتج في مسودة فاتورة",
                        unitSnapshot = "قطعة",
                        quantity = "1",
                        unitCostSnapshot = "2",
                        unitPriceSnapshot = "3",
                        lineTotal = "3"
                    )
                )
            )

            assertEquals(
                ProductRemovalResult.ARCHIVED,
                repository.removeOrArchiveProduct(productId)
            )
            assertNotNull(database.productDao().findById(productId))
            assertEquals(1, database.invoiceDao().getLines(invoiceId).size)
        } finally {
            database.close()
        }
    }

    private fun newInMemoryDatabase(): AccountingDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, AccountingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}
