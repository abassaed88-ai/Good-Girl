package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.data.local.StockMovementEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.domain.InvoiceCalculator
import com.goodgirl.accounting.domain.InvoiceDiscountAllocator
import com.goodgirl.accounting.domain.InvoicePostingRules
import com.goodgirl.accounting.domain.ProductInputRules
import com.goodgirl.accounting.domain.ProductUnitInputRules
import com.goodgirl.accounting.domain.JournalEntryRules
import com.goodgirl.accounting.domain.JournalLineDraft
import com.goodgirl.accounting.domain.CashTransactionRules
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.UUID

data class InvoiceLineDraft(
    val productId: String,
    val quantity: String,
    val unitPrice: String,
    val discount: String = "0",
    val unitName: String? = null,
    val saleUnitCostSnapshot: String? = null,
    val associatedExpenses: List<DailySalesExpenseDraft> = emptyList()
)

data class DailySalesExpenseDraft(
    val id: String,
    val description: String,
    val amount: String,
    val currencyCode: String,
    val exchangeRateSnapshot: String
)

data class PostedInvoiceResult(
    val invoiceId: String,
    val invoiceNumber: String,
    val total: String,
    val paid: String,
    val remaining: String
)

data class InvoicePaymentResult(
    val paymentId: String,
    val invoiceNumber: String,
    val paid: String,
    val remaining: String,
    val currencyCode: String
)

private data class PurchaseCostRevaluationAccumulator(
    val originalProduct: ProductEntity,
    var incomingQuantity: BigDecimal = BigDecimal.ZERO,
    var incomingExtendedCost: BigDecimal = BigDecimal.ZERO,
    var latestIncomingUnitCost: BigDecimal? = null
)

class InvoiceRepository(private val database: AccountingDatabase) {
    fun observeInvoices(): Flow<List<InvoiceEntity>> = database.invoiceDao().observeAll()


    suspend fun recordPayment(
        invoiceId: String,
        amount: String,
        note: String = "",
        occurredAt: Long = System.currentTimeMillis()
    ): InvoicePaymentResult {
        val paymentId = UUID.randomUUID().toString()
        var result: InvoicePaymentResult? = null

        database.withTransaction {
            val invoice = database.invoiceDao().findById(invoiceId)
                ?: throw IllegalArgumentException("الفاتورة غير موجودة")
            require(invoice.status == "POSTED") { "لا يمكن تسجيل دفعة على فاتورة غير مرحلة" }
            val payment = com.goodgirl.accounting.domain.InvoicePaymentRules.calculate(
                remaining = invoice.remaining,
                alreadyPaid = invoice.paid,
                payment = amount
            )
            val newRemaining = payment.newRemaining.stripTrailingZeros().toPlainString()
            val newPaid = payment.newPaid.stripTrailingZeros().toPlainString()

            database.invoiceDao().updateInvoice(
                invoice.copy(
                    paid = newPaid,
                    remaining = newRemaining
                )
            )

            val isSale = invoice.kind == "SALE"
            val cleanAmount = payment.payment.stripTrailingZeros().toPlainString()
            database.ledgerDao().insert(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    partyId = invoice.partyId,
                    entryType = if (isSale) "CASH_IN" else "CASH_OUT",
                    amount = cleanAmount,
                    currencyCode = invoice.currencyCode,
                    exchangeRateSnapshot = invoice.exchangeRateSnapshot,
                    sourceType = if (isSale) "SALE_RECEIPT" else "PURCHASE_PAYMENT",
                    sourceId = paymentId,
                    occurredAt = occurredAt,
                    note = note.trim().ifEmpty { "دفعة للفاتورة ${invoice.invoiceNumber}" }
                )
            )
            database.ledgerDao().insert(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    partyId = invoice.partyId,
                    entryType = if (isSale) "RECEIVABLE_PAYMENT" else "PAYABLE_PAYMENT",
                    amount = cleanAmount,
                    currencyCode = invoice.currencyCode,
                    exchangeRateSnapshot = invoice.exchangeRateSnapshot,
                    sourceType = if (isSale) "RECEIVABLE_SETTLEMENT" else "PAYABLE_SETTLEMENT",
                    sourceId = paymentId,
                    occurredAt = occurredAt,
                    note = note.trim().ifEmpty { "تسوية رصيد الفاتورة ${invoice.invoiceNumber}" }
                )
            )

