package com.goodgirl.accounting

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField as Material3OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextRange
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Keeps Compose's full TextFieldValue, including the IME composing range,
 * while exposing a String state to the existing screens and repositories.
 */
internal fun reconcileArabicTextFieldValue(
    current: TextFieldValue,
    externalText: String
): TextFieldValue {
    if (current.text == externalText) return current
    return TextFieldValue(
        text = externalText,
        selection = TextRange(externalText.length)
    )
}

@Composable
internal fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    interactionSource: MutableInteractionSource? = null
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text = value)) }

    // Parent state is plain String for compatibility. Do not recreate the
    // TextFieldValue while the text matches: doing so would discard IME
    // composition and can make in-progress Arabic input appear only later.
    SideEffect {
        textFieldValue = reconcileArabicTextFieldValue(textFieldValue, value)
    }

    Material3OutlinedTextField(
        value = textFieldValue,
        onValueChange = { changed ->
            textFieldValue = changed
            onValueChange(changed.text)
        },
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        label = label,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = shape,
        interactionSource = interactionSource
    )
}
