package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.domain.PartyBalanceCalculator
import com.goodgirl.accounting.domain.PartyInvoiceAllocationRules
import com.goodgirl.accounting.domain.PartyPaymentRules
import java.util.UUID

data class PartyPaymentResult(
    val paymentId: String,
    val partyId: String,
    val amount: String,
    val currencyCode: String,
    val partyEntryType: String,
    val cashEntryType: String
)

/**
 * Records customer receipts and supplier payments as a party-ledger entry
 * and a matching cash movement in one database transaction.
 */
class PartyPaymentRepository(private val database: AccountingDatabase) {
    suspend fun recordPayment(
        partyId: String,
        amount: String,
        currencyCode: String,
        exchangeRate: String,
        note: String
    ): PartyPaymentResult {
        val cleanNote = note.trim()
        require(cleanNote.isNotEmpty()) { "بيان الدفعة مطلوب" }
        val paymentId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val recorded = database.withTransaction {
            val party = database.partyDao().findById(partyId)
                ?: throw IllegalArgumentException("جهة التعامل غير موجودة")
            val existingEntries = database.ledgerDao().getForParty(partyId)
            val balances = PartyBalanceCalculator.calculate(party.kind, existingEntries)
            val normalizedCurrency = currencyCode.trim().uppercase()
            val outstanding = balances[normalizedCurrency]?.toPlainString() ?: "0"
            val values = PartyPaymentRules.normalize(
                party.kind,
                amount,
                normalizedCurrency,
                exchangeRate,
                outstanding
            )

            val isCustomer = values.partyKind == "CUSTOMER"
            val invoiceKind = if (isCustomer) "SALE" else "PURCHASE"
            val openInvoices = database.invoiceDao().getPostedForPartyAndCurrency(
                partyId = partyId,
                kind = invoiceKind,
                currencyCode = values.currencyCode
            )
            val allocation = PartyInvoiceAllocationRules.allocate(
                values.amount.toBigDecimal(),
                openInvoices
            )
            allocation.updatedInvoices.forEach { database.invoiceDao().updateInvoice(it) }

            val partyEntryType = if (isCustomer) "RECEIVABLE_PAYMENT" else "PAYABLE_PAYMENT"
            val cashEntryType = if (isCustomer) "CASH_IN" else "CASH_OUT"

            database.ledgerDao().insert(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    partyId = partyId,
                    entryType = partyEntryType,
                    amount = values.amount,
                    currencyCode = values.currencyCode,
                    exchangeRateSnapshot = values.exchangeRate,
                    sourceType = if (isCustomer) "CUSTOMER_RECEIPT" else "SUPPLIER_PAYMENT",
                    sourceId = paymentId,
                    occurredAt = now,
                    note = cleanNote
                )
            )
            database.ledgerDao().insert(
                LedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    entryType = cashEntryType,
                    amount = values.amount,
                    currencyCode = values.currencyCode,
                    exchangeRateSnapshot = values.exchangeRate,
                    sourceType = if (isCustomer) "CUSTOMER_RECEIPT_CASH" else "SUPPLIER_PAYMENT_CASH",
                    sourceId = paymentId,
                    occurredAt = now,
                    note = cleanNote
                )
            )
            Triple(values, partyEntryType, cashEntryType)
        }

        return PartyPaymentResult(
            paymentId = paymentId,
            partyId = partyId,
            amount = recorded.first.amount,
            currencyCode = recorded.first.currencyCode,
            partyEntryType = recorded.second,
            cashEntryType = recorded.third
        )
    }
}
