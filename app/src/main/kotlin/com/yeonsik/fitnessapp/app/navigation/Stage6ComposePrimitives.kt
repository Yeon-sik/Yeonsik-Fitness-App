package com.yeonsik.fitnessapp.app.navigation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import com.yeonsik.fitnessapp.core.ui.*

// Navigation migration adapters. All tokens and visuals are owned by core.ui.
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
