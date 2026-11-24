package com.goodgirl.accounting

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualJournalLineEditorTextInputTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun arabicLettersRenderImmediatelyAndRemainWhenSwitchingRows() {
        val lines = testLines()
        composeRule.setContent {
            MaterialTheme {
                Column {
                    lines.forEachIndexed { index, line ->
                        key(line.id) {
                            ManualJournalLineEditor(
                                index = index,
                                line = line,
                                accounts = emptyList(),
                                onChange = { updated -> lines[index] = updated },
                                onRemove = { lines.remove(line) }
                            )
                        }
                    }
                }
            }
        }

        val firstField = composeRule.onNodeWithTag("manual-journal-memo-first")
        val secondField = composeRule.onNodeWithTag("manual-journal-memo-second")

        firstField.performTextInput("عطر ورد")
        firstField.assertTextEquals("عطر ورد")
        composeRule.runOnIdle {
            assertEquals("عطر ورد", lines[0].memo)
        }

        secondField.performTextInput("ملاحظة عربية")
        secondField.assertTextEquals("ملاحظة عربية")
        composeRule.runOnIdle {
            assertEquals("عطر ورد", lines[0].memo)
            assertEquals("ملاحظة عربية", lines[1].memo)
        }

        firstField.performClick()
        firstField.assertTextEquals("عطر ورد")
        secondField.assertTextEquals("ملاحظة عربية")
    }

    @Test
    fun arabicTextCanBeEditedInTheMiddleWithoutLosingAdjacentCharacters() {
        val lines = testLines()
        composeRule.setContent {
            MaterialTheme {
                Column {
                    lines.forEachIndexed { index, line ->
                        key(line.id) {
                            ManualJournalLineEditor(
                                index = index,
                                line = line,
                                accounts = emptyList(),
                                onChange = { updated -> lines[index] = updated },
                                onRemove = { lines.remove(line) }
                            )
                        }
                    }
                }
            }
        }

        val memo = composeRule.onNodeWithTag("manual-journal-memo-first")
        memo.performTextInput("عطر ورد")
        memo.performTextInputSelection(TextRange(4))
        memo.performTextInput("فاخر ")
        memo.assertTextEquals("عطر فاخر ورد")

        memo.performTextInputSelection(TextRange(4, 9))
        memo.performTextInput("مميز ")
        memo.assertTextEquals("عطر مميز ورد")
        composeRule.runOnIdle {
            assertEquals("عطر مميز ورد", lines[0].memo)
        }
    }

    private fun testLines() = mutableStateListOf(
        ManualJournalLineInput(id = "first"),
        ManualJournalLineInput(id = "second")
    )
}
