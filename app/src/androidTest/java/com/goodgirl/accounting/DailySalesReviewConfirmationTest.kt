package com.goodgirl.accounting

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.DailySalesDraftRepository
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.DailySalesDraftEntity
import com.goodgirl.accounting.data.local.DailySalesDraftLineEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.data.local.ProductUnitEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DailySalesReviewConfirmationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dailySalesPostingRequiresReviewConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = AccountingDatabase.getInstance(context)
        val repository = DailySalesDraftRepository(database)
        val productId = "review-test-" + UUID.randomUUID()
        val timestamp = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        runBlocking {
            database.productDao().insert(
                ProductEntity(
                    id = productId,
                    sku = "REVIEW-TEST",
                    name = "مادة اختبار مراجعة الترحيل",
                    baseUnit = "قطعة",
                    purchaseCost = "2",
                    salePrice = "5",
                    priceCurrencyCode = "SYP",
                    stockQuantity = "10",
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )
            database.productUnitDao().insert(
                ProductUnitEntity(
                    id = UUID.randomUUID().toString(),
                    productId = productId,
                    unitName = "قطعة",
                    baseFactor = "1",
                    isBaseUnit = true
                )
            )
            repository.saveSnapshot(
                DailySalesDraftEntity(
                    selectedDay = todayStart,
                    updatedAt = timestamp
                ),
                listOf(
                    DailySalesDraftLineEntity(
                        id = UUID.randomUUID().toString(),
                        lineOrder = 0,
                        productId = productId,
                        productNameSnapshot = "مادة اختبار مراجعة الترحيل",
                        quantity = "2",
                        unitName = "قطعة",
                        unitFactor = "1",
                        unitPrice = "5",
                        discount = "0",
                        currencyCode = "SYP",
                        exchangeRate = "1",
                        paidAmount = "10",
                        customerId = null,
                        note = "اختبار شاشة المراجعة",
                        issuedAt = timestamp,
                        purchaseCostSnapshot = "2"
                    )
                )
            )
        }

        val invoiceCountBefore = runBlocking { database.invoiceDao().getAllInvoices().size }

        try {
            composeRule.onNodeWithText("المبيعات اليومية")
                .performScrollTo()
                .performClick()

            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText("مراجعة السطور قبل الترحيل")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("مراجعة السطور قبل الترحيل")
                .performScrollTo()
                .performClick()

            composeRule.onNodeWithText("مراجعة المبيعات قبل الترحيل").assertIsDisplayed()
            composeRule.onNodeWithText("تأكيد الترحيل").assertIsDisplayed()
            composeRule.onNodeWithText("عودة للتعديل").assertIsDisplayed()

            val invoiceCountAfterOpeningReview = runBlocking {
                database.invoiceDao().getAllInvoices().size
            }
            assertEquals(invoiceCountBefore, invoiceCountAfterOpeningReview)

            composeRule.onNodeWithText("عودة للتعديل").performClick()
            composeRule.waitForIdle()
            assertEquals(invoiceCountBefore, runBlocking { database.invoiceDao().getAllInvoices().size })
        } finally {
            runBlocking {
                repository.clearDraft()
                database.productUnitDao().deleteForProduct(productId)
                database.productDao().archive(productId, System.currentTimeMillis())
            }
        }
    }
}
