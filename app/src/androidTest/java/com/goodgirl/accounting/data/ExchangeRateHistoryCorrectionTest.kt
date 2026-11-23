package com.goodgirl.accounting.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.ExchangeRateAuditEntity
import com.goodgirl.accounting.data.local.ExchangeRateHistoryEntity
import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExchangeRateHistoryCorrectionTest {

    @Test
    fun previewAndApplyCorrectionUpdatesOnlyMatchingSnapshotsAndPreservesAmounts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            AccountingDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            database.exchangeRateHistoryDao().insertAll(
                listOf(
                    ExchangeRateHistoryEntity(
                        id = "rate-old", currencyPair = "USD/SYP", rate = "15000",
                        effectiveAt = 100L, recordedAt = 100L, source = "USER"
                    ),
                    ExchangeRateHistoryEntity(
                        id = "rate-current", currencyPair = "USD/SYP", rate = "16000",
                        effectiveAt = 300L, recordedAt = 300L, source = "USER"
                    )
                )
            )

            database.invoiceDao().insertAllInvoices(
                listOf(
                    InvoiceEntity(
                        id = "invoice-match", invoiceNumber = "S-001", kind = "SALE",
                        currencyCode = "USD", exchangeRateSnapshot = "15000",
                        subtotal = "100", discount = "0", total = "100", paid = "20",
                        remaining = "80", status = "POSTED", issuedAt = 150L, createdAt = 150L
                    ),
                    InvoiceEntity(
                        id = "invoice-after-period", invoiceNumber = "S-002", kind = "SALE",
                        currencyCode = "USD", exchangeRateSnapshot = "15000",
                        subtotal = "50", discount = "0", total = "50", paid = "50",
                        remaining = "0", status = "POSTED", issuedAt = 350L, createdAt = 350L
                    ),
                    InvoiceEntity(
                        id = "invoice-manual-rate", invoiceNumber = "S-003", kind = "SALE",
                        currencyCode = "USD", exchangeRateSnapshot = "15500",
                        subtotal = "70", discount = "0", total = "70", paid = "70",
                        remaining = "0", status = "POSTED", issuedAt = 175L, createdAt = 175L
                    ),
                    InvoiceEntity(
                        id = "invoice-syp", invoiceNumber = "S-004", kind = "SALE",
                        currencyCode = "SYP", exchangeRateSnapshot = "1",
                        subtotal = "70000", discount = "0", total = "70000", paid = "70000",
                        remaining = "0", status = "POSTED", issuedAt = 175L, createdAt = 175L
                    )
                )
            )

            database.ledgerDao().insertAll(
                listOf(
                    LedgerEntryEntity(
                        id = "ledger-match", entryType = "CASH_IN", amount = "20",
                        currencyCode = "USD", exchangeRateSnapshot = "15000",
                        sourceType = "CUSTOMER_RECEIPT_CASH", sourceId = "payment-1",
                        occurredAt = 160L, note = "قبض عميل"
                    ),
                    LedgerEntryEntity(
                        id = "ledger-after-period", entryType = "CASH_IN", amount = "10",
                        currencyCode = "USD", exchangeRateSnapshot = "15000",
                        sourceType = "CUSTOMER_RECEIPT_CASH", sourceId = "payment-2",
                        occurredAt = 350L, note = "قبض لاحق"
                    )
                )
            )

            database.accountDao().insertAll(
                listOf(
                    AccountEntity("cash", "1000", "الصندوق", "ASSET", "DEBIT", true, true, 1L),
                    AccountEntity("sales", "4000", "المبيعات", "REVENUE", "CREDIT", true, true, 1L)
                )
            )
            database.journalEntryDao().insertAll(
                listOf(
                    JournalEntryEntity(
                        id = "journal-match", journalNumber = "JE-001",
                        sourceType = "SALE_INVOICE", sourceId = "invoice-match",
                        currencyCode = "USD", exchangeRateSnapshot = "15000",
                        occurredAt = 170L, description = "قيد بيع", createdAt = 170L
                    ),
                    JournalEntryEntity(
                        id = "journal-after-period", journalNumber = "JE-002",
                        sourceType = "SALE_INVOICE", sourceId = "invoice-after-period",
                        currencyCode = "USD", exchangeRateSnapshot = "15000",
                        occurredAt = 350L, description = "قيد بيع لاحق", createdAt = 350L
                    )
                )
            )
            database.journalLineDao().insertAll(
                listOf(
                    JournalLineEntity("jl-1", "journal-match", 1, "cash", "100", "0", ""),
                    JournalLineEntity("jl-2", "journal-match", 2, "sales", "0", "100", "")
                )
            )

            context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE)
                .edit().putString("usd_syp_rate", "16000").commit()

            val preview = previewExchangeRateCorrection(database, "rate-old", "15100")
            assertEquals(1, preview.invoiceCount)
            assertEquals(1, preview.ledgerCount)
            assertEquals(1, preview.journalCount)
            assertEquals(3, preview.records.size)
            assertTrue(preview.records.any { it.key == "INVOICE:invoice-match" })
            assertTrue(preview.records.any { it.sourceType == "CUSTOMER_RECEIPT_CASH" })
            assertTrue(preview.records.any { it.sourceType == "SALE_INVOICE" })

            val result = applyExchangeRateCorrection(
                context = context,
                database = database,
                historyId = "rate-old",
                proposedRate = "15100",
                reason = "تصحيح سعر صرف تم إدخاله بالخطأ"
            )
            assertEquals(1, result.updatedInvoiceCount)
            assertEquals(1, result.updatedLedgerCount)
            assertEquals(1, result.updatedJournalCount)
            assertFalse(result.currentRateUpdated)

            val invoices = database.invoiceDao().getAllInvoices().associateBy { it.id }
            assertEquals("15100", invoices.getValue("invoice-match").exchangeRateSnapshot)
            assertEquals("100", invoices.getValue("invoice-match").total)
            assertEquals("20", invoices.getValue("invoice-match").paid)
            assertEquals("15000", invoices.getValue("invoice-after-period").exchangeRateSnapshot)
            assertEquals("15500", invoices.getValue("invoice-manual-rate").exchangeRateSnapshot)
            assertEquals("1", invoices.getValue("invoice-syp").exchangeRateSnapshot)

            val ledgers = database.ledgerDao().getAll().associateBy { it.id }
            assertEquals("15100", ledgers.getValue("ledger-match").exchangeRateSnapshot)
            assertEquals("20", ledgers.getValue("ledger-match").amount)
            assertEquals("15000", ledgers.getValue("ledger-after-period").exchangeRateSnapshot)

            val journals = database.journalEntryDao().getAll().associateBy { it.id }
            assertEquals("15100", journals.getValue("journal-match").exchangeRateSnapshot)
            assertEquals("15000", journals.getValue("journal-after-period").exchangeRateSnapshot)
            val journalLines = database.journalLineDao().getForEntry("journal-match")
            assertEquals("100", journalLines.first { it.lineNumber == 1 }.debit)
            assertEquals("100", journalLines.first { it.lineNumber == 2 }.credit)

            val audits = database.exchangeRateAuditDao().getAll()
            assertEquals(1, audits.size)
            assertEquals("15000", audits.single().oldRate)
            assertEquals("15100", audits.single().newRate)
            assertEquals(1, audits.single().affectedInvoiceCount)
            assertFalse(audits.single().financialAmountsChanged)
            assertEquals(3, database.exchangeRateCorrectionItemDao().getAll().size)
            assertEquals("16000", context.getSharedPreferences(
                "good_girl_settings", android.content.Context.MODE_PRIVATE
            ).getString("usd_syp_rate", null))
        } finally {
            database.close()
        }
    }

    @Test
    fun archivesOldRateWithoutDeletingItAndBlocksArchivingCurrentRate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            AccountingDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            database.exchangeRateHistoryDao().insertAll(
                listOf(
                    ExchangeRateHistoryEntity(
                        "rate-old", "USD/SYP", "15000", 100L, 100L, "USER"
                    ),
                    ExchangeRateHistoryEntity(
                        "rate-current", "USD/SYP", "16000", 200L, 200L, "USER"
                    )
                )
            )
            archiveExchangeRate(database, "rate-old", "سعر قديم لم يعد مستخدماً")
            val archived = database.exchangeRateHistoryDao().findById("rate-old")
            assertTrue(archived != null && archived.isArchived)
            assertEquals(2, database.exchangeRateHistoryDao().getAll().size)
            assertEquals(1, database.exchangeRateHistoryDao().observeAll().let { 1 })
            assertEquals("RATE_ARCHIVED", database.exchangeRateAuditDao().getAll().single().eventType)

            val failed = runCatching {
                archiveExchangeRate(database, "rate-current", "محاولة أرشفة السعر الحالي")
            }
            assertTrue(failed.isFailure)
            assertFalse(database.exchangeRateHistoryDao().findById("rate-current")!!.isArchived)
        } finally {
            database.close()
        }
    }
}
