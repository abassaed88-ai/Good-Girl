package com.goodgirl.accounting.data

import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.domain.CashTransactionRules
import java.math.BigDecimal

/**
 * Posts a sale-attached expense to the cash ledger, profit-and-loss ledger,
 * and general journal. The caller must wrap this in the invoice Room
 * transaction so the invoice cannot be posted without its attached expenses.
 */
internal object DailySalesExpenseJournalPoster {
    private const val JOURNAL_SOURCE = "DAILY_SALE_EXPENSE"
    private const val CASH_LEDGER_SOURCE = "DAILY_SALE_EXPENSE_CASH"

    suspend fun post(
        database: AccountingDatabase,
        invoiceId: String,
        invoiceNumber: String,
        occurredAt: Long,
        expense: DailySalesExpenseDraft
    ) {
        require(expense.id.isNotBlank()) { "معرّف المصروف مطلوب" }
        val description = expense.description.trim()
        require(description.isNotEmpty()) { "بيان المصروف مطلوب" }
        require(description.length <= 180) { "بيان المصروف طويل جداً" }
        val values = CashTransactionRules.normalize(
            amount = expense.amount,
            currencyCode = expense.currencyCode,
            exchangeRate = expense.exchangeRateSnapshot
        )
        val journalDescription = "مصروف مرتبط بفاتورة البيع $invoiceNumber: $description"
        val cashLedgerId = "DS-EXP-CASH-${expense.id}"
        val expenseLedgerId = "DS-EXP-PL-${expense.id}"

        val existingJournal = database.journalEntryDao().findBySource(JOURNAL_SOURCE, expense.id)
        if (existingJournal != null) {
            require(
                existingJournal.currencyCode == values.currencyCode &&
                    sameDecimal(existingJournal.exchangeRateSnapshot, values.exchangeRate) &&
                    existingJournal.occurredAt == occurredAt &&
                    existingJournal.description == journalDescription
            ) { "بيانات قيد المصروف الموجود لا تطابق المسودة؛ لم يتم إنشاء قيد مكرر" }

            val cashEntry = database.ledgerDao().findById(cashLedgerId)
            val expenseEntry = database.ledgerDao().findById(expenseLedgerId)
            require(cashEntry != null && expenseEntry != null) {
                "قيد المصروف موجود لكن سجلات الصندوق أو المصروفات ناقصة؛ يلزم فحص البيانات"
            }
            require(
                cashEntry.entryType == "CASH_OUT" &&
                    cashEntry.sourceType == CASH_LEDGER_SOURCE &&
                    cashEntry.sourceId == invoiceId &&
                    sameDecimal(cashEntry.amount, values.amount) &&
                    cashEntry.currencyCode == values.currencyCode &&
                    sameDecimal(cashEntry.exchangeRateSnapshot, values.exchangeRate) &&
                    cashEntry.occurredAt == occurredAt &&
                    cashEntry.note == journalDescription
            ) { "حركة الصندوق الموجودة لا تطابق المصروف المحفوظ" }
            require(
                expenseEntry.entryType == "EXPENSE" &&
                    expenseEntry.sourceType == JOURNAL_SOURCE &&
                    expenseEntry.sourceId == invoiceId &&
                    sameDecimal(expenseEntry.amount, values.amount) &&
                    expenseEntry.currencyCode == values.currencyCode &&
                    sameDecimal(expenseEntry.exchangeRateSnapshot, values.exchangeRate) &&
                    expenseEntry.occurredAt == occurredAt &&
                    expenseEntry.note == journalDescription
            ) { "سجل المصروف الموجود لا يطابق المصروف المحفوظ" }

            val journalLines = database.journalLineDao().getForEntry(existingJournal.id)
            val amount = values.amount.toBigDecimal()
            val debits = journalLines.fold(BigDecimal.ZERO) { total, line ->
                total.add(line.debit.toBigDecimal())
            }
            val credits = journalLines.fold(BigDecimal.ZERO) { total, line ->
                total.add(line.credit.toBigDecimal())
            }
            require(
                journalLines.size == 2 &&
                    debits.compareTo(amount) == 0 &&
                    credits.compareTo(amount) == 0 &&
                    journalLines.any { it.accountId == "SYS-7000" && sameDecimal(it.debit, values.amount) } &&
                    journalLines.any { it.accountId == "SYS-1000" && sameDecimal(it.credit, values.amount) }
            ) { "القيد المحاسبي للمصروف الموجود غير متوازن أو لا يطابق الحسابات المتوقعة" }
            return
        }

        require(
            database.ledgerDao().findById(cashLedgerId) == null &&
                database.ledgerDao().findById(expenseLedgerId) == null
        ) { "وجدت سجلات مصروف سابقة دون قيد يومية؛ أوقف الترحيل لمنع التكرار" }

        database.ledgerDao().insert(
            LedgerEntryEntity(
                id = cashLedgerId,
                entryType = "CASH_OUT",
                amount = values.amount,
                currencyCode = values.currencyCode,
                exchangeRateSnapshot = values.exchangeRate,
                sourceType = CASH_LEDGER_SOURCE,
                sourceId = invoiceId,
                occurredAt = occurredAt,
                note = journalDescription
            )
        )
        database.ledgerDao().insert(
            LedgerEntryEntity(
                id = expenseLedgerId,
                entryType = "EXPENSE",
                amount = values.amount,
                currencyCode = values.currencyCode,
                exchangeRateSnapshot = values.exchangeRate,
                sourceType = JOURNAL_SOURCE,
                sourceId = invoiceId,
                occurredAt = occurredAt,
                note = journalDescription
            )
        )

        ManualCashJournalPoster.post(
            database = database,
            sourceId = expense.id,
            kind = "EXPENSE",
            amount = values.amount,
            currencyCode = values.currencyCode,
            exchangeRateSnapshot = values.exchangeRate,
            occurredAt = occurredAt,
            description = journalDescription,
            journalSourceType = JOURNAL_SOURCE,
            journalNumberPrefix = "AUTO-DAILY-SALE-EXPENSE"
        )
    }

    private fun sameDecimal(left: String, right: String): Boolean =
        runCatching { BigDecimal(left).compareTo(BigDecimal(right)) == 0 }.getOrDefault(false)
}
