package com.goodgirl.accounting.data

import com.goodgirl.accounting.data.local.CategoryEntity
import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.data.local.PartyEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.data.local.ProductUnitEntity
import com.goodgirl.accounting.data.local.StockMovementEntity
import java.math.BigDecimal

/**
 * Checks referential and accounting integrity before a backup is allowed to
 * replace the current database. Room entities currently do not declare
 * foreign-key constraints, so these checks must happen before insertion.
 */
object BackupIntegrityValidator {
    private val currencies = setOf("SYP", "USD")
    private val ledgerTypes = setOf(
        "CASH_IN", "CASH_OUT", "RECEIVABLE", "PAYABLE",
        "RECEIVABLE_PAYMENT", "PAYABLE_PAYMENT", "INCOME", "EXPENSE"
    )

    fun validate(
        categories: List<CategoryEntity>,
        products: List<ProductEntity>,
        units: List<ProductUnitEntity>,
        parties: List<PartyEntity>,
        invoices: List<InvoiceEntity>,
        invoiceLines: List<InvoiceLineEntity>,
        movements: List<StockMovementEntity>,
        ledgerEntries: List<LedgerEntryEntity>
    ) {
        ensureUnique(categories.map { it.id }, "معرّفات التصنيفات")
        ensureUnique(products.map { it.id }, "معرّفات المنتجات")
        ensureUnique(units.map { it.id }, "معرّفات وحدات القياس")
        ensureUnique(parties.map { it.id }, "معرّفات الحسابات")
        ensureUnique(invoices.map { it.id }, "معرّفات الفواتير")
        ensureUnique(invoices.map { it.invoiceNumber }, "أرقام الفواتير")
        ensureUnique(invoiceLines.map { it.id }, "معرّفات بنود الفواتير")
        ensureUnique(movements.map { it.id }, "معرّفات حركات المخزون")
        ensureUnique(ledgerEntries.map { it.id }, "معرّفات القيود المالية")
        ensureUnique(categories.map { it.name }, "أسماء التصنيفات")

        val categoryIds = categories.map { it.id }.toSet()
        val productById = products.associateBy { it.id }
        val partyById = parties.associateBy { it.id }
        val invoiceById = invoices.associateBy { it.id }

        products.forEach { product ->
            require(product.name.isNotBlank()) { "يوجد منتج بلا اسم في النسخة الاحتياطية" }
            require(product.categoryId == null || product.categoryId in categoryIds) {
                "تصنيف المنتج ${product.name} غير موجود في النسخة الاحتياطية"
            }
            requireCurrency(product.priceCurrencyCode, "عملة المنتج ${product.name}")
            nonNegative(product.stockQuantity, "رصيد المنتج ${product.name}")
            nonNegative(product.purchaseCost, "كلفة المنتج ${product.name}")
            nonNegative(product.salePrice, "سعر المنتج ${product.name}")
            nonNegative(product.lowStockThreshold, "حد تنبيه المنتج ${product.name}")
        }

        ensureUnique(units.map { it.productId + "|" + it.unitName }, "أسماء وحدات المنتج")
        units.forEach { unit ->
            require(unit.productId in productById) {
                "وحدة القياس ${unit.unitName} مرتبطة بمنتج غير موجود"
            }
            require(unit.unitName.isNotBlank()) { "يوجد اسم وحدة قياس فارغ" }
            require(positive(unit.baseFactor, "معامل وحدة ${unit.unitName}") > BigDecimal.ZERO) {
                "معامل وحدة القياس يجب أن يكون أكبر من صفر"
            }
        }
        products.forEach { product ->
            val baseUnits = units.filter { it.productId == product.id && it.isBaseUnit }
            require(baseUnits.size == 1 && baseUnits.single().unitName == product.baseUnit) {
                "وحدة الأساس للمنتج ${product.name} غير مكتملة أو غير متطابقة"
            }
            require(decimal(baseUnits.single().baseFactor, "معامل وحدة الأساس").compareTo(BigDecimal.ONE) == 0) {
                "معامل وحدة الأساس للمنتج ${product.name} يجب أن يساوي 1"
            }
        }

        parties.forEach { party ->
            require(party.name.isNotBlank()) { "يوجد حساب بلا اسم في النسخة الاحتياطية" }
            require(party.kind == "CUSTOMER" || party.kind == "SUPPLIER") {
                "نوع الحساب ${party.name} غير مدعوم"
            }
            requireCurrency(party.currencyCode, "عملة الحساب ${party.name}")
            nonNegative(party.openingBalance, "الرصيد الافتتاحي للحساب ${party.name}")
            requireRate(party.openingExchangeRate, party.currencyCode, "سعر صرف الحساب ${party.name}")
        }

        invoices.forEach { invoice ->
            require(invoice.invoiceNumber.isNotBlank()) { "يوجد رقم فاتورة فارغ" }
            require(invoice.kind == "SALE" || invoice.kind == "PURCHASE") {
                "نوع الفاتورة ${invoice.invoiceNumber} غير مدعوم"
            }
            require(invoice.status in setOf("DRAFT", "POSTED", "VOID")) {
                "حالة الفاتورة ${invoice.invoiceNumber} غير مدعومة"
            }
            requireCurrency(invoice.currencyCode, "عملة الفاتورة ${invoice.invoiceNumber}")
            requireRate(invoice.exchangeRateSnapshot, invoice.currencyCode, "سعر صرف الفاتورة ${invoice.invoiceNumber}")
            val subtotal = nonNegative(invoice.subtotal, "مجموع الفاتورة ${invoice.invoiceNumber}")
            val discount = nonNegative(invoice.discount, "خصم الفاتورة ${invoice.invoiceNumber}")
            val total = nonNegative(invoice.total, "إجمالي الفاتورة ${invoice.invoiceNumber}")
            val paid = nonNegative(invoice.paid, "المدفوع من الفاتورة ${invoice.invoiceNumber}")
            val remaining = nonNegative(invoice.remaining, "المتبقي من الفاتورة ${invoice.invoiceNumber}")
            require(discount <= subtotal && subtotal.subtract(discount).compareTo(total) == 0) {
                "إجمالي الفاتورة ${invoice.invoiceNumber} لا يطابق المجموع والخصم"
            }
            require(paid.add(remaining).compareTo(total) == 0) {
                "المدفوع والمتبقي في الفاتورة ${invoice.invoiceNumber} لا يطابقان إجماليها"
            }
            if (invoice.partyId != null) {
                val party = partyById[invoice.partyId]
                    ?: throw IllegalArgumentException("حساب الفاتورة ${invoice.invoiceNumber} غير موجود")
                val requiredKind = if (invoice.kind == "SALE") "CUSTOMER" else "SUPPLIER"
                require(party.kind == requiredKind) {
                    "نوع الحساب المرتبط بالفاتورة ${invoice.invoiceNumber} غير متوافق"
                }
            }
        }

        invoiceLines.forEach { line ->
            val invoice = invoiceById[line.invoiceId]
                ?: throw IllegalArgumentException("يوجد بند فاتورة بلا فاتورة مرتبطة")
            require(line.productId == null || line.productId in productById) {
                "المادة المرتبطة ببند الفاتورة ${line.productNameSnapshot} غير موجودة"
            }
            val quantity = positive(line.quantity, "كمية ${line.productNameSnapshot}")
            val unitPrice = nonNegative(line.unitPriceSnapshot, "سعر ${line.productNameSnapshot}")
            val discount = nonNegative(line.discount, "خصم ${line.productNameSnapshot}")
            val lineTotal = nonNegative(line.lineTotal, "إجمالي ${line.productNameSnapshot}")
            val unitCost = nonNegative(line.unitCostSnapshot, "كلفة ${line.productNameSnapshot}")
            val factor = positive(line.unitFactorSnapshot, "معامل وحدة ${line.productNameSnapshot}")
            val baseQuantity = positive(line.baseQuantity, "كمية الأساس ${line.productNameSnapshot}")
            val gross = quantity.multiply(unitPrice)
            require(discount <= gross && gross.subtract(discount).compareTo(lineTotal) == 0) {
                "قيمة بند الفاتورة ${line.productNameSnapshot} غير متطابقة"
            }
            require(quantity.multiply(factor).compareTo(baseQuantity) == 0) {
                "كمية الأساس لبند ${line.productNameSnapshot} غير متطابقة"
            }
            require(invoice.currencyCode in currencies) {
                "عملة بند الفاتورة ${line.productNameSnapshot} غير صالحة"
            }
            require(unitCost >= BigDecimal.ZERO) { "كلفة بند الفاتورة غير صالحة" }
        }

        movements.forEach { movement ->
            require(movement.productId in productById) { "حركة مخزون مرتبطة بمنتج غير موجود" }
            require(decimal(movement.quantityDelta, "كمية حركة المخزون") != BigDecimal.ZERO) {
                "يوجد سجل حركة مخزون بكمية صفرية"
            }
            nonNegative(movement.unitCostSnapshot, "كلفة حركة المخزون")
            requireCurrency(movement.currencyCode, "عملة حركة المخزون")
        }

        ledgerEntries.forEach { entry ->
            require(entry.partyId == null || entry.partyId in partyById) {
                "يوجد قيد مالي مرتبط بحساب غير موجود"
            }
            require(entry.entryType in ledgerTypes) { "نوع قيد مالي غير معروف: ${entry.entryType}" }
            require(positive(entry.amount, "مبلغ القيد المالي") > BigDecimal.ZERO) {
                "مبلغ القيد المالي يجب أن يكون أكبر من صفر"
            }
            requireCurrency(entry.currencyCode, "عملة القيد المالي")
            requireRate(entry.exchangeRateSnapshot, entry.currencyCode, "سعر صرف القيد المالي")
        }
    }

    private fun ensureUnique(values: List<String>, label: String) {
        require(values.all { it.isNotBlank() } && values.size == values.toSet().size) {
            "$label مفقودة أو مكررة في النسخة الاحتياطية"
        }
    }

    private fun requireCurrency(currency: String, label: String) {
        require(currency in currencies) { "$label غير مدعومة" }
    }

    private fun requireRate(rate: String, currency: String, label: String) {
        val value = positive(rate, label)
        if (currency == "SYP") {
            require(value.compareTo(BigDecimal.ONE) == 0) { "$label يجب أن يساوي 1 لليرة السورية" }
        }
    }

    private fun positive(value: String, label: String): BigDecimal {
        val parsed = decimal(value, label)
        require(parsed > BigDecimal.ZERO) { "$label يجب أن يكون أكبر من صفر" }
        return parsed
    }

    private fun nonNegative(value: String, label: String): BigDecimal {
        val parsed = decimal(value, label)
        require(parsed >= BigDecimal.ZERO) { "$label لا يمكن أن يكون سالباً" }
        return parsed
    }

    private fun decimal(value: String, label: String): BigDecimal =
        try {
            BigDecimal(value)
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException("$label غير صالح في النسخة الاحتياطية", error)
        }
}
