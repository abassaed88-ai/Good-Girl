package com.goodgirl.accounting.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductBarcodeRepositoryIntegrationTest {

    @Test
    fun createsOneStableInternalBarcodeAndResolvesInternalAndOriginalCodes() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            ProductRepository(database).addProduct(
                sku = "BARCODE-001",
                name = "مادة اختبار الباركود",
                unit = "قطعة",
                purchaseCost = "5",
                salePrice = "9",
                openingStock = "3",
                note = "اختبار"
            )
            val product = database.productDao().getAll().single()
            val repository = ProductBarcodeRepository(database)

            val firstInternal = repository.createInternalBarcode(product.id)
            val secondInternal = repository.createInternalBarcode(product.id)
            assertEquals(firstInternal, secondInternal)
            assertEquals("CODE_128", firstInternal.format)
            assertEquals("INTERNAL", firstInternal.origin)
            assertTrue(firstInternal.barcodeValue.startsWith("GG"))
            assertEquals(product.id, repository.findProductByBarcode(firstInternal.barcodeValue)?.id)

            val original = repository.registerOriginalBarcode(
                productId = product.id,
                value = "4006381333931",
                format = "EAN_13"
            )
            assertEquals("ORIGINAL", original.origin)
            assertEquals(product.id, repository.findProductByBarcode(" 4006381333931 ")?.id)
            assertNull(repository.findProductByBarcode("0000000000000"))
        } finally {
            database.close()
        }
    }

    @Test
    fun rejectsDuplicateOriginalBarcodeAcrossDifferentProducts() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val products = ProductRepository(database)
            products.addProduct("BARCODE-002", "منتج أول", "قطعة", "1", "2", "0", "اختبار")
            products.addProduct("BARCODE-003", "منتج ثان", "قطعة", "1", "2", "0", "اختبار")
            val registeredProducts = database.productDao().getAll()
            val repository = ProductBarcodeRepository(database)

            repository.registerOriginalBarcode(registeredProducts[0].id, "4006381333931", "EAN_13")
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.registerOriginalBarcode(registeredProducts[1].id, "4006381333931", "EAN_13")
                }
            }
            assertEquals(1, database.productBarcodeDao().getAll().size)
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
