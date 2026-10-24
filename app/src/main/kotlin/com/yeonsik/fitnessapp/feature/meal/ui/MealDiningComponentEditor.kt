package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.AppTextField
import com.yeonsik.fitnessapp.data.*

/** Plain content; the Meal ViewModel owns every input, including incomplete component edits. */
@Composable
internal fun MealDiningComponentEditor(menu: MealDiningDraftItem, state: MealUiState.Ready,
    actions: MealScreenActions, enabled: Boolean, modifier: Modifier = Modifier) {
    var choicesOpen by rememberSaveable(menu.id) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        menu.components.forEach { item -> key(item.id) {
            var expanded by rememberSaveable(item.id) { mutableStateOf(item.name.isBlank()) }
            fun change(next: MealDiningComponentDraft) = actions.updateDiningMenu(menu.copy(components =
                menu.components.map { if (it.id == item.id) next else it }))
            TextButton({ expanded = !expanded }, enabled = enabled,
                modifier = Modifier.testTag("meal-component-edit-${item.id}")) {
                Text("${item.name.ifBlank { "새 구성품" }} · ${CompositionGroupType.labelOf(item.groupType)} · " +
                    "${DiningOutProvisionType.labelOf(item.provisionType)} · ${item.quantity}개 · ${item.consumedPercent}%")
            }
            if (expanded) {
                ComponentChoice("종류", item.groupType, CompositionGroupType.values().map { it.value() to it.label() },
                    enabled) { change(item.copy(groupType = it)) }
                AppTextField(item.name, { name -> change(item.copy(name = name, catalogFoodId = null, memberId = null)) },
                    Modifier.fillMaxWidth().testTag("meal-component-name-${item.id}"), label = { Text("구성품") }, enabled = enabled)
                MealQuantityAdjuster(item.quantity, "개", 1.0, { change(item.copy(quantity = it)) }, enabled,
                    "meal-component-quantity-${item.id}", item.name.ifBlank { "구성품" })
                ComponentChoice("제공 방식", item.provisionType, DiningOutProvisionType.values().map { it.value() to it.label() },
                    enabled) { change(item.copy(provisionType = it)) }
                MealConsumedPercentControl(item.consumedPercent, { change(item.copy(consumedPercent = it)) },
                    enabled, "meal-component-percent-${item.id}")
                Text("구성품 1개 기준 영양정보 · 모르는 값은 비워 두세요", style = MaterialTheme.typography.bodySmall)
                listOf(Triple(NutritionProfile.CALORIES_KCAL, "칼로리", "kcal"),
                    Triple(NutritionProfile.PROTEIN_GRAMS, "단백질", "g"), Triple(NutritionProfile.CARBS_GRAMS, "탄수화물", "g"),
                    Triple(NutritionProfile.FAT_GRAMS, "지방", "g"), Triple(NutritionProfile.SODIUM_MG, "나트륨", "mg"),
                    Triple(NutritionProfile.SUGARS_GRAMS, "당류", "g"), Triple(NutritionProfile.SATURATED_FAT_GRAMS, "포화지방", "g"),
                    Triple(NutritionProfile.TRANS_FAT_GRAMS, "트랜스지방", "g")).forEach { (key, label, unit) ->
                    MealNutritionField(item.nutrients[key].orEmpty(), { change(item.copy(nutrients = item.nutrients + (key to it))) },
                        label, unit, Modifier.fillMaxWidth().testTag("meal-component-$key-${item.id}"), enabled, required = false)
                }
                item.error()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton({ actions.updateDiningMenu(menu.copy(components = menu.components.filterNot { it.id == item.id })) },
                    enabled = enabled, modifier = Modifier.testTag("meal-component-remove-${item.id}")) { Text("구성 빼기") }
            }
        } }
        TextButton({ actions.updateDiningMenu(menu.copy(components = menu.components + MealDiningComponentDraft())) },
            enabled = enabled, modifier = Modifier.testTag("meal-component-add-${menu.id}")) { Text("구성 추가") }
        TextButton({ choicesOpen = !choicesOpen; if (choicesOpen) actions.loadDiningComponentChoices(menu.id) },
            enabled = enabled, modifier = Modifier.testTag("meal-component-saved-${menu.id}")) { Text("저장된 구성에서 선택") }
        if (choicesOpen) {
            if (state.componentChoicesLoading) Text("저장된 구성 불러오는 중")
            state.componentChoicesError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!state.componentChoicesLoading && state.componentChoices.isEmpty()) Text("저장된 구성이 없습니다.")
            state.componentChoices.forEachIndexed { index, component ->
                TextButton({ actions.updateDiningMenu(menu.copy(components = menu.components + MealDiningComponentDraft.from(component))) },
                    enabled = enabled, modifier = Modifier.testTag("meal-component-choice-${menu.id}-$index")) {
                    Text("${component.name} · ${CompositionGroupType.labelOf(component.groupType)}")
                }
            }
        }
    }
}

@Composable
private fun ComponentChoice(label: String, value: String, options: List<Pair<String, String>>,
    enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, enabled = enabled) { Text("$label · ${options.firstOrNull { it.first == value }?.second.orEmpty()}") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onChange(key); expanded = false }) }
        }
    }
}
