package com.goodgirl.accounting.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.DailySalesDraftEntity
import com.goodgirl.accounting.data.local.DailySalesDraftLineEntity
import com.goodgirl.accounting.data.local.DailySalesDraftExpenseEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailySalesDraftRepositoryIntegrationTest {

    @Test
    fun editorAndMultiplePendingRowsSurviveDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "daily-sales-draft-reopen-test.db"
        context.deleteDatabase(databaseName)
        try {
            val firstDatabase = Room.databaseBuilder(
                context,
                AccountingDatabase::class.java,
                databaseName
            ).build()
            val header = sampleHeader()
            val lines = listOf(
                sampleLine("draft-line-1", 0, purchaseCostSnapshot = "2"),
                sampleLine("draft-line-2", 1)
            )
            val expenses = listOf(
                DailySalesDraftExpenseEntity(
                    id = "draft-expense-1",
                    lineId = lines[0].id,
                    description = "مصروف توصيل",
                    amount = "2.5",
                    currencyCode = "USD",
                    exchangeRateSnapshot = "15000",
                    createdAt = header.updatedAt
                )
            )
            DailySalesDraftRepository(firstDatabase).saveSnapshot(header, lines, expenses)
            firstDatabase.close()

            val reopened = Room.databaseBuilder(
                context,
                AccountingDatabase::class.java,
                databaseName
            ).build()
            try {
                val restored = DailySalesDraftRepository(reopened).loadSnapshot()
                assertEquals(header.selectedDay, restored.header!!.selectedDay)
                assertEquals("USD", restored.header.currencyCode)
                assertEquals("مادة لم يكتمل إدخالها", restored.header.selectedProductNameSnapshot)
                assertEquals("1.75", restored.header.quantity)
                assertEquals(2, restored.lines.size)
                assertEquals("draft-line-1", restored.lines[0].id)
                assertEquals("draft-line-2", restored.lines[1].id)
                assertEquals("2", restored.lines[0].purchaseCostSnapshot)
                assertEquals(1, restored.expenses.size)
                assertEquals(lines[0].id, restored.expenses.single().lineId)
                assertEquals("مصروف توصيل", restored.expenses.single().description)
                assertEquals("2.5", restored.expenses.single().amount)
                assertEquals("USD", restored.expenses.single().currencyCode)
                assertEquals("15000", restored.expenses.single().exchangeRateSnapshot)
                assertEquals("12345", restored.header.paidAmount)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun alreadyPostedDraftLineIsRecognizedAndRemovedWithoutPostingAgain() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val productRepository = ProductRepository(database)
            val invoiceRepository = InvoiceRepository(database)
            productRepository.addProduct(
                sku = "DRAFT-IDEMPOTENT",
                name = "مادة اختبار المسودة",
                unit = "قطعة",
                purchaseCost = "2",
                salePrice = "5",
                openingStock = "5",
                note = "اختبار منع التكرار"
            )
            val product = database.productDao().getAll().single()
            val line = sampleLine(
                id = "00000000-0000-0000-0000-000000000001",
                lineOrder = 0,
                productId = product.id,
                productName = product.name,
                unitName = product.baseUnit,
                currencyCode = "SYP",
                exchangeRate = "1",
                quantity = "1",
                unitFactor = "1",
                unitPrice = "5",
                discount = "0",
                paidAmount = "5"
            )
            val invoiceNumber = DailySalesDraftRepository.invoiceNumberFor(line.id)

            // Simulate process death after the accounting transaction succeeded,
            // but before the draft row was removed from Room.
            invoiceRepository.postInvoice(
                invoiceNumber = invoiceNumber,
                kind = "SALE",
                partyId = null,
                currencyCode = line.currencyCode,
                exchangeRateSnapshot = line.exchangeRate,
                lines = listOf(
                    InvoiceLineDraft(
                        productId = line.productId,
                        quantity = line.quantity,
                        unitPrice = line.unitPrice,
                        discount = line.discount,
                        unitName = line.unitName
                    )
                ),
                paidAmount = line.paidAmount,
                issuedAt = line.issuedAt,
                note = line.note
            )
            val quantityAfterOriginalPost = database.productDao().findById(product.id)!!.stockQuantity
            DailySalesDraftRepository(database).saveSnapshot(sampleHeader(), listOf(line))

            DailySalesDraftRepository(database).postPendingLine(line)

            assertEquals(1, database.invoiceDao().getAllInvoices().count { it.invoiceNumber == invoiceNumber })
            assertEquals(quantityAfterOriginalPost, database.productDao().findById(product.id)!!.stockQuantity)
            assertNull(DailySalesDraftRepository(database).loadSnapshot().lines.firstOrNull { it.id == line.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun postingUsesThePurchaseCostCapturedWhenTheDraftRowWasAdded() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            ProductRepository(database).addProduct(
                sku = "DRAFT-COST-SNAPSHOT",
                name = "مادة اختبار تثبيت كلفة المسودة",
                unit = "قطعة",
                purchaseCost = "2",
                salePrice = "5",
                openingStock = "5",
                note = "اختبار عدم تغير ربح المسودة"
            )
            val product = database.productDao().getAll().single()
            val line = sampleLine(
                id = "00000000-0000-0000-0000-000000000005",
                lineOrder = 0,
                productId = product.id,
                productName = product.name,
                unitName = product.baseUnit,
                currencyCode = "SYP",
                exchangeRate = "1",
                quantity = "1",
                unitFactor = "1",
                unitPrice = "5",
                discount = "0",
                paidAmount = "5",
                purchaseCostSnapshot = "2"
            )
            val repository = DailySalesDraftRepository(database)
            repository.saveSnapshot(sampleHeader(), listOf(line))

            // A purchase or cost correction changes today's item master cost,
            // but must not rewrite the already-entered draft line's gross profit.
            database.productDao().update(
                product.copy(purchaseCost = "4", updatedAt = System.currentTimeMillis())
            )

            val result = repository.postPendingLine(line)
            val invoiceLine = database.invoiceDao().getLines(result.invoiceId).single()
            val saleMovement = database.stockMovementDao().getAll()
                .single { it.sourceType == "SALE_INVOICE" }

            assertEquals("2", invoiceLine.unitCostSnapshot)
            assertEquals("2", saleMovement.unitCostSnapshot)
            assertEquals("4", database.productDao().findById(product.id)!!.stockQuantity)
        } finally {
            database.close()
        }
    }

    @Test
    fun multipleAttachedExpensesPostWithSaleAndRetryWithoutDuplicateAccounting() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            ProductRepository(database).addProduct(
                sku = "DRAFT-EXPENSE-001",
                name = "مادة اختبار المصروفات المرتبطة",
                unit = "قطعة",
                purchaseCost = "2",
                salePrice = "5",
                openingStock = "5",
                note = "اختبار ترحيل المصروفات"
            )
            val product = database.productDao().getAll().single()
            val line = sampleLine(
                id = "00000000-0000-0000-0000-000000000006",
                lineOrder = 0,
                productId = product.id,
                productName = product.name,
                unitName = product.baseUnit,
                currencyCode = "SYP",
                exchangeRate = "1",
                quantity = "1",
                unitFactor = "1",
                unitPrice = "5",
                discount = "0",
                paidAmount = "5",
                purchaseCostSnapshot = "2"
            )
            val expenses = listOf(
                DailySalesDraftExpenseEntity(
                    id = "00000000-0000-0000-0000-000000000101",
                    lineId = line.id,
                    description = "توصيل الطلب",
                    amount = "2.5",
                    currencyCode = "USD",
                    exchangeRateSnapshot = "15000",
                    createdAt = line.issuedAt
                ),
                DailySalesDraftExpenseEntity(
                    id = "00000000-0000-0000-0000-000000000102",
                    lineId = line.id,
                    description = "تغليف",
                    amount = "3000",
                    currencyCode = "SYP",
                    exchangeRateSnapshot = "1",
                    createdAt = line.issuedAt + 1
                )
            )
            val repository = DailySalesDraftRepository(database)
            repository.saveSnapshot(sampleHeader(), listOf(line), expenses)

            val posted = repository.postPendingLine(line, expenses)

            val invoice = database.invoiceDao().findById(posted.invoiceId)!!
            assertEquals("SALE", invoice.kind)
            assertEquals(1, database.invoiceDao().getAllInvoices().count { it.invoiceNumber == posted.invoiceNumber })
            assertEquals("4", database.productDao().findById(product.id)!!.stockQuantity)

            val expenseJournals = database.journalEntryDao().getAll()
                .filter { it.sourceType == "DAILY_SALE_EXPENSE" }
            assertEquals(2, expenseJournals.size)
            val usdJournal = expenseJournals.single { it.sourceId == expenses[0].id }
            val sypJournal = expenseJournals.single { it.sourceId == expenses[1].id }
            assertEquals("USD", usdJournal.currencyCode)
            assertEquals("15000", usdJournal.exchangeRateSnapshot)
            assertEquals("SYP", sypJournal.currencyCode)
            assertEquals("1", sypJournal.exchangeRateSnapshot)

            val accountCodes = database.accountDao().getAll().associate { it.id to it.code }
            listOf(usdJournal, sypJournal).forEachIndexed { index, journal ->
                val expectedAmount = expenses[index].amount
                val journalLines = database.journalLineDao().getForEntry(journal.id)
                assertEquals(2, journalLines.size)
                assertDecimal(expectedAmount, journalLines.single { accountCodes[it.accountId] == "7000" }.debit)
                assertDecimal(expectedAmount, journalLines.single { accountCodes[it.accountId] == "1000" }.credit)
                assertEquals(0, journalLines.sumOf { it.debit.toBigDecimal() }
                    .compareTo(journalLines.sumOf { it.credit.toBigDecimal() }))
            }

            val linkedLedger = database.ledgerDao().getAll().filter { it.sourceId == posted.invoiceId }
            assertEquals(4, linkedLedger.size)
            assertEquals(2, linkedLedger.count { it.entryType == "CASH_OUT" })
            assertEquals(2, linkedLedger.count { it.entryType == "EXPENSE" })
            assertEquals(2, linkedLedger.count { it.sourceType == "DAILY_SALE_EXPENSE_CASH" })
            assertEquals(2, linkedLedger.count { it.sourceType == "DAILY_SALE_EXPENSE" })

            // Simulate process death after posting but before the draft line is
            // removed. Retrying must recognize and verify the invoice and both
            // currency-separated expenses without posting any amount twice.
            repository.saveSnapshot(sampleHeader(), listOf(line), expenses)
            repository.postPendingLine(line, expenses)

            assertEquals(1, database.invoiceDao().getAllInvoices().count { it.invoiceNumber == posted.invoiceNumber })
            assertEquals(2, database.journalEntryDao().getAll().count { it.sourceType == "DAILY_SALE_EXPENSE" })
            assertEquals(4, database.ledgerDao().getAll().count { it.sourceId == posted.invoiceId })
            assertEquals("4", database.productDao().findById(product.id)!!.stockQuantity)
            assertTrue(repository.loadSnapshot().lines.isEmpty())
            assertTrue(repository.loadSnapshot().expenses.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun partialPostingRemovesCompletedRowAndRetainsOnlyFailedRows() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            ProductRepository(database).addProduct(
                sku = "DRAFT-PARTIAL-001",
                name = "مادة صالحة للترحيل",
                unit = "قطعة",
                purchaseCost = "2",
                salePrice = "5",
                openingStock = "5",
                note = "اختبار الترحيل الجزئي"
            )
            val product = database.productDao().getAll().single()
            val completedLine = sampleLine(
                id = "00000000-0000-0000-0000-000000000003",
                lineOrder = 0,
                productId = product.id,
                productName = product.name,
                unitName = product.baseUnit,
                currencyCode = "SYP",
                exchangeRate = "1",
                quantity = "1",
                unitFactor = "1",
                unitPrice = "5",
                discount = "0",
                paidAmount = "5"
            )
            val failedLine = sampleLine(
                id = "00000000-0000-0000-0000-000000000004",
                lineOrder = 1,
                productId = "missing-product-after-first-row"
            )
            val completedExpense = DailySalesDraftExpenseEntity(
                id = "partial-expense-completed",
                lineId = completedLine.id,
                description = "تغليف السطر المكتمل",
                amount = "1",
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                createdAt = completedLine.issuedAt
            )
            val failedExpense = DailySalesDraftExpenseEntity(
                id = "partial-expense-retained",
                lineId = failedLine.id,
                description = "توصيل السطر غير المكتمل",
                amount = "2.5",
                currencyCode = "USD",
                exchangeRateSnapshot = "15000",
                createdAt = failedLine.issuedAt
            )
            val repository = DailySalesDraftRepository(database)
            repository.saveSnapshot(
                sampleHeader(),
                listOf(completedLine, failedLine),
                listOf(completedExpense, failedExpense)
            )

            repository.postPendingLine(completedLine, listOf(completedExpense))
            val failure = runCatching {
                repository.postPendingLine(failedLine, listOf(failedExpense))
            }.exceptionOrNull()
            val restored = repository.loadSnapshot()
            val remaining = restored.lines

            assertNotNull(failure)
            assertEquals(listOf(failedLine.id), remaining.map { it.id })
            assertEquals(listOf(failedExpense.id), restored.expenses.map { it.id })
            assertEquals(failedLine.id, restored.expenses.single().lineId)
            assertEquals(1, database.invoiceDao().getAllInvoices().size)
            assertEquals(0, database.productDao().findById(product.id)!!.stockQuantity.toBigDecimal().compareTo(java.math.BigDecimal("4")))
        } finally {
            database.close()
        }
    }

    @Test
    fun failedPostingKeepsThePendingLineForCorrectionAndRetry() = runBlocking {
        val database = newInMemoryDatabase()
        try {
            val line = sampleLine(
                id = "00000000-0000-0000-0000-000000000002",
                lineOrder = 0,
                productId = "missing-product-id"
            )
            val repository = DailySalesDraftRepository(database)
            repository.saveSnapshot(sampleHeader(), listOf(line))

            val failure = runCatching { repository.postPendingLine(line) }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals(1, repository.loadSnapshot().lines.size)
            assertEquals(0, database.invoiceDao().getAllInvoices().size)
        } finally {
            database.close()
        }
    }

    private fun sampleHeader() = DailySalesDraftEntity(
        selectedDay = 1_798_000_000_000L,
        selectedProductId = "archived-product-id",
        selectedProductNameSnapshot = "مادة لم يكتمل إدخالها",
        selectedUnitName = "علبة",
        productSearchQuery = "مادة",
        selectedProductCategoryId = "archived-category-id",
        quantity = "1.75",
        salePrice = "20",
        lineDiscount = "1",
        currencyCode = "USD",
        exchangeRate = "15000",
        paidAmount = "12345",
        paidAmountTouched = true,
        selectedPartyId = "archived-customer-id",
        note = "مسودة غير مكتملة",
        updatedAt = 1_798_000_000_123L
    )

    private fun sampleLine(
        id: String,
        lineOrder: Int,
        productId: String = "archived-product-id",
        productName: String = "مادة محفوظة",
        unitName: String = "علبة",
        currencyCode: String = "USD",
        exchangeRate: String = "15000",
        quantity: String = "1",
        unitFactor: String = "1",
        unitPrice: String = "20",
        discount: String = "0",
        paidAmount: String = "20",
        purchaseCostSnapshot: String? = null
    ) = DailySalesDraftLineEntity(
        id = id,
        lineOrder = lineOrder,
        productId = productId,
        productNameSnapshot = productName,
        quantity = quantity,
        unitName = unitName,
        unitFactor = unitFactor,
        unitPrice = unitPrice,
        discount = discount,
        currencyCode = currencyCode,
        purchaseCostSnapshot = purchaseCostSnapshot,
        exchangeRate = exchangeRate,
        paidAmount = paidAmount,
        customerId = null,
        note = "سطر مسودة",
        issuedAt = 1_798_000_000_123L
    )

    private fun assertDecimal(expected: String, actual: String) {
        assertEquals(
            0,
            java.math.BigDecimal(expected).compareTo(java.math.BigDecimal(actual))
        )
    }

    private fun newInMemoryDatabase(): AccountingDatabase {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, AccountingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}
