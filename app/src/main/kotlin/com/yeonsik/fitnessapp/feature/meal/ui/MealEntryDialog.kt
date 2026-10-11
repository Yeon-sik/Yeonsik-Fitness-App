package com.yeonsik.fitnessapp.feature.meal.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.feature.nutrition.ui.*
import com.yeonsik.fitnessapp.feature.nutrition.model.FoodPortionDraft
import com.yeonsik.fitnessapp.data.NutritionProfile
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
internal fun MealEntryDialog(actions: MealScreenActions, editor: MealUiState.Ready, priceTraceState: PriceTraceUiState, mealLabel: String) {
    val foods = mealFoodItems(editor)
    val totals = mealEntryNutritionTotals(editor)
    val inputEnabled = !editor.saving && !editor.draftLoading
    val validInput = mealEntryRegistrationError(editor) == null
    val diningEditor = rememberDiningOutEditorState(editor, priceTraceState)
    val manualEntryRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    NutritionEntryFrame(
        "$mealLabel 기록", actions::closeDraft, subtitle = editor.date, closeEnabled = !editor.saving,
        showScrollToTop = true,
        introduction = {
            NutritionFormSection("섭취 시각", Modifier.fillMaxWidth()) {
                MealTimeControl(editor.draft.time, actions::updateTime, enabled = inputEnabled)
            }
            NutritionTotalPreview(totals, Modifier.testTag("meal-entry-totals"),
                label = "담은 항목 ${foods.size + diningMealMenus(editor).size}개 · 섭취 합계")
        },
        collapsedSummary = {
            Text(editor.draft.time, style = MaterialTheme.typography.labelLarge)
            FlowRow(Modifier.testTag("meal-entry-summary"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("K" to NutritionProfile.CALORIES_KCAL, "C" to NutritionProfile.CARBS_GRAMS,
                    "P" to NutritionProfile.PROTEIN_GRAMS, "F" to NutritionProfile.FAT_GRAMS).forEach { (label, nutrient) ->
                    Text("$label: ${nutritionTotalText(totals.total(nutrient))}${if (label == "K") " kcal" else " g"}",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        },
        footer = {
            editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            AppButton(actions::saveFood, Modifier.fillMaxWidth().testTag("meal-entry-save"), enabled = inputEnabled && validInput) {
                Text(if (editor.saving) "저장 중…" else "$mealLabel 기록하기")
            }
        }
    ) {
        FlowRow(Modifier.fillMaxWidth().testTag("meal-entry-categories"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealCatalogCategory.entries.forEach { category ->
                FilterChip(selected = editor.category == category, enabled = inputEnabled,
                    onClick = { actions.chooseCategory(category) }, label = { Text(category.label) },
                    modifier = Modifier.testTag("meal-category-${category.name}"))
            }
        }
        if (editor.draftLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        editor.notice?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        if (editor.diningOut) DiningOutEditor(actions, editor, priceTraceState, diningEditor, manualEntryRequester) else FoodMealEditor(actions, editor)
        if (foods.isNotEmpty() || editor.diningMenus.isNotEmpty()) Text("이번 식단에 담은 내용", style = MaterialTheme.typography.titleMedium)
        FoodPortionList(foods.map { FoodPortionDraft(it.food, it.quantity, it.amountMode) }, actions::updateFoodQuantity,
            onRemove = { focusManager.clearFocus(); actions.removeFood(it) }, enabled = inputEnabled,
            modifier = Modifier.fillMaxWidth().testTag("meal-food-portions"), amountLabel = "전체 양",
            onModeChange = actions::updateFoodAmountMode, scrollable = false, itemContent = { portion ->
                val item = foods.first { it.food.id == portion.food.id }
                MealConsumedPercentControl(item.consumedPercent, { actions.updateFoodConsumedPercent(item.id, it) }, inputEnabled,
                    "meal-food-percent-${portion.food.id}")
                Text("실제 섭취 · ${nutritionTotalText(mealFoodNutritionTotals(listOf(item)).total(NutritionProfile.CALORIES_KCAL))} kcal",
                    style = MaterialTheme.typography.bodyMedium)
            })
        DiningMenuDraftList(editor.diningMenus, actions::updateDiningMenu, actions::removeDiningMenu, inputEnabled,
            onAddAtStore = { menuId ->
                actions.chooseCategory(MealCatalogCategory.DINING_OUT)
                actions.startAnotherDiningMenu(menuId)
                scope.launch { withFrameNanos { }; manualEntryRequester.bringIntoView() }
            })
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
