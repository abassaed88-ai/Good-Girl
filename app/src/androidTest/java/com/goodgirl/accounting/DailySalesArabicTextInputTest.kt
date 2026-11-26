package com.goodgirl.accounting

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DailySalesArabicTextInputTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun arabicLettersRenderImmediatelyInDailySalesSearchAndNoteFields() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = AccountingDatabase.getInstance(context)
        val productId = "arabic-input-test-${UUID.randomUUID()}"
        val timestamp = System.currentTimeMillis()

        runBlocking {
            database.productDao().insert(
                ProductEntity(
                    id = productId,
                    name = "مادة اختبار الإدخال العربي",
                    stockQuantity = "7",
                    purchaseCost = "1",
                    salePrice = "2",
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )
        }

        try {
            composeRule.onNodeWithText("المبيعات اليومية")
                .performScrollTo()
                .performClick()

            val searchField = composeRule.onNodeWithTag("daily-sales-product-search")
            assertArabicCharactersAppearOneByOne(searchField, "عطر ورد")
            searchField.performTextClearance()

            composeRule.onNodeWithText("اختيار المادة").performClick()
            composeRule.onNodeWithText("مادة اختبار الإدخال العربي — المتوفر: 7 قطعة")
                .performClick()

            val noteField = composeRule.onNodeWithTag("daily-sales-note").performScrollTo()
            assertArabicCharactersAppearOneByOne(noteField, "ملاحظة عربية")
        } finally {
            runBlocking {
                database.productDao().archive(productId, System.currentTimeMillis())
            }
        }
    }

    private fun assertArabicCharactersAppearOneByOne(
        field: SemanticsNodeInteraction,
        expected: String
    ) {
        var typed = ""
        expected.forEach { character ->
            typed += character
            field.performTextInput(character.toString())
            composeRule.waitForIdle()
            val visibleValue = field.fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text
            assertEquals("Arabic input after '$character'", typed, visibleValue)
        }
    }
}
