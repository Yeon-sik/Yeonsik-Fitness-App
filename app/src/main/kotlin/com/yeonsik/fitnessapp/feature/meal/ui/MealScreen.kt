package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.BuildConfig
import com.yeonsik.fitnessapp.cardio.*
import com.yeonsik.fitnessapp.config.*
import com.yeonsik.fitnessapp.core.account.*
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitness.shared.feature.cardio.model.*
import com.yeonsik.fitnessapp.feature.cardio.ui.*
import com.yeonsik.fitnessapp.feature.exercise.ui.*
import com.yeonsik.fitnessapp.feature.home.ui.*
import com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary
import com.yeonsik.fitnessapp.feature.nutrition.ui.*
import com.yeonsik.fitnessapp.feature.nutrition.model.foodPortionTotals
import com.yeonsik.fitnessapp.feature.routine.ui.*
import com.yeonsik.fitnessapp.feature.supplement.ui.*
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitnessapp.feature.workout.ui.*
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.*
import java.time.LocalDate

interface MealScreenActions {
    fun back()
    fun selectDate(date: String)
    fun startDraft()
    fun closeDraft()
    fun chooseFood()
    fun chooseDiningOut()
    fun searchFood(query: String)
    fun selectFood(food: NutritionFood)
    fun useDiningOutFood(food: NutritionFood)
    fun saveReusableDiningOutMenu()
    fun updateQuantity(value: String)
    fun updateFoodQuantity(foodId: String, value: String) = updateQuantity(value)
    fun removeFood(foodId: String) {}
    fun updateDiningPortion(value: String) {}
    fun openNutritionEditor() {}
    fun updateTime(value: String)
    fun saveFood()
    fun updateStore(value: String)
    fun updateBranch(value: String)
    fun updateMenu(value: String)
    fun updateCalories(value: String)
    fun updateCarbs(value: String)
    fun updateProtein(value: String)
    fun updateFat(value: String)
    fun updateSodium(value: String)
    fun updateSugars(value: String)
    fun updateSaturatedFat(value: String)
    fun updatePriceTraceQuery(value: String)
    fun searchPriceTraceRestaurants()
    fun loadPriceTraceRestaurant(restaurantId: String)
    fun openNutritionPublication()
    fun closeNutritionPublication()
    fun selectNutritionPublicationMenu(foodId: String)
    fun syncNutritionPublicationCatalog()
    fun publishNutritionMenu(locationId: String, menuId: String, catalogProductId: String)
    fun verifyNutritionMenu(locationId: String, menuId: String, catalogProductId: String) {}
    fun createDiningOutMenu() {
        closeNutritionPublication()
        startDraft()
        chooseDiningOut()
    }
    fun applyPriceTraceSelection(
        restaurantId: String,
        restaurantName: String,
        locationId: String,
        branchName: String,
        menuId: String,
        menuName: String,
        catalogProductId: String
    )
    fun saveDiningOut()
    fun editMeal(meal: HomeMealSummary)
    fun deleteMeal(recordId: String)
    fun saveMealTime(recordId: String, mealTime: String)
    fun cancelMealEdit()
}

internal data class MealEditorScrollTarget(
    val editing: Boolean,
    val diningOut: Boolean
)

