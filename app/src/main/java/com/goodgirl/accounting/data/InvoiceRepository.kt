package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.data.local.StockMovementEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.data.local.StockCostRecalculationAuditEntity
import com.goodgirl.accounting.data.local.StockCostRecalculationItemEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.domain.InvoiceCalculator
import com.goodgirl.accounting.domain.InvoiceDiscountAllocator
import com.goodgirl.accounting.domain.InvoicePostingRules
import com.goodgirl.accounting.domain.ProductInputRules
import com.goodgirl.accounting.domain.ProductUnitInputRules
import com.goodgirl.accounting.domain.HistoricalStockCostBaseline
import com.goodgirl.accounting.domain.HistoricalStockCostEvent
import com.goodgirl.accounting.domain.HistoricalStockCostReconciler
import com.goodgirl.accounting.domain.HistoricalStockCostChange
import com.goodgirl.accounting.domain.JournalEntryRules
import com.goodgirl.accounting.domain.JournalLineDraft
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.UUID

data class InvoiceLineDraft(
    val productId: String,
    val quantity: String,
    val unitPrice: String,
    val discount: String = "0",
    val unitName: String? = null
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

private data class HistoricalStockLossCorrection(
    val change: HistoricalStockCostChange,
    val movement: StockMovementEntity,
    val ledgerEntry: LedgerEntryEntity
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
                    unitCostSnapshot = currentCost.stripTrailingZeros().toPlainString()

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
                    accumulator.incomingExtendedCost = accumulator.incomingExtendedCost.add(
                        baseQuantity.multiply(incomingUnitCost)
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
                if (valueDelta.compareTo(BigDecimal.ZERO) == 0) {
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
                inventoryRevaluations = inventoryRevaluations
            )

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


    /**
     * Replays the affected products from their trusted opening baselines.
     * Original invoice lines and stock movement snapshots remain untouched;
     * differences are stored as append-only audit rows and reversing-value
     * journals in the same transaction as the invoice posting.
     */
    private suspend fun reconcileHistoricalStockCosts(
        triggerType: String,
        triggerSourceId: String,
        reason: String,
        originalProducts: Map<String, ProductEntity>,
        now: Long
    ) {
        if (originalProducts.isEmpty()) return

        val invoicesById = database.invoiceDao().getAllInvoices().associateBy { it.id }
        val invoiceLinesById = database.invoiceDao().getAllLines().associateBy { it.id }
        val audits = database.stockCostRecalculationAuditDao().getAll()
        val auditsById = audits.associateBy { it.id }
        val existingItems = database.stockCostRecalculationItemDao().getAll()
        val latestItemByMovement = existingItems.groupBy { it.movementId }.mapValues { (_, items) ->
            items.maxWithOrNull(
                compareBy<StockCostRecalculationItemEntity> {
                    auditsById[it.auditId]?.createdAt ?: Long.MIN_VALUE
                }.thenBy { it.auditId }
            )!!
        }
        val latestAuditAt = audits.maxOfOrNull { it.createdAt } ?: 0L
        val auditCreatedAt = maxOf(now, if (latestAuditAt == Long.MAX_VALUE) latestAuditAt else latestAuditAt + 1L)

        originalProducts.forEach { (productId, originalProduct) ->
            val baselineEntity = database.stockCostBaselineDao().findByProductId(productId)
                ?: throw IllegalStateException("لا توجد نقطة بداية موثوقة لكلفة المادة ${originalProduct.name}")
            val baseline = HistoricalStockCostBaseline(
                productId = baselineEntity.productId,
                quantity = baselineEntity.quantity,
                averageCost = baselineEntity.averageCost,
                currencyCode = baselineEntity.currencyCode,
                effectiveAt = baselineEntity.effectiveAt,
                recordedAt = baselineEntity.recordedAt
            )
            val movements = database.stockMovementDao().getAll().filter { it.productId == productId }
            val replayMovements = movements.filter { it.recordedAt > baseline.recordedAt }
            val events = replayMovements.map { movement ->
                HistoricalStockCostEvent(
                    id = movement.id,
                    sourceType = movement.sourceType,
                    quantityDelta = movement.quantityDelta,
                    unitCostSnapshot = movement.unitCostSnapshot,
                    currencyCode = movement.currencyCode,
                    occurredAt = movement.occurredAt,
                    recordedAt = movement.recordedAt
                )
            }
            val effectiveCosts = replayMovements
                .filter { it.quantityDelta.toBigDecimal() < BigDecimal.ZERO }
                .associate { movement ->
                    movement.id to (
                        latestItemByMovement[movement.id]?.newUnitCost
                            ?: movement.unitCostSnapshot
                        )
                }
            val movementSourceIds = replayMovements.associate { it.id to it.sourceId }
            val plan = HistoricalStockCostReconciler.plan(
                baseline = baseline,
                events = events,
                currentlyEffectiveUnitCosts = effectiveCosts,
                movementSourceIds = movementSourceIds
            )

            val saleChanges = plan.changes.filter {
                it.sourceType.trim().uppercase() == "SALE_INVOICE"
            }
            val stockLossChanges = plan.changes.filter {
                it.sourceType.trim().uppercase() in setOf("MANUAL_ADJUSTMENT", "STOCKTAKE_ADJUSTMENT")
            }
            require(plan.changes.size == saleChanges.size + stockLossChanges.size) {
                "إعادة الاحتساب تؤثر على نوع إخراج مخزون لا يدعم التصحيح المحاسبي؛ أوقفنا الترحيل لحماية الحسابات"
            }

            val saleChangesWithInvoices = saleChanges.map { change ->
                val line = invoiceLinesById[change.sourceId]
                    ?: throw IllegalStateException("تعذر ربط حركة الصرف ببند فاتورة البيع")
                val invoice = invoicesById[line.invoiceId]
                    ?: throw IllegalStateException("تعذر العثور على فاتورة البيع المرتبطة بتصحيح الكلفة")
                require(invoice.kind == "SALE" && invoice.status == "POSTED") {
                    "حركة صرف المخزون لا ترتبط بفاتورة بيع مرحلة"
                }
                change to invoice
            }

            val movementsById = replayMovements.associateBy { it.id }
            val allLedgerEntries = database.ledgerDao().getAll()
            val stockLossCorrections = stockLossChanges.map { change ->
                val movement = movementsById[change.movementId]
                    ?: throw IllegalStateException("حركة خسارة المخزون غير موجودة لتصحيح كلفتها")
                require(movement.quantityDelta.toBigDecimal().signum() < 0) {
                    "لا يمكن تطبيق تسوية خسارة على حركة مخزون غير سالبة"
                }
                val ledgerSourceType = when (movement.sourceType.trim().uppercase()) {
                    "MANUAL_ADJUSTMENT" -> "MANUAL_STOCK_LOSS"
                    "STOCKTAKE_ADJUSTMENT" -> "STOCKTAKE_LOSS"
                    else -> throw IllegalStateException("نوع حركة الخسارة غير مدعوم")
                }
                val ledgerEntry = allLedgerEntries.singleOrNull {
                    it.sourceType == ledgerSourceType && it.sourceId == movement.sourceId
                } ?: throw IllegalStateException("سجل مصروف حركة المخزون غير موجود لتصحيح كلفته")
                require(ledgerEntry.currencyCode == change.currencyCode) {
                    "عملة مصروف حركة المخزون لا تطابق عملة الكلفة المعاد احتسابها"
                }
                require(ledgerEntry.amount.toBigDecimal().compareTo(change.oldExtendedCost.toBigDecimal()) == 0) {
                    "قيمة مصروف حركة المخزون لا تطابق آخر كلفة تاريخية فعّالة"
                }
                val sourceJournal = database.journalEntryDao().findBySource(
                    ledgerSourceType,
                    movement.sourceId
                ) ?: throw IllegalStateException("القيد الأصلي لخسارة المخزون غير موجود")
                require(sourceJournal.currencyCode == change.currencyCode) {
                    "عملة القيد الأصلي لخسارة المخزون لا تطابق عملة الكلفة المعاد احتسابها"
                }
                HistoricalStockLossCorrection(
                    change = change,
                    movement = movement,
                    ledgerEntry = ledgerEntry
                )
            }

            val changedCurrencies = (
                saleChangesWithInvoices.map { it.first.currencyCode } +
                    stockLossCorrections.map { it.change.currencyCode }
                ).toSet()
            require(changedCurrencies.size <= 1 &&
                (changedCurrencies.isEmpty() || changedCurrencies.single() == plan.currencyCode)
            ) {
                "إعادة الاحتساب تمس عمليات ضمن أكثر من عملة للمادة نفسها؛ أوقفنا الترحيل لمنع خلط العملات"
            }

            val currentProduct = database.productDao().findById(productId)
                ?: throw IllegalStateException("المادة غير موجودة بعد ترحيل الفاتورة")
            val newQuantity = plan.endingQuantity.toBigDecimal()
            val currentQuantity = currentProduct.stockQuantity.toBigDecimal()
            require(newQuantity.compareTo(currentQuantity) == 0) {
                "الرصيد الناتج عن إعادة الاحتساب لا يطابق رصيد المادة الحالي؛ أوقفنا الترحيل لحماية المخزون"
            }
            val newAverage = plan.endingAverageCost.toBigDecimal()
            val oldAverage = originalProduct.purchaseCost.toBigDecimal()
            val hasEndingCostChange = oldAverage.compareTo(newAverage) != 0
            if (hasEndingCostChange || plan.changes.isNotEmpty()) {
                val auditId = UUID.randomUUID().toString()
                val auditCurrency = plan.currencyCode ?: baseline.currencyCode
                database.stockCostRecalculationAuditDao().insertAll(
                    listOf(
                        StockCostRecalculationAuditEntity(
                            id = auditId,
                            productId = productId,
                            triggerType = triggerType,
                            triggerSourceId = triggerSourceId,
                            reason = reason,
                            createdAt = auditCreatedAt,
                            currencyCode = auditCurrency,
                            oldEndingQuantity = originalProduct.stockQuantity,
                            newEndingQuantity = plan.endingQuantity,
                            oldEndingAverageCost = originalProduct.purchaseCost,
                            newEndingAverageCost = plan.endingAverageCost,
                            affectedStockOutCount = plan.changes.size
                        )
                    )
                )
                if (plan.changes.isNotEmpty()) {
                    val invoiceIdsByMovement = saleChangesWithInvoices.associate {
                        it.first.movementId to it.second.id
                    }
                    database.stockCostRecalculationItemDao().insertAll(
                        plan.changes.map { change ->
                            StockCostRecalculationItemEntity(
                                id = UUID.randomUUID().toString(),
                                auditId = auditId,
                                movementId = change.movementId,
                                sourceType = change.sourceType,
                                sourceId = change.sourceId,
                                invoiceId = invoiceIdsByMovement[change.movementId].orEmpty(),
                                currencyCode = change.currencyCode,
                                quantity = change.quantity,
                                oldUnitCost = change.oldUnitCost,
                                newUnitCost = change.newUnitCost,
                                oldExtendedCost = change.oldExtendedCost,
                                newExtendedCost = change.newExtendedCost
                            )
                        }
                    )
                    saleChangesWithInvoices.groupBy { it.second.id }.forEach { (_, invoiceChanges) ->
                        postStockCostAdjustmentJournal(
                            auditId = auditId,
                            invoice = invoiceChanges.first().second,
                            changes = invoiceChanges.map { it.first },
                            createdAt = auditCreatedAt
                        )
                    }
                    stockLossCorrections.forEach { correction ->
                        PeriodClosingGuard.ensureOpen(
                            database,
                            correction.change.currencyCode,
                            correction.movement.occurredAt
                        )
                        database.ledgerDao().update(
                            correction.ledgerEntry.copy(amount = correction.change.newExtendedCost)
                        )
                        postStockLossCostAdjustmentJournal(
                            auditId = auditId,
                            correction = correction,
                            createdAt = auditCreatedAt
                        )
                    }
                }
            }

            database.productDao().update(
                currentProduct.copy(
                    stockQuantity = plan.endingQuantity,
                    purchaseCost = plan.endingAverageCost,
                    updatedAt = now
                )
            )
        }
    }

    private suspend fun postStockCostAdjustmentJournal(
        auditId: String,
        invoice: InvoiceEntity,
        changes: List<HistoricalStockCostChange>,
        createdAt: Long
    ) {
        val delta = changes.fold(BigDecimal.ZERO) { total, change ->
            total.add(change.newExtendedCost.toBigDecimal().subtract(change.oldExtendedCost.toBigDecimal()))
        }
        if (delta.compareTo(BigDecimal.ZERO) == 0) return

        val inventory = database.accountDao().findById("SYS-1200")
            ?: throw IllegalStateException("حساب المخزون غير موجود لتسوية الكلفة")
        val costOfGoodsSold = database.accountDao().findById("SYS-5000")
            ?: throw IllegalStateException("حساب تكلفة البضاعة المباعة غير موجود لتسوية الكلفة")
        require(inventory.isActive && inventory.accountType == "ASSET" && inventory.normalSide == "DEBIT") {
            "حساب المخزون غير صالح لترحيل تسوية الكلفة"
        }
        require(costOfGoodsSold.isActive && costOfGoodsSold.accountType == "EXPENSE" &&
            costOfGoodsSold.normalSide == "DEBIT"
        ) { "حساب تكلفة البضاعة المباعة غير صالح لترحيل تسوية الكلفة" }

        PeriodClosingGuard.ensureOpen(database, invoice.currencyCode, invoice.issuedAt)
        val amount = delta.abs().stripTrailingZeros().toPlainString()
        val lines = if (delta > BigDecimal.ZERO) {
            listOf(
                JournalLineDraft(
                    accountId = costOfGoodsSold.id,
                    debit = amount,
                    memo = "زيادة تكلفة البضاعة المباعة نتيجة إعادة الاحتساب التاريخي"
                ),
                JournalLineDraft(
                    accountId = inventory.id,
                    credit = amount,
                    memo = "تخفيض المخزون مقابل إعادة احتساب تكلفة فاتورة ${invoice.invoiceNumber}"
                )
            )
        } else {
            listOf(
                JournalLineDraft(
                    accountId = inventory.id,
                    debit = amount,
                    memo = "زيادة قيمة المخزون نتيجة تصحيح الكلفة التاريخية"
                ),
                JournalLineDraft(
                    accountId = costOfGoodsSold.id,
                    credit = amount,
                    memo = "تخفيض تكلفة البضاعة المباعة بعد إعادة الاحتساب"
                )
            )
        }
        val normalized = JournalEntryRules.normalize(
            currencyCode = invoice.currencyCode,
            exchangeRateSnapshot = invoice.exchangeRateSnapshot,
            lines = lines
        )
        val sourceId = "$auditId:${invoice.id}"
        require(database.journalEntryDao().findBySource("STOCK_COST_RECALCULATION", sourceId) == null) {
            "قيد تسوية الكلفة موجود مسبقاً لهذه العملية"
        }
        val entryId = UUID.randomUUID().toString()
        database.journalEntryDao().insert(
            JournalEntryEntity(
                id = entryId,
                journalNumber = "AUTO-COST-" + UUID.randomUUID().toString().replace("-", "").take(16),
                sourceType = "STOCK_COST_RECALCULATION",
                sourceId = sourceId,
                currencyCode = normalized.currencyCode,
                exchangeRateSnapshot = normalized.exchangeRateSnapshot,
                occurredAt = invoice.issuedAt,
                description = "تسوية كلفة المخزون التاريخية للفاتورة ${invoice.invoiceNumber}",
                createdAt = createdAt
            )
        )
        database.journalLineDao().insertAll(
            normalized.lines.mapIndexed { index, line ->
                JournalLineEntity(
                    id = UUID.randomUUID().toString(),
                    journalEntryId = entryId,
                    lineNumber = index + 1,
                    accountId = line.accountId,
                    debit = line.debit,
                    credit = line.credit,
                    memo = line.memo
                )
            }
        )
    }

    private suspend fun postStockLossCostAdjustmentJournal(
        auditId: String,
        correction: HistoricalStockLossCorrection,
        createdAt: Long
    ) {
        val change = correction.change
        val movement = correction.movement
        val difference = change.newExtendedCost.toBigDecimal()
            .subtract(change.oldExtendedCost.toBigDecimal())
        if (difference.compareTo(BigDecimal.ZERO) == 0) return

        val inventory = database.accountDao().findById("SYS-1200")
            ?: throw IllegalStateException("حساب المخزون غير موجود لتسوية خسارة المخزون")
        val operatingExpenses = database.accountDao().findById("SYS-7000")
            ?: throw IllegalStateException("حساب المصروفات التشغيلية غير موجود لتسوية خسارة المخزون")
        require(inventory.isActive && inventory.accountType == "ASSET" && inventory.normalSide == "DEBIT") {
            "حساب المخزون غير صالح لترحيل تسوية الكلفة"
        }
        require(
            operatingExpenses.isActive &&
                operatingExpenses.accountType == "EXPENSE" &&
                operatingExpenses.normalSide == "DEBIT"
        ) { "حساب المصروفات التشغيلية غير صالح لترحيل تسوية الكلفة" }

        val sourceType = when (movement.sourceType.trim().uppercase()) {
            "MANUAL_ADJUSTMENT" -> "MANUAL_STOCK_LOSS"
            "STOCKTAKE_ADJUSTMENT" -> "STOCKTAKE_LOSS"
            else -> throw IllegalArgumentException("نوع حركة خسارة المخزون غير مدعوم")
        }
        val originalJournal = database.journalEntryDao().findBySource(sourceType, movement.sourceId)
            ?: throw IllegalStateException("القيد الأصلي لخسارة المخزون غير موجود")
        val amount = difference.abs().stripTrailingZeros().toPlainString()
        val lines = if (difference > BigDecimal.ZERO) {
            listOf(
                JournalLineDraft(
                    accountId = operatingExpenses.id,
                    debit = amount,
                    memo = "زيادة خسارة المخزون بعد إعادة الاحتساب التاريخي"
                ),
                JournalLineDraft(
                    accountId = inventory.id,
                    credit = amount,
                    memo = "تخفيض قيمة المخزون مقابل تصحيح كلفة الخسارة"
                )
            )
        } else {
            listOf(
                JournalLineDraft(
                    accountId = inventory.id,
                    debit = amount,
                    memo = "زيادة قيمة المخزون بعد تصحيح كلفة الخسارة"
                ),
                JournalLineDraft(
                    accountId = operatingExpenses.id,
                    credit = amount,
                    memo = "تخفيض مصروف خسارة المخزون بعد إعادة الاحتساب"
                )
            )
        }
        val normalized = JournalEntryRules.normalize(
            currencyCode = change.currencyCode,
            exchangeRateSnapshot = originalJournal.exchangeRateSnapshot,
            lines = lines
        )
        val sourceId = "$auditId:${movement.id}"
        require(database.journalEntryDao().findBySource("STOCK_COST_RECALCULATION", sourceId) == null) {
            "قيد تسوية خسارة المخزون موجود مسبقاً لهذه العملية"
        }
        val entryId = UUID.randomUUID().toString()
        database.journalEntryDao().insert(
            JournalEntryEntity(
                id = entryId,
                journalNumber = "AUTO-COST-" + UUID.randomUUID().toString().replace("-", "").take(16),
                sourceType = "STOCK_COST_RECALCULATION",
                sourceId = sourceId,
                currencyCode = normalized.currencyCode,
                exchangeRateSnapshot = normalized.exchangeRateSnapshot,
                occurredAt = movement.occurredAt,
                description = "تسوية كلفة خسارة المخزون: ${movement.note.ifBlank { sourceType }}",
                createdAt = createdAt
            )
        )
        database.journalLineDao().insertAll(
            normalized.lines.mapIndexed { index, line ->
                JournalLineEntity(
                    id = UUID.randomUUID().toString(),
                    journalEntryId = entryId,
                    lineNumber = index + 1,
                    accountId = line.accountId,
                    debit = line.debit,
                    credit = line.credit,
                    memo = line.memo
                )
            }
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
