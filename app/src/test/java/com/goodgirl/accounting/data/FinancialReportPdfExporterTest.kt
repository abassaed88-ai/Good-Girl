package com.goodgirl.accounting.data

import com.goodgirl.accounting.domain.CurrencyFinancialSummary
import com.goodgirl.accounting.domain.FinancialReport
import com.goodgirl.accounting.domain.ProductFinancialSummary
import java.math.BigDecimal
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialReportPdfExporterTest {
    @Test
    fun reportPdfContentIncludesPeriodCurrencyTotalsAndProductAnalysis() {
        val report = FinancialReport(
            currencySummaries = listOf(
                CurrencyFinancialSummary(
                    currencyCode = "SYP",
                    sales = BigDecimal("120000"),
                    purchases = BigDecimal("70000"),
                    costOfGoodsSold = BigDecimal("50000"),
                    grossProfit = BigDecimal("70000"),
                    otherIncome = BigDecimal("5000"),
                    expenses = BigDecimal("10000"),
                    netProfit = BigDecimal("65000")
                ),
                CurrencyFinancialSummary(
                    currencyCode = "USD",
                    sales = BigDecimal("20"),
                    purchases = BigDecimal("8"),
                    costOfGoodsSold = BigDecimal("10"),
                    grossProfit = BigDecimal("10"),
                    otherIncome = BigDecimal.ZERO,
                    expenses = BigDecimal("2"),
                    netProfit = BigDecimal("8")
                )
            ),
            products = listOf(
                ProductFinancialSummary(
                    productName = "زيت عطري",
                    currencyCode = "USD",
                    quantity = BigDecimal("2"),
                    revenue = BigDecimal("20"),
                    cost = BigDecimal("10"),
                    profit = BigDecimal("10")
                )
            )
        )

        val content = FinancialReportPdfExporter.buildContent(
            report = report,
            periodLabel = "هذا الشهر",
            generatedAt = 0L
        )

        assertTrue(content.contains("هذا الشهر"))
        assertTrue(content.contains("العملة: SYP"))
        assertTrue(content.contains("العملة: USD"))
        assertTrue(content.contains("صافي الربح"))
        assertTrue(content.contains("زيت عطري"))
        assertTrue(content.contains("لا يجري تحويلها أو جمعها تلقائياً"))
    }

    @Test
    fun emptyReportHasClearNoDataText() {
        val content = FinancialReportPdfExporter.buildContent(
            report = FinancialReport(emptyList(), emptyList()),
            periodLabel = "اليوم",
            generatedAt = 0L
        )

        assertTrue(content.contains("لا توجد بيانات مالية"))
        assertTrue(content.contains("لا توجد مبيعات مواد"))
    }
}
