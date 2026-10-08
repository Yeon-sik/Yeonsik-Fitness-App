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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
    fun updateFoodQuantity(itemId: String, value: String)
    fun removeFood(itemId: String)
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
    actions: MealScreenActions
) {
    val ready = homeState as? HomeUiState.Ready
    val editor = editorState as? MealUiState.Ready
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val editorScrollTarget = mealEditorScrollTarget(editor)
    val editorStartRequester = remember { BringIntoViewRequester() }
    var previousEditorScrollTarget by remember {
        mutableStateOf<MealEditorScrollTarget?>(null)
    }
    LaunchedEffect(editorScrollTarget) {
        val targetChanged = previousEditorScrollTarget != null &&
            previousEditorScrollTarget != editorScrollTarget
        previousEditorScrollTarget = editorScrollTarget
        if (targetChanged && editorScrollTarget?.editing == true) {
            editorStartRequester.bringIntoView()
        }
    }

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
                    .semantics { contentDescription = "이전 날짜" },
                enabled = editor?.saving != true
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
                enabled = selectedDate.isBefore(LocalDate.now()) && editor?.saving != true
            ) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        AppOutlinedButton(
            onClick = { actions.selectDate(LocalDate.now().toString()) },
            enabled = !selectedDate.isEqual(LocalDate.now()) && editor?.saving != true,
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
    if (editor == null || editor.ownerId != ownerId || editor.date != today) {
        Text("식단 입력을 준비하는 중입니다.")
    } else if (!editor.editing) {
        editor.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        MealInputNotice(
            if (hasMealRegistrationDraft(editor)) "작성 중인 식단이 있어요. 이어서 입력할 수 있어요."
            else "먹은 음식과 양을 기록해 보세요."
        )
        AppButton(onClick = actions::startDraft, Modifier.fillMaxWidth()) {
            Text(if (hasMealRegistrationDraft(editor)) "작성 중인 식단 이어서" else "식단 기록하기")
        }
    } else {
        Text("식단 기록", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(editorStartRequester),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)
        ) {
            AppOutlinedButton(
                onClick = actions::chooseFood,
                modifier = Modifier.weight(1f),
                selected = !editor.diningOut,
                enabled = !editor.saving
            ) { Text("음식 선택") }
            AppOutlinedButton(
                onClick = actions::chooseDiningOut,
                modifier = Modifier.weight(1f),
                selected = editor.diningOut,
                enabled = !editor.saving
            ) { Text("외식 입력") }
        }
        if (editor.diningOut) {
            DiningOutEditor(actions, editor, priceTraceState)
        } else {
            FoodMealEditor(actions, editor)
        }
    }

    Spacer(Modifier.height(AppSpacing.section))
    Text("기록한 식단", style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold)
    if (snapshot.todayMeals.isEmpty()) {
        MealInputNotice("아직 기록한 식단이 없어요. 위에서 첫 식단을 기록해 보세요.")
    }
    snapshot.todayMeals.forEach { meal ->
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AppSpacing.card),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text(meal.previewTitle, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                if (meal.compositionCount > 0 && !MealRecordKind.isDiningOut(meal.mealKind)) {
                    Text("${meal.mealLabel} · 음식 ${meal.compositionCount}개",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(meal.previewSubtitle(), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${meal.calories} kcal · 단백질 ${meal.proteinGrams}g",
                    style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    AppOutlinedButton(
                        onClick = { actions.editMeal(meal) },
                        modifier = Modifier.weight(1f),
                        enabled = meal.timeEditable && editor?.saving != true && editor?.recordActionSaving != true
                    ) { Text("수정") }
                    AppOutlinedButton(
                        onClick = { deleteTargetId = meal.id },
                        modifier = Modifier.weight(1f),
                        enabled = editor?.saving != true && editor?.recordActionSaving != true,
                        destructive = true
                    ) { Text("삭제") }
                }
            }
        }
    }
    Spacer(Modifier.height(AppSpacing.gap))
    AppOutlinedButton(
        onClick = actions::openNutritionPublication,
        modifier = Modifier.fillMaxWidth(),
        enabled = editor?.saving != true
    ) { Text("외식 영양정보 연결 · 공개") }

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
                    MealTimeField(
                        value = editTime,
                        onValueChange = { editTime = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !editor.recordActionSaving
                    )
                    editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { actions.saveMealTime(recordEditor.recordId, editTime) },
                    enabled = !editor.recordActionSaving && mealTimeError(editTime) == null
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
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
private fun FoodMealEditor(actions: MealScreenActions, editor: MealUiState.Ready) {
    val focusManager = LocalFocusManager.current
    var addingFood by rememberSaveable(editor.ownerId, editor.date) {
        mutableStateOf(false)
    }
    val lastFoodRequester = remember { BringIntoViewRequester() }
    val searchRequester = remember { BringIntoViewRequester() }
    val lastFoodId = editor.foodItems.lastOrNull()?.id
    LaunchedEffect(lastFoodId, addingFood) {
        if (lastFoodId != null && !addingFood) lastFoodRequester.bringIntoView()
        if (addingFood) searchRequester.bringIntoView()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        Text("한 끼에 먹은 음식", style = MaterialTheme.typography.titleMedium)
        MealInputNotice("밥, 반찬, 음료를 함께 담으세요. 음식마다 먹은 양을 바꿀 수 있어요.")
        editor.foodItems.forEach { item ->
            key(item.id) {
                MealFoodDraftCard(
                    item = item,
                    onQuantityChange = { actions.updateFoodQuantity(item.id, it) },
                    onRemove = { focusManager.clearFocus(); actions.removeFood(item.id) },
                    enabled = !editor.saving,
                    modifier = if (item.id == lastFoodId) {
                        Modifier.fillMaxWidth().bringIntoViewRequester(lastFoodRequester)
                    } else Modifier.fillMaxWidth()
                )
            }
        }
        if (editor.foodItems.isNotEmpty()) {
            AppOutlinedButton(
                onClick = { addingFood = !addingFood; focusManager.clearFocus(); if (addingFood) actions.searchFood("") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !editor.saving
            ) { Text(if (addingFood) "음식 검색 닫기" else "음식 더 담기") }
        }
        if (editor.foodItems.isEmpty() || addingFood) {
            MealFoodSearch(
                editor = editor,
                onSearch = actions::searchFood,
                onSelect = {
                    addingFood = false
                    actions.selectFood(it)
                },
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(searchRequester)
            )
        }
        mealDraftNutritionTotals(editor.foodItems)?.let { totals ->
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(AppSpacing.card), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text("한 끼 합계 · 음식 ${editor.foodItems.size}개", style = MaterialTheme.typography.titleSmall)
                    Text(NutritionCalculator.describeTotal(totals.total(NutritionProfile.CALORIES_KCAL)) + " kcal",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    listOf(
                        "탄수화물" to NutritionProfile.CARBS_GRAMS,
                        "단백질" to NutritionProfile.PROTEIN_GRAMS,
                        "지방" to NutritionProfile.FAT_GRAMS
                    ).forEach { (label, nutrient) ->
                        Text("$label ${NutritionCalculator.describeTotal(totals.total(nutrient))} g",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        MealTimeField(editor.draft.time, actions::updateTime, Modifier.fillMaxWidth(), enabled = !editor.saving)
        editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val saveHint = foodMealRegistrationError(editor)
        saveHint?.let { MealInputNotice(it) }
        MealEditorActions(
            onLater = actions::closeDraft,
            onSave = actions::saveFood,
            saving = editor.saving,
            canSave = saveHint == null,
            label = "한 끼 저장",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MealFoodDraftCard(
    item: MealFoodDraftItem,
    onQuantityChange: (String) -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val food = item.food
    val focusManager = LocalFocusManager.current
    val quantity = mealQuantityValue(item.quantity)
    val quantityError = mealQuantityError(item.quantity)
    AppCard(modifier) {
        Column(Modifier.padding(AppSpacing.card), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (food.isPackagedFood()) food.packagedProductLabel() else food.displayName(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onRemove, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "${food.displayName()} 음식 빼기" }) {
                    Text("빼기")
                }
            }
            Text("영양정보 기준 · ${food.basisLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppTextField(
                value = item.quantity,
                onValueChange = onQuantityChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("먹은 양 (${mealQuantityUnit(food.basisUnit)})") },
                enabled = enabled,
                isError = quantityError != null,
                supportingText = {
                    Text(quantityError ?: "기준량이 입력되어 있어요. 실제 먹은 양으로 바꿔 주세요.")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
            if (food.basisAmount.isFinite() && food.basisAmount > 0.0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    listOf(0.5, 1.0, 2.0).forEach { multiple ->
                        val amount = food.basisAmount * multiple
                        if (amount.isFinite() && amount > 0.0) {
                            AppOutlinedButton(
                                onClick = { focusManager.clearFocus(); onQuantityChange(mealQuantityText(amount)) },
                                selected = quantity == amount,
                                enabled = enabled
                            ) { Text("${mealQuantityText(amount)} ${mealQuantityUnit(food.basisUnit)}") }
                        }
                    }
                }
            }
            quantity?.let { amount ->
                runCatching { NutritionCalculator.forQuantity(food, amount) }.getOrNull()?.let { profile ->
                    MealNutritionPreview(profile, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DiningOutEditor(
    actions: MealScreenActions,
    editor: MealUiState.Ready,
    priceTraceState: PriceTraceUiState
) {
    val draft = editor.draft
    var showPriceTrace by rememberSaveable(editor.ownerId, editor.date) { mutableStateOf(false) }
    var showExtraNutrition by rememberSaveable(editor.ownerId, editor.date) {
        mutableStateOf(listOf(draft.sodium, draft.sugars, draft.saturatedFat).any { it.isNotBlank() })
    }
    val selectedMenuRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(editor.selectedFood?.id) {
        if (editor.selectedFood != null) selectedMenuRequester.bringIntoView()
    }
    val nutritionError = diningOutRegistrationError(draft)
    val saveHint = nutritionError ?: mealTimeError(draft.time)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        MealInputNotice("먹은 메뉴와 영양정보를 입력하세요. 저장된 메뉴를 고르면 입력값이 채워져요.")
        SavedDiningOutMenuPicker(actions, editor, selectedMenuRequester)
        AppOutlinedButton(
            onClick = { showPriceTrace = !showPriceTrace },
            modifier = Modifier.fillMaxWidth(),
            enabled = !editor.saving
        ) { Text(if (showPriceTrace) "식당 목록 닫기" else "식당·메뉴 목록에서 선택") }
        if (showPriceTrace) PriceTraceDiningOutPicker(actions, editor, priceTraceState)
        Text("먹은 메뉴", style = MaterialTheme.typography.titleSmall)
        AppTextField(draft.store, actions::updateStore, Modifier.fillMaxWidth(), { Text("상호명 (필수)") },
            enabled = !editor.saving,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        AppTextField(draft.branch, actions::updateBranch, Modifier.fillMaxWidth(), { Text("지점명 (선택)") },
            enabled = !editor.saving,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        AppTextField(draft.menu, actions::updateMenu, Modifier.fillMaxWidth(), { Text("먹은 메뉴 (필수)") },
            enabled = !editor.saving,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
        MealTimeField(draft.time, actions::updateTime, Modifier.fillMaxWidth(), enabled = !editor.saving)
        Text("먹은 양의 영양정보 (필수)", style = MaterialTheme.typography.titleSmall)
        MealInputNotice("실제로 먹은 양에 해당하는 값을 입력하세요. 0인 값은 0으로 입력해 주세요.")
        MealNutritionField(draft.calories, actions::updateCalories, "칼로리", "kcal", Modifier.fillMaxWidth(), !editor.saving)
        MealNutritionField(draft.carbs, actions::updateCarbs, "탄수화물", "g", Modifier.fillMaxWidth(), !editor.saving)
        MealNutritionField(draft.protein, actions::updateProtein, "단백질", "g", Modifier.fillMaxWidth(), !editor.saving)
        MealNutritionField(draft.fat, actions::updateFat, "지방", "g", Modifier.fillMaxWidth(), !editor.saving)
        AppOutlinedButton(
            onClick = { showExtraNutrition = !showExtraNutrition },
            modifier = Modifier.fillMaxWidth(),
            enabled = !editor.saving
        ) { Text(if (showExtraNutrition) "선택 영양정보 접기" else "나트륨·당류·포화지방 입력 (선택)") }
        if (showExtraNutrition) {
            MealInputNotice("모르는 값은 비워 두면 미확인으로 저장돼요.")
            MealNutritionField(draft.sodium, actions::updateSodium, "나트륨", "mg", Modifier.fillMaxWidth(), !editor.saving, required = false)
            MealNutritionField(draft.sugars, actions::updateSugars, "당류", "g", Modifier.fillMaxWidth(), !editor.saving, required = false)
            MealNutritionField(draft.saturatedFat, actions::updateSaturatedFat, "포화지방", "g", Modifier.fillMaxWidth(), !editor.saving, required = false)
        }
        editor.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        saveHint?.let { MealInputNotice(it) }
        MealEditorActions(actions::closeDraft, actions::saveDiningOut, editor.saving, saveHint == null,
            "외식 저장", Modifier.fillMaxWidth())
        AppOutlinedButton(
            onClick = actions::saveReusableDiningOutMenu,
            modifier = Modifier.fillMaxWidth(),
            enabled = !editor.saving && nutritionError == null
        ) { Text("다음에도 쓸 메뉴로 저장") }
        MealInputNotice("메뉴 저장은 음식 목록에만 추가돼요. 먹은 기록은 ‘외식 저장’을 눌러 주세요.")
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SavedDiningOutMenuPicker(
    actions: MealScreenActions,
    editor: MealUiState.Ready,
    requester: BringIntoViewRequester
) {
    var choosingMenu by rememberSaveable(editor.ownerId, editor.date) { mutableStateOf(false) }
    AppOutlinedButton(
        onClick = { choosingMenu = !choosingMenu; if (choosingMenu) actions.searchFood("") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !editor.saving
    ) { Text(if (choosingMenu) "저장된 메뉴 목록 닫기" else "저장된 메뉴에서 선택") }
    if (choosingMenu) {
        MealFoodSearch(
            editor = editor,
            onSearch = actions::searchFood,
            onSelect = { choosingMenu = false; actions.useDiningOutFood(it) },
            modifier = Modifier.fillMaxWidth()
        )
    }
    editor.selectedFood?.let { food ->
        AppCard(Modifier.fillMaxWidth().bringIntoViewRequester(requester)) {
            Column(Modifier.padding(AppSpacing.card),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text("${food.displayName()}에서 입력값을 가져왔어요.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("메뉴와 영양정보를 확인하고 먹은 양에 맞게 수정하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PriceTraceDiningOutPicker(
    actions: MealScreenActions,
    editor: MealUiState.Ready,
    priceTraceState: PriceTraceUiState
) {
    val state = priceTraceState as? PriceTraceUiState.Ready
    val query = state?.query ?: editor.priceTraceQuery

    AppTextField(query, actions::updatePriceTraceQuery, Modifier.fillMaxWidth(),
        label = { Text("식당 검색") }, enabled = !editor.saving,
        placeholder = { Text("상호명으로 검색") })
    AppButton(
        onClick = actions::searchPriceTraceRestaurants,
        enabled = query.isNotBlank() && state?.loading != true && !editor.saving,
        modifier = Modifier.fillMaxWidth()
    ) { Text("검색") }
    if (state?.loading == true) Text("PriceTrace 정보를 불러오는 중입니다.")
    state?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state?.restaurants.orEmpty().forEach { restaurant ->
        AppOutlinedButton(
            onClick = { actions.loadPriceTraceRestaurant(restaurant.restaurantId) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state?.loading != true && !editor.saving
        ) { Text(restaurant.restaurantName) }
    }
    state?.detail?.let { restaurant ->
        Text("${restaurant.restaurantName} 메뉴", fontWeight = FontWeight.Bold)
        restaurant.menus.forEach { menu ->
            restaurant.locations.forEach { location ->
                AppOutlinedButton(
                    onClick = {
                        actions.applyPriceTraceSelection(
                            restaurant.restaurantId,
                            restaurant.restaurantName,
                            location.restaurantLocationId,
                            location.branchName,
                            menu.restaurantMenuId,
                            menu.menuName,
                            menu.catalogProductId
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state?.loading != true && !editor.saving
                ) {
                    Text("${restaurant.restaurantName} · ${location.branchName.ifBlank { "본점" }} · ${menu.menuName}")
                }
            }
        }
    }
}


@Composable
private fun NutritionPublicationDialog(
    state: NutritionPublicationUiState,
    priceTraceState: PriceTraceUiState,
    actions: MealScreenActions
) {
    var menuQuery by remember { mutableStateOf("") }
    var pendingTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val priceTrace = priceTraceState as? PriceTraceUiState.Ready
    val selectedNutritionFood = state.selectedFood
    val busy = state.loading || state.syncing || state.publishing
    val visibleMenus = state.menus.filter { food ->
        val query = menuQuery.trim()
        query.isEmpty() || food.displayName().contains(query, ignoreCase = true)
                || food.name.contains(query, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) actions.closeNutritionPublication() },
        title = { Text("외식 영양정보 연결 · 공개") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                Text("Nutrition에 저장된 외식 영양정보를 기존 PriceTrace 식당·지점·메뉴에 연결합니다.")
                Text("영양 값은 외식 메뉴 추정값입니다. 공개 전에 선택한 메뉴와 지점을 확인하세요.")

                when {
                    state.loading -> Text("Nutrition 외식 메뉴를 불러오는 중입니다.")
                    state.menus.isEmpty() -> {
                        Text("저장된 외식 메뉴가 없습니다. OCR 등록 자료가 원격 Nutrition에 있다면 먼저 동기화해야 할 수 있습니다.")
                        Text("동기화는 현재 Nutrition 계정의 기존 push/pull 규칙으로 카탈로그 전체를 처리합니다.", style = MaterialTheme.typography.bodySmall)
                        AppButton(
                            onClick = actions::syncNutritionPublicationCatalog,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy
                        ) { Text(if (state.syncing) "Nutrition 동기화 중" else "Nutrition 동기화 후 목록 불러오기") }
                    }
                    else -> {
                        AppTextField(
                            value = menuQuery,
                            onValueChange = { menuQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("등록된 Nutrition 외식 메뉴 검색") }
                        )
                        visibleMenus.forEach { food ->
                            val selected = food.id == state.selectedFoodId
                            AppOutlinedButton(
                                onClick = { actions.selectNutritionPublicationMenu(food.id) },
                                modifier = Modifier.fillMaxWidth(),
                                selected = selected,
                                enabled = !busy
                            ) {
                                Text("${food.displayName()} · 추정 영양정보")
                            }
                        }
                        state.selectedFood?.let { food ->
                            AppCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(AppSpacing.card)) {
                                    Text("선택된 Nutrition 행", fontWeight = FontWeight.Bold)
                                    Text(food.displayName())
                                    Text(food.extendedNutritionLabel())
                                    Text("출처 유형 · ${food.sourceType}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            AppTextField(
                                value = priceTrace?.query.orEmpty(),
                                onValueChange = actions::updatePriceTraceQuery,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("PriceTrace 식당 검색") }
                            )
                            AppButton(
                                onClick = actions::searchPriceTraceRestaurants,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !busy && !priceTrace?.query.isNullOrBlank()
                            ) { Text("PriceTrace 식당 다시 검색") }
                            if (priceTrace?.loading == true) Text("PriceTrace 식당·메뉴를 불러오는 중입니다.")
                            priceTrace?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            priceTrace?.restaurants.orEmpty().forEach { restaurant ->
                                AppOutlinedButton(
                                    onClick = { actions.loadPriceTraceRestaurant(restaurant.restaurantId) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !busy
                                ) { Text(restaurant.restaurantName) }
                            }
                            priceTrace?.detail?.let { detail ->
                                Text("${detail.restaurantName} · 지점과 기존 메뉴를 선택하세요", fontWeight = FontWeight.Bold)
                                if (detail.locations.isEmpty() || detail.menus.isEmpty()) {
                                    Text("선택할 수 있는 PriceTrace 지점 또는 메뉴가 없습니다.")
                                }
                                detail.locations.forEach { location ->
                                    detail.menus.forEach { menu ->
                                        AppOutlinedButton(
                                            onClick = {
                                                pendingTarget = Triple(
                                                    location.restaurantLocationId,
                                                    menu.restaurantMenuId,
                                                    menu.catalogProductId
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !busy
                                        ) {
                                            Text("${detail.restaurantName} · ${location.branchName.ifBlank { "본점" }} · ${menu.menuName}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = actions::closeNutritionPublication, enabled = !busy) {
                Text(if (state.publishing) "공개 중" else "닫기")
            }
        }
    )

    val target = pendingTarget
    val detail = priceTrace?.detail
    val selectedLocation = target?.let { pending ->
        detail?.locations?.singleOrNull { it.restaurantLocationId == pending.first }
    }
    val selectedMenu = target?.let { pending ->
        detail?.menus?.singleOrNull {
            it.restaurantMenuId == pending.second && it.catalogProductId == pending.third
        }
    }
    if (target != null && selectedNutritionFood != null && selectedLocation != null && selectedMenu != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) pendingTarget = null },
            title = { Text("공개 연결 확인") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text("${selectedNutritionFood.displayName()}의 추정 영양정보를 공개합니다.")
                    Text("PriceTrace · ${detail?.restaurantName} · ${selectedLocation.branchName.ifBlank { "본점" }} · ${selectedMenu.menuName}")
                    Text("공개하면 Fitness와 PriceTrace 공개 조회에서 이 메뉴의 영양정보를 확인할 수 있습니다.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.publishNutritionMenu(target.first, target.second, target.third)
                        pendingTarget = null
                    },
                    enabled = !busy
                ) { Text("공개 연결") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTarget = null }, enabled = !busy) { Text("취소") }
            }
        )
    }
}
