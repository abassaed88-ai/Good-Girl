package com.goodgirl.accounting

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ArabicTextFieldStateTest {

    @Test
    fun unchangedExternalTextPreservesTheImeCompositionRange() {
        val composingValue = TextFieldValue(
            text = "م",
            selection = TextRange(1),
            composition = TextRange(0, 1)
        )

        assertEquals(
            composingValue,
            reconcileArabicTextFieldValue(composingValue, "م")
        )
    }

    @Test
    fun externallyChangedTextMovesSelectionToTheEndAndClearsOldComposition() {
        val oldValue = TextFieldValue(
            text = "م",
            selection = TextRange(1),
            composition = TextRange(0, 1)
        )

        val updated = reconcileArabicTextFieldValue(oldValue, "ملاحظة")

        assertEquals("ملاحظة", updated.text)
        assertEquals(TextRange("ملاحظة".length), updated.selection)
        assertEquals(null, updated.composition)
    }
}
