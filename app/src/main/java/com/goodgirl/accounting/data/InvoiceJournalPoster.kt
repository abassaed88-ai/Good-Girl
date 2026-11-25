package com.goodgirl.accounting.data

import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.domain.JournalEntryRules
import com.goodgirl.accounting.domain.JournalLineDraft
import java.math.BigDecimal
import java.util.UUID

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
        lines: List<InvoiceLineEntity>
    ) {
        require(invoice.status == "POSTED") { "لا يمكن ترحيل قيد لفاتورة غير مرحلة" }
        require(invoice.kind == "SALE" || invoice.kind == "PURCHASE") { "نوع الفاتورة غير مدعوم محاسبياً" }

        val accounts = listOf(
            account(CASH, "1000", "الصندوق", "ASSET", "DEBIT"),
            account(RECEIVABLES, "1100", "ذمم العملاء", "ASSET", "DEBIT"),
            account(INVENTORY, "1200", "المخزون", "ASSET", "DEBIT"),
            account(PAYABLES, "2000", "ذمم الموردين", "LIABILITY", "CREDIT"),
            account("SYS-3000", "3000", "رأس المال", "EQUITY", "CREDIT"),
            account(SALES, "4000", "المبيعات", "REVENUE", "CREDIT"),
            account(COST_OF_GOODS_SOLD, "5000", "تكلفة البضاعة المباعة", "EXPENSE", "DEBIT"),
            account("SYS-6000", "6000", "إيرادات أخرى", "REVENUE", "CREDIT"),
            account("SYS-7000", "7000", "مصروفات تشغيلية", "EXPENSE", "DEBIT")
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
        if (drafts.isEmpty()) return

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
        PeriodClosingGuard.ensureOpen(database, normalized.currencyCode, occurredAt)
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
