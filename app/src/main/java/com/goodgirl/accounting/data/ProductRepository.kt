package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.data.local.ProductUnitEntity
import com.goodgirl.accounting.data.local.StockMovementEntity
import com.goodgirl.accounting.data.local.StockCostBaselineEntity
import com.goodgirl.accounting.domain.ProductInputRules
import com.goodgirl.accounting.domain.BarcodeInputRules
import com.goodgirl.accounting.data.local.ProductBarcodeEntity
import com.goodgirl.accounting.domain.JournalEntryRules
import com.goodgirl.accounting.domain.JournalLineDraft
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.math.BigDecimal

class ProductRepository(private val database: AccountingDatabase) {
    fun observeProducts(): Flow<List<ProductEntity>> =
        database.productDao().observeActiveProducts()

    suspend fun addProduct(
        sku: String,
        name: String,
        unit: String,
        purchaseCost: String,
        salePrice: String,
        openingStock: String,
        note: String,
        priceCurrencyCode: String = "SYP",
        categoryId: String? = null,
        lowStockThreshold: String = "0",
        openingExchangeRate: String = "1",
        initialBarcode: String? = null,
        initialBarcodeFormat: String? = null
    ): String {
        val now = System.currentTimeMillis()
        val productId = UUID.randomUUID().toString()
        val cleanName = ProductInputRules.requireName(name)
        val cleanUnit = unit.trim().ifEmpty { "قطعة" }
        val cleanCost = ProductInputRules.normalizeNonNegativeDecimal(purchaseCost, "كلفة الشراء")
        val cleanPrice = ProductInputRules.normalizeNonNegativeDecimal(salePrice, "سعر البيع")
        val cleanStock = ProductInputRules.normalizeNonNegativeDecimal(openingStock, "الرصيد الافتتاحي")
        val cleanThreshold = ProductInputRules.normalizeNonNegativeDecimal(lowStockThreshold, "حد تنبيه المخزون")
        val cleanSku = sku.trim()
        val cleanCurrency = ProductInputRules.normalizeCurrencyCode(priceCurrencyCode)
        val cleanInitialBarcode = initialBarcode?.trim()?.takeIf { it.isNotEmpty() }
        val cleanInitialBarcodeFormat = initialBarcodeFormat?.trim()?.takeIf { it.isNotEmpty() }
        require((cleanInitialBarcode == null) == (cleanInitialBarcodeFormat == null)) {
            "يجب تحديد رقم الباركود وصيغته معاً"
        }
        val normalizedInitialBarcode = cleanInitialBarcode?.let {
            BarcodeInputRules.normalize(it, cleanInitialBarcodeFormat!!)
        }

        return database.withTransaction {
            if (categoryId != null) {
                require(database.categoryDao().findById(categoryId) != null) { "التصنيف المحدد غير موجود" }
            }
            if (normalizedInitialBarcode != null) {
                require(database.productBarcodeDao().findByValue(normalizedInitialBarcode) == null) {
                    "رقم الباركود مستخدم لمنتج آخر"
                }
            }
            database.productDao().insert(
                ProductEntity(
                    id = productId,
                    sku = cleanSku,
                    name = cleanName,
                    baseUnit = cleanUnit,
                    purchaseCost = cleanCost,
                    salePrice = cleanPrice,
                    priceCurrencyCode = cleanCurrency,
                    categoryId = categoryId,
                    stockQuantity = cleanStock,
                    lowStockThreshold = cleanThreshold,
                    note = note.trim(),
                    createdAt = now,
                    updatedAt = now
                )
            )

            if (normalizedInitialBarcode != null) {
                database.productBarcodeDao().insert(
                    ProductBarcodeEntity(
                        id = UUID.randomUUID().toString(),
                        productId = productId,
                        barcodeValue = normalizedInitialBarcode,
                        format = cleanInitialBarcodeFormat!!,
                        origin = "ORIGINAL",
                        createdAt = now
                    )
                )
            }

            database.stockCostBaselineDao().insert(
                StockCostBaselineEntity(
                    productId = productId,
                    quantity = "0",
                    averageCost = "0",
                    currencyCode = cleanCurrency,
                    effectiveAt = now,
                    recordedAt = now,
                    source = "PRODUCT_ZERO_OPENING"
                )
            )

            database.productUnitDao().insert(
                ProductUnitEntity(
                    id = UUID.randomUUID().toString(),
                    productId = productId,
                    unitName = cleanUnit,
                    baseFactor = "1",
                    isBaseUnit = true
                )
            )

            if (cleanStock.toBigDecimal() > java.math.BigDecimal.ZERO) {
                database.stockMovementDao().insert(
                    StockMovementEntity(
                        id = UUID.randomUUID().toString(),
                        productId = productId,
                        quantityDelta = cleanStock,
                        unitCostSnapshot = cleanCost,
                        currencyCode = cleanCurrency,
                        sourceType = "OPENING_BALANCE",
                        sourceId = productId,
                        occurredAt = now,
                        recordedAt = nextMovementRecordedAt(productId, now),
                        note = "رصيد افتتاحي عند إنشاء المنتج"
                    )
                )
                ProductOpeningStockJournalPoster.post(
                    database = database,
                    productId = productId,
                    quantity = cleanStock,
                    unitCost = cleanCost,
                    currencyCode = cleanCurrency,
                    exchangeRateSnapshot = openingExchangeRate,
                    occurredAt = now
                )
            }
            productId
        }
    }

