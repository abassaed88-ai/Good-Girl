package com.goodgirl.accounting.data

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.domain.JournalEntryRules
import com.goodgirl.accounting.domain.JournalLineDraft
import java.math.BigDecimal
import java.util.UUID

/**
 * Signed inventory carrying-value change generated when a purchase replaces
 * the current unit cost of the product's remaining stock.
 */
internal data class InventoryRevaluationAdjustment(
    val productId: String,
    val productName: String,
    val previousStockQuantity: String,
    val previousUnitCost: String,
    val incomingQuantity: String,
    val incomingExtendedCost: String,
    val newUnitCost: String,
    val valueDelta: String
)

/**
 * Posts the accounting effect of a posted invoice.
 *
 * The caller must invoke this inside the same Room transaction that posts
 * the invoice, stock movements, and party/cash ledger rows.
 */
internal object InvoiceJournalPoster {
    private const val CASH = "SYS-1000"
    private const val RECEIVABLES = "SYS-1100"
    private const val INVENTORY = "SYS-1200"
    private const val PAYABLES = "SYS-2000"
    private const val SALES = "SYS-4000"
    private const val COST_OF_GOODS_SOLD = "SYS-5000"

    suspend fun post(
        database: AccountingDatabase,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        inventoryRevaluations: List<InventoryRevaluationAdjustment> = emptyList(),
        revaluationOccurredAt: Long = System.currentTimeMillis()
    ) {
        require(invoice.status == "POSTED") { "لا يمكن ترحيل قيد لفاتورة غير مرحلة" }
        require(invoice.kind == "SALE" || invoice.kind == "PURCHASE") { "نوع الفاتورة غير مدعوم محاسبياً" }
        PeriodClosingGuard.ensureOpen(database, invoice.currencyCode, invoice.issuedAt)

        val accounts = listOf(
            account(CASH, "1000", "الصندوق", "ASSET", "DEBIT"),
            account(RECEIVABLES, "1100", "ذمم العملاء", "ASSET", "DEBIT"),
            account(INVENTORY, "1200", "المخزون", "ASSET", "DEBIT"),
            account(PAYABLES, "2000", "ذمم الموردين", "LIABILITY", "CREDIT"),
            account("SYS-3000", "3000", "رأس المال", "EQUITY", "CREDIT"),
            account("SYS-3100", "3100", "الأرباح المحتجزة", "EQUITY", "CREDIT"),
            account(SALES, "4000", "المبيعات", "REVENUE", "CREDIT"),
            account("SYS-4100", "4100", "أرباح إعادة تقييم المخزون", "REVENUE", "CREDIT"),
            account(COST_OF_GOODS_SOLD, "5000", "تكلفة البضاعة المباعة", "EXPENSE", "DEBIT"),
            account("SYS-6000", "6000", "إيرادات أخرى", "REVENUE", "CREDIT"),
            account("SYS-7000", "7000", "مصروفات تشغيلية", "EXPENSE", "DEBIT"),
            account("SYS-7100", "7100", "خسائر إعادة تقييم المخزون", "EXPENSE", "DEBIT")
        )
        database.accountDao().insertIgnore(accounts)

        val total = invoice.total.toBigDecimal()
        val paid = invoice.paid.toBigDecimal()
        val remaining = invoice.remaining.toBigDecimal()
        val costOfGoodsSold = if (invoice.kind == "SALE") {
            lines.fold(BigDecimal.ZERO) { sum, line ->
                sum.add(line.unitCostSnapshot.toBigDecimal().multiply(line.baseQuantity.toBigDecimal()))
            }
        } else {
            BigDecimal.ZERO
        }

        val drafts = mutableListOf<JournalLineDraft>()
        fun addDebit(accountId: String, amount: BigDecimal, memo: String) {
            if (amount.signum() > 0) drafts += JournalLineDraft(
                accountId = accountId,
                debit = amount.stripTrailingZeros().toPlainString(),
                memo = memo
            )
        }
        fun addCredit(accountId: String, amount: BigDecimal, memo: String) {
            if (amount.signum() > 0) drafts += JournalLineDraft(
                accountId = accountId,
                credit = amount.stripTrailingZeros().toPlainString(),
                memo = memo
            )
        }

        if (invoice.kind == "SALE") {
            require(inventoryRevaluations.isEmpty()) {
                "لا يجوز إرفاق إعادة تقييم مخزون بفاتورة بيع"
            }
            addDebit(CASH, paid, "مقبوضات فاتورة البيع " + invoice.invoiceNumber)
            addDebit(RECEIVABLES, remaining, "ذمم العميل من فاتورة البيع " + invoice.invoiceNumber)
            addCredit(SALES, total, "إيراد فاتورة البيع " + invoice.invoiceNumber)
            addDebit(COST_OF_GOODS_SOLD, costOfGoodsSold, "تكلفة بضاعة فاتورة البيع " + invoice.invoiceNumber)
            addCredit(INVENTORY, costOfGoodsSold, "صرف مخزون فاتورة البيع " + invoice.invoiceNumber)
        } else {
            addDebit(INVENTORY, total, "مخزون فاتورة الشراء " + invoice.invoiceNumber)
            addCredit(CASH, paid, "مدفوعات فاتورة الشراء " + invoice.invoiceNumber)
            addCredit(PAYABLES, remaining, "ذمم المورد من فاتورة الشراء " + invoice.invoiceNumber)

        }

        // A zero-value invoice with no cost has no financial journal impact.
        if (drafts.isEmpty()) {
            postInventoryRevaluationJournal(
                database = database,
                invoice = invoice,
                adjustments = inventoryRevaluations,
                occurredAt = revaluationOccurredAt
            )
            return
        }

        val normalized = JournalEntryRules.normalize(
            currencyCode = invoice.currencyCode,
            exchangeRateSnapshot = invoice.exchangeRateSnapshot,
            lines = drafts
        )
        normalized.lines.forEach { line ->
            val account = database.accountDao().findById(line.accountId)
                ?: throw IllegalStateException("حساب محاسبي مطلوب غير موجود")
            require(account.isActive) { "لا يمكن ترحيل القيد إلى حساب موقوف: " + account.name }
        }

        val sourceType = if (invoice.kind == "SALE") "SALE_INVOICE" else "PURCHASE_INVOICE"
        require(database.journalEntryDao().findBySource(sourceType, invoice.id) == null) {
            "تم إنشاء قيد محاسبي لهذه الفاتورة مسبقاً"
        }
        val entryId = UUID.randomUUID().toString()
        database.journalEntryDao().insert(
            JournalEntryEntity(
                id = entryId,
                journalNumber = "AUTO-" + invoice.invoiceNumber,
                sourceType = sourceType,
                sourceId = invoice.id,
                currencyCode = normalized.currencyCode,
                exchangeRateSnapshot = normalized.exchangeRateSnapshot,
                occurredAt = invoice.issuedAt,
                description = if (invoice.kind == "SALE") {
                    "قيد فاتورة بيع " + invoice.invoiceNumber
                } else {
                    "قيد فاتورة شراء " + invoice.invoiceNumber
                },
                createdAt = System.currentTimeMillis()
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

        postInventoryRevaluationJournal(
            database = database,
            invoice = invoice,
            adjustments = inventoryRevaluations,
            occurredAt = revaluationOccurredAt
        )
    }

    /**
     * Posts the repricing of stock that remains on hand when the purchase is
     * recorded. Its date is the actual recording time, not a backdated invoice
     * issue date, so an old invoice date cannot rewrite earlier stock valuation
     * reports. The sourceId links this immutable journal to the purchase.
     */
    private suspend fun postInventoryRevaluationJournal(
        database: AccountingDatabase,
        invoice: InvoiceEntity,
        adjustments: List<InventoryRevaluationAdjustment>,
        occurredAt: Long
    ) {
        if (adjustments.isEmpty()) return
        require(invoice.kind == "PURCHASE") {
            "إعادة تقييم المخزون تتطلب فاتورة شراء"
        }
        PeriodClosingGuard.ensureOpen(database, invoice.currencyCode, occurredAt)
        require(
            database.journalEntryDao().findBySource("PURCHASE_INVENTORY_REVALUATION", invoice.id) == null
        ) { "تم ترحيل إعادة تقييم المخزون لهذه الفاتورة مسبقاً" }

        val inventory = database.accountDao().findById(INVENTORY)
            ?: throw IllegalStateException("حساب المخزون غير موجود")
        val gain = database.accountDao().findById("SYS-4100")
            ?: throw IllegalStateException("حساب أرباح إعادة تقييم المخزون غير موجود")
        val loss = database.accountDao().findById("SYS-7100")
            ?: throw IllegalStateException("حساب خسائر إعادة تقييم المخزون غير موجود")
        require(inventory.isActive && inventory.accountType == "ASSET" && inventory.normalSide == "DEBIT") {
            "حساب المخزون غير صالح لإعادة التقييم"
        }
        require(gain.isActive && gain.accountType == "REVENUE" && gain.normalSide == "CREDIT") {
            "حساب أرباح إعادة تقييم المخزون غير صالح"
        }
        require(loss.isActive && loss.accountType == "EXPENSE" && loss.normalSide == "DEBIT") {
            "حساب خسائر إعادة تقييم المخزون غير صالح"
        }

        val drafts = mutableListOf<JournalLineDraft>()
        adjustments.forEach { adjustment ->
            val delta = adjustment.valueDelta.toBigDecimal()
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                val amount = delta.abs().stripTrailingZeros().toPlainString()
                val memo = buildString {
                    append("تقييم مخزون ${adjustment.productName} (${adjustment.productId})")
                    append(": رصيد سابق ${adjustment.previousStockQuantity} × ${adjustment.previousUnitCost}")
                    append("، وارد جديد ${adjustment.incomingQuantity} بقيمة ${adjustment.incomingExtendedCost}")
                    append("، التكلفة الحالية ${adjustment.newUnitCost}")
                    append("، فرق التقييم ${delta.stripTrailingZeros().toPlainString()}")
                }
                if (delta.signum() > 0) {
                    drafts += JournalLineDraft(
                        accountId = inventory.id,
                        debit = amount,
                        memo = memo
                    )
                    drafts += JournalLineDraft(
                        accountId = gain.id,
                        credit = amount,
                        memo = memo
                    )
                } else {
                    drafts += JournalLineDraft(
                        accountId = loss.id,
                        debit = amount,
                        memo = memo
                    )
                    drafts += JournalLineDraft(
                        accountId = inventory.id,
                        credit = amount,
                        memo = memo
                    )
                }
            }
        }
        if (drafts.isEmpty()) return

        // Mirror each revaluation side into the simplified profit report as
        // separate non-cash income/expense rows. The general journal remains
        // the accounting source of truth; both records share the invoice source.
        val totalGain = adjustments.fold(BigDecimal.ZERO) { total, item ->
            val delta = item.valueDelta.toBigDecimal()
            if (delta.signum() > 0) total.add(delta) else total
        }
        val totalLoss = adjustments.fold(BigDecimal.ZERO) { total, item ->
            val delta = item.valueDelta.toBigDecimal()
            if (delta.signum() < 0) total.add(delta.abs()) else total
        }
        if (totalGain.signum() > 0) {
            database.ledgerDao().insert(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    entryType = "INCOME",
                    amount = totalGain.stripTrailingZeros().toPlainString(),
                    currencyCode = invoice.currencyCode,
                    exchangeRateSnapshot = invoice.exchangeRateSnapshot,
                    sourceType = "PURCHASE_INVENTORY_REVALUATION_GAIN",
                    sourceId = invoice.id,
                    occurredAt = occurredAt,
                    note = "أرباح إعادة تقييم المخزون حسب أحدث تكلفة شراء للفاتورة ${invoice.invoiceNumber}"
                )
            )
        }
        if (totalLoss.signum() > 0) {
            database.ledgerDao().insert(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    entryType = "EXPENSE",
                    amount = totalLoss.stripTrailingZeros().toPlainString(),
                    currencyCode = invoice.currencyCode,
                    exchangeRateSnapshot = invoice.exchangeRateSnapshot,
                    sourceType = "PURCHASE_INVENTORY_REVALUATION_LOSS",
                    sourceId = invoice.id,
                    occurredAt = occurredAt,
                    note = "خسائر إعادة تقييم المخزون حسب أحدث تكلفة شراء للفاتورة ${invoice.invoiceNumber}"
                )
            )
        }

        val normalized = JournalEntryRules.normalize(
            currencyCode = invoice.currencyCode,
            exchangeRateSnapshot = invoice.exchangeRateSnapshot,
            lines = drafts
        )
        val entryId = UUID.randomUUID().toString()
        database.journalEntryDao().insert(
            JournalEntryEntity(
                id = entryId,
                journalNumber = "AUTO-REVAL-" + UUID.randomUUID().toString().replace("-", "").take(16),
                sourceType = "PURCHASE_INVENTORY_REVALUATION",
                sourceId = invoice.id,
                currencyCode = normalized.currencyCode,
                exchangeRateSnapshot = normalized.exchangeRateSnapshot,
                occurredAt = occurredAt,
                description = "إعادة تقييم المخزون حسب أحدث تكلفة شراء للفاتورة ${invoice.invoiceNumber}",
                createdAt = System.currentTimeMillis()
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

    private fun account(
        id: String,
        code: String,
        name: String,
        type: String,
        normalSide: String
    ) = AccountEntity(
        id = id,
        code = code,
        name = name,
        accountType = type,
        normalSide = normalSide,
        isSystem = true,
        isActive = true,
        createdAt = System.currentTimeMillis()
    )
}
