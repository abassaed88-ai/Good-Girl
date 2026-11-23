package com.goodgirl.accounting.data

import com.goodgirl.accounting.domain.CashFlowActivityTotals
import com.goodgirl.accounting.domain.CashFlowStatement
import com.goodgirl.accounting.domain.CashFlowStatementCalculator
import com.goodgirl.accounting.domain.CurrencyBalanceSheet
import com.goodgirl.accounting.domain.CurrencyCashFlowStatement
import com.goodgirl.accounting.domain.CurrencyIncomeStatement
import com.goodgirl.accounting.domain.GeneralLedgerFinancialStatements
import com.goodgirl.accounting.domain.StatementAccountAmount
import com.goodgirl.accounting.domain.TrialBalance
import com.goodgirl.accounting.domain.TrialBalanceCurrencySummary
import com.goodgirl.accounting.domain.TrialBalanceRow
import java.math.BigDecimal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralLedgerStatementsPdfExporterTest {
    private fun decimal(value: String) = BigDecimal(value)

    private val trialBalance = TrialBalance(
        asOfInclusive = 300L,
        rows = listOf(
            TrialBalanceRow(
                accountId = "cash-syp",
                accountCode = "1000",
                accountName = "الصندوق",
                accountType = "ASSET",
                currencyCode = "SYP",
                totalDebit = decimal("1000"),
                totalCredit = decimal("100"),
                debitBalance = decimal("900"),
                creditBalance = BigDecimal.ZERO
            ),
            TrialBalanceRow(
                accountId = "cash-usd",
                accountCode = "1000",
                accountName = "الصندوق",
                accountType = "ASSET",
                currencyCode = "USD",
                totalDebit = decimal("100"),
                totalCredit = decimal("10"),
                debitBalance = decimal("90"),
                creditBalance = BigDecimal.ZERO
            )
        ),
        currencySummaries = listOf(
            TrialBalanceCurrencySummary("SYP", decimal("1000"), decimal("100"), decimal("900"), BigDecimal.ZERO),
            TrialBalanceCurrencySummary("USD", decimal("100"), decimal("10"), decimal("90"), BigDecimal.ZERO)
        )
    )

    private val financialStatements = GeneralLedgerFinancialStatements(
        periodStartInclusive = 100L,
        periodEndInclusive = 300L,
        asOfInclusive = 300L,
        balanceSheets = listOf(
            CurrencyBalanceSheet(
                currencyCode = "SYP",
                assets = listOf(StatementAccountAmount("cash-syp", "1000", "الصندوق", decimal("985"))),
                liabilities = listOf(StatementAccountAmount("payable-syp", "2000", "ذمم الموردين", decimal("200"))),
                equity = listOf(StatementAccountAmount("capital-syp", "3000", "رأس المال", decimal("700"))),
                totalAssets = decimal("985"),
                totalLiabilities = decimal("200"),
                totalEquity = decimal("700"),
                retainedEarnings = decimal("85"),
                totalLiabilitiesAndEquity = decimal("985")
            ),
            CurrencyBalanceSheet(
                currencyCode = "USD",
                assets = listOf(StatementAccountAmount("cash-usd", "1000", "الصندوق", decimal("90"))),
                liabilities = emptyList(),
                equity = listOf(StatementAccountAmount("capital-usd", "3000", "رأس المال", decimal("90"))),
                totalAssets = decimal("90"),
                totalLiabilities = BigDecimal.ZERO,
                totalEquity = decimal("90"),
                retainedEarnings = BigDecimal.ZERO,
                totalLiabilitiesAndEquity = decimal("90")
            )
        ),
        incomeStatements = listOf(
            CurrencyIncomeStatement(
                currencyCode = "SYP",
                revenues = listOf(StatementAccountAmount("sales-syp", "4000", "المبيعات", decimal("150"))),
                expenses = listOf(StatementAccountAmount("expense-syp", "7000", "مصروف تشغيلي", decimal("65"))),
                totalRevenue = decimal("150"),
                totalExpenses = decimal("65"),
                netIncome = decimal("85")
            ),
            CurrencyIncomeStatement(
                currencyCode = "USD",
                revenues = listOf(StatementAccountAmount("sales-usd", "4000", "المبيعات", decimal("20"))),
                expenses = listOf(StatementAccountAmount("expense-usd", "7000", "مصروف تشغيلي", decimal("5"))),
                totalRevenue = decimal("20"),
                totalExpenses = decimal("5"),
                netIncome = decimal("15")
            )
        )
    )

    private fun cashFlow(
        coverageAvailable: Boolean = true,
        historyTruncated: Boolean = false
    ): CashFlowStatement = CashFlowStatement(
        requestedStartInclusive = 50L,
        effectiveStartInclusive = if (historyTruncated) 100L else 50L,
        periodEndInclusive = 300L,
        asOfInclusive = 300L,
        migrationBoundaryInclusive = 100L,
        coverageAvailable = coverageAvailable,
        historyTruncated = historyTruncated,
        statements = if (!coverageAvailable) emptyList() else listOf(
            CurrencyCashFlowStatement(
                currencyCode = "SYP",
                openingCashBalance = decimal("900"),
                activities = listOf(
                    CashFlowActivityTotals(
                        section = CashFlowStatementCalculator.OPERATING,
                        cashInflows = decimal("100"),
                        cashOutflows = decimal("20"),
                        netCashFlow = decimal("80"),
                        transactionCount = 3
                    ),
                    CashFlowActivityTotals(
                        section = CashFlowStatementCalculator.UNCLASSIFIED,
                        cashInflows = decimal("5"),
                        cashOutflows = BigDecimal.ZERO,
                        netCashFlow = decimal("5"),
                        transactionCount = 1
                    )
                ),
                netCashChange = decimal("85"),
                closingCashBalance = decimal("985"),
                actualClosingCashBalance = decimal("984"),
                reconciliationDifference = decimal("-1"),
                unclassifiedTransactionCount = 1
            )
        )
    )

    @Test
    fun contentIncludesAllGeneralLedgerStatementsAndKeepsCurrenciesSeparate() {
        val content = GeneralLedgerStatementsPdfExporter.buildContent(
            trialBalance = trialBalance,
            financialStatements = financialStatements,
            cashFlowStatement = cashFlow(),
            generatedAt = 300L
        )

        listOf(
            "ميزان المراجعة",
            "قائمة الدخل من دفتر الأستاذ العام",
            "الميزانية العمومية من دفتر الأستاذ العام",
            "قائمة التدفقات النقدية المباشرة",
            "العملة: SYP",
            "العملة: USD",
            "إجمالي المدين: 1000",
            "صافي ربح / خسارة الفترة: 85",
            "إجمالي الأصول: 985",
            "النقدية الافتتاحية: 900",
            "حركات نقدية غير مصنفة",
            "فرق المطابقة: -1"
        ).forEach { expected ->
            assertTrue("Missing PDF content: $expected", content.contains(expected))
        }
        assertFalse("PDF content must not combine currency totals", content.contains("SYP + USD"))
    }

    @Test
    fun contentDisclosesTruncatedHistoricalCoverage() {
        val content = GeneralLedgerStatementsPdfExporter.buildContent(
            trialBalance = trialBalance,
            financialStatements = financialStatements,
            cashFlowStatement = cashFlow(historyTruncated = true),
            generatedAt = 300L
        )

        assertTrue(content.contains("تنبيه: يبدأ التقرير من حد التغطية التاريخية"))
        assertTrue(content.contains("حركات نقدية غير مصنفة"))
    }

    @Test
    fun contentExplainsWhenCashFlowPeriodIsOutsideMigrationCoverage() {
        val content = GeneralLedgerStatementsPdfExporter.buildContent(
            trialBalance = trialBalance,
            financialStatements = financialStatements,
            cashFlowStatement = cashFlow(coverageAvailable = false),
            generatedAt = 300L
        )

        assertTrue(content.contains("الفترة المطلوبة تسبق حد التغطية التاريخية للأرصدة الافتتاحية"))
        assertFalse(content.contains("النقدية الافتتاحية:"))
    }

    @Test
    fun contentDisclosesWhenCashFlowCouldNotBeCalculated() {
        val content = GeneralLedgerStatementsPdfExporter.buildContent(
            trialBalance = trialBalance,
            financialStatements = financialStatements,
            cashFlowStatement = null,
            generatedAt = 300L
        )

        assertTrue(content.contains("تعذر إنشاء قائمة التدفقات النقدية بسبب خطأ في البيانات"))
    }
}
