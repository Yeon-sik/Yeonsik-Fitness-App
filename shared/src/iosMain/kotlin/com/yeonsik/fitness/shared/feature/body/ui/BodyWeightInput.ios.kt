package com.yeonsik.fitness.shared.feature.body.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSSelectorFromString
import platform.UIKit.*

@Composable
@OptIn(ExperimentalForeignApi::class)
internal actual fun BodyWeightInput(
    value: String, onValueChange: (String) -> Unit, label: String, error: String?, modifier: Modifier
) {
    val publish by rememberUpdatedState(onValueChange)
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        UIKitView(
            factory = {
                val field = object : UITextField(CGRectMake(0.0, 0.0, 0.0, 0.0)) {
                    @ObjCAction fun editingChanged() { publish(text.orEmpty()) }
                }
                field.apply {
                    keyboardType = UIKeyboardTypeDecimalPad
                    borderStyle = UITextBorderStyle.UITextBorderStyleRoundedRect
                    adjustsFontForContentSizeCategory = true
                    font = UIFont.preferredFontForTextStyle(UIFontTextStyleBody)
                    accessibilityIdentifier = "weight-input"
                    addTarget(this, NSSelectorFromString(field::editingChanged.name), UIControlEventEditingChanged)
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true),
            update = { field ->
                field.accessibilityLabel = label
                field.accessibilityHint = error
                if (field.text.orEmpty() != value) field.text = value
            }
        )
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