internal fun mealEditorScrollTarget(
    editor: MealUiState.Ready?
): MealEditorScrollTarget? = editor?.let {
    MealEditorScrollTarget(editing = it.editing, diningOut = it.diningOut)
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun MealScreen(
    homeState: HomeUiState,
    editorState: MealUiState,
    priceTraceState: PriceTraceUiState,
    nutritionPublicationState: NutritionPublicationUiState,
    ownerId: String,
    today: String,
    actions: MealScreenActions,
    nutritionEditorState: NutritionEditorState = NutritionEditorState(),
    nutritionEditorActions: NutritionEditorActions? = null,
    onUseComposition: (String) -> Unit = {}
) {
    val ready = homeState as? HomeUiState.Ready
    val editor = editorState as? MealUiState.Ready
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }

    AppHeader("식단", back = actions::back)
    if (ready == null || ready.snapshot.ownerId != ownerId || ready.snapshot.today != today) {
        Text("식단 기록을 불러오는 중입니다.")
        return
    }
    if (nutritionPublicationState.open && nutritionPublicationState.ownerId == ownerId) {
        NutritionPublicationDialog(
            state = nutritionPublicationState,
            priceTraceState = priceTraceState,
            actions = actions
        )
    }

    val snapshot = ready.snapshot
    val nextMealLabel = MealEntryPolicy.labelForIndex(snapshot.todayMeals.size)
    val totals = snapshot.mealNutritionTotals[today]
    val selectedDate = runCatching { LocalDate.parse(today) }.getOrNull()
    if (selectedDate != null) {
        val largeText = LocalDensity.current.fontScale >= 1.35f
        if (largeText) {
            Text(
                today,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = FitnessSpacing.touch),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { actions.selectDate(selectedDate.minusDays(1).toString()) },
                modifier = Modifier
                    .heightIn(min = FitnessSpacing.touch)
                    .semantics { contentDescription = "이전 날짜" }
            ) { Text("‹", style = MaterialTheme.typography.titleLarge) }
            if (!largeText) {
                Text(
                    today,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            TextButton(
                onClick = { actions.selectDate(selectedDate.plusDays(1).toString()) },
                modifier = Modifier
                    .heightIn(min = FitnessSpacing.touch)
                    .semantics { contentDescription = "다음 날짜" },
                enabled = selectedDate.isBefore(LocalDate.now())
            ) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        AppOutlinedButton(
            onClick = { actions.selectDate(LocalDate.now().toString()) },
            enabled = !selectedDate.isEqual(LocalDate.now()),
            modifier = Modifier.fillMaxWidth()
        ) { Text("오늘로 이동") }
    }
    Spacer(Modifier.height(AppSpacing.gap))
    MealOverviewGlassSurface {
        Text(
            if (selectedDate == LocalDate.now()) "오늘의 식단" else "이날의 식단",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        MealDailyMetrics(
            count = snapshot.todayMeals.size,
            calories = totals?.total("calories_kcal")?.describedValue() ?: "?"
        )
        MealMacroMetrics(
            carbs = totals?.total("carbs_grams")?.describedValue() ?: "?",
            protein = totals?.total("protein_grams")?.describedValue() ?: "?",
            fat = totals?.total("fat_grams")?.describedValue() ?: "?"
        )
    }
    Spacer(Modifier.height(AppSpacing.gap))
    snapshot.todayMeals.forEach { meal ->
        AppCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(AppSpacing.card),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                Text(meal.mealLabel, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(meal.previewTitle, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(meal.previewSubtitle(), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${meal.calories} kcal · 단백질 ${meal.proteinGrams}g",
                    style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
                ) {
                    AppOutlinedButton(
                        onClick = { actions.editMeal(meal) },
                        modifier = Modifier.weight(1f),
                        enabled = meal.timeEditable
                    ) { Text("수정") }
                    AppOutlinedButton(
                        onClick = { deleteTargetId = meal.id },
                        modifier = Modifier.weight(1f),
                        destructive = true
                    ) { Text("삭제") }
                }
            }
        }
    }

    if (editor == null || editor.ownerId != ownerId || editor.date != today) {
        Text("식단 입력을 준비하는 중입니다.")
    } else if (!editor.editing) {
        editor.notice?.let { Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        AppButton(onClick = actions::startDraft, Modifier.fillMaxWidth(),
            enabled = !editor.draftLoading && !editor.saving && !editor.recordActionSaving) {
            Text(if (editor.draftLoading) "입력 초안을 불러오는 중…" else "$nextMealLabel 기록하기")
        }
    } else {
        key(ownerId, today) {
            MealEntryDialog(actions, editor, priceTraceState, nextMealLabel)
        }
    }
    AppOutlinedButton(actions::openNutritionEditor, Modifier.fillMaxWidth()) { Text("내 식단 · 식품 관리") }
    TextButton(actions::openNutritionPublication, modifier = Modifier.fillMaxWidth()) { Text("외식 영양정보 관리") }
    if (nutritionPublicationState.ownerId == ownerId && !nutritionPublicationState.open) {
        val publicationMessage = nutritionPublicationState.progressLabel
            ?: nutritionPublicationState.error ?: nutritionPublicationState.notice
        publicationMessage?.let {
            Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = if (nutritionPublicationState.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (nutritionEditorActions != null) {
        NutritionEditorDialog(nutritionEditorState, nutritionEditorActions, onUseComposition)
    }

    val deleteTarget = deleteTargetId?.let { id -> snapshot.todayMeals.firstOrNull { it.id == id } }
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("식단 기록 삭제") },
            text = { Text("${deleteTarget.previewTitle} 기록을 삭제할까요?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTargetId = null
                        actions.deleteMeal(deleteTarget.id)
                    },
                    enabled = editor?.recordActionSaving != true
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) { Text("취소") }
            }
        )
    }

    val recordEditor = editor?.recordEditor
    if (recordEditor != null) {
        var editTime by rememberSaveable(recordEditor.recordId) {
            mutableStateOf(recordEditor.time)
        }
        AlertDialog(
            onDismissRequest = actions::cancelMealEdit,
            title = { Text("식단 수정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(recordEditor.title, fontWeight = FontWeight.Bold)
                    MealTimeControl(editTime, { editTime = it }, enabled = !editor.recordActionSaving)
                    editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { actions.saveMealTime(recordEditor.recordId, editTime) },
                    enabled = !editor.recordActionSaving
                ) { Text(if (editor.recordActionSaving) "저장 중" else "저장") }
            },
            dismissButton = {
                TextButton(onClick = actions::cancelMealEdit) { Text("취소") }
            }
        )
    }
}

@Composable
private fun MealMacroMetrics(carbs: String, protein: String, fat: String) {
    val metrics = listOf(
        "탄수화물" to carbs,
        "단백질" to protein,
        "지방" to fat
    )
    if (LocalDensity.current.fontScale >= 1.35f) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            metrics.forEach { (label, value) -> MealMacroMetric(label, value, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)
        ) {
            metrics.forEach { (label, value) -> MealMacroMetric(label, value, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun MealMacroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("g", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun FoodMealEditor(actions: MealScreenActions, editor: MealUiState.Ready) {
    AppOutlinedButton(actions::openNutritionEditor, Modifier.fillMaxWidth(), enabled = !editor.saving && !editor.draftLoading) {
        Text("내 식단에서 불러오기 · 구성 관리")
    }
    Text("한 끼 구성 · 식품 ${editor.foodPortions.size}개", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (editor.foodPortions.isEmpty() && !editor.draftLoading) {
        NutritionFormSection("식품을 추가하세요", Modifier.fillMaxWidth(), "함께 먹은 식품을 모아 한 끼로 기록합니다.") {}
    }
    FoodPortionList(editor.foodPortions, actions::updateFoodQuantity, actions::removeFood, Modifier.fillMaxWidth().testTag("meal-food-portions"),
        enabled = !editor.saving && !editor.draftLoading)
    NutritionFormSection("식품 추가", Modifier.fillMaxWidth()) {
        MealCatalogSearch(actions, editor, Modifier.fillMaxWidth())
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun DiningOutEditor(
    actions: MealScreenActions,
    editor: MealUiState.Ready,
    priceTraceState: PriceTraceUiState,
    controls: DiningOutEditorState
) {
    val draft = editor.draft
    val selectedMenuRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(editor.selectedFood?.id) {
        if (editor.selectedFood != null) selectedMenuRequester.bringIntoView()
    }
    NutritionFormSection("외식 메뉴", Modifier.fillMaxWidth(), "저장한 메뉴를 불러오거나 직접 입력하세요.") {
        SavedDiningOutMenuPicker(actions, editor, selectedMenuRequester)
    }
    AppOutlinedButton(
        onClick = { controls.showPriceTrace = !controls.showPriceTrace },
        modifier = Modifier.fillMaxWidth(), enabled = !editor.saving && !editor.draftLoading
    ) { Text(if (controls.showPriceTrace) "식당 검색 닫기" else "식당 · 메뉴 검색") }
    if (controls.showPriceTrace) {
        PriceTraceDiningOutPicker(actions, editor, priceTraceState, controls)
    }
    TextButton({ controls.editMenu = !controls.editMenu }, enabled = !editor.saving) { Text(if (controls.editMenu) "메뉴 정보 접기" else "메뉴 정보 직접 수정") }
    if (controls.editMenu) {
        NutritionFormSection("식당과 메뉴", Modifier.fillMaxWidth()) {
            AppTextField(draft.store, actions::updateStore, Modifier.fillMaxWidth(), { Text("상호명") }, enabled = !editor.saving)
            AppTextField(draft.branch, actions::updateBranch, Modifier.fillMaxWidth(), { Text("지점명 (선택)") }, enabled = !editor.saving)
            AppTextField(draft.menu, actions::updateMenu, Modifier.fillMaxWidth(), { Text("먹은 메뉴") }, enabled = !editor.saving)
        }
    }
    NutritionFormSection("얼마나 먹었나요?", Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("0.5" to "반 인분", "1" to "1인분", "1.5" to "1.5인분").forEach { (amount, label) ->
                AppOutlinedButton({ actions.updateDiningPortion(amount) }, Modifier.weight(1f),
                    selected = editor.diningPortion.toDoubleOrNull() == amount.toDouble(), enabled = !editor.saving) { Text(label) }
            }
        }
        NutritionNumberField(editor.diningPortion, actions::updateDiningPortion, "먹은 양", "인분", Modifier.fillMaxWidth(),
            enabled = !editor.saving, showError = editor.diningPortion.isNotBlank(), positive = true)
    }
    NutritionFormSection("영양정보 · 1인분 기준", Modifier.fillMaxWidth(), "외식 · 추정 영양정보") {
        TextButton({ controls.editNutrition = !controls.editNutrition }, enabled = !editor.saving) { Text(if (controls.editNutrition) "영양정보 접기" else "영양정보 직접 수정") }
        if (controls.editNutrition || editor.error != null) {
            val values = mapOf(FoodEntryField.CALORIES to draft.calories, FoodEntryField.PROTEIN to draft.protein,
                FoodEntryField.CARBS to draft.carbs, FoodEntryField.FAT to draft.fat, FoodEntryField.SODIUM to draft.sodium,
                FoodEntryField.SUGARS to draft.sugars, FoodEntryField.SATURATED_FAT to draft.saturatedFat)
            val change: (FoodEntryField, String) -> Unit = { field, value ->
                when (field) {
                    FoodEntryField.CALORIES -> actions.updateCalories(value)
                    FoodEntryField.PROTEIN -> actions.updateProtein(value)
                    FoodEntryField.CARBS -> actions.updateCarbs(value)
                    FoodEntryField.FAT -> actions.updateFat(value)
                    FoodEntryField.SODIUM -> actions.updateSodium(value)
                    FoodEntryField.SUGARS -> actions.updateSugars(value)
                    FoodEntryField.SATURATED_FAT -> actions.updateSaturatedFat(value)
                    else -> Unit
                }
            }
            NutritionFieldGrid(listOf(FoodEntryField.CALORIES, FoodEntryField.PROTEIN, FoodEntryField.CARBS, FoodEntryField.FAT),
                { values[it].orEmpty() }, change, enabled = !editor.saving, submitted = editor.error != null)
            TextButton({ controls.additional = !controls.additional }, enabled = !editor.saving) { Text(if (controls.additional) "추가 영양정보 접기" else "추가 영양정보 (선택)") }
            if (controls.additional) {
                Text("모르는 값은 비워 두세요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NutritionFieldGrid(listOf(FoodEntryField.SODIUM, FoodEntryField.SUGARS, FoodEntryField.SATURATED_FAT),
                    { values[it].orEmpty() }, change, enabled = !editor.saving, required = false, submitted = editor.error != null)
            }
        } else {
            NutritionTotalPreview(diningNutritionTotals(editor.copy(diningPortion = "1")), label = "1인분")
        }
        AppOutlinedButton(actions::saveReusableDiningOutMenu, Modifier.fillMaxWidth(), enabled = !editor.saving) { Text("이 메뉴를 내 외식 목록에 저장") }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SavedDiningOutMenuPicker(
    actions: MealScreenActions,
    editor: MealUiState.Ready,
    requester: BringIntoViewRequester
) {
    MealCatalogSearch(actions, editor, Modifier.fillMaxWidth())
    editor.selectedFood?.let { food ->
        AppCard(Modifier.fillMaxWidth().bringIntoViewRequester(requester)) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text("불러온 메뉴", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(food.displayName(), fontWeight = FontWeight.Bold)
                Text(food.extendedNutritionLabel())
            }
        }
    }
}
