package com.yeonsik.fitnessapp.feature.meal.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.yeonsik.fitnessapp.core.ui.AppCard
import com.yeonsik.fitnessapp.core.ui.AppOutlinedButton
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.core.ui.AppTextField
import com.yeonsik.fitnessapp.data.MealEntryPolicy
import com.yeonsik.fitnessapp.data.NutritionCalculator
import com.yeonsik.fitnessapp.data.NutritionProfile
import java.time.LocalTime
import java.util.Locale

@Composable
internal fun MealTimeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val normalized = runCatching { MealEntryPolicy.requireMealTime(value) }.getOrNull()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        Text("먹은 시간", style = MaterialTheme.typography.titleSmall)
        AppOutlinedButton(
            onClick = { focusManager.clearFocus(); showPicker = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        ) {
            Text(normalized ?: "시간 선택", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium)
            Text("변경")
        }
    }
    if (showPicker && enabled) {
        DisposableEffect(context) {
            val initial = normalized?.let(LocalTime::parse) ?: LocalTime.now()
            val dialog = TimePickerDialog(
                context,
                { _, hour, minute ->
                    latestOnValueChange(String.format(Locale.ROOT, "%02d:%02d", hour, minute))
                    showPicker = false
                },
                initial.hour, initial.minute, DateFormat.is24HourFormat(context)
            )
            dialog.setTitle("먹은 시간")
            dialog.setOnDismissListener { showPicker = false }
            dialog.show()
            onDispose {
                dialog.setOnDismissListener(null)
                dialog.dismiss()
            }
        }
    }
}

@Composable
internal fun MealNutritionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    required: Boolean = true
) {
    val focusManager = LocalFocusManager.current
    val error = mealNutritionInputError(value, label, required).takeIf { value.isNotBlank() }
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text("$label $unit${if (required) "" else " (선택)"}") },
        enabled = enabled,
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = {
            focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next)
        })
    )
}

@Composable
internal fun MealNutritionPreview(profile: NutritionProfile, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column(Modifier.padding(AppSpacing.card), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text("먹은 양 기준 영양정보", style = MaterialTheme.typography.titleSmall)
            Text(mealNutritionValue(profile, NutritionProfile.CALORIES_KCAL, "kcal"),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("탄수화물 ${mealNutritionValue(profile, NutritionProfile.CARBS_GRAMS, "g")} · " +
                "단백질 ${mealNutritionValue(profile, NutritionProfile.PROTEIN_GRAMS, "g")} · " +
                "지방 ${mealNutritionValue(profile, NutritionProfile.FAT_GRAMS, "g")}",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun mealNutritionValue(profile: NutritionProfile, key: String, unit: String): String =
    profile.value(key)?.let { "${NutritionCalculator.trim(it)} $unit" } ?: "미확인"

@Composable
internal fun MealInputNotice(message: String, modifier: Modifier = Modifier) {
    Text(message, modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
