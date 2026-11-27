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
            val barcodes = listOf(
                ProductBarcodeEntity("EAN13", product.id, "4006381333931", "EAN_13", "ORIGINAL", 1L),
                ProductBarcodeEntity("EAN8", product.id, "96385074", "EAN_8", "ORIGINAL", 1L),
                ProductBarcodeEntity("UPCA", product.id, "036000291452", "UPC_A", "ORIGINAL", 1L),
                ProductBarcodeEntity("CODE128", product.id, "GG123456789012", "CODE_128", "INTERNAL", 1L)
            )

            val labelCount = ProductBarcodeLabelPdfExporter.write(
                context = context,
                destination = Uri.fromFile(output),
                product = product,
                barcodes = barcodes,
                copiesPerBarcode = 2,
                includePrice = false,
                columns = 3,
                rows = 8
            )

            assertEquals(8, labelCount)
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

    @Test
    fun writesOnePdfForMultipleProductsWithIndependentCopyCounts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.cacheDir, "barcode-batch-test-${System.nanoTime()}.pdf")
        try {
            val firstProduct = ProductEntity(
                id = "BATCH-1",
                name = "المادة الأولى",
                createdAt = 1L,
                updatedAt = 1L
            )
            val secondProduct = ProductEntity(
                id = "BATCH-2",
                name = "المادة الثانية",
                createdAt = 1L,
                updatedAt = 1L
            )
            val firstBarcode = ProductBarcodeEntity(
                "BATCH-CODE-1", firstProduct.id, "GG-BATCH-001", "CODE_128", "INTERNAL", 1L
            )
            val secondBarcode = ProductBarcodeEntity(
                "BATCH-CODE-2", secondProduct.id, "GG-BATCH-002", "CODE_128", "INTERNAL", 1L
            )

            val labelCount = ProductBarcodeLabelPdfExporter.writeBatch(
                context = context,
                destination = Uri.fromFile(output),
                items = listOf(
                    ProductBarcodeLabelBatchItem(firstProduct, listOf(firstBarcode), copiesPerBarcode = 2),
                    ProductBarcodeLabelBatchItem(secondProduct, listOf(secondBarcode), copiesPerBarcode = 3)
                ),
                includePrice = true,
                columns = 3,
                rows = 8
            )

            assertEquals(5, labelCount)
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
