package com.goodgirl.accounting.data

import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.data.local.PartyEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyStatementPdfExporterTest {
    @Test
    fun customerStatementShowsMovementAndRunningBalanceWithoutCurrencyMixing() {
        val party = PartyEntity("p1", "عميل تجريبي", "CUSTOMER", phone = "0990000000", createdAt = 500L)
        val entries = listOf(
            LedgerEntryEntity("e1", "p1", "RECEIVABLE", "100", "SYP", "1", "INVOICE", "i1", 1000L, "فاتورة"),
            LedgerEntryEntity("e2", "p1", "RECEIVABLE_PAYMENT", "25", "SYP", "1", "PAYMENT", "x1", 2000L, "دفعة"),
            LedgerEntryEntity("e3", "p1", "RECEIVABLE", "10", "USD", "15000", "INVOICE", "i2", 3000L, "")
        )
        val content = PartyStatementPdfExporter.buildContent(party, entries, 4000L)
        assertTrue(content.contains("عميل تجريبي"))
        assertTrue(content.contains("100 SYP"))
        assertTrue(content.contains("75 SYP"))
        assertTrue(content.contains("10 USD"))
        assertTrue(content.contains("لا تُعرض") .not())
        assertTrue(content.contains("دون تحويل أو جمع تلقائي"))
    }

    @Test
    fun emptySupplierStatementHasHelpfulMessage() {
        val party = PartyEntity("p2", "مورد تجريبي", "SUPPLIER", createdAt = 500L)
        val content = PartyStatementPdfExporter.buildContent(party, emptyList(), 4000L)
        assertTrue(content.contains("مورد تجريبي"))
        assertTrue(content.contains("لا توجد حركات مالية"))
    }
}
