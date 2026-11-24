package com.goodgirl.accounting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun cashFormOnlyWarnsWhenAmountOrDescriptionExists() {
        assertFalse(hasUnsavedCashDraft("", ""))
        assertTrue(hasUnsavedCashDraft("250", ""))
        assertTrue(hasUnsavedCashDraft("", "مصاريف"))
    }

    @Test
    fun dailySaleDraftTracksProductAndEditedFields() {
        assertFalse(hasUnsavedDailySaleDraft(DailySaleDraftState()))
        assertTrue(hasUnsavedDailySaleDraft(DailySaleDraftState(hasSelectedProduct = true)))
        assertTrue(hasUnsavedDailySaleDraft(DailySaleDraftState(note = "مبيعة نقدية")))
        assertTrue(hasUnsavedDailySaleDraft(DailySaleDraftState(paidAmountTouched = true)))
    }

    @Test
    fun untouchedPartyFormIsCleanAndChangedFieldsAreDirty() {
        assertFalse(hasUnsavedPartyForm(PartyFormValues()))
        assertTrue(hasUnsavedPartyForm(PartyFormValues(name = "عميل")))
        val original = PartyFormValues(name = "عميل", phone = "123")
        assertFalse(hasUnsavedPartyForm(original, original))
        assertTrue(hasUnsavedPartyForm(original.copy(phone = "456"), original))
    }

    @Test
    fun categoryFormAndUnitDraftDetectEdits() {
        assertFalse(hasUnsavedCategoryForm(CategoryFormValues()))
        assertTrue(hasUnsavedCategoryForm(CategoryFormValues(name = "عناية")))
        val original = CategoryFormValues(name = "عطور")
        assertFalse(hasUnsavedCategoryForm(original, original))
        assertTrue(hasUnsavedCategoryForm(original.copy(note = "ملاحظات"), original))
        assertFalse(hasUnsavedUnitDraft("", "1"))
        assertTrue(hasUnsavedUnitDraft("كرتونة", "12"))
    }


    @Test
    fun invoiceCurrencyAndExchangeRateDraftsAreTracked() {
        assertTrue(
            hasUnsavedInvoiceDraft(
                InvoiceDraftState(
                    currencyCode = "USD",
                    exchangeRate = "15000",
                    defaultExchangeRate = "15000"
                )
            )
        )
        assertTrue(
            hasUnsavedInvoiceDraft(
                InvoiceDraftState(
                    currencyCode = "USD",
                    exchangeRate = "15100",
                    defaultExchangeRate = "15000"
                )
            )
        )
        assertFalse(
            hasUnsavedInvoiceDraft(
                InvoiceDraftState(
                    currencyCode = "SYP",
                    exchangeRate = "15100",
                    defaultExchangeRate = "15000"
                )
            )
        )
        assertFalse(hasUnsavedInvoiceExchangeRateChanged("USD", "15000", "15000"))
        assertTrue(hasUnsavedInvoiceExchangeRateChanged("USD", "15100", "15000"))
        assertFalse(hasUnsavedInvoiceExchangeRateChanged("SYP", "15100", "15000"))
    }

    @Test
    fun stockAdjustmentUnitCostChangesAreTracked() {
        assertFalse(
            hasUnsavedStockAdjustment(
                StockAdjustmentDraft(
                    isOpen = true,
                    currentStock = "10",
                    countedQuantity = "10",
                    unitCost = "15",
                    originalUnitCost = "15"
                )
            )
        )
        assertTrue(
            hasUnsavedStockAdjustment(
                StockAdjustmentDraft(
                    isOpen = true,
                    currentStock = "10",
                    countedQuantity = "10",
                    unitCost = "18",
                    originalUnitCost = "15"
                )
            )
        )
    }

    @Test
    fun partyPaymentOnlyWarnsForEnteredAmountOrNote() {
        assertFalse(hasUnsavedPartyPaymentDraft(isFormOpen = false, amount = "100", note = ""))
        assertFalse(hasUnsavedPartyPaymentDraft(isFormOpen = true, amount = "", note = ""))
        assertTrue(hasUnsavedPartyPaymentDraft(isFormOpen = true, amount = "100", note = ""))
        assertTrue(hasUnsavedPartyPaymentDraft(isFormOpen = true, amount = "", note = "تحصيل نقدي"))
    }


    @Test
    fun ledgerAccountDraftDetectsCodeNameAndType() {
        assertFalse(hasUnsavedLedgerAccountDraft(code = "", name = "", type = "ASSET"))
        assertTrue(hasUnsavedLedgerAccountDraft(code = "101", name = "", type = "ASSET"))
        assertTrue(hasUnsavedLedgerAccountDraft(code = "", name = "صندوق", type = "ASSET"))
        assertTrue(hasUnsavedLedgerAccountDraft(code = "", name = "", type = "LIABILITY"))
    }

    @Test
    fun manualJournalDraftTracksHeaderCurrencyAndLines() {
        assertFalse(
            hasUnsavedManualJournalDraft(
                journalNumberChanged = false,
                description = "",
                currencyCode = "SYP",
                exchangeRate = "15000",
                defaultExchangeRate = "15000",
                lineCount = 2,
                hasLineContent = false
            )
        )
        assertTrue(
            hasUnsavedManualJournalDraft(
                journalNumberChanged = false,
                description = "قيد افتتاحي",
                currencyCode = "SYP",
                exchangeRate = "1",
                defaultExchangeRate = "1",
                lineCount = 2,
                hasLineContent = false
            )
        )
        assertTrue(
            hasUnsavedManualJournalDraft(
                journalNumberChanged = false,
                description = "",
                currencyCode = "USD",
                exchangeRate = "15000",
                defaultExchangeRate = "15000",
                lineCount = 2,
                hasLineContent = false
            )
        )
        assertTrue(
            hasUnsavedManualJournalDraft(
                journalNumberChanged = false,
                description = "",
                currencyCode = "SYP",
                exchangeRate = "1",
                defaultExchangeRate = "1",
                lineCount = 3,
                hasLineContent = false
            )
        )
        assertTrue(
            hasUnsavedManualJournalDraft(
                journalNumberChanged = false,
                description = "",
                currencyCode = "SYP",
                exchangeRate = "1",
                defaultExchangeRate = "1",
                lineCount = 2,
                hasLineContent = true
            )
        )
    }


    @Test
    fun reversalDialogOnlyWarnsAfterReasonIsEntered() {
        assertFalse(hasUnsavedReversalDraft(""))
        assertFalse(hasUnsavedReversalDraft("   "))
        assertTrue(hasUnsavedReversalDraft("تصحيح قيد خاطئ"))
    }


    @Test
    fun backupScreenTracksPasswordsAndRestoreConfirmation() {
        assertFalse(
            hasUnsavedBackupDraft(
                exportPassword = "",
                confirmExportPassword = "",
                restorePassword = "",
                confirmRestore = false
            )
        )
        assertTrue(
            hasUnsavedBackupDraft(
                exportPassword = "password123",
                confirmExportPassword = "",
                restorePassword = "",
                confirmRestore = false
            )
        )
        assertTrue(
            hasUnsavedBackupDraft(
                exportPassword = "",
                confirmExportPassword = "password123",
                restorePassword = "",
                confirmRestore = false
            )
        )
        assertTrue(
            hasUnsavedBackupDraft(
                exportPassword = "",
                confirmExportPassword = "",
                restorePassword = "password123",
                confirmRestore = false
            )
        )
        assertTrue(
            hasUnsavedBackupDraft(
                exportPassword = "",
                confirmExportPassword = "",
                restorePassword = "",
                confirmRestore = true
            )
        )
    }


    @Test
    fun fontScaleIsClampedToReadableSupportedRange() {
        assertEquals(0.9f, normalizeAppFontScale(0.5f))
        assertEquals(1.0f, normalizeAppFontScale(1.0f))
        assertEquals(1.2f, normalizeAppFontScale(1.8f))
    }
}