            SettlementJournalPoster.post(
                database = database,
                sourceType = if (isSale) "SALE_RECEIPT" else "PURCHASE_PAYMENT",
                sourceId = paymentId,
                partyKind = if (isSale) "CUSTOMER" else "SUPPLIER",
                amount = cleanAmount,
                currencyCode = invoice.currencyCode,
                exchangeRateSnapshot = invoice.exchangeRateSnapshot,
                occurredAt = occurredAt,
                description = "تسوية دفعة الفاتورة ${invoice.invoiceNumber}"
            )

            result = InvoicePaymentResult(
                paymentId = paymentId,
                invoiceNumber = invoice.invoiceNumber,
                paid = newPaid,
                remaining = newRemaining,
                currencyCode = invoice.currencyCode
            )
        }

        return result ?: error("تعذر تسجيل الدفعة")
    }

    suspend fun postInvoice(
        invoiceNumber: String,
        kind: String,
        partyId: String?,
        currencyCode: String,
        exchangeRateSnapshot: String,
        lines: List<InvoiceLineDraft>,
        invoiceDiscount: String = "0",
        paidAmount: String = "0",
        issuedAt: Long = System.currentTimeMillis(),
        note: String = ""
    ): PostedInvoiceResult {
        val cleanKind = kind.trim().uppercase()
        require(cleanKind == "SALE" || cleanKind == "PURCHASE") { "نوع الفاتورة غير صحيح" }
        val cleanNumber = invoiceNumber.trim()
        require(cleanNumber.isNotEmpty()) { "رقم الفاتورة مطلوب" }
        require(cleanNumber.length <= 40) { "رقم الفاتورة طويل جداً" }
        val cleanCurrency = currencyCode.trim().uppercase()
        require(cleanCurrency == "SYP" || cleanCurrency == "USD") { "العملة غير مدعومة" }
        val cleanRate = ProductInputRules.normalizeNonNegativeDecimal(exchangeRateSnapshot, "سعر الصرف")
        require(cleanRate.toBigDecimal() > BigDecimal.ZERO) { "سعر الصرف يجب أن يكون أكبر من صفر" }
        require(lines.isNotEmpty()) { "أضف مادة واحدة على الأقل إلى الفاتورة" }
        val attachedExpenses = lines.flatMap { it.associatedExpenses }
        require(cleanKind == "SALE" || attachedExpenses.isEmpty()) {
            "لا يمكن إرفاق مصروفات مبيعات بفاتورة شراء"
        }
        require(attachedExpenses.map { it.id }.distinct().size == attachedExpenses.size) {
            "يوجد معرّف مصروف مكرر ضمن الفاتورة"
        }
        attachedExpenses.forEach { expense ->
            require(expense.id.isNotBlank()) { "معرّف المصروف مطلوب" }
            require(expense.description.trim().isNotEmpty()) { "بيان المصروف مطلوب" }
            require(expense.description.trim().length <= 180) { "بيان المصروف طويل جداً" }
            CashTransactionRules.normalize(
                amount = expense.amount,
                currencyCode = expense.currencyCode,
                exchangeRate = expense.exchangeRateSnapshot
            )
        }

        val cleanInvoiceDiscount =
            ProductInputRules.normalizeNonNegativeDecimal(invoiceDiscount, "خصم الفاتورة").toBigDecimal()
        val cleanPaid = ProductInputRules.normalizeNonNegativeDecimal(paidAmount, "المبلغ المدفوع").toBigDecimal()
        val invoiceId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        var subtotal = BigDecimal.ZERO
        var lineDiscountTotal = BigDecimal.ZERO
        val storedLines = mutableListOf<InvoiceLineEntity>()
        val productStateBeforePosting = linkedMapOf<String, ProductEntity>()
        val purchaseCostRevaluations = linkedMapOf<String, PurchaseCostRevaluationAccumulator>()

        database.withTransaction {
            if (partyId != null) {
                val party = database.partyDao().findById(partyId)
                    ?: throw IllegalArgumentException("العميل أو المورد غير موجود")
                val requiredKind = if (cleanKind == "SALE") "CUSTOMER" else "SUPPLIER"
                require(party.kind == requiredKind) { "نوع الحساب لا يتوافق مع نوع الفاتورة" }
            }

            val lineNetAmounts = lines.map { draft ->
                val quantity = InvoicePostingRules.normalizePositiveQuantity(draft.quantity).toBigDecimal()
                val unitPrice = ProductInputRules.normalizeNonNegativeDecimal(draft.unitPrice, "سعر الوحدة").toBigDecimal()
                val lineDiscount = ProductInputRules.normalizeNonNegativeDecimal(draft.discount, "خصم السطر").toBigDecimal()
                val grossLine = quantity.multiply(unitPrice)
                require(lineDiscount <= grossLine) { "خصم السطر أكبر من قيمة السطر" }
                grossLine.subtract(lineDiscount)
            }
            val netBeforeInvoiceDiscount = lineNetAmounts.fold(BigDecimal.ZERO, BigDecimal::add)
            require(cleanInvoiceDiscount <= netBeforeInvoiceDiscount) {
                "خصم الفاتورة أكبر من قيمة الفاتورة بعد خصومات المواد"
            }
            val invoiceDiscountShares = InvoiceDiscountAllocator.allocate(
                lineNetAmounts,
                cleanInvoiceDiscount
            )
            val purchaseUnitCosts = lines.mapIndexed { index, draft ->
                val product = database.productDao().findById(draft.productId)
                    ?: throw IllegalArgumentException("إحدى المواد غير موجودة")
                val selectedUnit = database.productUnitDao().findByName(
                    product.id,
                    draft.unitName ?: product.baseUnit
                ) ?: throw IllegalArgumentException("وحدة القياس المحددة غير مرتبطة بهذه المادة")
                val quantity = InvoicePostingRules.normalizePositiveQuantity(draft.quantity).toBigDecimal()
                val factor = ProductUnitInputRules.normalizeFactor(selectedUnit.baseFactor).toBigDecimal()
                val baseQuantity = quantity.multiply(factor)
                lineNetAmounts[index].subtract(invoiceDiscountShares[index])
                    .divide(baseQuantity, 8, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString()
            }

            val recordedAtByProduct = mutableMapOf<String, Long>()
            lines.forEachIndexed { lineIndex, draft ->
                val product = database.productDao().findById(draft.productId)
                    ?: throw IllegalArgumentException("إحدى المواد غير موجودة")
                require(product.isActive) { "لا يمكن استخدام مادة مؤرشفة في فاتورة جديدة" }
                productStateBeforePosting.putIfAbsent(product.id, product)
                require(product.priceCurrencyCode == cleanCurrency) {
                    "عملة المنتج " + product.name + " هي " + product.priceCurrencyCode + "، ولا تطابق عملة الفاتورة " + cleanCurrency
                }

                val quantityText = InvoicePostingRules.normalizePositiveQuantity(draft.quantity)
                val quantity = quantityText.toBigDecimal()
                val selectedUnit = database.productUnitDao().findByName(
                    product.id,
                    draft.unitName ?: product.baseUnit
                ) ?: throw IllegalArgumentException("وحدة القياس المحددة غير مرتبطة بهذه المادة")
                val unitFactor = ProductUnitInputRules.normalizeFactor(selectedUnit.baseFactor).toBigDecimal()
                val baseQuantity = quantity.multiply(unitFactor)
                val unitPrice = ProductInputRules.normalizeNonNegativeDecimal(draft.unitPrice, "سعر الوحدة").toBigDecimal()
                val lineDiscount = ProductInputRules.normalizeNonNegativeDecimal(draft.discount, "خصم السطر").toBigDecimal()
                val grossLine = quantity.multiply(unitPrice)
                require(lineDiscount <= grossLine) { "خصم السطر أكبر من قيمة السطر للمنتج: " + product.name }
                val lineTotal = grossLine.subtract(lineDiscount)
                val movementId = UUID.randomUUID().toString()
                val lineId = UUID.randomUUID().toString()
                val currentStock = ProductInputRules.normalizeNonNegativeDecimal(product.stockQuantity, "رصيد المنتج").toBigDecimal()
                val currentCost = ProductInputRules.normalizeNonNegativeDecimal(product.purchaseCost, "كلفة المنتج").toBigDecimal()
                val previousRecordedAt = recordedAtByProduct[product.id]
                val recordedAt = nextMovementRecordedAt(product.id, now, previousRecordedAt)
                recordedAtByProduct[product.id] = recordedAt
                val unitCostSnapshot: String

                if (cleanKind == "SALE") {
                    require(currentStock >= baseQuantity) { "الكمية المتاحة غير كافية للمنتج: " + product.name }
                    val newStock = currentStock.subtract(baseQuantity).stripTrailingZeros().toPlainString()
                    database.productDao().updateStock(product.id, newStock, now)
                    unitCostSnapshot = draft.saleUnitCostSnapshot?.let {
                        ProductInputRules.normalizeNonNegativeDecimal(it, "كلفة الوحدة المحفوظة")
                    } ?: currentCost.stripTrailingZeros().toPlainString()

                    database.stockMovementDao().insert(
                        StockMovementEntity(
                            id = movementId,
                            productId = product.id,
                            quantityDelta = baseQuantity.negate().stripTrailingZeros().toPlainString(),
                            unitCostSnapshot = unitCostSnapshot,
                            currencyCode = cleanCurrency,
                            sourceType = "SALE_INVOICE",
                            sourceId = lineId,
                            occurredAt = issuedAt,
                            recordedAt = recordedAt,
                            note = "صرف مخزون من فاتورة بيع " + cleanNumber
                        )
                    )
                } else {
                    val incomingBaseCost = purchaseUnitCosts[lineIndex]
                    val incomingUnitCost = incomingBaseCost.toBigDecimal()
                    val accumulator = purchaseCostRevaluations.getOrPut(product.id) {
                        PurchaseCostRevaluationAccumulator(
                            originalProduct = productStateBeforePosting.getValue(product.id)
                        )
                    }
                    accumulator.incomingQuantity = accumulator.incomingQuantity.add(baseQuantity)
                    // Use the exact discounted invoice-line value here, not
                    // rounded unit cost × quantity. The purchase journal debits
                    // this exact line value; the revaluation then reconciles it
                    // to the product's stored (8-decimal) current unit cost.
                    val incomingLineValue = lineNetAmounts[lineIndex]
                        .subtract(invoiceDiscountShares[lineIndex])
                    accumulator.incomingExtendedCost = accumulator.incomingExtendedCost.add(
                        incomingLineValue
                    )
                    // For repeated product lines, the last invoice line is
                    // treated as the latest incoming purchase cost.
                    accumulator.latestIncomingUnitCost = incomingUnitCost

                    val newStock = currentStock.add(baseQuantity).stripTrailingZeros().toPlainString()
                    database.productDao().update(
                        product.copy(
                            stockQuantity = newStock,
                            purchaseCost = incomingUnitCost.stripTrailingZeros().toPlainString(),
                            updatedAt = now
                        )
                    )
                    unitCostSnapshot = incomingBaseCost

                    database.stockMovementDao().insert(
                        StockMovementEntity(
                            id = movementId,
                            productId = product.id,
                            quantityDelta = baseQuantity.stripTrailingZeros().toPlainString(),
                            unitCostSnapshot = unitCostSnapshot,
                            currencyCode = cleanCurrency,
                            sourceType = "PURCHASE_INVOICE",
                            sourceId = lineId,
                            occurredAt = issuedAt,
                            recordedAt = recordedAt,
                            note = "إضافة مخزون من فاتورة شراء " + cleanNumber
                        )
                    )
                }

                subtotal = subtotal.add(grossLine)
                lineDiscountTotal = lineDiscountTotal.add(lineDiscount)
                storedLines += InvoiceLineEntity(
                    id = lineId,
                    invoiceId = invoiceId,
                    productId = product.id,
                    productNameSnapshot = product.name,
                    unitSnapshot = selectedUnit.unitName,
                    quantity = quantityText,
                    unitCostSnapshot = unitCostSnapshot,
                    unitPriceSnapshot = unitPrice.stripTrailingZeros().toPlainString(),
                    discount = lineDiscount.stripTrailingZeros().toPlainString(),
                    lineTotal = lineTotal.stripTrailingZeros().toPlainString(),
                    unitFactorSnapshot = unitFactor.stripTrailingZeros().toPlainString(),
                    baseQuantity = baseQuantity.stripTrailingZeros().toPlainString()
                )
            }

            val inventoryRevaluations = purchaseCostRevaluations.mapNotNull { (productId, accumulator) ->
                val original = accumulator.originalProduct
                val previousQuantity = original.stockQuantity.toBigDecimal()
                val previousUnitCost = original.purchaseCost.toBigDecimal()
                val latestUnitCost = accumulator.latestIncomingUnitCost
                    ?: throw IllegalStateException("لم تُحدد أحدث تكلفة شراء للمادة $productId")
                val targetCarryingValue = previousQuantity.add(accumulator.incomingQuantity)
                    .multiply(latestUnitCost)
                val preRevaluationValue = previousQuantity.multiply(previousUnitCost)
                    .add(accumulator.incomingExtendedCost)
                val valueDelta = targetCarryingValue.subtract(preRevaluationValue)
                if (previousQuantity.compareTo(BigDecimal.ZERO) == 0 ||
                    valueDelta.compareTo(BigDecimal.ZERO) == 0
                ) {
                    null
                } else {
                    InventoryRevaluationAdjustment(
                        productId = productId,
                        productName = original.name,
                        previousStockQuantity = previousQuantity.stripTrailingZeros().toPlainString(),
                        previousUnitCost = previousUnitCost.stripTrailingZeros().toPlainString(),
                        incomingQuantity = accumulator.incomingQuantity.stripTrailingZeros().toPlainString(),
                        incomingExtendedCost = accumulator.incomingExtendedCost.stripTrailingZeros().toPlainString(),
                        newUnitCost = latestUnitCost.stripTrailingZeros().toPlainString(),
                        valueDelta = valueDelta.stripTrailingZeros().toPlainString()
                    )
                }
            }

            require(cleanInvoiceDiscount <= subtotal.subtract(lineDiscountTotal)) {
                "خصم الفاتورة أكبر من قيمة الفاتورة بعد خصومات المواد"
            }
            val totalDiscount = lineDiscountTotal.add(cleanInvoiceDiscount)
            val amounts = InvoiceCalculator.calculate(subtotal, totalDiscount, cleanPaid)
            require(cleanPaid <= amounts.total) { "المبلغ المدفوع لا يمكن أن يتجاوز إجمالي الفاتورة" }
            require(amounts.remaining.compareTo(BigDecimal.ZERO) == 0 || partyId != null) {
                "اختر حساب العميل أو المورد عند وجود مبلغ آجل"
            }

            database.invoiceDao().insertInvoice(
                InvoiceEntity(
                    id = invoiceId,
                    invoiceNumber = cleanNumber,
                    kind = cleanKind,
                    partyId = partyId,
                    currencyCode = cleanCurrency,
                    exchangeRateSnapshot = cleanRate,
                    subtotal = subtotal.stripTrailingZeros().toPlainString(),
                    discount = totalDiscount.stripTrailingZeros().toPlainString(),
                    total = amounts.total.stripTrailingZeros().toPlainString(),
                    paid = amounts.paid.stripTrailingZeros().toPlainString(),
                    remaining = amounts.remaining.stripTrailingZeros().toPlainString(),
                    status = "POSTED",
                    issuedAt = issuedAt,
                    note = note.trim(),
                    createdAt = now
                )
            )
            database.invoiceDao().insertLines(storedLines)
            val postedInvoice = database.invoiceDao().findById(invoiceId)
                ?: error("تعذر استرجاع الفاتورة بعد ترحيلها")
            InvoiceJournalPoster.post(
                database = database,
                invoice = postedInvoice,
                lines = storedLines,
                inventoryRevaluations = inventoryRevaluations,
                revaluationOccurredAt = now
            )
            if (cleanKind == "SALE") {
                attachedExpenses.forEach { expense ->
                    DailySalesExpenseJournalPoster.post(
                        database = database,
                        invoiceId = invoiceId,
                        invoiceNumber = cleanNumber,
                        occurredAt = issuedAt,
                        expense = expense
                    )
                }
            }

            if (amounts.remaining > BigDecimal.ZERO) {
                database.ledgerDao().insert(
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        partyId = partyId,
                        entryType = if (cleanKind == "SALE") "RECEIVABLE" else "PAYABLE",
                        amount = amounts.remaining.stripTrailingZeros().toPlainString(),
                        currencyCode = cleanCurrency,
                        exchangeRateSnapshot = cleanRate,
                        sourceType = if (cleanKind == "SALE") "SALE_INVOICE_BALANCE" else "PURCHASE_INVOICE_BALANCE",
                        sourceId = invoiceId,
                        occurredAt = issuedAt,
                        note = "الرصيد المتبقي من الفاتورة " + cleanNumber
                    )
                )
            }
            if (amounts.paid > BigDecimal.ZERO) {
                database.ledgerDao().insert(
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        partyId = partyId,
                        entryType = if (cleanKind == "SALE") "CASH_IN" else "CASH_OUT",
                        amount = amounts.paid.stripTrailingZeros().toPlainString(),
                        currencyCode = cleanCurrency,
                        exchangeRateSnapshot = cleanRate,
                        sourceType = if (cleanKind == "SALE") "SALE_INVOICE_PAYMENT" else "PURCHASE_INVOICE_PAYMENT",
                        sourceId = invoiceId,
                        occurredAt = issuedAt,
                        note = "دفعة مرتبطة بالفاتورة " + cleanNumber
                    )
                )
            }

        }

        val totalDiscount = lineDiscountTotal.add(cleanInvoiceDiscount)
        val totals = InvoiceCalculator.calculate(subtotal, totalDiscount, cleanPaid)
        return PostedInvoiceResult(
            invoiceId = invoiceId,
            invoiceNumber = cleanNumber,
            total = totals.total.stripTrailingZeros().toPlainString(),
            paid = totals.paid.stripTrailingZeros().toPlainString(),
            remaining = totals.remaining.stripTrailingZeros().toPlainString()
        )
    }


    private suspend fun nextMovementRecordedAt(
        productId: String,
        requestedAt: Long,
        previousRecordedAt: Long? = null
    ): Long {
        val latestMovement = previousRecordedAt ?: database.stockMovementDao().getLatestRecordedAt(productId)
        val baselineRecordedAt = database.stockCostBaselineDao().findByProductId(productId)?.recordedAt
        val previous = listOfNotNull(latestMovement, baselineRecordedAt).maxOrNull()
        return if (previous == null) requestedAt else maxOf(requestedAt, Math.addExact(previous, 1L))
    }
}
