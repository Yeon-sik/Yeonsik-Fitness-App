package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.feature.nutrition.ui.NutritionFormSection
import com.yeonsik.fitnessapp.feature.nutrition.ui.nutritionTotalText
import com.yeonsik.fitnessapp.data.NutritionTotals

/** Plain per-menu input content; durable values and writes belong to MealViewModel. */
@Composable
internal fun DiningMenuDraftList(menus: List<MealDiningDraftItem>, onChange: (MealDiningDraftItem) -> Unit,
    onRemove: (String) -> Unit, enabled: Boolean, modifier: Modifier = Modifier,
    onAddAtStore: (String) -> Unit = {}) {
    if (menus.isEmpty()) return
    Text("담은 메뉴 ${menus.size}개", style = MaterialTheme.typography.titleMedium)
    menus.distinctBy { listOf(it.draft.restaurantId, it.draft.restaurantLocationId, it.draft.store, it.draft.branch) }
        .forEach { menu ->
            AppOutlinedButton({ onAddAtStore(menu.id) }, Modifier.fillMaxWidth().testTag("meal-dining-add-at-${menu.id}"), enabled = enabled) {
                Text("${listOf(menu.draft.store, menu.draft.branch).filter(String::isNotBlank).joinToString(" · ")} 메뉴 추가")
            }
        }
    Column(modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())
        .testTag("meal-dining-menus"), verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        menus.forEach { menu -> key(menu.id) { DiningMenuDraftCard(menu, onChange, onRemove, enabled) } }
    }
}

@Composable
private fun DiningMenuDraftCard(menu: MealDiningDraftItem, onChange: (MealDiningDraftItem) -> Unit,
    onRemove: (String) -> Unit, enabled: Boolean) {
    var editingNutrition by rememberSaveable(menu.id) { mutableStateOf(false) }
    val draft = menu.draft
    val totals = NutritionTotals.builder().add(diningMenuNutritionProfile(menu)).build()
    NutritionFormSection(draft.menu, Modifier.fillMaxWidth().testTag("meal-dining-menu-${menu.id}"),
        listOf(draft.store, draft.branch).filter(String::isNotBlank).joinToString(" · ")) {
        Text("100% 기준 · ${draft.calories} kcal · 단백질 ${draft.protein} g", style = MaterialTheme.typography.bodyMedium)
        MealNutritionField(menu.quantity, { onChange(menu.copy(quantity = it)) }, "전체 양", "인분",
            Modifier.fillMaxWidth().testTag("meal-dining-quantity-${menu.id}"), enabled)
        MealConsumedPercentControl(menu.consumedPercent, { onChange(menu.copy(consumedPercent = it)) }, enabled,
            "meal-dining-percent-${menu.id}")
        Text("실제 섭취 · ${nutritionTotalText(totals.total(NutritionProfile.CALORIES_KCAL))} kcal · " +
            "단백질 ${nutritionTotalText(totals.total(NutritionProfile.PROTEIN_GRAMS))} g", style = MaterialTheme.typography.titleSmall)
        AppOutlinedButton({ editingNutrition = !editingNutrition }, Modifier.fillMaxWidth()
            .testTag("meal-dining-edit-${menu.id}"), enabled = enabled) {
            Text(if (editingNutrition) "영양정보 수정 닫기" else "100% 기준 영양정보 수정")
        }
        if (editingNutrition) {
            AppTextField(draft.store, { onChange(menu.copy(draft = draft.copy(store = it))) }, Modifier.fillMaxWidth(), { Text("상호명") }, enabled = enabled)
            AppTextField(draft.branch, { onChange(menu.copy(draft = draft.copy(branch = it))) }, Modifier.fillMaxWidth(), { Text("지점명 (선택)") }, enabled = enabled)
            AppTextField(draft.menu, { onChange(menu.copy(draft = draft.copy(menu = it))) }, Modifier.fillMaxWidth(), { Text("메뉴명") }, enabled = enabled)
            MealNutritionField(draft.calories, { onChange(menu.copy(draft = draft.copy(calories = it))) }, "칼로리", "kcal",
                Modifier.fillMaxWidth().testTag("meal-dining-calories-${menu.id}"), enabled)
            MealNutritionField(draft.protein, { onChange(menu.copy(draft = draft.copy(protein = it))) }, "단백질", "g", Modifier.fillMaxWidth(), enabled)
            MealNutritionField(draft.carbs, { onChange(menu.copy(draft = draft.copy(carbs = it))) }, "탄수화물", "g", Modifier.fillMaxWidth(), enabled)
            MealNutritionField(draft.fat, { onChange(menu.copy(draft = draft.copy(fat = it))) }, "지방", "g", Modifier.fillMaxWidth(), enabled)
            MealNutritionField(draft.sodium, { onChange(menu.copy(draft = draft.copy(sodium = it))) }, "나트륨", "mg", Modifier.fillMaxWidth(), enabled, required = false)
            MealNutritionField(draft.sugars, { onChange(menu.copy(draft = draft.copy(sugars = it))) }, "당류", "g", Modifier.fillMaxWidth(), enabled, required = false)
            MealNutritionField(draft.saturatedFat, { onChange(menu.copy(draft = draft.copy(saturatedFat = it))) }, "포화지방", "g", Modifier.fillMaxWidth(), enabled, required = false)
        }
        diningMenuRegistrationError(menu)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        AppOutlinedButton({ onRemove(menu.id) }, Modifier.fillMaxWidth().testTag("meal-dining-remove-${menu.id}"), enabled = enabled) { Text("이 메뉴 삭제") }
    }
}
