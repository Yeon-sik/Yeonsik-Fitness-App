package com.yeonsik.fitnessapp.feature.meal.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
    val totals = mealEntryNutritionTotals(editor)
    val inputEnabled = !editor.saving && !editor.draftLoading &&
        (editor.recordEditor == null || editor.recordEditor.updatedAt.isNotBlank())
    val registrationError = mealEntryRegistrationError(editor)
    val diningEditor = rememberDiningOutEditorState(editor, priceTraceState)
    val manualEntryRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val recordId = editor.recordEditor?.recordId
    var showCart by rememberSaveable(editor.ownerId, editor.date, recordId) { mutableStateOf(recordId != null) }
    var expandedKey by rememberSaveable(editor.ownerId, editor.date, recordId) { mutableStateOf<String?>(null) }
    val searchScroll = rememberScrollState()
    val cartScroll = rememberScrollState()
    val contentStateHolder = rememberSaveableStateHolder()
    LaunchedEffect(recordId, editor.draftLoading) {
        if (recordId != null && !editor.draftLoading && expandedKey == null) {
            expandedKey = mealFoodItems(editor).firstOrNull()?.let { "food-${it.food.id}" }
                ?: diningMealMenus(editor).firstOrNull()?.let { "dining-${it.id}" }
        }
    }
    val reviewFood: (com.yeonsik.fitnessapp.data.NutritionFood) -> Unit = { food ->
        focusManager.clearFocus()
        expandedKey = mealFoodItems(editor).firstOrNull { it.food.id == food.id }?.let { "food-${it.food.id}" }
            ?: diningMealMenus(editor).firstOrNull { it.nutritionFoodId == food.id }?.let { "dining-${it.id}" }
        showCart = true
    }
    NutritionEntryFrame(
        if (recordId != null) "$mealLabel 수정" else "$mealLabel 기록", actions::closeDraft,
        subtitle = editor.date, closeEnabled = !editor.saving, adaptiveFooter = true,
        scrollState = if (showCart) cartScroll else searchScroll,
        introduction = {
            MealTimeControl(editor.draft.time, actions::updateTime, enabled = inputEnabled)
        },
        footer = {
            editor.removedItem?.let { removed ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${removed.label}을 식단에서 뺐어요.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(actions::undoRemoveItem, enabled = inputEnabled, modifier = Modifier.testTag("meal-cart-undo")) { Text("실행 취소") }
                }
            }
            MealCartNutritionSummary(totals, { focusManager.clearFocus(); showCart = true }, inputEnabled,
                Modifier.fillMaxWidth().testTag("meal-entry-totals"))
            (editor.error ?: registrationError?.takeIf { totals.itemCount() > 0 && !editor.draftLoading })?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("meal-entry-error"))
            }
            AppButton(actions::saveFood, Modifier.fillMaxWidth().testTag("meal-entry-save"), enabled = inputEnabled && registrationError == null) {
                Text(if (editor.saving) "저장 중…" else if (recordId != null) "수정 저장" else "$mealLabel 기록하기")
            }
        }
    ) {
        TabRow(selectedTabIndex = if (showCart) 1 else 0, indicator = { positions ->
            TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[if (showCart) 1 else 0]),
                color = MaterialTheme.colorScheme.onSurface)
        }) {
            Tab(selected = !showCart, onClick = { focusManager.clearFocus(); showCart = false }, enabled = inputEnabled,
                selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text("음식 찾기") }, modifier = Modifier.testTag("meal-entry-find"))
            Tab(selected = showCart, onClick = { focusManager.clearFocus(); showCart = true }, enabled = inputEnabled,
                selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text("이번 식단 (${totals.itemCount()})") }, modifier = Modifier.testTag("meal-entry-cart"))
        }
        if (editor.draftLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (recordId != null && editor.recordEditor?.updatedAt?.isNotBlank() == true) Text("기록한 음식과 양을 수정한 뒤 저장하세요.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        editor.notice?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        contentStateHolder.SaveableStateProvider("${editor.ownerId}:${editor.date}:$recordId:${if (showCart) "cart" else "find"}") {
            if (showCart) {
                MealCartContent(actions, editor, expandedKey, { focusManager.clearFocus(); expandedKey = it },
                    onFindMore = { focusManager.clearFocus(); showCart = false },
                    onAddAtStore = { menuId ->
                        actions.chooseCategory(MealCatalogCategory.DINING_OUT)
                        actions.startAnotherDiningMenu(menuId)
                        showCart = false
                        scope.launch { withFrameNanos { }; manualEntryRequester.bringIntoView() }
                    }, modifier = Modifier.fillMaxWidth().testTag("meal-cart-list"))
            } else {
                FlowRow(Modifier.fillMaxWidth().testTag("meal-entry-categories"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealCatalogCategory.entries.forEach { category ->
                        FilterChip(selected = editor.category == category, enabled = inputEnabled,
                            onClick = { focusManager.clearFocus(); actions.chooseCategory(category) }, label = { Text(category.label) },
                            modifier = Modifier.testTag("meal-category-${category.name}"))
                    }
                }
                if (editor.diningOut) DiningOutEditor(actions, editor, priceTraceState, diningEditor, manualEntryRequester, reviewFood)
                else FoodMealEditor(actions, editor, reviewFood)
            }
        }
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
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("섭취 시각", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alignByBaseline())
            Text(value.ifBlank { "시간 선택" }, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.alignByBaseline())
            Text("변경", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alignByBaseline())
        }
    }
}