    suspend fun updateProduct(
        id: String,
        sku: String,
        name: String,
        unit: String,
        purchaseCost: String,
        salePrice: String,
        note: String,
        priceCurrencyCode: String = "SYP",
        categoryId: String? = null,
        lowStockThreshold: String = "0",
        currencyCorrectionReason: String? = null,
        currencyCorrectionExchangeRate: String = "1"
    ): Boolean {
        val cleanName = ProductInputRules.requireName(name)
        val cleanUnit = unit.trim().ifEmpty { "قطعة" }
        val cleanCost = ProductInputRules.normalizeNonNegativeDecimal(purchaseCost, "كلفة الشراء")
        val cleanPrice = ProductInputRules.normalizeNonNegativeDecimal(salePrice, "سعر البيع")
        val cleanCurrency = ProductInputRules.normalizeCurrencyCode(priceCurrencyCode)
        val cleanThreshold = ProductInputRules.normalizeNonNegativeDecimal(lowStockThreshold, "حد تنبيه المخزون")
        var updated = false

        database.withTransaction {
            if (categoryId != null) {
                require(database.categoryDao().findById(categoryId) != null) { "التصنيف المحدد غير موجود" }
            }
            val current = database.productDao().findById(id)
            if (current != null && current.isActive) {
                val currentStock = current.stockQuantity.toBigDecimal()
                val trackedPurchaseCost = ProductInputRules.requirePurchaseCostChangeIsTracked(
                    currentQuantity = current.stockQuantity,
                    currentCost = current.purchaseCost,
                    requestedCost = cleanCost
                )
                val currencyChanged = cleanCurrency != current.priceCurrencyCode
                require(cleanUnit == current.baseUnit) {
                    "وحدة الأساس ثابتة بعد إنشاء المنتج. أضف وحدات تحويل إضافية من شاشة الوحدات."
                }
                if (currencyChanged) {
                    val reason = currencyCorrectionReason?.trim().orEmpty()
                    require(reason.isNotBlank()) { "اكتب سبب تصحيح عملة المنتج قبل الحفظ" }
                    require(reason.length <= 300) { "سبب تصحيح العملة طويل جداً" }

                    val correctionId = UUID.randomUUID().toString()
                    val correctedAt = System.currentTimeMillis()
                    val oldInventoryValue = currentStock
                        .multiply(current.purchaseCost.toBigDecimal())
                        .stripTrailingZeros().toPlainString()
                    val newInventoryValue = currentStock
                        .multiply(trackedPurchaseCost.toBigDecimal())
                        .stripTrailingZeros().toPlainString()

                    ProductCurrencyCorrectionJournalPoster.post(
                        database = database,
                        correctionId = correctionId,
                        productName = current.name,
                        oldCurrencyCode = current.priceCurrencyCode,
                        newCurrencyCode = cleanCurrency,
                        oldInventoryValue = oldInventoryValue,
                        newInventoryValue = newInventoryValue,
                        exchangeRateSnapshot = currencyCorrectionExchangeRate,
                        occurredAt = correctedAt,
                        reason = reason
                    )
                    val correctionRecordedAt = nextMovementRecordedAt(current.id, correctedAt)
                    database.stockMovementDao().insert(
                        StockMovementEntity(
                            id = correctionId,
                            productId = current.id,
                            quantityDelta = "0",
                            unitCostSnapshot = trackedPurchaseCost,
                            currencyCode = cleanCurrency,
                            sourceType = "PRODUCT_CURRENCY_CORRECTION",
                            sourceId = correctionId,
                            occurredAt = correctedAt,
                            recordedAt = correctionRecordedAt,
                            note = "تصحيح عملة المنتج من ${current.priceCurrencyCode} إلى $cleanCurrency. بقيت القيم الرقمية دون تحويل. الرصيد: ${current.stockQuantity}. كلفة الشراء: ${current.purchaseCost} → $trackedPurchaseCost. سعر البيع: ${current.salePrice} → $cleanPrice. السبب: $reason"
                        )
                    )
                }

                database.productDao().update(
                    current.copy(
                        sku = sku.trim(),
                        name = cleanName,
                        baseUnit = cleanUnit,
                        purchaseCost = trackedPurchaseCost,
                        salePrice = cleanPrice,
                        priceCurrencyCode = cleanCurrency,
                        categoryId = categoryId,
                        lowStockThreshold = cleanThreshold,
                        note = note.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                updated = true
            }
        }
        return updated
    }

    /**
     * Updates only the default sale price for future sales.
     * Existing invoice lines keep their saved unit-price snapshots.
     */
    suspend fun updateSalePrice(id: String, salePrice: String): Boolean {
        val cleanPrice = ProductInputRules.normalizeNonNegativeDecimal(salePrice, "سعر البيع")
        var updated = false

        database.withTransaction {
            val current = database.productDao().findById(id)
            if (current != null && current.isActive) {
                database.productDao().update(
                    current.copy(
                        salePrice = cleanPrice,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                updated = true
            }
        }
        return updated
    }

    suspend fun archiveProduct(id: String) {
        database.productDao().archive(id, System.currentTimeMillis())
    }

    private suspend fun nextMovementRecordedAt(productId: String, requestedAt: Long): Long {
        val latestMovement = database.stockMovementDao().getLatestRecordedAt(productId)
        val baselineRecordedAt = database.stockCostBaselineDao().findByProductId(productId)?.recordedAt
        val previous = listOfNotNull(latestMovement, baselineRecordedAt).maxOrNull()
        return if (previous == null) requestedAt else maxOf(requestedAt, Math.addExact(previous, 1L))
    }
}

/**
 * Reclassifies the carrying value of the remaining stock when a product's
 * currency label was selected incorrectly. Historical invoices and stock
 * movements are never rewritten. Each currency receives its own balanced
 * journal because journal entries are single-currency documents.
 */
internal object ProductCurrencyCorrectionJournalPoster {
    private const val INVENTORY_ACCOUNT_ID = "SYS-1200"
    private const val INVENTORY_ACCOUNT_CODE = "1200"
    private const val CORRECTION_EQUITY_ACCOUNT_ID = "SYS-3100"
    private const val CORRECTION_EQUITY_ACCOUNT_CODE = "3100"

    suspend fun post(
        database: AccountingDatabase,
        correctionId: String,
        productName: String,
        oldCurrencyCode: String,
        newCurrencyCode: String,
        oldInventoryValue: String,
        newInventoryValue: String,
        exchangeRateSnapshot: String,
        occurredAt: Long,
        reason: String
    ) {
        val oldValue = oldInventoryValue.toBigDecimal()
        val newValue = newInventoryValue.toBigDecimal()
        if (oldValue.compareTo(BigDecimal.ZERO) <= 0 && newValue.compareTo(BigDecimal.ZERO) <= 0) return

        val now = System.currentTimeMillis()
        database.accountDao().insertIgnore(
            listOf(
                AccountEntity(
                    INVENTORY_ACCOUNT_ID, INVENTORY_ACCOUNT_CODE, "المخزون",
                    "ASSET", "DEBIT", true, true, now
                ),
                AccountEntity(
                    CORRECTION_EQUITY_ACCOUNT_ID, CORRECTION_EQUITY_ACCOUNT_CODE,
                    "تسويات تصحيح عملة المخزون", "EQUITY", "CREDIT", true, true, now
                )
            )
        )
        val accounts = database.accountDao().getAll()
        val inventoryAccount = accounts.firstOrNull { it.code == INVENTORY_ACCOUNT_CODE }
            ?: error("حساب المخزون غير متاح لترحيل تصحيح العملة")
        val correctionAccount = accounts.firstOrNull { it.code == CORRECTION_EQUITY_ACCOUNT_CODE }
            ?: error("حساب تسوية تصحيح العملة غير متاح")
        require(inventoryAccount.accountType == "ASSET" && inventoryAccount.normalSide == "DEBIT" && inventoryAccount.isActive) {
            "حساب المخزون غير صالح لترحيل تصحيح العملة"
        }
        require(correctionAccount.accountType == "EQUITY" && correctionAccount.normalSide == "CREDIT" && correctionAccount.isActive) {
            "رمز الحساب 3100 مستخدم لحساب لا يصلح لتسوية تصحيح العملة؛ اختر رمزاً آخر قبل المتابعة"
        }

        if (oldValue.compareTo(BigDecimal.ZERO) > 0) {
            postEntry(
                database = database,
                correctionId = correctionId,
                suffix = "OLD",
                sourceType = "PRODUCT_CURRENCY_CORRECTION_OLD",
                currencyCode = oldCurrencyCode,
                exchangeRateSnapshot = if (oldCurrencyCode == "SYP") "1" else exchangeRateSnapshot,
                occurredAt = occurredAt,
                description = "إزالة القيمة الدفترية السابقة لمخزون المنتج $productName عند تصحيح العملة",
                lines = listOf(
                    JournalLineDraft(
                        correctionAccount.id,
                        debit = oldValue.stripTrailingZeros().toPlainString(),
                        memo = "تسوية تصحيح عملة مخزون المنتج: $reason"
                    ),
                    JournalLineDraft(
                        inventoryAccount.id,
                        credit = oldValue.stripTrailingZeros().toPlainString(),
                        memo = "إزالة قيمة المخزون من العملة السابقة"
                    )
                )
            )
        }
        if (newValue.compareTo(BigDecimal.ZERO) > 0) {
            postEntry(
                database = database,
                correctionId = correctionId,
                suffix = "NEW",
                sourceType = "PRODUCT_CURRENCY_CORRECTION_NEW",
                currencyCode = newCurrencyCode,
                exchangeRateSnapshot = if (newCurrencyCode == "SYP") "1" else exchangeRateSnapshot,
                occurredAt = occurredAt,
                description = "إثبات القيمة الدفترية الحالية لمخزون المنتج $productName بالعملة المصححة",
                lines = listOf(
                    JournalLineDraft(
                        inventoryAccount.id,
                        debit = newValue.stripTrailingZeros().toPlainString(),
                        memo = "إثبات قيمة المخزون بالعملة المصححة"
                    ),
                    JournalLineDraft(
                        correctionAccount.id,
                        credit = newValue.stripTrailingZeros().toPlainString(),
                        memo = "مقابل تسوية تصحيح عملة المخزون: $reason"
                    )
                )
            )
        }
    }

    private suspend fun postEntry(
        database: AccountingDatabase,
        correctionId: String,
        suffix: String,
        sourceType: String,
        currencyCode: String,
        exchangeRateSnapshot: String,
        occurredAt: Long,
        description: String,
        lines: List<JournalLineDraft>
    ) {
        require(database.journalEntryDao().findBySource(sourceType, correctionId) == null) {
            "تم ترحيل هذا الجزء من تصحيح عملة المخزون مسبقاً"
        }
        val normalized = JournalEntryRules.normalize(
            currencyCode = currencyCode,
            exchangeRateSnapshot = exchangeRateSnapshot,
            lines = lines
        )
        val entryId = UUID.randomUUID().toString()
        database.journalEntryDao().insert(
            JournalEntryEntity(
                id = entryId,
                journalNumber = "PCC-$suffix-${correctionId.replace("-", "")}",
                sourceType = sourceType,
                sourceId = correctionId,
                currencyCode = normalized.currencyCode,
                exchangeRateSnapshot = normalized.exchangeRateSnapshot,
                occurredAt = occurredAt,
                description = description,
                createdAt = System.currentTimeMillis()
            )
        )
        database.journalLineDao().insertAll(
            normalized.lines.mapIndexed { index, line ->
                JournalLineEntity(
                    id = UUID.randomUUID().toString(),
                    journalEntryId = entryId,
                    lineNumber = index + 1,
                    accountId = line.accountId,
                    debit = line.debit,
                    credit = line.credit,
                    memo = line.memo
                )
            }
        )
    }
}
