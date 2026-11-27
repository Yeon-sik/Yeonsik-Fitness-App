package com.yeonsik.fitness.shared.feature.body.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType

@Composable
internal actual fun BodyWeightInput(
    value: String, onValueChange: (String) -> Unit, label: String, error: String?, modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        modifier = modifier.testTag("weight-input")
    )
}
