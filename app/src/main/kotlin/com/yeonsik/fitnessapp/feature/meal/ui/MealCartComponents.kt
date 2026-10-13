package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.model.*

private fun mealPortionUnitLabel(portion: FoodPortionDraft): String =
    if (portion.mode == PortionInputMode.BASIS && portion.food.basisUnit == NutritionUnit.SERVING) "인분" else portion.unitLabel()

private fun mealFoodBasisLabel(food: NutritionFood): String =
    "${NutritionCalculator.trim(food.basisAmount)} ${if (food.basisUnit == NutritionUnit.SERVING) "인분" else NutritionUnit.display(food.basisUnit)}"

internal fun mealFoodPortionSummary(item: MealFoodDraftItem): String {
    val portion = FoodPortionDraft(item.food, item.quantity, item.amountMode)
    val amount = portion.enteredAmount ?: return "양을 입력하세요"
    val fraction = mealConsumedFraction(item.consumedPercent) ?: return "먹은 비율을 확인하세요"
    val actual = NutritionCalculator.trim(amount * fraction)
    val unit = mealPortionUnitLabel(portion)
    val weight = when {
        portion.mode == PortionInputMode.COUNT -> item.food.packageAmount?.let {
            " (${NutritionCalculator.trim(it * amount * fraction)} ${NutritionUnit.display(item.food.packageUnit)})"
        }.orEmpty()
        portion.mode == PortionInputMode.BASIS -> recipeGramsPerServing(item.food)?.let {
            " (${NutritionCalculator.trim(it * amount * fraction)} g)"
        }.orEmpty()
        else -> ""
    }
    return if (fraction == 1.0) "$actual $unit$weight"
    else "${NutritionCalculator.trim(amount)} $unit 중 ${item.consumedPercent}% · 실제 $actual $unit$weight"
}

internal fun mealDiningPortionSummary(menu: MealDiningDraftItem): String {
    val actual = mealConsumedQuantity(menu.quantity, menu.consumedPercent) ?: return "양과 먹은 비율을 확인하세요"
    return if (mealConsumedFraction(menu.consumedPercent) == 1.0) "${NutritionCalculator.trim(actual)} 인분"
    else "${menu.quantity} 인분 중 ${menu.consumedPercent}% · 실제 ${NutritionCalculator.trim(actual)} 인분"
}

internal fun mealMacroTotalText(total: NutritionTotals.Total): String = when {
    total.knownCount() == 0 -> "미확인"
    total.missingCount() > 0 -> "${NutritionCalculator.trim(total.knownSum())} 이상"
    else -> NutritionCalculator.trim(total.knownSum())
}

