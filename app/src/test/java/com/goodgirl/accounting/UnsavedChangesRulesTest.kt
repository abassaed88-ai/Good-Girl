package com.goodgirl.accounting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsavedChangesRulesTest {

    @Test
    fun untouchedInvoiceDraftIsClean() {
        assertFalse(hasUnsavedInvoiceDraft(InvoiceDraftState()))
    }

    @Test
    fun invoiceLinesAndEditedFieldsAreMarkedUnsaved() {
        assertTrue(hasUnsavedInvoiceDraft(InvoiceDraftState(draftLineCount = 1)))
        assertTrue(hasUnsavedInvoiceDraft(InvoiceDraftState(note = "ملاحظة")))
        assertTrue(hasUnsavedInvoiceDraft(InvoiceDraftState(currencyCode = "USD")))
        assertTrue(hasUnsavedInvoiceDraft(InvoiceDraftState(paymentPanelOpen = true)))
        assertTrue(hasUnsavedInvoiceDraft(InvoiceDraftState(paymentAmount = "100")))
    }

    @Test
    fun untouchedNewProductFormIsClean() {
        assertFalse(hasUnsavedProductForm(ProductFormValues()))
    }

    @Test
    fun changedNewProductFormIsMarkedUnsaved() {
        assertTrue(hasUnsavedProductForm(ProductFormValues(name = "منتج جديد")))
    }

    @Test
    fun unchangedExistingProductFormIsClean() {
        val saved = ProductFormValues(name = "مادة", sku = "P-1", purchaseCost = "15", salePrice = "20")
        assertFalse(hasUnsavedProductForm(current = saved, original = saved))
    }

    @Test
    fun editedExistingProductFormIsMarkedUnsaved() {
        val saved = ProductFormValues(name = "مادة", sku = "P-1", purchaseCost = "15", salePrice = "20")
        val edited = saved.copy(salePrice = "22")
        assertTrue(hasUnsavedProductForm(current = edited, original = saved))
    }

    @Test
    fun untouchedStockAdjustmentIsClean() {
        assertFalse(
            hasUnsavedStockAdjustment(
                StockAdjustmentDraft(
                    isOpen = true,
                    currentStock = "10",
                    countedQuantity = "10"
                )
            )
        )
    }

    @Test
    fun editedStockAdjustmentIsMarkedUnsaved() {
        assertTrue(
            hasUnsavedStockAdjustment(
                StockAdjustmentDraft(
                    isOpen = true,
                    currentStock = "10",
                    quantityDelta = "2"
                )
            )
        )
        assertTrue(
            hasUnsavedStockAdjustment(
                StockAdjustmentDraft(
                    isOpen = true,
                    stocktakeMode = true,
                    currentStock = "10",
                    countedQuantity = "8"
                )
            )
        )
    }
}
