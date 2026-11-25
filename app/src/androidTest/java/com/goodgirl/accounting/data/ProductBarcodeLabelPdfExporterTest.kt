package com.goodgirl.accounting.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.ProductBarcodeEntity
import com.goodgirl.accounting.data.local.ProductEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProductBarcodeLabelPdfExporterTest {

    @Test
    fun writesPrintablePdfWithRequestedCopiesAndPriceOption() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.cacheDir, "barcode-label-test-${System.nanoTime()}.pdf")
        try {
            val product = ProductEntity(
                id = "PRODUCT-1",
                sku = "GG-001",
                name = "مادة اختبار الملصق",
                purchaseCost = "10",
                salePrice = "15",
                priceCurrencyCode = "SYP",
                stockQuantity = "5",
                createdAt = 1L,
                updatedAt = 1L
            )
            val barcode = ProductBarcodeEntity(
                id = "BARCODE-1",
                productId = product.id,
                barcodeValue = "4006381333931",
                format = "EAN_13",
                origin = "ORIGINAL",
                createdAt = 1L
            )

            val labelCount = ProductBarcodeLabelPdfExporter.write(
                context = context,
                destination = Uri.fromFile(output),
                product = product,
                barcodes = listOf(barcode),
                copiesPerBarcode = 3,
                includePrice = false,
                columns = 3,
                rows = 8
            )

            assertEquals(3, labelCount)
            assertTrue(output.exists())
            assertTrue(output.length() > 500L)
            val header = output.inputStream().use {
                ByteArray(5).also { bytes -> it.read(bytes) }
            }
            assertEquals("%PDF-", String(header, Charsets.US_ASCII))
        } finally {
            output.delete()
        }
    }
}
