package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import java.math.BigDecimal
import java.math.RoundingMode

data class CurrencyFinancialSummary(
    val currencyCode: String,
    val sales: BigDecimal,
    val purchases: BigDecimal,
    val costOfGoodsSold: BigDecimal,
    val grossProfit: BigDecimal,
    val otherIncome: BigDecimal,
    val expenses: BigDecimal,
    val netProfit: BigDecimal
)

data class ProductFinancialSummary(
    val productName: String,
    val currencyCode: String,
    val quantity: BigDecimal,
    val revenue: BigDecimal,
    val cost: BigDecimal,
    val profit: BigDecimal
)

data class FinancialReport(
    val currencySummaries: List<CurrencyFinancialSummary>,
    val products: List<ProductFinancialSummary>
)

object FinancialReportCalculator {
    fun calculate(
        fromInclusive: Long,
        toInclusive: Long,
        invoices: List<InvoiceEntity>,
        lines: List<InvoiceLineEntity>,
        ledgerEntries: List<LedgerEntryEntity>
    ): FinancialReport {
        val periodInvoices = invoices.filter {
            it.status == "POSTED" && it.issuedAt in fromInclusive..toInclusive
        }
        val invoiceById = periodInvoices.associateBy { it.id }
        val salesInvoices = periodInvoices.filter { it.kind == "SALE" }
        val purchaseInvoices = periodInvoices.filter { it.kind == "PURCHASE" }
        val saleIds = salesInvoices.map { it.id }.toSet()
        val salesLines = lines.filter { it.invoiceId in saleIds }

        val sales = mutableMapOf<String, BigDecimal>()
        salesInvoices.forEach { invoice ->
            sales[invoice.currencyCode] = (sales[invoice.currencyCode] ?: BigDecimal.ZERO)
                .add(invoice.total.toBigDecimal())
        }
        val purchases = mutableMapOf<String, BigDecimal>()
        purchaseInvoices.forEach { invoice ->
            purchases[invoice.currencyCode] = (purchases[invoice.currencyCode] ?: BigDecimal.ZERO)
                .add(invoice.total.toBigDecimal())
        }
        val costs = mutableMapOf<String, BigDecimal>()
        salesLines.forEach { line ->
            val invoice = invoiceById[line.invoiceId] ?: return@forEach
            val cost = line.baseQuantity.toBigDecimal().multiply(line.unitCostSnapshot.toBigDecimal())
            costs[invoice.currencyCode] = (costs[invoice.currencyCode] ?: BigDecimal.ZERO).add(cost)
        }

        val periodLedger = ledgerEntries.filter { it.occurredAt in fromInclusive..toInclusive }
        val otherIncome = mutableMapOf<String, BigDecimal>()
        val expenses = mutableMapOf<String, BigDecimal>()
        periodLedger.forEach { entry ->
            when (entry.entryType) {
                "INCOME" -> otherIncome[entry.currencyCode] =
                    (otherIncome[entry.currencyCode] ?: BigDecimal.ZERO).add(entry.amount.toBigDecimal())
                "EXPENSE" -> expenses[entry.currencyCode] =
                    (expenses[entry.currencyCode] ?: BigDecimal.ZERO).add(entry.amount.toBigDecimal())
            }
        }

        val currencies = (sales.keys + purchases.keys + costs.keys + otherIncome.keys + expenses.keys).toSortedSet()
        val summaries = currencies.map { currency ->
            val revenue = sales[currency] ?: BigDecimal.ZERO
            val purchased = purchases[currency] ?: BigDecimal.ZERO
            val cogs = costs[currency] ?: BigDecimal.ZERO
            val income = otherIncome[currency] ?: BigDecimal.ZERO
            val expense = expenses[currency] ?: BigDecimal.ZERO
            val gross = revenue.subtract(cogs)
            CurrencyFinancialSummary(
                currencyCode = currency,
                sales = revenue,
                purchases = purchased,
                costOfGoodsSold = cogs,
                grossProfit = gross,
                otherIncome = income,
                expenses = expense,
                netProfit = gross.add(income).subtract(expense)
            )
        }

        val productTotals = mutableMapOf<Pair<String, String>, MutableList<BigDecimal>>()
        salesInvoices.forEach { invoice ->
            val invoiceLines = salesLines.filter { it.invoiceId == invoice.id }
            val totalLineNet = invoiceLines.fold(BigDecimal.ZERO) { total, line ->
                total.add(line.lineTotal.toBigDecimal())
            }
            val lineDiscountSum = invoiceLines.fold(BigDecimal.ZERO) { total, line ->
                total.add(line.discount.toBigDecimal())
            }
            val invoiceLevelDiscount = invoice.discount.toBigDecimal()
                .subtract(lineDiscountSum)
                .max(BigDecimal.ZERO)

            invoiceLines.forEach { line ->
                val lineNet = line.lineTotal.toBigDecimal()
                val allocatedDiscount = if (totalLineNet.compareTo(BigDecimal.ZERO) > 0) {
                    invoiceLevelDiscount.multiply(lineNet)
                        .divide(totalLineNet, 8, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                val revenue = lineNet.subtract(allocatedDiscount)
                val cost = line.baseQuantity.toBigDecimal().multiply(line.unitCostSnapshot.toBigDecimal())
                val key = line.productNameSnapshot to invoice.currencyCode
                val totals = productTotals.getOrPut(key) {
                    mutableListOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                }
                totals[0] = totals[0].add(line.quantity.toBigDecimal())
                totals[1] = totals[1].add(revenue)
                totals[2] = totals[2].add(cost)
            }
        }
        val products = productTotals.map { (key, values) ->
            ProductFinancialSummary(
                productName = key.first,
                currencyCode = key.second,
                quantity = values[0],
                revenue = values[1],
                cost = values[2],
                profit = values[1].subtract(values[2])
            )
        }.sortedByDescending { it.revenue }
        return FinancialReport(summaries, products)
    }
}