@Composable
internal fun MealCatalogResultList(
    foods: List<NutritionFood>,
    onAdd: (NutritionFood) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    includedFoodIds: Set<String> = emptySet(),
    actionLabel: String = "담기",
    onReviewFood: (NutritionFood) -> Unit = {}
) {
    LazyColumn(modifier.heightIn(max = 288.dp)) {
        items(foods, key = { it.id }) { food ->
            val included = food.id in includedFoodIds
            val portion = initialFoodPortion(food, preferCount = true)
            Row(Modifier.fillMaxWidth().testTag("food-result-${food.id}")
                .semantics { if (included) stateDescription = "담김" }
                .clickable(enabled = enabled, role = Role.Button,
                    onClickLabel = if (included) "${food.displayName()} 수정" else "${food.displayName()} $actionLabel") {
                    if (included) onReviewFood(food) else onAdd(food)
                }.heightIn(min = 72.dp).padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (food.isPackagedFood()) food.packagedProductLabel() else food.displayName(),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${mealCatalogLabel(food)} · ${if (portion.mode == PortionInputMode.COUNT) "1개 · ${food.packagedVariantLabel()}" else mealFoodBasisLabel(food)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(portion.profile?.value(NutritionProfile.CALORIES_KCAL)?.let { "${NutritionCalculator.trim(it)} kcal" } ?: "칼로리 미확인",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (included) "담김 · 수정" else actionLabel, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(max = 64.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun MealCartNutritionSummary(totals: NutritionTotals, onReview: () -> Unit, enabled: Boolean,
    modifier: Modifier = Modifier) {
    val keys = listOf(NutritionProfile.CALORIES_KCAL, NutritionProfile.CARBS_GRAMS,
        NutritionProfile.PROTEIN_GRAMS, NutritionProfile.FAT_GRAMS)
    val incomplete = totals.itemCount() > 0 && keys.any { totals.total(it).missingCount() > 0 }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onReview, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.testTag("meal-cart-review").heightIn(min = 48.dp)) {
                Text("담은 음식 ${totals.itemCount()}개 보기")
            }
            val calories = totals.total(NutritionProfile.CALORIES_KCAL)
            Text(when {
                totals.itemCount() == 0 -> "0 kcal"
                calories.knownCount() == 0 -> "칼로리 미확인"
                else -> "${NutritionCalculator.trim(calories.knownSum())} kcal${if (calories.missingCount() > 0) " 이상" else ""}"
            },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("탄수" to NutritionProfile.CARBS_GRAMS, "단백질" to NutritionProfile.PROTEIN_GRAMS,
                "지방" to NutritionProfile.FAT_GRAMS).forEach { (label, key) ->
                val total = totals.total(key)
                val value = when {
                    totals.itemCount() == 0 -> "0 g"
                    total.knownCount() == 0 -> "미확인"
                    else -> "${NutritionCalculator.trim(total.knownSum())} g${if (total.missingCount() > 0) " 이상" else ""}"
                }
                Text("$label $value",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (incomplete) Text("확인된 합계 · 일부 영양정보 미확인", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** This content owns no repository or draft state; all edits go through the caller. */
@Composable
internal fun MealCartContent(actions: MealScreenActions, editor: MealUiState.Ready, expandedKey: String?,
    onExpandedChange: (String?) -> Unit, onFindMore: () -> Unit, onAddAtStore: (String) -> Unit,
    modifier: Modifier = Modifier) {
    val foods = mealFoodItems(editor)
    val menus = diningMealMenus(editor)
    val enabled = !editor.saving && !editor.draftLoading
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("이번 식단 · 음식 ${foods.size + menus.size}개",
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onFindMore, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.testTag("meal-cart-find-more")) { Text("음식 더 담기") }
        }
        if (foods.isEmpty() && menus.isEmpty()) {
            Text("아직 담은 음식이 없어요. 음식 찾기에서 이번에 먹은 내용을 담으세요.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        foods.forEach { item -> key("food-${item.food.id}") {
            val rowKey = "food-${item.food.id}"
            val expanded = rowKey == expandedKey
            MealCartRow(item.food.displayName(), "${mealCatalogLabel(item.food)} · ${mealFoodPortionSummary(item)}",
                mealMacroTotalText(mealFoodNutritionTotals(listOf(item)).total(NutritionProfile.CALORIES_KCAL)),
                expanded, { onExpandedChange(if (expanded) null else rowKey) }, enabled,
                Modifier.testTag("meal-cart-$rowKey")) {
                MealFoodAmountEditor(item, actions, enabled)
                TextButton({ actions.removeFood(item.food.id) }, enabled = enabled,
                    modifier = Modifier.testTag("meal-cart-remove-$rowKey").heightIn(min = 48.dp)) {
                    Text("식단에서 빼기", color = MaterialTheme.colorScheme.error)
                }
            }
        } }
        menus.forEach { menu -> key("dining-${menu.id}") {
            val rowKey = "dining-${menu.id}"
            val expanded = rowKey == expandedKey
            MealCartRow(menu.draft.menu.ifBlank { "입력 중인 메뉴" },
                listOf("외식", menu.draft.store, menu.draft.branch, mealDiningPortionSummary(menu)).filter(String::isNotBlank).joinToString(" · "),
                mealMacroTotalText(NutritionTotals.builder().add(diningMenuNutritionProfile(menu)).build().total(NutritionProfile.CALORIES_KCAL)),
                expanded, { onExpandedChange(if (expanded) null else rowKey) }, enabled,
                Modifier.testTag("meal-cart-$rowKey")) {
                MealDiningAmountEditor(menu, actions::updateDiningMenu, enabled)
                TextButton({ onAddAtStore(menu.id) }, enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.testTag("meal-dining-add-at-${menu.id}")) { Text("이 식당의 다른 메뉴 담기") }
                TextButton({ actions.removeDiningMenu(menu.id) }, enabled = enabled,
                    modifier = Modifier.testTag("meal-cart-remove-$rowKey").heightIn(min = 48.dp)) {
                    Text("식단에서 빼기", color = MaterialTheme.colorScheme.error)
                }
            }
        } }
    }
}

@Composable
private fun MealCartRow(name: String, description: String, calories: String, expanded: Boolean,
    onToggle: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier, details: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(when {
                    calories == "미확인" -> "칼로리 미확인"
                    calories.endsWith(" 이상") -> "${calories.removeSuffix(" 이상")} kcal 이상"
                    else -> "$calories kcal"
                }, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onToggle, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.heightIn(min = 48.dp)
                .semantics { contentDescription = "$name ${if (expanded) "수정 닫기" else "수정"}" }) {
                Text(if (expanded) "접기" else "수정")
            }
        }
        if (expanded) details()
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MealFoodAmountEditor(item: MealFoodDraftItem, actions: MealScreenActions, enabled: Boolean) {
    val portion = FoodPortionDraft(item.food, item.quantity, item.amountMode)
    val step = when (portion.mode) {
        PortionInputMode.COUNT -> 1.0
        PortionInputMode.GRAMS -> (recipeGramsPerServing(item.food) ?: 100.0) / 4.0
        PortionInputMode.BASIS -> item.food.basisAmount / 4.0
    }
    if (portion.availableModes().size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        portion.availableModes().forEach { mode ->
            FilterChip(selected = portion.mode == mode, onClick = { actions.updateFoodAmountMode(item.food.id, mode) },
                enabled = enabled, label = { Text(portion.modeLabel(mode)) },
                modifier = Modifier.testTag("meal-food-mode-${item.food.id}-${mode.name}"))
        }
    }
    Text("영양정보 기준 · ${mealFoodBasisLabel(item.food)}", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    MealQuantityAdjuster(item.quantity, mealPortionUnitLabel(portion), step, { actions.updateFoodQuantity(item.food.id, it) }, enabled,
        "meal-food-quantity-${item.food.id}", item.food.displayName())
    MealOptionalPercent(item.consumedPercent, { actions.updateFoodConsumedPercent(item.id, it) }, enabled,
        "meal-food-percent-${item.food.id}")
    if (mealFoodConsumedQuantity(item) == null) Text("섭취량을 확인하세요.", color = MaterialTheme.colorScheme.error)
}

@Composable
private fun MealQuantityAdjuster(value: String, unit: String, step: Double, onChange: (String) -> Unit,
    enabled: Boolean, tag: String, name: String) {
    val amount = mealQuantityValue(value)
    val focus = LocalFocusManager.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        OutlinedButton({ focus.clearFocus(); onChange(NutritionCalculator.trim(amount!! - step)) },
            enabled = enabled && amount != null && amount > step,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "$name 전체 양 줄이기" }, contentPadding = PaddingValues(0.dp)) { Text("−") }
        AppTextField(value, onChange, Modifier.weight(1f).testTag(tag), label = { Text("전체 양 ($unit)") },
            enabled = enabled, isError = amount == null,
            supportingText = if (amount == null) { { Text("0보다 큰 양을 입력하세요.") } } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        OutlinedButton({ focus.clearFocus(); onChange(NutritionCalculator.trim(amount!! + step)) },
            enabled = enabled && amount != null && (amount + step).isFinite(),
            modifier = Modifier.size(48.dp).semantics { contentDescription = "$name 전체 양 늘리기" }, contentPadding = PaddingValues(0.dp)) { Text("+") }
    }
}

@Composable
internal fun MealOptionalPercent(value: String, onChange: (String) -> Unit, enabled: Boolean, tag: String) {
    var open by rememberSaveable(tag) { mutableStateOf(false) }
    TextButton({ open = !open }, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.testTag("$tag-toggle")) {
        Text(if (open) "먹은 비율 접기" else "먹은 비율 조절 · $value%")
    }
    if (open || mealConsumedFraction(value) != 1.0) MealConsumedPercentControl(value, onChange, enabled, tag)
}

@Composable
private fun MealDiningAmountEditor(menu: MealDiningDraftItem, onChange: (MealDiningDraftItem) -> Unit, enabled: Boolean) {
    var nutritionOpen by rememberSaveable(menu.id) { mutableStateOf(false) }
    MealQuantityAdjuster(menu.quantity, "인분", 0.25, { onChange(menu.copy(quantity = it)) }, enabled,
        "meal-dining-quantity-${menu.id}", menu.draft.menu)
    MealOptionalPercent(menu.consumedPercent, { onChange(menu.copy(consumedPercent = it)) }, enabled,
        "meal-dining-percent-${menu.id}")
    TextButton({ nutritionOpen = !nutritionOpen }, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.testTag("meal-dining-edit-${menu.id}")) {
        Text(if (nutritionOpen) "영양정보 수정 닫기" else "1인분 기준 영양정보 수정")
    }
    if (nutritionOpen || diningMenuRegistrationError(menu) != null) {
        val d = menu.draft
        MealInputNotice(com.yeonsik.fitnessapp.feature.nutrition.model.NutritionConsistencyPolicy.LABEL_GUIDANCE)
        AppTextField(d.store, { onChange(menu.copy(draft = d.copy(store = it))) }, Modifier.fillMaxWidth(), { Text("상호명") }, enabled = enabled)
        AppTextField(d.branch, { onChange(menu.copy(draft = d.copy(branch = it))) }, Modifier.fillMaxWidth(), { Text("지점명 (선택)") }, enabled = enabled)
        AppTextField(d.menu, { onChange(menu.copy(draft = d.copy(menu = it))) }, Modifier.fillMaxWidth(), { Text("메뉴명") }, enabled = enabled)
        MealNutritionField(d.calories, { onChange(menu.copy(draft = d.copy(calories = it))) }, "칼로리", "kcal",
            Modifier.fillMaxWidth().testTag("meal-dining-calories-${menu.id}"), enabled)
        MealNutritionField(d.protein, { onChange(menu.copy(draft = d.copy(protein = it))) }, "단백질", "g", Modifier.fillMaxWidth(), enabled)
        MealNutritionField(d.carbs, { onChange(menu.copy(draft = d.copy(carbs = it))) }, "탄수화물 (총)", "g", Modifier.fillMaxWidth(), enabled)
        MealNutritionField(d.fat, { onChange(menu.copy(draft = d.copy(fat = it))) }, "지방 (총)", "g", Modifier.fillMaxWidth(), enabled)
        MealNutritionField(d.sodium, { onChange(menu.copy(draft = d.copy(sodium = it))) }, "나트륨", "mg", Modifier.fillMaxWidth(), enabled, required = false)
        MealNutritionField(d.sugars, { onChange(menu.copy(draft = d.copy(sugars = it))) }, "당류", "g", Modifier.fillMaxWidth(), enabled, required = false)
        MealNutritionField(d.saturatedFat, { onChange(menu.copy(draft = d.copy(saturatedFat = it))) }, "포화지방", "g", Modifier.fillMaxWidth(), enabled, required = false)
        MealNutritionField(d.transFat, { onChange(menu.copy(draft = d.copy(transFat = it))) }, "트랜스지방", "g", Modifier.fillMaxWidth(), enabled, required = false)
    }
    diningMenuRegistrationError(menu)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
