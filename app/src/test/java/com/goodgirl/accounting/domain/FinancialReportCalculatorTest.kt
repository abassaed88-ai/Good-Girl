package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialReportCalculatorTest {
    private fun invoice(id: String, kind: String, total: String, discount: String = "0", currency: String = "SYP") =
        InvoiceEntity(
            id = id,
            invoiceNumber = id,
            kind = kind,
            currencyCode = currency,
            exchangeRateSnapshot = "1",
            subtotal = total,
            discount = discount,
            total = total,
            paid = total,
            remaining = "0",
            status = "POSTED",
            issuedAt = 100L,
            createdAt = 100L
        )

    private fun line(invoiceId: String, quantity: String, price: String, cost: String, discount: String = "0") =
        InvoiceLineEntity(
            id = invoiceId + "-line",
            invoiceId = invoiceId,
            productId = "product-1",
            productNameSnapshot = "كريم",
            unitSnapshot = "قطعة",
            quantity = quantity,
            unitCostSnapshot = cost,
            unitPriceSnapshot = price,
            discount = discount,
            lineTotal = quantity.toBigDecimal().multiply(price.toBigDecimal()).subtract(discount.toBigDecimal()).toPlainString()
        )

    private fun ledger(type: String, amount: String) = LedgerEntryEntity(
        id = type,
        entryType = type,
        amount = amount,
        currencyCode = "SYP",
        exchangeRateSnapshot = "1",
        sourceType = "TEST",
        sourceId = type,
        occurredAt = 100L
    )

    @Test
    fun calculatesGrossAndNetProfitWithoutDoubleCountingCash() {
        val report = FinancialReportCalculator.calculate(
            0L, 200L,
            listOf(invoice("sale-1", "SALE", "90"), invoice("purchase-1", "PURCHASE", "50")),
            listOf(line("sale-1", "2", "50", "20")),
            listOf(ledger("CASH_IN", "90"), ledger("INCOME", "10"), ledger("EXPENSE", "5"))
        )
        val summary = report.currencySummaries.single()
        assertEquals("90", summary.sales.stripTrailingZeros().toPlainString())
        assertEquals("50", summary.purchases.stripTrailingZeros().toPlainString())
        assertEquals("40", summary.costOfGoodsSold.stripTrailingZeros().toPlainString())
        assertEquals("50", summary.grossProfit.stripTrailingZeros().toPlainString())
        assertEquals("55", summary.netProfit.stripTrailingZeros().toPlainString())
    }

    @Test
    fun allocatesInvoiceLevelDiscountAcrossProducts() {
        val report = FinancialReportCalculator.calculate(
            0L, 200L,
            listOf(invoice("sale-2", "SALE", "90", "10")),
            listOf(line("sale-2", "1", "100", "40")),
            emptyList()
        )
        val product = report.products.single()
        assertEquals("90", product.revenue.stripTrailingZeros().toPlainString())
        assertEquals("40", product.cost.stripTrailingZeros().toPlainString())
        assertEquals("50", product.profit.stripTrailingZeros().toPlainString())
    }

    @Test
    fun combinesLineAndInvoiceDiscountsAndExcludesDraftAndOutOfPeriodInvoices() {
        val valid = invoice("sale-valid", "SALE", "80", "20")
        val draft = invoice("sale-draft", "SALE", "80", "20").copy(status = "DRAFT")
        val outsidePeriod = invoice("sale-outside", "SALE", "80", "20").copy(issuedAt = 201L)

        val report = FinancialReportCalculator.calculate(
            0L, 200L,
            listOf(valid, draft, outsidePeriod),
            listOf(
                line("sale-valid", "1", "100", "40", "5"),
                line("sale-draft", "1", "100", "40", "5"),
                line("sale-outside", "1", "100", "40", "5")
            ),
            emptyList()
        )

        val summary = report.currencySummaries.single()
        val product = report.products.single()
        assertEquals("80", summary.sales.stripTrailingZeros().toPlainString())
        assertEquals("80", product.revenue.stripTrailingZeros().toPlainString())
        assertEquals("40", product.cost.stripTrailingZeros().toPlainString())
        assertEquals("40", product.profit.stripTrailingZeros().toPlainString())
    }

    @Test
    fun keepsCurrenciesSeparate() {
        val report = FinancialReportCalculator.calculate(
            0L, 200L,
            listOf(invoice("sale-usd", "SALE", "4", currency = "USD"), invoice("sale-syp", "SALE", "100")),
            emptyList(),
            emptyList()
        )
        assertEquals(setOf("SYP", "USD"), report.currencySummaries.map { it.currencyCode }.toSet())
    }
}
