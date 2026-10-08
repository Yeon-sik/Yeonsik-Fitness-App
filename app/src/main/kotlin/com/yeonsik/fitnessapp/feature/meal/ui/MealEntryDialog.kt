package com.yeonsik.fitnessapp.feature.meal.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.data.NutritionTotals
import com.yeonsik.fitnessapp.feature.nutrition.model.foodPortionTotals
import com.yeonsik.fitnessapp.feature.nutrition.ui.*
import java.time.LocalTime
import java.util.Locale

@Composable
internal fun MealEntryDialog(actions: MealScreenActions, editor: MealUiState.Ready, priceTraceState: PriceTraceUiState, mealLabel: String) {
    val totals = if (editor.diningOut) diningNutritionTotals(editor) else foodPortionTotals(editor.foodPortions)
    val inputEnabled = !editor.saving && !editor.draftLoading
    val diningPortionValid = editor.diningPortion.trim().toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true
    val diningEditor = rememberDiningOutEditorState(editor, priceTraceState)
    NutritionEntryFrame(
        "$mealLabel 기록", actions::closeDraft, subtitle = editor.date, closeEnabled = !editor.saving,
        footer = {
            NutritionTotalPreview(totals, label = if (editor.diningOut) "${editor.diningPortion}인분 · 추정" else "식품 ${editor.foodPortions.size}개")
            editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            AppButton(if (editor.diningOut) actions::saveDiningOut else actions::saveFood, Modifier.fillMaxWidth().testTag("meal-entry-save"),
                enabled = inputEnabled && (editor.diningOut && diningPortionValid || !editor.diningOut && editor.foodPortions.isNotEmpty())) {
                Text(if (editor.saving) "저장 중…" else "$mealLabel 기록하기")
            }
        }
    ) {
        NutritionFormSection("등록 순서대로 기록해요", Modifier.fillMaxWidth(), "끼니 번호는 하루마다 1끼부터 시작합니다.") {
            MealTimeControl(editor.draft.time, actions::updateTime, enabled = inputEnabled)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppOutlinedButton(actions::chooseFood, Modifier.weight(1f), enabled = inputEnabled, selected = !editor.diningOut) { Text("식품으로 구성") }
            AppOutlinedButton(actions::chooseDiningOut, Modifier.weight(1f), enabled = inputEnabled, selected = editor.diningOut) { Text("외식") }
        }
        if (editor.draftLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        editor.notice?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        if (editor.diningOut) DiningOutEditor(actions, editor, priceTraceState, diningEditor) else FoodMealEditor(actions, editor)
    }
}

@Composable
internal fun MealTimeControl(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val context = LocalContext.current
    AppOutlinedButton(onClick = {
        val time = runCatching { LocalTime.parse(value) }.getOrElse { LocalTime.now() }
        TimePickerDialog(context, { _, hour, minute -> onChange(String.format(Locale.ROOT, "%02d:%02d", hour, minute)) },
            time.hour, time.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
    }, modifier = modifier.fillMaxWidth(), enabled = enabled) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text("섭취 시각", style = MaterialTheme.typography.labelMedium)
            Text(value.ifBlank { "시간 선택" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Missing values stay absent even when the portion changes. */
internal fun diningNutritionTotals(editor: MealUiState.Ready): NutritionTotals {
    val amount = editor.diningPortion.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val draft = editor.draft
    val profile = NutritionProfile.builder().apply {
        if (amount != null) {
            listOf(NutritionProfile.CALORIES_KCAL to draft.calories, NutritionProfile.PROTEIN_GRAMS to draft.protein,
                NutritionProfile.CARBS_GRAMS to draft.carbs, NutritionProfile.FAT_GRAMS to draft.fat,
                NutritionProfile.SODIUM_MG to draft.sodium, NutritionProfile.SUGARS_GRAMS to draft.sugars,
                NutritionProfile.SATURATED_FAT_GRAMS to draft.saturatedFat).forEach { (key, raw) ->
                raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                    ?.let { (it * amount).takeIf(Double::isFinite) }?.let { value(key, it) }
            }
        }
    }.build()
    return NutritionTotals.builder().add(profile).build()
}
