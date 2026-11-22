package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.data.local.StockMovementEntity
import com.goodgirl.accounting.domain.InvoiceCalculator
import com.goodgirl.accounting.domain.InvoicePostingRules
import com.goodgirl.accounting.domain.ProductInputRules
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.UUID

data class InvoiceLineDraft(
    val productId: String,
    val quantity: String,
    val unitPrice: String,
    val discount: String = "0"
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

        database.withTransaction {
            if (partyId != null) {
                val party = database.partyDao().findById(partyId)
                    ?: throw IllegalArgumentException("العميل أو المورد غير موجود")
                val requiredKind = if (cleanKind == "SALE") "CUSTOMER" else "SUPPLIER"
                require(party.kind == requiredKind) { "نوع الحساب لا يتوافق مع نوع الفاتورة" }
            }

            lines.forEach { draft ->
                val product = database.productDao().findById(draft.productId)
                    ?: throw IllegalArgumentException("إحدى المواد غير موجودة")
                require(product.isActive) { "لا يمكن استخدام مادة مؤرشفة في فاتورة جديدة" }
                require(product.priceCurrencyCode == cleanCurrency) {
                    "عملة المنتج " + product.name + " هي " + product.priceCurrencyCode + "، ولا تطابق عملة الفاتورة " + cleanCurrency
                }

                val quantityText = InvoicePostingRules.normalizePositiveQuantity(draft.quantity)
                val quantity = quantityText.toBigDecimal()
                val unitPrice = ProductInputRules.normalizeNonNegativeDecimal(draft.unitPrice, "سعر الوحدة").toBigDecimal()
                val lineDiscount = ProductInputRules.normalizeNonNegativeDecimal(draft.discount, "خصم السطر").toBigDecimal()
                val grossLine = quantity.multiply(unitPrice)
                require(lineDiscount <= grossLine) { "خصم السطر أكبر من قيمة السطر للمنتج: " + product.name }
                val lineTotal = grossLine.subtract(lineDiscount)
                val movementId = UUID.randomUUID().toString()
                val lineId = UUID.randomUUID().toString()
                val currentStock = ProductInputRules.normalizeNonNegativeDecimal(product.stockQuantity, "رصيد المنتج").toBigDecimal()
                val currentCost = ProductInputRules.normalizeNonNegativeDecimal(product.purchaseCost, "كلفة المنتج").toBigDecimal()
                val unitCostSnapshot: String

                if (cleanKind == "SALE") {
                    require(currentStock >= quantity) { "الكمية المتاحة غير كافية للمنتج: " + product.name }
                    val newStock = currentStock.subtract(quantity).stripTrailingZeros().toPlainString()
                    database.productDao().updateStock(product.id, newStock, now)
                    unitCostSnapshot = currentCost.stripTrailingZeros().toPlainString()

                    database.stockMovementDao().insert(
                        StockMovementEntity(
                            id = movementId,
                            productId = product.id,
                            quantityDelta = quantity.negate().stripTrailingZeros().toPlainString(),
                            unitCostSnapshot = unitCostSnapshot,
                            currencyCode = cleanCurrency,
                            sourceType = "SALE_INVOICE",
                            sourceId = lineId,
                            occurredAt = issuedAt,
                            note = "صرف مخزون من فاتورة بيع " + cleanNumber
                        )
                    )
                } else {
                    val newStock = currentStock.add(quantity).stripTrailingZeros().toPlainString()
                    val newAverageCost = InvoicePostingRules.calculateWeightedAverageCost(
                        currentQuantity = product.stockQuantity,
                        currentUnitCost = product.purchaseCost,
                        incomingQuantity = quantityText,
                        incomingUnitCost = unitPrice.stripTrailingZeros().toPlainString()
                    )
                    database.productDao().update(
                        product.copy(
                            stockQuantity = newStock,
                            purchaseCost = newAverageCost,
                            updatedAt = now
                        )
                    )
                    unitCostSnapshot = unitPrice.stripTrailingZeros().toPlainString()

                    database.stockMovementDao().insert(
                        StockMovementEntity(
                            id = movementId,
                            productId = product.id,
                            quantityDelta = quantity.stripTrailingZeros().toPlainString(),
                            unitCostSnapshot = unitCostSnapshot,
                            currencyCode = cleanCurrency,
                            sourceType = "PURCHASE_INVOICE",
                            sourceId = lineId,
                            occurredAt = issuedAt,
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
                    unitSnapshot = product.baseUnit,
                    quantity = quantityText,
                    unitCostSnapshot = unitCostSnapshot,
                    unitPriceSnapshot = unitPrice.stripTrailingZeros().toPlainString(),
                    discount = lineDiscount.stripTrailingZeros().toPlainString(),
                    lineTotal = lineTotal.stripTrailingZeros().toPlainString()
                )
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
}
