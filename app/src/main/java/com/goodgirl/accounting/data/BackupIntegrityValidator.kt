package com.goodgirl.accounting.data

import com.goodgirl.accounting.data.local.CategoryEntity
import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.data.local.PartyEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.data.local.ProductBarcodeEntity
import com.goodgirl.accounting.data.local.ProductUnitEntity
import com.goodgirl.accounting.data.local.StockMovementEntity
import com.goodgirl.accounting.data.local.StockCostBaselineEntity
import com.goodgirl.accounting.data.local.StockCostRecalculationAuditEntity
import com.goodgirl.accounting.data.local.StockCostRecalculationItemEntity
import com.goodgirl.accounting.data.local.DailySalesDraftEntity
import com.goodgirl.accounting.data.local.DailySalesDraftLineEntity
import com.goodgirl.accounting.data.local.DailySalesDraftExpenseEntity
import com.goodgirl.accounting.data.local.ExchangeRateHistoryEntity
import com.goodgirl.accounting.data.local.ExchangeRateAuditEntity
import com.goodgirl.accounting.data.local.ExchangeRateCorrectionItemEntity
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
        productBarcodes: List<ProductBarcodeEntity> = emptyList(),
        units: List<ProductUnitEntity>,
        parties: List<PartyEntity>,
        invoices: List<InvoiceEntity>,
        invoiceLines: List<InvoiceLineEntity>,
        movements: List<StockMovementEntity>,
        ledgerEntries: List<LedgerEntryEntity>,
        accounts: List<AccountEntity> = emptyList(),
        journalEntries: List<JournalEntryEntity> = emptyList(),
        journalLines: List<JournalLineEntity> = emptyList(),
        exchangeRateHistory: List<ExchangeRateHistoryEntity> = emptyList(),
        exchangeRateAudits: List<ExchangeRateAuditEntity> = emptyList(),
        exchangeRateCorrectionItems: List<ExchangeRateCorrectionItemEntity> = emptyList(),
        stockCostBaselines: List<StockCostBaselineEntity> = emptyList(),
        stockCostRecalculationAudits: List<StockCostRecalculationAuditEntity> = emptyList(),
        stockCostRecalculationItems: List<StockCostRecalculationItemEntity> = emptyList(),
        dailySalesDraft: DailySalesDraftEntity? = null,
        dailySalesDraftLines: List<DailySalesDraftLineEntity> = emptyList(),
        dailySalesDraftExpenses: List<DailySalesDraftExpenseEntity> = emptyList()
    ) {
        ensureUnique(categories.map { it.id }, "معرّفات التصنيفات")
        ensureUnique(products.map { it.id }, "معرّفات المنتجات")
        ensureUnique(productBarcodes.map { it.id }, "معرّفات الباركود")
        ensureUnique(productBarcodes.map { it.barcodeValue }, "أرقام الباركود")
        ensureUnique(units.map { it.id }, "معرّفات وحدات القياس")
        ensureUnique(parties.map { it.id }, "معرّفات الحسابات")
        ensureUnique(invoices.map { it.id }, "معرّفات الفواتير")
        ensureUnique(invoices.map { it.invoiceNumber }, "أرقام الفواتير")
        ensureUnique(invoiceLines.map { it.id }, "معرّفات بنود الفواتير")
        ensureUnique(movements.map { it.id }, "معرّفات حركات المخزون")
        ensureUnique(stockCostBaselines.map { it.productId }, "نقاط بداية كلفة المخزون")
        ensureUnique(stockCostRecalculationAudits.map { it.id }, "معرّفات تدقيق إعادة احتساب الكلفة")
        ensureUnique(stockCostRecalculationItems.map { it.id }, "معرّفات تفاصيل إعادة احتساب الكلفة")
        ensureUnique(stockCostRecalculationItems.map { it.auditId + "|" + it.movementId }, "حركات تفاصيل إعادة احتساب الكلفة")
        ensureUnique(ledgerEntries.map { it.id }, "معرّفات القيود المالية")
        ensureUnique(accounts.map { it.id }, "معرّفات دليل الحسابات")
        ensureUnique(accounts.map { it.code }, "رموز دليل الحسابات")
        ensureUnique(journalEntries.map { it.id }, "معرّفات قيود اليومية")
        ensureUnique(journalEntries.map { it.journalNumber }, "أرقام قيود اليومية")
        ensureUnique(journalEntries.map { it.sourceType + "|" + it.sourceId }, "مصادر قيود اليومية")
        ensureUnique(journalLines.map { it.id }, "معرّفات أسطر قيود اليومية")
        ensureUnique(exchangeRateHistory.map { it.id }, "معرّفات سجل أسعار الصرف")
        ensureUnique(exchangeRateAudits.map { it.id }, "معرّفات سجل تدقيق أسعار الصرف")
        ensureUnique(exchangeRateCorrectionItems.map { it.id }, "معرّفات تفاصيل تصحيحات أسعار الصرف")
        ensureUnique(exchangeRateCorrectionItems.map { it.auditId + "|" + it.recordType + "|" + it.recordId }, "سجلات العمليات المصححة")
        ensureUnique(dailySalesDraftLines.map { it.id }, "معرّفات سطور مسودة المبيعات")
        ensureUnique(dailySalesDraftLines.map { it.draftId + "|" + it.lineOrder }, "ترتيب سطور مسودة المبيعات")
        ensureUnique(dailySalesDraftExpenses.map { it.id }, "معرّفات مصروفات مسودة المبيعات")
        require(dailySalesDraft == null || dailySalesDraft.id == "active") {
            "معرّف مسودة المبيعات غير صالح في النسخة الاحتياطية"
        }
        require(dailySalesDraftLines.isEmpty() || dailySalesDraft != null) {
            "تحتوي النسخة الاحتياطية على سطور مسودة دون رأس للمسودة"
        }
        require(dailySalesDraftLines.size <= 100) {
            "تجاوزت مسودة المبيعات الحد الأقصى لعدد السطور"
        }
        require(dailySalesDraftExpenses.isEmpty() || dailySalesDraft != null) {
            "تحتوي النسخة الاحتياطية على مصروفات مسودة دون رأس للمسودة"
        }
        require(dailySalesDraftExpenses.size <= 500) {
            "تجاوزت مسودة المبيعات الحد الأقصى لعدد المصروفات"
        }
        dailySalesDraftLines.forEachIndexed { index, line ->
            require(line.draftId == dailySalesDraft?.id && line.lineOrder == index) {
                "ترتيب أو ارتباط سطور مسودة المبيعات غير صالح"
            }
            require(line.productNameSnapshot.isNotBlank() && line.unitName.isNotBlank()) {
                "يوجد سطر مسودة دون اسم مادة أو وحدة قياس"
            }
            requireCurrency(line.currencyCode, "عملة سطر مسودة المبيعات")
            positive(line.quantity, "كمية سطر مسودة المبيعات")
            positive(line.unitFactor, "عامل وحدة سطر مسودة المبيعات")
            nonNegative(line.unitPrice, "سعر سطر مسودة المبيعات")
            line.purchaseCostSnapshot?.let {
                nonNegative(it, "كلفة الشراء المحفوظة في سطر المسودة")
            }
            nonNegative(line.discount, "خصم سطر مسودة المبيعات")
            nonNegative(line.paidAmount, "المبلغ المقبوض في سطر المسودة")
            requireRate(line.exchangeRate, line.currencyCode, "سعر الصرف في سطر المسودة")
            val gross = BigDecimal(line.quantity).multiply(BigDecimal(line.unitPrice))
            require(BigDecimal(line.discount) <= gross) { "خصم سطر مسودة المبيعات أكبر من إجماليه" }
            require(BigDecimal(line.paidAmount) <= gross.subtract(BigDecimal(line.discount))) {
                "المبلغ المقبوض في سطر المسودة أكبر من قيمته"
            }
        }
        val draftLineIds = dailySalesDraftLines.map { it.id }.toSet()
        dailySalesDraftExpenses.forEach { expense ->
            require(expense.draftId == dailySalesDraft?.id && expense.lineId in draftLineIds) {
                "يوجد مصروف مسودة غير مرتبط بسطر محفوظ"
            }
            require(expense.description.isNotBlank() && expense.description.length <= 180) {
                "بيان مصروف مسودة المبيعات غير صالح"
            }
            positive(expense.amount, "مبلغ مصروف مسودة المبيعات")
            requireCurrency(expense.currencyCode, "عملة مصروف مسودة المبيعات")
            requireRate(
                expense.exchangeRateSnapshot,
                expense.currencyCode,
                "سعر صرف مصروف مسودة المبيعات"
            )
        }
        ensureUnique(journalLines.map { it.journalEntryId + "|" + it.lineNumber }, "ترتيب أسطر قيود اليومية")
        ensureUnique(categories.map { it.name }, "أسماء التصنيفات")

        val categoryIds = categories.map { it.id }.toSet()
        val accountById = accounts.associateBy { it.id }
        val journalById = journalEntries.associateBy { it.id }
        val rateHistoryById = exchangeRateHistory.associateBy { it.id }
        val rateAuditsById = exchangeRateAudits.associateBy { it.id }
        val productById = products.associateBy { it.id }
        val partyById = parties.associateBy { it.id }
        val invoiceById = invoices.associateBy { it.id }
        val movementById = movements.associateBy { it.id }
        val stockCostAuditById = stockCostRecalculationAudits.associateBy { it.id }
        val ledgerBySource = ledgerEntries.associateBy { it.sourceType + "|" + it.sourceId }
        val journalBySource = journalEntries.associateBy { it.sourceType + "|" + it.sourceId }
        val journalLinesByEntry = journalLines.groupBy { it.journalEntryId }
        val latestCostItemByMovement = stockCostRecalculationItems
            .groupBy { it.movementId }
            .mapValues { (_, items) ->
                items.maxWithOrNull(
                    compareBy<StockCostRecalculationItemEntity> {
                        stockCostAuditById[it.auditId]?.createdAt ?: Long.MIN_VALUE
                    }.thenBy { it.auditId }
                )!!
            }

        productBarcodes.forEach { barcode ->
            require(barcode.productId in productById) {
                "باركود مرتبط بمنتج غير موجود"
            }
            require(barcode.barcodeValue.isNotBlank()) { "يوجد رقم باركود فارغ" }
            require(barcode.format in setOf("EAN_13", "EAN_8", "UPC_A", "CODE_128")) {
                "صيغة باركود غير مدعومة"
            }
            require(barcode.origin in setOf("ORIGINAL", "INTERNAL")) {
                "مصدر الباركود غير مدعوم"
            }
        }

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

        val allowedAccountTypes = setOf("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE")
        accounts.forEach { account ->
            require(account.code.isNotBlank() && account.name.isNotBlank()) {
                "يوجد حساب بلا رمز أو اسم في دليل الحسابات"
            }
            require(account.accountType in allowedAccountTypes) {
                "نوع الحساب ${account.name} غير مدعوم"
            }
            require(account.normalSide == "DEBIT" || account.normalSide == "CREDIT") {
                "الطبيعة المحاسبية للحساب ${account.name} غير صالحة"
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

        val linesByInvoice = invoiceLines.groupBy { it.invoiceId }
        invoices.forEach { invoice ->
            val lines = linesByInvoice[invoice.id].orEmpty()
            if (invoice.status == "POSTED") {
                require(lines.isNotEmpty()) {
                    "الفاتورة المرحلة ${invoice.invoiceNumber} لا تحتوي على بنود"
                }
            }
            if (lines.isNotEmpty()) {
                val grossLines = lines.fold(BigDecimal.ZERO) { total, line ->
                    total.add(line.quantity.toBigDecimal().multiply(line.unitPriceSnapshot.toBigDecimal()))
                }
                val lineDiscounts = lines.fold(BigDecimal.ZERO) { total, line ->
                    total.add(line.discount.toBigDecimal())
                }
                val netLines = lines.fold(BigDecimal.ZERO) { total, line ->
                    total.add(line.lineTotal.toBigDecimal())
                }
                require(grossLines.compareTo(invoice.subtotal.toBigDecimal()) == 0) {
                    "مجموع بنود الفاتورة ${invoice.invoiceNumber} لا يطابق مجموعها قبل الخصومات"
                }
                require(lineDiscounts <= invoice.discount.toBigDecimal()) {
                    "خصومات البنود في الفاتورة ${invoice.invoiceNumber} تتجاوز خصم الفاتورة"
                }
                val documentDiscount = invoice.discount.toBigDecimal().subtract(lineDiscounts)
                require(documentDiscount <= netLines &&
                    netLines.subtract(documentDiscount).compareTo(invoice.total.toBigDecimal()) == 0
                ) {
                    "مجموع بنود وخصومات الفاتورة ${invoice.invoiceNumber} لا يطابق إجماليها"
                }
            }
            if (invoice.remaining.toBigDecimal() > BigDecimal.ZERO) {
                require(invoice.partyId != null) {
                    "الفاتورة الآجلة ${invoice.invoiceNumber} لا ترتبط بحساب عميل أو مورد"
                }
            }
        }

        movements.forEach { movement ->
            require(movement.productId in productById) { "حركة مخزون مرتبطة بمنتج غير موجود" }
            val movementType = movement.sourceType.trim().uppercase()
            val movementDelta = decimal(movement.quantityDelta, "كمية حركة المخزون")
            if (movementType == "PRODUCT_CURRENCY_CORRECTION") {
                require(movementDelta.compareTo(BigDecimal.ZERO) == 0) {
                    "حركة تصحيح العملة يجب أن تكون بلا تغيير في الكمية"
                }
            } else {
                require(movementDelta.compareTo(BigDecimal.ZERO) != 0) {
                    "يوجد سجل حركة مخزون بكمية صفرية"
                }
            }
            nonNegative(movement.unitCostSnapshot, "كلفة حركة المخزون")
            requireCurrency(movement.currencyCode, "عملة حركة المخزون")
        }

        if (stockCostBaselines.isNotEmpty()) {
            require(stockCostBaselines.size == products.size &&
                stockCostBaselines.map { it.productId }.toSet() == productById.keys
            ) { "نقاط بداية كلفة المخزون يجب أن تغطي كل منتج مرة واحدة" }
            stockCostBaselines.forEach { baseline ->
                require(baseline.productId in productById) {
                    "نقطة بداية كلفة مرتبطة بمنتج غير موجود"
                }
                nonNegative(baseline.quantity, "كمية نقطة بداية المخزون")
                nonNegative(baseline.averageCost, "متوسط كلفة نقطة بداية المخزون")
                requireCurrency(baseline.currencyCode, "عملة نقطة بداية المخزون")
                require(baseline.effectiveAt > 0L && baseline.recordedAt > 0L) {
                    "تاريخ نقطة بداية المخزون غير صالح"
                }
                require(baseline.source in setOf(
                    "ACCOUNTING_RECONSTRUCTED",
                    "PRODUCT_ZERO_OPENING",
                    "CURRENT_SNAPSHOT_FALLBACK",
                    "LEGACY_BACKUP_FALLBACK"
                )) { "مصدر نقطة بداية كلفة المخزون غير مدعوم" }
            }
        }

        stockCostRecalculationAudits.forEach { audit ->
            require(audit.productId in productById) { "سجل تدقيق كلفة مرتبط بمنتج غير موجود" }
            require(audit.triggerType.isNotBlank() && audit.triggerSourceId.isNotBlank() && audit.reason.isNotBlank()) {
                "بيانات سبب إعادة احتساب كلفة المخزون غير مكتملة"
            }
            require(audit.createdAt > 0L) { "تاريخ سجل تدقيق كلفة المخزون غير صالح" }
            requireCurrency(audit.currencyCode, "عملة سجل تدقيق كلفة المخزون")
            nonNegative(audit.oldEndingQuantity, "الكمية السابقة في سجل تدقيق الكلفة")
            nonNegative(audit.newEndingQuantity, "الكمية الجديدة في سجل تدقيق الكلفة")
            nonNegative(audit.oldEndingAverageCost, "متوسط الكلفة السابق في سجل التدقيق")
            nonNegative(audit.newEndingAverageCost, "متوسط الكلفة الجديد في سجل التدقيق")
            require(audit.affectedStockOutCount >= 0) { "عدد حركات إخراج المخزون المتأثرة غير صالح" }
        }

        val costItemsByAudit = stockCostRecalculationItems.groupBy { it.auditId }
        stockCostRecalculationItems.forEach { item ->
            val audit = stockCostAuditById[item.auditId]
                ?: throw IllegalArgumentException("تفصيل إعادة احتساب كلفة مرتبط بسجل تدقيق غير موجود")
            val movement = movementById[item.movementId]
                ?: throw IllegalArgumentException("تفصيل إعادة احتساب كلفة مرتبط بحركة مخزون غير موجود")
            require(movement.productId == audit.productId) {
                "حركة المخزون في سجل التدقيق لا تخص المنتج نفسه"
            }
            val movementQuantity = decimal(movement.quantityDelta, "كمية حركة المخزون في تدقيق الكلفة")
            require(movementQuantity < BigDecimal.ZERO) {
                "تفصيل إعادة احتساب الكلفة يجب أن يرتبط بحركة إخراج مخزون"
            }
            require(positive(item.quantity, "الكمية المتأثرة في تدقيق الكلفة")
                .compareTo(movementQuantity.abs()) == 0
            ) { "الكمية في تفصيل تدقيق الكلفة لا تطابق حركة المخزون" }
            require(item.sourceType == movement.sourceType && item.sourceId == movement.sourceId) {
                "مصدر تفصيل تدقيق الكلفة لا يطابق حركة المخزون"
            }
            require(item.currencyCode == audit.currencyCode && item.currencyCode == movement.currencyCode) {
                "عملة تفصيل تدقيق الكلفة لا تطابق سجل التدقيق وحركة المخزون"
            }
            nonNegative(item.oldUnitCost, "الكلفة السابقة في تفصيل التدقيق")
            nonNegative(item.newUnitCost, "الكلفة الجديدة في تفصيل التدقيق")
            nonNegative(item.oldExtendedCost, "القيمة السابقة في تفصيل التدقيق")
            nonNegative(item.newExtendedCost, "القيمة الجديدة في تفصيل التدقيق")
            val oldExtended = decimal(item.oldExtendedCost, "القيمة السابقة في تفصيل التدقيق")
            val newExtended = decimal(item.newExtendedCost, "القيمة الجديدة في تفصيل التدقيق")
            require(
                decimal(item.oldUnitCost, "الكلفة السابقة").compareTo(decimal(item.newUnitCost, "الكلفة الجديدة")) != 0 ||
                    oldExtended.compareTo(newExtended) != 0
            ) { "تفصيل تدقيق الكلفة لا يتضمن أي تغيير فعلي" }

            when (movement.sourceType.trim().uppercase()) {
                "SALE_INVOICE" -> {
                    val invoice = invoiceById[item.invoiceId]
                        ?: throw IllegalArgumentException("فاتورة تفصيل تدقيق الكلفة غير موجودة")
                    require(invoice.kind == "SALE") {
                        "تفصيل تدقيق كلفة المخزون يجب أن يرتبط بفاتورة مبيعات"
                    }
                }
                "MANUAL_ADJUSTMENT", "STOCKTAKE_ADJUSTMENT" -> {
                    require(item.invoiceId.isBlank()) {
                        "خسارة المخزون اليدوية أو الجردية لا يجوز ربطها بفاتورة مبيعات"
                    }
                    val expectedLedgerSource = if (movement.sourceType == "MANUAL_ADJUSTMENT") {
                        "MANUAL_STOCK_LOSS"
                    } else {
                        "STOCKTAKE_LOSS"
                    }
                    val sourceLedger = ledgerBySource[expectedLedgerSource + "|" + movement.sourceId]
                        ?: throw IllegalArgumentException("مصروف خسارة المخزون غير موجود في النسخة الاحتياطية")
                    require(sourceLedger.entryType == "EXPENSE" &&
                        sourceLedger.currencyCode == item.currencyCode
                    ) { "مصروف خسارة المخزون لا يطابق نوع الحركة أو عملتها" }
                    val latestItem = latestCostItemByMovement[movement.id]
                    if (latestItem?.id == item.id) {
                        require(decimal(sourceLedger.amount, "مصروف خسارة المخزون")
                            .compareTo(newExtended) == 0
                        ) { "مصروف خسارة المخزون لا يطابق أحدث كلفة تاريخية مدققة" }
                    }

                    val sourceJournal = journalBySource[expectedLedgerSource + "|" + movement.sourceId]
                        ?: throw IllegalArgumentException("القيد الأصلي لخسارة المخزون غير موجود")
                    require(sourceJournal.currencyCode == item.currencyCode &&
                        sourceJournal.occurredAt == movement.occurredAt
                    ) { "القيد الأصلي لخسارة المخزون لا يطابق العملة أو تاريخ الحركة" }

                    val adjustmentSourceId = audit.id + ":" + movement.id
                    val adjustmentJournal = journalBySource["STOCK_COST_RECALCULATION|" + adjustmentSourceId]
                        ?: throw IllegalArgumentException("قيد تسوية خسارة المخزون غير موجود")
                    require(adjustmentJournal.currencyCode == item.currencyCode &&
                        adjustmentJournal.occurredAt == movement.occurredAt
                    ) { "قيد تسوية خسارة المخزون لا يطابق العملة أو تاريخ الحركة" }
                    val adjustmentLines = journalLinesByEntry[adjustmentJournal.id].orEmpty()
                    require(adjustmentLines.size == 2) {
                        "قيد تسوية خسارة المخزون يجب أن يحتوي على سطرين"
                    }
                    val debitLine = adjustmentLines.single { it.debit.toBigDecimal() > BigDecimal.ZERO }
                    val creditLine = adjustmentLines.single { it.credit.toBigDecimal() > BigDecimal.ZERO }
                    val difference = newExtended.subtract(oldExtended)
                    val expectedDebitCode = if (difference > BigDecimal.ZERO) "7000" else "1200"
                    val expectedCreditCode = if (difference > BigDecimal.ZERO) "1200" else "7000"
                    val expectedAmount = difference.abs()
                    require(accountById[debitLine.accountId]?.code == expectedDebitCode &&
                        accountById[creditLine.accountId]?.code == expectedCreditCode &&
                        decimal(debitLine.debit, "مدين تسوية خسارة المخزون").compareTo(expectedAmount) == 0 &&
                        decimal(creditLine.credit, "دائن تسوية خسارة المخزون").compareTo(expectedAmount) == 0
                    ) { "قيد تسوية خسارة المخزون لا يعكس فرق الكلفة الصحيح" }
                }
                else -> throw IllegalArgumentException("نوع حركة إخراج المخزون غير مدعوم في تدقيق الكلفة")
            }
        }
        stockCostRecalculationAudits.forEach { audit ->
            require(audit.affectedStockOutCount == costItemsByAudit[audit.id].orEmpty().size) {
                "عدد حركات إخراج المخزون في سجل التدقيق لا يطابق تفاصيله"
            }
        }

        // The product's stored balance must equal the sum of its complete movement history.
        // This catches backups where the current stock was edited independently of the audit trail.
        val movementBalanceByProduct = movements.groupBy { it.productId }.mapValues { (_, productMovements) ->
            productMovements.fold(BigDecimal.ZERO) { balance, movement ->
                balance.add(decimal(movement.quantityDelta, "كمية حركة المخزون"))
            }
        }
        products.forEach { product ->
            val movementBalance = movementBalanceByProduct[product.id] ?: BigDecimal.ZERO
            require(movementBalance.compareTo(decimal(product.stockQuantity, "رصيد المنتج ${product.name}")) == 0) {
                "رصيد المنتج ${product.name} لا يطابق مجموع حركات المخزون"
            }
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
        journalLines.forEach { line ->
            require(line.journalEntryId in journalById) { "سطر قيد يومية مرتبط بقيد غير موجود" }
            require(line.accountId in accountById) { "سطر قيد يومية مرتبط بحساب غير موجود" }
            require(line.lineNumber > 0) { "ترتيب سطر قيد اليومية غير صالح" }
            val debit = nonNegative(line.debit, "المدين في قيد اليومية")
            val credit = nonNegative(line.credit, "الدائن في قيد اليومية")
            require((debit > BigDecimal.ZERO) xor (credit > BigDecimal.ZERO)) {
                "يجب أن يحتوي سطر قيد اليومية على مدين أو دائن فقط"
            }
        }

        exchangeRateHistory.forEach { rate ->
            require(rate.currencyPair == "USD/SYP") { "زوج عملة سجل سعر الصرف غير مدعوم" }
            positive(rate.rate, "سعر الصرف التاريخي")
            require(rate.effectiveAt > 0L && rate.recordedAt > 0L && rate.source.isNotBlank()) {
                "بيانات سجل سعر الصرف غير مكتملة"
            }
        }

        exchangeRateAudits.forEach { audit ->
            require(audit.historyId in rateHistoryById) {
                "حدث تدقيق مرتبط بسجل سعر صرف غير موجود"
            }
            require(audit.eventType in setOf("RATE_IMPORTED", "RATE_SET", "RATE_CORRECTION", "RATE_ARCHIVED")) {
                "نوع حدث تدقيق سعر الصرف غير معروف"
            }
            require(audit.reason.isNotBlank() && audit.correctedAt > 0L) {
                "بيانات تدقيق سعر الصرف غير مكتملة"
            }
            require(audit.affectedInvoiceCount >= 0 && audit.affectedLedgerCount >= 0 &&
                audit.affectedJournalCount >= 0
            ) { "عدد العمليات المتأثرة في سجل التدقيق غير صالح" }
            positive(audit.newRate, "سعر الصرف الجديد في سجل التدقيق")
            if (audit.oldRate.isNotBlank()) positive(audit.oldRate, "سعر الصرف السابق في سجل التدقيق")
            if (audit.eventType == "RATE_CORRECTION") {
                require(audit.oldRate.isNotBlank() && audit.oldRate != audit.newRate) {
                    "تفاصيل تصحيح سعر الصرف غير مكتملة"
                }
            }
        }

        val correctionItemsByAudit = exchangeRateCorrectionItems.groupBy { it.auditId }
        exchangeRateCorrectionItems.forEach { item ->
            require(item.auditId in rateAuditsById) { "تفصيل تصحيح مرتبط بسجل تدقيق غير موجود" }
            require(item.recordType in setOf("INVOICE", "LEDGER", "JOURNAL")) {
                "نوع العملية المرتبطة بتصحيح سعر الصرف غير مدعوم"
            }
            require(item.recordId.isNotBlank() && item.sourceType.isNotBlank() && item.sourceId.isNotBlank()) {
                "مرجع العملية المصححة غير مكتمل"
            }
            positive(item.oldRate, "سعر الصرف السابق للعملية المصححة")
            positive(item.newRate, "سعر الصرف الجديد للعملية المصححة")
        }
        exchangeRateAudits.forEach { audit ->
            val items = correctionItemsByAudit[audit.id].orEmpty()
            require(
                audit.affectedInvoiceCount == items.count { it.recordType == "INVOICE" } &&
                    audit.affectedLedgerCount == items.count { it.recordType == "LEDGER" } &&
                    audit.affectedJournalCount == items.count { it.recordType == "JOURNAL" }
            ) { "أعداد العمليات في سجل تدقيق سعر الصرف لا تطابق تفاصيله" }
        }

        journalEntries.forEach { entry ->
            require(entry.journalNumber.isNotBlank() && entry.sourceType.isNotBlank() &&
                entry.sourceId.isNotBlank() && entry.description.isNotBlank()
            ) { "بيانات قيد اليومية غير مكتملة" }
            requireCurrency(entry.currencyCode, "عملة قيد اليومية")
            requireRate(entry.exchangeRateSnapshot, entry.currencyCode, "سعر صرف قيد اليومية")
            val lines = journalLinesByEntry[entry.id].orEmpty()
            require(lines.size >= 2) { "قيد اليومية ${entry.journalNumber} يجب أن يحتوي سطرين على الأقل" }
            val totalDebit = lines.fold(BigDecimal.ZERO) { total, line ->
                total.add(line.debit.toBigDecimal())
            }
            val totalCredit = lines.fold(BigDecimal.ZERO) { total, line ->
                total.add(line.credit.toBigDecimal())
            }
            require(totalDebit.compareTo(totalCredit) == 0) {
                "قيد اليومية ${entry.journalNumber} غير متوازن"
            }
        }

        val closeReversals = journalEntries.filter { it.sourceType == "PERIOD_CLOSE_REVERSAL" }
        closeReversals.forEach { reversal ->
            val original = journalById[reversal.sourceId]
                ?: throw IllegalArgumentException("قيد عكس الإقفال مرتبط بقيد أصلي غير موجود")
            require(original.sourceType == "PERIOD_CLOSE") {
                "قيد العكس لا يشير إلى قيد إقفال فترة"
            }
            require(original.currencyCode == reversal.currencyCode &&
                BigDecimal(original.exchangeRateSnapshot).compareTo(BigDecimal(reversal.exchangeRateSnapshot)) == 0
            ) { "عملة أو سعر صرف قيد عكس الإقفال لا يطابق القيد الأصلي" }

            val range = original.sourceId.split(':')
            require(range.size in 3..4 && range[0] == original.currencyCode) {
                "مرجع نطاق الفترة في قيد الإقفال الأصلي غير صالح"
            }
            val periodStart = range[1].toLongOrNull()
            val periodEnd = range[2].toLongOrNull()
            require(periodStart != null && periodEnd != null && periodStart >= 0L && periodEnd >= periodStart) {
                "نطاق قيد الإقفال الأصلي غير صالح"
            }
            require(reversal.occurredAt > periodEnd) {
                "تاريخ قيد عكس الإقفال يجب أن يلي نهاية الفترة"
            }

            val originalLines = journalLinesByEntry[original.id].orEmpty().associateBy { it.lineNumber }
            val reversalLines = journalLinesByEntry[reversal.id].orEmpty().associateBy { it.lineNumber }
            require(originalLines.isNotEmpty() && originalLines.keys == reversalLines.keys) {
                "أسطر قيد عكس الإقفال لا تطابق القيد الأصلي"
            }
            originalLines.forEach { (lineNumber, originalLine) ->
                val reversalLine = reversalLines.getValue(lineNumber)
                require(
                    reversalLine.accountId == originalLine.accountId &&
                        BigDecimal(reversalLine.debit).compareTo(BigDecimal(originalLine.credit)) == 0 &&
                        BigDecimal(reversalLine.credit).compareTo(BigDecimal(originalLine.debit)) == 0
                ) { "قيد عكس الإقفال لا يعكس جميع أسطر القيد الأصلي بدقة" }
            }
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
