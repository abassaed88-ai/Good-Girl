package com.goodgirl.accounting

internal data class InvoiceDraftState(
    val draftLineCount: Int = 0,
    val hasSelectedProduct: Boolean = false,
    val hasSelectedParty: Boolean = false,
    val quantity: String = "1",
    val unitPrice: String = "0",
    val lineDiscount: String = "0",
    val invoiceDiscount: String = "0",
    val paidAmount: String = "0",
    val currencyCode: String = "SYP",
    val note: String = "",
    val paymentPanelOpen: Boolean = false,
    val paymentAmount: String = "",
    val paymentNote: String = ""
)

internal fun hasUnsavedInvoiceDraft(state: InvoiceDraftState): Boolean =
    state.draftLineCount > 0 ||
        state.hasSelectedProduct ||
        state.hasSelectedParty ||
        state.quantity != "1" ||
        state.unitPrice != "0" ||
        state.lineDiscount != "0" ||
        state.invoiceDiscount != "0" ||
        state.paidAmount != "0" ||
        state.currencyCode != "SYP" ||
        state.note.isNotBlank() ||
        state.paymentPanelOpen ||
        state.paymentAmount.isNotBlank() ||
        state.paymentNote.isNotBlank()

internal data class ProductFormValues(
    val categoryId: String? = null,
    val name: String = "",
    val sku: String = "",
    val baseUnit: String = "قطعة",
    val purchaseCost: String = "0",
    val salePrice: String = "0",
    val currencyCode: String = "SYP",
    val openingStock: String = "0",
    val lowStockThreshold: String = "0",
    val note: String = ""
)

internal fun hasUnsavedProductForm(
    current: ProductFormValues,
    original: ProductFormValues? = null
): Boolean = current != (original ?: ProductFormValues())

internal data class StockAdjustmentDraft(
    val isOpen: Boolean = false,
    val stocktakeMode: Boolean = false,
    val currentStock: String = "",
    val countedQuantity: String = "",
    val quantityDelta: String = "",
    val unitCost: String = "0",
    val originalUnitCost: String = "0",
    val note: String = ""
)

internal fun hasUnsavedStockAdjustment(draft: StockAdjustmentDraft): Boolean =
    draft.isOpen && (
        if (draft.stocktakeMode) {
            draft.countedQuantity != draft.currentStock || draft.note.isNotBlank()
        } else {
            draft.quantityDelta.isNotBlank() || draft.note.isNotBlank()
        }
    )

internal fun hasUnsavedCashDraft(amount: String, description: String): Boolean =
    amount.isNotBlank() || description.isNotBlank()

internal data class DailySaleDraftState(
    val hasSelectedProduct: Boolean = false,
    val hasSelectedParty: Boolean = false,
    val quantity: String = "1",
    val salePrice: String = "0",
    val lineDiscount: String = "0",
    val paidAmountTouched: Boolean = false,
    val paidAmount: String = "",
    val note: String = ""
)

internal fun hasUnsavedDailySaleDraft(state: DailySaleDraftState): Boolean =
    state.hasSelectedProduct ||
        state.hasSelectedParty ||
        state.quantity != "1" ||
        state.salePrice != "0" ||
        state.lineDiscount != "0" ||
        state.paidAmountTouched ||
        state.paidAmount.isNotBlank() ||
        state.note.isNotBlank()

internal data class PartyFormValues(
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val note: String = "",
    val openingBalance: String = "0",
    val openingCurrency: String = "SYP",
    val openingExchangeRate: String = "1"
)

internal fun hasUnsavedPartyForm(
    current: PartyFormValues,
    original: PartyFormValues? = null
): Boolean = current != (original ?: PartyFormValues())

internal data class CategoryFormValues(
    val name: String = "",
    val note: String = ""
)

internal fun hasUnsavedCategoryForm(
    current: CategoryFormValues,
    original: CategoryFormValues? = null
): Boolean = current != (original ?: CategoryFormValues())

internal fun hasUnsavedUnitDraft(name: String, factor: String): Boolean =
    name.isNotBlank() || factor != "1"
