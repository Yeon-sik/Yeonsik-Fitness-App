package com.yeonsik.fitnessapp.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun FitnessHeader(title: String, subtitle: String? = null, back: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = FitnessSpacing.small),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
        if (back != null) {
            TextButton(onClick = back, contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier.heightIn(min = FitnessSpacing.touch)) { Text("‹ 뒤로") }
        }
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FitnessSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = FitnessSpacing.section),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
fun FitnessCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
                content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    if (onClick == null) {
        Card(modifier, shape = FitnessShape.card, colors = colors, border = border,
            elevation = elevation, content = content)
    } else {
        Card(onClick, modifier, shape = FitnessShape.card, colors = colors, border = border,
            elevation = elevation, content = content)
    }
}

@Composable
fun FitnessButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                  content: @Composable RowScope.() -> Unit) {
    Button(onClick, modifier.heightIn(min = FitnessSpacing.touch), enabled = enabled,
        shape = FitnessShape.button,
        contentPadding = PaddingValues(horizontal = FitnessSpacing.card, vertical = FitnessSpacing.gap),
        colors = ButtonDefaults.buttonColors(containerColor = LocalFitnessColors.current.action,
            contentColor = LocalFitnessColors.current.onAction), content = content)
}

@Composable
fun FitnessOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                          destructive: Boolean = false, selected: Boolean = false,
                          content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier.heightIn(min = FitnessSpacing.touch)
        .semantics { this.selected = selected }, enabled = enabled,
        shape = FitnessShape.button,
        contentPadding = PaddingValues(horizontal = FitnessSpacing.card, vertical = FitnessSpacing.gap),
        border = BorderStroke(1.dp, if (selected) LocalFitnessColors.current.action else MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = when {
                destructive -> MaterialTheme.colorScheme.error
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
        ), content = content)
}

@Composable
fun FitnessTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
                     label: @Composable (() -> Unit)? = null, singleLine: Boolean = true,
                     keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                     keyboardActions: KeyboardActions? = null,
                     visualTransformation: VisualTransformation = VisualTransformation.None) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value, onValueChange, modifier, label = label, singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge, shape = FitnessShape.input,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions ?: KeyboardActions(onDone = { focus.clearFocus() }),
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ))
}

@Composable
fun FitnessFactCard(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    FitnessCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(FitnessSpacing.card), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Metrics reflow with accessibility font sizes instead of clipping their values. */
@Composable
fun FitnessFactRow(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    if (LocalDensity.current.fontScale >= 1.3f) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
            first(); second()
        }
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap),
            verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f).fillMaxHeight(), propagateMinConstraints = true) { first() }
            Box(Modifier.weight(1f).fillMaxHeight(), propagateMinConstraints = true) { second() }
        }
    }
}

/** Repeated record/summary content uses the same title, detail and surface rhythm. */
@Composable
fun FitnessDataRow(title: String, detail: String, modifier: Modifier = Modifier) {
    FitnessCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(FitnessSpacing.card), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
