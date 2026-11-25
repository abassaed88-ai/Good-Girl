package com.goodgirl.accounting

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationRulesTest {

    @Test
    fun detailScreensReturnToTheirParentScreen() {
        assertEquals("general_accounting", previousAppPage("chart_of_accounts"))
        assertEquals("general_accounting", previousAppPage("period_close"))
        assertEquals("chart_of_accounts", previousAppPage("account_ledger"))
        assertEquals("products", previousAppPage("categories"))
        assertEquals("products", previousAppPage("product_units"))
        assertEquals("products", previousAppPage("stock_history"))
    }

    @Test
    fun journalDetailReturnsToTheScreenThatOpenedIt() {
        assertEquals(
            "account_ledger",
            previousAppPage("journal_detail", journalReturnPage = "account_ledger")
        )
        assertEquals(
            "journal_book",
            previousAppPage("journal_detail", journalReturnPage = "journal_book")
        )
    }

    @Test
    fun statementsSalesAndPartiesReturnToTheirRecordedOrigins() {
        assertEquals(
            "accounts",
            previousAppPage("statement", statementReturnPage = "accounts")
        )
        assertEquals(
            "daily_sales",
            previousAppPage("sales", salesReturnPage = "daily_sales")
        )
        assertEquals(
            "accounts",
            previousAppPage("parties", partiesReturnPage = "accounts")
        )
    }

    @Test
    fun dashboardAndUnknownPagesFallBackSafely() {
        assertEquals("dashboard", previousAppPage("dashboard"))
        assertEquals("dashboard", previousAppPage("unknown"))
    }
}
