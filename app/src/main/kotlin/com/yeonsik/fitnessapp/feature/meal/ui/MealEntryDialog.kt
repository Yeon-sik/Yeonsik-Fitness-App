package com.yeonsik.fitnessapp.feature.meal.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.feature.nutrition.ui.*
import java.time.LocalTime
import java.util.Locale

@Composable
internal fun MealEntryDialog(actions: MealScreenActions, editor: MealUiState.Ready, priceTraceState: PriceTraceUiState, mealLabel: String) {
    val foods = mealFoodItems(editor)
    val totals = if (editor.diningOut) diningNutritionTotals(editor) else mealFoodNutritionTotals(foods)
    val inputEnabled = !editor.saving && !editor.draftLoading
    val validInput = if (editor.diningOut) diningMealRegistrationError(editor) == null
        else foods.isNotEmpty() && foods.all { mealConsumedQuantity(it.quantity, it.consumedPercent) != null }
    val diningEditor = rememberDiningOutEditorState(editor, priceTraceState)
    NutritionEntryFrame(
        "$mealLabel 기록", actions::closeDraft, subtitle = editor.date, closeEnabled = !editor.saving,
        showScrollToTop = true,
        footer = {
            NutritionTotalPreview(totals, Modifier.testTag("meal-entry-totals"),
                label = if (editor.diningOut) "메뉴 ${diningMealMenus(editor).size}개 · 섭취 합계" else "식품 ${foods.size}개")
            editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            AppButton(if (editor.diningOut) actions::saveDiningOut else actions::saveFood, Modifier.fillMaxWidth().testTag("meal-entry-save"),
                enabled = inputEnabled && validInput) {
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
@OptIn(ExperimentalLayoutApi::class)
internal fun MealConsumedPercentControl(value: String, onChange: (String) -> Unit, enabled: Boolean, tag: String) {
    val error = mealConsumedPercentError(value)
    AppTextField(value, onChange, Modifier.fillMaxWidth().testTag(tag),
        label = { Text("먹은 비율 (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        enabled = enabled, isError = error != null,
        supportingText = { Text(error ?: "이 메뉴의 전체 양을 모두 먹었을 때가 100%입니다.") })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(25, 50, 75, 100).forEach { percent ->
            AppOutlinedButton({ onChange(percent.toString()) }, Modifier.testTag("$tag-$percent"), enabled = enabled,
                selected = mealConsumedFraction(value) == percent / 100.0) { Text("$percent%") }
        }
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
