package com.goodgirl.accounting.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import java.math.BigDecimal
import java.util.UUID

/**
 * One-time conversion from the previous weighted-average product-cost snapshot
 * to the latest posted purchase cost. Old sale lines and journals are immutable;
 * the value change is posted only against stock that is still on hand.
 *
 * This helper is also used when importing backups made by database versions
 * before the latest-purchase-cost policy was introduced.
 */
internal object LatestPurchaseCostPolicyMigrator {
    private data class ProductCostRow(
        val id: String,
        val name: String,
        val quantity: BigDecimal,
        val previousCost: BigDecimal,
        val currencyCode: String
    )

    private data class LatestPurchaseRow(
        val unitCost: BigDecimal,
        val exchangeRateSnapshot: String
    )

    fun migrate(database: SupportSQLiteDatabase, appliedAt: Long = System.currentTimeMillis()) {
        require(appliedAt > 0L) { "وقت تطبيق سياسة تكلفة الشراء غير صالح" }

        val products = mutableListOf<ProductCostRow>()
        database.query(
            "SELECT id, name, stockQuantity, purchaseCost, priceCurrencyCode FROM products ORDER BY id"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val currency = cursor.getString(4).trim().uppercase()
                require(currency == "SYP" || currency == "USD") {
                    "عملة المنتج غير مدعومة أثناء ترقية تكلفة الشراء"
                }
                val quantity = decimal(cursor.getString(2), "رصيد المنتج")
                val previousCost = decimal(cursor.getString(3), "كلفة الشراء الحالية")
                require(quantity >= BigDecimal.ZERO && previousCost >= BigDecimal.ZERO) {
                    "رصيد أو كلفة المنتج لا يمكن أن يكونا سالبين أثناء ترقية تكلفة الشراء"
                }
                products += ProductCostRow(
                    id = cursor.getString(0),
                    name = cursor.getString(1),
                    quantity = quantity,
                    previousCost = previousCost,
                    currencyCode = currency
                )
            }
        }

        products.forEach { product ->
            val latestPurchase = findLatestPurchase(database, product.id, product.currencyCode)
                ?: return@forEach
            val latestCost = latestPurchase.unitCost
            val costDelta = latestCost.subtract(product.previousCost)
            val stockValueDelta = product.quantity.multiply(costDelta)

            // The reference cost is updated even when no stock remains. In that
            // case there is no inventory value to revalue and no journal is posted.
            database.execSQL(
                "UPDATE products SET purchaseCost = ?, updatedAt = ? WHERE id = ?",
                arrayOf(latestCost.stripTrailingZeros().toPlainString(), appliedAt, product.id)
            )

            if (product.quantity.compareTo(BigDecimal.ZERO) == 0 ||
                stockValueDelta.compareTo(BigDecimal.ZERO) == 0
            ) {
                return@forEach
            }

            val inventoryAccount = database.query(
                "SELECT accountType, normalSide, isActive FROM accounts WHERE id = 'SYS-1200' LIMIT 1"
            ).use { cursor ->
                require(cursor.moveToFirst()) { "حساب المخزون النظامي غير موجود أثناء ترقية تكلفة الشراء" }
                require(cursor.getString(0) == "ASSET" && cursor.getString(1) == "DEBIT" && cursor.getInt(2) == 1) {
                    "حساب المخزون النظامي غير صالح لإعادة التقييم"
                }
                "SYS-1200"
            }

            val positive = stockValueDelta.signum() > 0
            val revaluationAccountId = ensureRevaluationAccount(
                database = database,
                id = if (positive) "SYS-4100" else "SYS-7100",
                preferredCode = if (positive) "4100" else "7100",
                name = if (positive) "أرباح إعادة تقييم المخزون" else "خسائر إعادة تقييم المخزون",
                accountType = if (positive) "REVENUE" else "EXPENSE",
                normalSide = if (positive) "CREDIT" else "DEBIT",
                createdAt = appliedAt
            )

            val amount = stockValueDelta.abs().stripTrailingZeros().toPlainString()
            val memo = buildString {
                append("ترقية تكلفة الشراء للمادة ${product.name} (${product.id})")
                append(": الكمية المتبقية ${product.quantity.stripTrailingZeros().toPlainString()}")
                append(" × الكلفة السابقة ${product.previousCost.stripTrailingZeros().toPlainString()}")
                append(" ← الكلفة الأحدث ${latestCost.stripTrailingZeros().toPlainString()}")
                append("؛ فرق إعادة التقييم ${stockValueDelta.stripTrailingZeros().toPlainString()}")
            }
            val journalId = UUID.randomUUID().toString()
            val journalNumber = "AUTO-UPG-LC11-" + UUID.randomUUID().toString().replace("-", "").take(16)

            database.execSQL(
                "INSERT INTO journal_entries " +
                    "(id, journalNumber, sourceType, sourceId, currencyCode, exchangeRateSnapshot, occurredAt, description, createdAt) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    journalId,
                    journalNumber,
                    "MIGRATION_LATEST_PURCHASE_COST",
                    product.id,
                    product.currencyCode,
                    latestPurchase.exchangeRateSnapshot,
                    appliedAt,
                    "إعادة تقييم المخزون وفق أحدث فاتورة شراء للمادة ${product.name}",
                    appliedAt
                )
            )

