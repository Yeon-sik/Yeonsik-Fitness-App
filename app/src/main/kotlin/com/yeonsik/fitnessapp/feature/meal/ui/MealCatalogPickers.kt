package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.NutritionUnit
import com.yeonsik.fitnessapp.feature.nutrition.model.*
import com.yeonsik.fitnessapp.feature.nutrition.ui.*

@Composable
internal fun MealCatalogSearch(actions: MealScreenActions, editor: MealUiState.Ready, modifier: Modifier = Modifier,
    onReviewFood: (com.yeonsik.fitnessapp.data.NutritionFood) -> Unit = {}) {
    val focus = LocalFocusManager.current
    val enabled = !editor.saving && !editor.draftLoading
    val dining = editor.diningOut
    val noun = if (dining) "외식 메뉴" else if (editor.category == MealCatalogCategory.ALL) "음식" else "식품"
    var pendingFoodId by rememberSaveable(editor.ownerId, editor.date, editor.category) { mutableStateOf<String?>(null) }
    val pendingFood = editor.searchResults.firstOrNull { it.id == pendingFoodId }
    if (pendingFood != null) key(editor.ownerId, editor.date, pendingFood.id) {
        MealFoodPortionDialog(pendingFood, enabled, onDismiss = { pendingFoodId = null }, onAdd = { portion ->
            focus.clearFocus()
            actions.selectFood(portion.food, portion.quantity, portion.mode)
            pendingFoodId = null
        })
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(editor.query, actions::searchFood, Modifier.fillMaxWidth().testTag("meal-catalog-query"),
            label = { Text(if (dining) "저장된 외식 메뉴 검색" else if (editor.category == MealCatalogCategory.ALL) "음식 이름 검색" else "${editor.category.label} 이름 검색") }, enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { actions.searchFood(editor.query); focus.clearFocus() }))
        when {
            editor.searchLoading -> MealCatalogProgress("$noun 목록을 불러오는 중")
            editor.searchError != null -> {
                MealCatalogFeedback(editor.searchError)
                AppOutlinedButton({ actions.searchFood(editor.query) }, Modifier.fillMaxWidth(), enabled = enabled) { Text("목록 다시 불러오기") }
            }
            editor.searchResults.isEmpty() -> {
                Text(if (editor.query.isNotBlank()) "‘${editor.query.trim()}’에 맞는 $noun${if (dining) "가" else "이"} 없어요."
                    else if (dining) "아직 저장한 외식 메뉴가 없어요. 아래에서 식당과 메뉴를 직접 입력하세요."
                    else "식품을 검색하거나 자주 먹는 식품을 등록해 보세요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!dining) TextButton(actions::openNutritionEditor, enabled = enabled) { Text("식품 등록 · 관리") }
            }
            else -> {
                Text("${if (editor.query.isBlank()) noun else "$noun 검색 결과"} ${editor.searchResults.size}개 · 목록 안에서 스크롤",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MealCatalogResultList(editor.searchResults, {
                    focus.clearFocus()
                    if (dining) actions.useDiningOutFood(it) else pendingFoodId = it.id
                }, Modifier.fillMaxWidth().testTag(if (dining) "meal-dining-results" else "meal-food-results"), enabled = enabled,
                    includedFoodIds = mealFoodItems(editor).map { it.food.id }.toSet() + diningMealMenus(editor).mapNotNull { it.nutritionFoodId },
                    actionLabel = if (dining) "불러오기" else "담기", onReviewFood = onReviewFood,
                    onRemoveFood = if (dining) null else { food -> actions.removeFood(food.id) })
            }
        }
        if (editor.query.isNotBlank()) TextButton({ actions.searchFood(""); focus.clearFocus() }, enabled = enabled) { Text("검색 지우기") }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MealFoodPortionDialog(food: NutritionFood, enabled: Boolean,
    onDismiss: () -> Unit, onAdd: (FoodPortionDraft) -> Unit) {
    val initial = remember(food) { initialFoodPortion(food, preferCount = true) }
    var quantity by rememberSaveable(food.id) { mutableStateOf(initial.quantity) }
    var mode by rememberSaveable(food.id) { mutableStateOf(initial.mode) }
    val portion = FoodPortionDraft(food, quantity, mode)
    val unit = if (mode == PortionInputMode.BASIS && food.basisUnit == NutritionUnit.SERVING) "인분" else portion.unitLabel()
    val step = when (mode) {
        PortionInputMode.COUNT -> 1.0
        PortionInputMode.GRAMS -> (recipeGramsPerServing(food) ?: 100.0) / 4.0
        PortionInputMode.BASIS -> food.basisAmount / 4.0
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.displayName()) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("먹은 개수나 양을 정한 뒤 식단에 담으세요.")
                Text("영양정보 기준 · ${food.basisLabel()}", style = MaterialTheme.typography.bodySmall)
                if (portion.availableModes().size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    portion.availableModes().forEach { next ->
                        FilterChip(selected = mode == next, enabled = enabled, onClick = {
                            val converted = portion.withMode(next)
                            quantity = converted?.quantity ?: ""
                            mode = next
                        }, label = { Text(portion.modeLabel(next)) },
                            modifier = Modifier.testTag("meal-add-mode-${next.name}"))
                    }
                }
                MealQuantityAdjuster(quantity, unit, step, { quantity = it }, enabled,
                    "meal-add-quantity", food.displayName())
                portion.profile?.let { MealNutritionPreview(it, Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            TextButton({ onAdd(portion) }, enabled = enabled && portion.amount != null,
                modifier = Modifier.testTag("meal-add-confirm")) { Text("식단에 담기") }
        },
        dismissButton = { TextButton(onDismiss, modifier = Modifier.testTag("meal-add-cancel")) { Text("취소") } }
    )
}

@Composable
internal fun PriceTraceDiningOutPicker(
    actions: MealScreenActions,
    editor: MealUiState.Ready,
    modifier: Modifier = Modifier
) {
    val state = editor.diningSearch
    val enabled = !editor.saving && !editor.draftLoading
    val focus = LocalFocusManager.current
    NutritionFormSection("PriceTrace 메뉴 찾기", modifier.fillMaxWidth(), "메뉴를 선택하고, 지점이 여러 곳이면 지점을 골라 주세요.") {
        AppTextField(state.query, actions::updateDiningSearchQuery,
            Modifier.fillMaxWidth().testTag("meal-dining-query"), label = { Text("식당 또는 메뉴 검색") },
            enabled = enabled, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (enabled) { focus.clearFocus(); actions.searchDiningTargets() } }))
        when {
            state.loading -> MealCatalogProgress("식당·메뉴 검색 중")
            state.error != null -> {
                MealCatalogFeedback(state.error)
                if (state.query.isNotBlank()) TextButton(actions::searchDiningTargets, enabled = enabled) { Text("메뉴 검색 다시 시도") }
            }
            state.searched && state.rows.isEmpty() ->
                Text("검색 결과가 없습니다. 다른 식당·메뉴 이름을 검색하거나 아래에서 직접 입력하세요.", style = MaterialTheme.typography.bodyMedium)
        }
        if (state.rows.isNotEmpty()) {
            NutritionChoiceList(state.rows, { it.restaurantMenuId + ":" + it.catalogProductId },
                state.pendingTarget?.let { it.restaurantMenuId + ":" + it.catalogProductId },
                { focus.clearFocus(); actions.selectDiningTarget(it) },
                Modifier.fillMaxWidth().heightIn(max = 240.dp).testTag("meal-dining-targets"), enabled = enabled && !state.loading) {
                Text(it.menuName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(it.restaurantName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.pendingTarget?.let { target ->
            Column(Modifier.fillMaxWidth().testTag("meal-dining-branches"), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text("지점 선택 · ${target.restaurantName}", style = MaterialTheme.typography.titleSmall)
                Text(target.menuName, style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    target.locations.forEach { location ->
                        AppOutlinedButton({ actions.selectDiningBranch(location.restaurantLocationId) },
                            Modifier.fillMaxWidth().testTag("meal-dining-branch-" + location.restaurantLocationId), enabled = enabled) {
                            Text(location.branchName ?: "지점명 미확인")
                        }
                    }
                }
                TextButton(actions::cancelDiningBranchSelection, enabled = enabled) { Text("지점 선택 취소") }
            }
        }
        Text("검색 없이도 아래에서 직접 입력할 수 있습니다. 1인분 영양정보는 확인해 입력하세요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MealCatalogProgress(label: String) {
    Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MealCatalogFeedback(message: String) {
    Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.medium) {
        Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
