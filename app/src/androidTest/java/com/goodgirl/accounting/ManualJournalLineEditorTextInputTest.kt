package com.goodgirl.accounting

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
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

        val firstTag = "manual-journal-memo-0"
        val secondTag = "manual-journal-memo-1"
        val firstField = composeRule.onNodeWithTag(firstTag)
        val secondField = composeRule.onNodeWithTag(secondTag)

        var firstExpected = ""
        "عطر ورد".forEach { character ->
            firstExpected += character
            firstField.performTextInput(character.toString())
            assertEditableText(firstTag, firstExpected)
            composeRule.runOnIdle {
                assertEquals(firstExpected, lines[0].memo)
            }
        }

        var secondExpected = ""
        "ملاحظة عربية".forEach { character ->
            secondExpected += character
            secondField.performTextInput(character.toString())
            assertEditableText(secondTag, secondExpected)
        }

        firstField.performClick()
        assertEditableText(firstTag, "عطر ورد")
        assertEditableText(secondTag, "ملاحظة عربية")
        composeRule.runOnIdle {
            assertEquals("عطر ورد", lines[0].memo)
            assertEquals("ملاحظة عربية", lines[1].memo)
        }
    }

    @OptIn(ExperimentalTestApi::class)
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

        val memoTag = "manual-journal-memo-0"
        val memo = composeRule.onNodeWithTag(memoTag)
        "عطر ورد".forEach { memo.performTextInput(it.toString()) }
        assertEditableText(memoTag, "عطر ورد")

        memo.performTextInputSelection(TextRange(4))
        memo.performTextInput("فاخر ")
        assertEditableText(memoTag, "عطر فاخر ورد")
        composeRule.runOnIdle {
            assertEquals("عطر فاخر ورد", lines[0].memo)
        }

        memo.performTextInputSelection(TextRange(4, 9))
        memo.performTextInput("مميز ")
        assertEditableText(memoTag, "عطر مميز ورد")
        composeRule.runOnIdle {
            assertEquals("عطر مميز ورد", lines[0].memo)
        }
    }

    private fun assertEditableText(tag: String, expected: String) {
        composeRule.waitForIdle()
        val actual = composeRule.onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text
        assertEquals("Editable text for $tag", expected, actual)
    }

    private fun testLines() = mutableStateListOf(
        ManualJournalLineInput(id = "first"),
        ManualJournalLineInput(id = "second")
    )
}