            val inventoryDebit = if (positive) amount else "0"
            val inventoryCredit = if (positive) "0" else amount
            val adjustmentDebit = if (positive) "0" else amount
            val adjustmentCredit = if (positive) amount else "0"
            database.execSQL(
                "INSERT INTO journal_lines (id, journalEntryId, lineNumber, accountId, debit, credit, memo) " +
                    "VALUES (?, ?, 1, ?, ?, ?, ?)",
                arrayOf(UUID.randomUUID().toString(), journalId, inventoryAccount, inventoryDebit, inventoryCredit, memo)
            )
            database.execSQL(
                "INSERT INTO journal_lines (id, journalEntryId, lineNumber, accountId, debit, credit, memo) " +
                    "VALUES (?, ?, 2, ?, ?, ?, ?)",
                arrayOf(
                    UUID.randomUUID().toString(),
                    journalId,
                    revaluationAccountId,
                    adjustmentDebit,
                    adjustmentCredit,
                    memo
                )
            )

            database.execSQL(
                "INSERT INTO ledger_entries " +
                    "(id, partyId, entryType, amount, currencyCode, exchangeRateSnapshot, sourceType, sourceId, occurredAt, note) " +
                    "VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    UUID.randomUUID().toString(),
                    if (positive) "INCOME" else "EXPENSE",
                    amount,
                    product.currencyCode,
                    latestPurchase.exchangeRateSnapshot,
                    if (positive) "MIGRATION_STOCK_REVALUATION_GAIN" else "MIGRATION_STOCK_REVALUATION_LOSS",
                    product.id,
                    appliedAt,
                    memo
                )
            )
        }
    }

    private fun findLatestPurchase(
        database: SupportSQLiteDatabase,
        productId: String,
        currencyCode: String
    ): LatestPurchaseRow? =
        database.query(
            "SELECT lines.unitCostSnapshot, invoices.exchangeRateSnapshot " +
                "FROM invoice_lines AS lines " +
                "INNER JOIN invoices ON invoices.id = lines.invoiceId " +
                "WHERE lines.productId = ? AND invoices.kind = 'PURCHASE' " +
                "AND invoices.status = 'POSTED' AND invoices.currencyCode = ? " +
                "ORDER BY invoices.createdAt DESC, invoices.id DESC, lines.rowid DESC LIMIT 1",
            arrayOf(productId, currencyCode)
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                val cost = decimal(cursor.getString(0), "كلفة أحدث فاتورة شراء")
                require(cost >= BigDecimal.ZERO) { "كلفة أحدث فاتورة شراء لا يمكن أن تكون سالبة" }
                val rate = decimal(cursor.getString(1), "سعر صرف أحدث فاتورة شراء")
                require(rate > BigDecimal.ZERO) { "سعر صرف أحدث فاتورة شراء يجب أن يكون موجباً" }
                LatestPurchaseRow(
                    unitCost = cost,
                    exchangeRateSnapshot = rate.stripTrailingZeros().toPlainString()
                )
            }
        }

    private fun ensureRevaluationAccount(
        database: SupportSQLiteDatabase,
        id: String,
        preferredCode: String,
        name: String,
        accountType: String,
        normalSide: String,
        createdAt: Long
    ): String {
        database.query(
            "SELECT accountType, normalSide, isActive FROM accounts WHERE id = ? LIMIT 1",
            arrayOf(id)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                require(cursor.getString(0) == accountType && cursor.getString(1) == normalSide && cursor.getInt(2) == 1) {
                    "حساب إعادة تقييم المخزون النظامي غير صالح"
                }
                return id
            }
        }

        var code = preferredCode
        var suffix = 1
        while (true) {
            val occupied = database.query(
                "SELECT id FROM accounts WHERE code = ? LIMIT 1",
                arrayOf(code)
            ).use { it.moveToFirst() }
            if (!occupied) break
            code = preferredCode + suffix
            suffix += 1
            require(suffix < 10_000) { "تعذر تخصيص رمز حساب لإعادة تقييم المخزون" }
        }

        database.execSQL(
            "INSERT INTO accounts (id, code, name, accountType, normalSide, isSystem, isActive, createdAt) " +
                "VALUES (?, ?, ?, ?, ?, 1, 1, ?)",
            arrayOf(id, code, name, accountType, normalSide, createdAt)
        )
        return id
    }

    private fun decimal(value: String, label: String): BigDecimal =
        try {
            BigDecimal(value.trim())
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException("$label غير صالح أثناء ترقية تكلفة الشراء", error)
        }
}
