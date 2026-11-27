package com.yeonsik.fitness.shared.feature.workout.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens

@Composable
actual fun WorkoutSetValueField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    suffix: String?,
    enabled: Boolean,
    modifier: Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var editorCreated by remember { mutableStateOf(false) }
    var requestEditorFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val activateEditor = { editorCreated = true; requestEditorFocus = true }
    val fieldModifier = modifier.fillMaxWidth()
        .heightIn(min = 48.dp)
        .border(if (focused) 2.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(FitnessComposeTokens.INPUT_RADIUS_DP.dp))
        .semantics { contentDescription = label }
    val bodyStyle = MaterialTheme.typography.bodyMedium
    val valueStyle = bodyStyle.copy(fontSize = bodyStyle.fontSize * WORKOUT_SET_CONTENT_SCALE,
        lineHeight = bodyStyle.lineHeight * WORKOUT_SET_CONTENT_SCALE,
        color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, fontFeatureSettings = "tnum")
    val suffixStyle = MaterialTheme.typography.labelSmall
    val decoration: @Composable (@Composable () -> Unit) -> Unit = { innerTextField ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .padding(horizontal = 4.dp * WORKOUT_SET_CONTENT_SCALE, vertical = FitnessComposeTokens.FORM_ITEM_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp * WORKOUT_SET_CONTENT_SCALE)) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) Text("—", Modifier.clearAndSetSemantics {},
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                innerTextField()
            }
            if (suffix != null) Text(suffix, Modifier.clearAndSetSemantics {},
                style = suffixStyle.copy(fontSize = suffixStyle.fontSize * WORKOUT_SET_CONTENT_SCALE,
                    lineHeight = suffixStyle.lineHeight * WORKOUT_SET_CONTENT_SCALE),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    // Do not create text-input sessions for every untouched cell on first entry.
    // A tap, keyboard focus or accessibility edit activates exactly that cell;
    // it remains mounted afterward so selection/IME state is never discarded on blur.
    if (!editorCreated) {
        Box(fieldModifier
            .onFocusChanged { if (it.isFocused && enabled) activateEditor() }
            .focusable(enabled)
            .focusProperties { canFocus = false }
            .clickable(enabled = enabled, onClick = activateEditor)
            .semantics {
                editableText = AnnotatedString(value)
                imeAction = keyboardOptions.imeAction
                setText { text ->
                    if (enabled) { onValueChange(text.text); true } else false
                }
            }, contentAlignment = Alignment.Center) {
            decoration { Text(value, style = valueStyle, maxLines = 1, softWrap = false,
                modifier = Modifier.clearAndSetSemantics {}) }
        }
        return
    }
    LaunchedEffect(requestEditorFocus) {
        if (requestEditorFocus) {
            focusRequester.requestFocus()
            requestEditorFocus = false
        }
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = fieldModifier.focusRequester(focusRequester),
        singleLine = true,
        enabled = enabled,
        textStyle = valueStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = decoration
    )
}
