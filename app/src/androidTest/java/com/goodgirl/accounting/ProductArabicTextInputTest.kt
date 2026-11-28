package com.goodgirl.accounting

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductArabicTextInputTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun arabicLettersRenderImmediatelyInProductNameUnitAndNoteFields() {
        composeRule.onNodeWithTag("dashboard-products")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product-management-screen").fetchSemanticsNode()

        assertArabicCharactersAppearOneByOne(
            composeRule.onNodeWithTag("product-name").performScrollTo(),
            "عطر الياسمين"
        )
        assertArabicCharactersAppearOneByOne(
            composeRule.onNodeWithTag("product-base-unit").performScrollTo(),
            "عبوة صغيرة"
        )
        assertArabicCharactersAppearOneByOne(
            composeRule.onNodeWithTag("product-note").performScrollTo(),
            "ملاحظات المادة"
        )
    }


    @Test
    fun arabicLettersRenderImmediatelyInDailySalesMaterialSearch() {
        composeRule.onNodeWithText("المبيعات اليومية")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("daily-sales-material-cell").performClick()
        composeRule.waitForIdle()

        assertArabicCharactersAppearOneByOne(
            composeRule.onNodeWithTag("daily-sales-product-search"),
            "عطر الياسمين"
        )
    }

    private fun assertArabicCharactersAppearOneByOne(
        field: SemanticsNodeInteraction,
        expected: String
    ) {
        field.performTextClearance()
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
