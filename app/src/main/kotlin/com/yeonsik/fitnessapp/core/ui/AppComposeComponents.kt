package com.yeonsik.fitnessapp.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Compatibility-stable App* visuals used by the current feature screens. */
internal object AppSpacing {
    val small = FitnessSpacing.small
    val gap = FitnessSpacing.gap
    val card = FitnessSpacing.card
    val section = FitnessSpacing.section
    val touch = FitnessSpacing.touch
}

@Composable
internal fun AppHeader(title: String, subtitle: String? = null, back: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = AppSpacing.small),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        if (back != null) TextButton(onClick = back) { Text("뒤로") }
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier,
        shape = FitnessShape.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

@Composable
internal fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick,
        modifier.heightIn(min = AppSpacing.touch),
        enabled = enabled,
        shape = FitnessShape.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = LocalFitnessColors.current.action,
            contentColor = LocalFitnessColors.current.onAction
        ),
        contentPadding = PaddingValues(
            horizontal = AppSpacing.card,
            vertical = AppSpacing.small
        ),
        content = content
    )
}

@Composable
internal fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick,
        modifier.heightIn(min = AppSpacing.touch),
        enabled = enabled,
        shape = FitnessShape.button,
        border = if (selected) BorderStroke(1.dp, LocalFitnessColors.current.action) else null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = when {
                destructive -> MaterialTheme.colorScheme.surface
                selected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = when {
                destructive -> MaterialTheme.colorScheme.error
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
        ),
        content = content
    )
}

@Composable
internal fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value,
        onValueChange,
        modifier,
        label = label,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions ?: KeyboardActions.Default,
        visualTransformation = visualTransformation,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = FitnessShape.input,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
internal fun AppDataRow(title: String, detail: String, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.card), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun StateMessage(title: String, message: String) {
    AppHeader(title)
    AppCard(Modifier.fillMaxWidth()) { Text(message, Modifier.padding(AppSpacing.card)) }
}
