package com.goodgirl.accounting.domain

import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import java.math.BigDecimal

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
    val profit: BigDecimal,
    /** Stable product identity; null only for legacy invoice lines without a product link. */
    val productId: String? = null
)

data class FinancialReport(
    val currencySummaries: List<CurrencyFinancialSummary>,
    val products: List<ProductFinancialSummary>
)

private data class ProductFinancialKey(
    val productId: String?,
    /** Legacy rows without productId are grouped by their saved name snapshot. */
    val legacyName: String?,
    val currencyCode: String
)

private data class ProductFinancialTotals(
    var productName: String,
    var latestIssuedAt: Long,
    var latestCreatedAt: Long,
    var quantity: BigDecimal = BigDecimal.ZERO,
    var revenue: BigDecimal = BigDecimal.ZERO,
    var cost: BigDecimal = BigDecimal.ZERO
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

        // Product reports must group by stable product identity, not by a mutable
        // display-name snapshot. This prevents unrelated products with equal names
        // from being merged and keeps one product together after it is renamed.
        val productTotals = mutableMapOf<ProductFinancialKey, ProductFinancialTotals>()
        salesInvoices.forEach { invoice ->
            val invoiceLines = salesLines.filter { it.invoiceId == invoice.id }
            val lineDiscountSum = invoiceLines.fold(BigDecimal.ZERO) { total, line ->
                total.add(line.discount.toBigDecimal())
            }
            val invoiceLevelDiscount = invoice.discount.toBigDecimal()
                .subtract(lineDiscountSum)
                .max(BigDecimal.ZERO)
            val allocatedDiscounts = InvoiceDiscountAllocator.allocate(
                invoiceLines.map { it.lineTotal.toBigDecimal() },
                invoiceLevelDiscount
            )

            invoiceLines.forEachIndexed { index, line ->
                val lineNet = line.lineTotal.toBigDecimal()
                val revenue = lineNet.subtract(allocatedDiscounts[index])
                val cost = line.baseQuantity.toBigDecimal().multiply(line.unitCostSnapshot.toBigDecimal())
                val key = ProductFinancialKey(
                    productId = line.productId,
                    legacyName = if (line.productId == null) line.productNameSnapshot else null,
                    currencyCode = invoice.currencyCode
                )
                val totals = productTotals.getOrPut(key) {
                    ProductFinancialTotals(
                        productName = line.productNameSnapshot,
                        latestIssuedAt = Long.MIN_VALUE,
                        latestCreatedAt = Long.MIN_VALUE
                    )
                }
                totals.quantity = totals.quantity.add(line.baseQuantity.toBigDecimal())
                totals.revenue = totals.revenue.add(revenue)
                totals.cost = totals.cost.add(cost)
                if (
                    invoice.issuedAt > totals.latestIssuedAt ||
                    (invoice.issuedAt == totals.latestIssuedAt && invoice.createdAt >= totals.latestCreatedAt)
                ) {
                    totals.productName = line.productNameSnapshot
                    totals.latestIssuedAt = invoice.issuedAt
                    totals.latestCreatedAt = invoice.createdAt
                }
            }
        }

        val products = productTotals.map { (key, totals) ->
            ProductFinancialSummary(
                productName = totals.productName,
                currencyCode = key.currencyCode,
                quantity = totals.quantity,
                revenue = totals.revenue,
                cost = totals.cost,
                profit = totals.revenue.subtract(totals.cost),
                productId = key.productId
            )
        }.sortedWith(
            compareByDescending<ProductFinancialSummary> { it.revenue }
                .thenBy { it.productName }
                .thenBy { it.productId.orEmpty() }
        )
        return FinancialReport(summaries, products)
    }
}
