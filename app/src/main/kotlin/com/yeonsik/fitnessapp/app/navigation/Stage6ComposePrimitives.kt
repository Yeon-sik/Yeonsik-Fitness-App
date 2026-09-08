package com.yeonsik.fitnessapp.app.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*

/*
 * Stage 6 navigation adapters remain available for the screens that already use
 * the shared core UI. The App* functions below preserve the current Compose
 * screen contract while the screens move into their feature packages.
 */
internal val Stage6Spacing = FitnessSpacing

@Composable
internal fun Stage6Header(title: String, subtitle: String? = null, back: (() -> Unit)? = null) =
    FitnessHeader(title, subtitle, back)

@Composable
internal fun Stage6Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    FitnessCard(modifier = modifier, content = content)

@Composable
internal fun Stage6Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) = FitnessButton(onClick, modifier, enabled, content)

@Composable
internal fun Stage6OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) = FitnessOutlinedButton(onClick, modifier, enabled, destructive, selected, content)

@Composable
internal fun Stage6TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions: KeyboardActions? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) = FitnessTextField(value, onValueChange, modifier, label, singleLine,
    keyboardOptions, keyboardActions, visualTransformation)

@Composable
internal fun Stage6DataRow(title: String, detail: String, modifier: Modifier = Modifier) =
    FitnessDataRow(title, detail, modifier)

internal object AppSpacing {
    val small = 8.dp
    val gap = 12.dp
    val card = 16.dp
    val section = 24.dp
    val touch = 48.dp
}

@Composable
internal fun AppHeader(title: String, subtitle: String? = null, back: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = AppSpacing.small),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        if (back != null) androidx.compose.material3.TextButton(onClick = back) { Text("뒤로") }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), content = content)
}

@Composable
internal fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                      content: @Composable RowScope.() -> Unit) {
    Button(onClick, modifier.heightIn(min = AppSpacing.touch), enabled = enabled,
        contentPadding = PaddingValues(horizontal = AppSpacing.card, vertical = AppSpacing.small), content = content)
}

@Composable
internal fun AppOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                              destructive: Boolean = false, selected: Boolean = false,
                              content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier.heightIn(min = AppSpacing.touch), enabled = enabled,
        border = BorderStroke(1.dp, when {
            destructive -> MaterialTheme.colorScheme.error
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        }), content = content)
}

@Composable
internal fun AppTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
                         label: @Composable (() -> Unit)? = null, singleLine: Boolean = true,
                         keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
                         keyboardActions: KeyboardActions? = null,
                         visualTransformation: VisualTransformation = VisualTransformation.None) {
    OutlinedTextField(value, onValueChange, modifier, label = label, singleLine = singleLine,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions ?: KeyboardActions.Default,
        visualTransformation = visualTransformation)
}

@Composable
internal fun AppDataRow(title: String, detail: String, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.card)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
