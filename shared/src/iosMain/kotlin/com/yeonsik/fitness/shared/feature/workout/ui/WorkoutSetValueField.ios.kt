@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.yeonsik.fitness.shared.feature.workout.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitView
import com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens
import kotlinx.cinterop.ObjCAction
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSSelectorFromString
import platform.UIKit.*

internal class SetKeyboard(private val labels: List<String>, private val done: () -> Unit) {
    val fields = mutableMapOf<String, UITextField>()
    fun advance(label: String) {
        val next = labels.getOrNull(labels.indexOf(label) + 1)?.let(fields::get)
        if (next != null) next.becomeFirstResponder()
        else {
            fields[label]?.resignFirstResponder()
            done()
        }
    }
}

internal val LocalSetKeyboard = staticCompositionLocalOf<SetKeyboard?> { null }

@Composable
actual fun WorkoutSetValueField(
    value: String, onValueChange: (String) -> Unit, label: String,
    keyboardOptions: KeyboardOptions, keyboardActions: KeyboardActions,
    suffix: String?, enabled: Boolean, modifier: Modifier
) {
    val publish by rememberUpdatedState(onValueChange)
    val keyboard = LocalSetKeyboard.current
    val advance by rememberUpdatedState { keyboard?.advance(label) }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(FitnessComposeTokens.INPUT_RADIUS_DP.dp)
    DisposableEffect(keyboard, label) { onDispose { keyboard?.fields?.remove(label) } }
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).border(if (focused) 2.dp else 1.dp,
        if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
        .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        UIKitView(
            factory = {
                val field = object : UITextField(CGRectMake(0.0, 0.0, 0.0, 0.0)) {
                    @ObjCAction fun edit() { publish(text.orEmpty()) }
                    @ObjCAction fun begin() { focused = true }
                    @ObjCAction fun end() { focused = false }
                    @ObjCAction fun nextField() { advance() }
                }
                field.apply {
                    placeholder = "—"
                    textAlignment = NSTextAlignmentCenter
                    font = UIFont.preferredFontForTextStyle(UIFontTextStyleBody)
                    adjustsFontForContentSizeCategory = true
                    keyboardType = if (keyboardOptions.keyboardType == KeyboardType.Decimal) UIKeyboardTypeDecimalPad else UIKeyboardTypeNumberPad
                    addTarget(this, NSSelectorFromString(field::edit.name), UIControlEventEditingChanged)
                    addTarget(this, NSSelectorFromString(field::begin.name), UIControlEventEditingDidBegin)
                    addTarget(this, NSSelectorFromString(field::end.name), UIControlEventEditingDidEnd)
                    inputAccessoryView = UIButton(frame = CGRectMake(0.0, 0.0, 320.0, 44.0)).also { button ->
                        button.setTitle(if (keyboardOptions.imeAction == ImeAction.Done) "입력 저장" else "다음", forState = UIControlStateNormal)
                        button.setTitleColor(UIColor.systemBlueColor, forState = UIControlStateNormal)
                        button.backgroundColor = UIColor.secondarySystemBackgroundColor
                        button.addTarget(this, NSSelectorFromString(field::nextField.name), UIControlEventTouchUpInside)
                    }
                }
            },
            modifier = Modifier.weight(1f).height(48.dp),
            // The row scrolls in Compose, but the editable rectangle itself belongs to UIKit.
            properties = UIKitInteropProperties(
                interactionMode = UIKitInteropInteractionMode.NonCooperative,
                isNativeAccessibilityEnabled = true
            ),
            update = { field ->
                field.accessibilityLabel = label
                field.enabled = enabled
                keyboard?.fields?.set(label, field)
                if (field.text.orEmpty() != value) field.text = value
            }
        )
        if (suffix != null) Text(suffix, style = MaterialTheme.typography.labelSmall)
    }
}
