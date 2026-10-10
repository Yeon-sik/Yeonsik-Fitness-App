package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
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
import com.yeonsik.fitnessapp.integration.nutrition.DiningProposal
import com.yeonsik.fitnessapp.integration.nutrition.latestDiningProposal
import org.json.JSONObject
import kotlinx.coroutines.launch

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
    fun updateDiningPortion(value: String) {}
    fun updateFoodConsumedPercent(itemId: String, value: String) {}
    fun updateDiningConsumedPercent(value: String) {}
    fun addDiningMenu() {}
    fun startAnotherDiningMenu(menuId: String) {}
    fun clearCurrentDiningMenu() {}
    fun updateDiningMenu(menu: MealDiningDraftItem) {}
    fun removeDiningMenu(menuId: String) {}
    fun openNutritionEditor() {}
    fun updateTime(value: String)
    fun saveFood()
    fun openManualFood()
    fun closeManualFood()
    fun updateManualFood(draft: ManualFoodDraft)
    fun saveManualFood()
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
    fun createDiningOutMenu() {}
    fun selectNutritionPublicationMenu(foodId: String)
    fun syncNutritionPublicationCatalog()
    fun publishNutritionMenu(locationId: String, menuId: String, catalogProductId: String)
    fun verifyNutritionMenu(locationId: String, menuId: String, catalogProductId: String) {}
    fun proposeDiningMerchant(facts: com.yeonsik.fitnessapp.integration.nutrition.DiningMerchantFacts) {}
    fun proposeDiningMenu(locationId: String?, merchantCandidateId: String?, menuName: String) {}
    fun resubmitDiningMerchant(previousCandidateId: String,
        facts: com.yeonsik.fitnessapp.integration.nutrition.DiningMerchantFacts, userVerified: Boolean) {}
    fun resubmitDiningMenu(previousCandidateId: String, restaurantId: String?, locationId: String?,
        merchantCandidateId: String?, menuName: String, userVerified: Boolean) {}
    fun refreshDiningProposals() {}
    fun publishApprovedDiningProposal() {}
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
    val currentReady = (homeState as? HomeUiState.Ready)?.takeIf {
        it.snapshot.ownerId == ownerId && it.snapshot.today == today
    }
    // Keep the last displayed layout across date loads, but never across account changes.
    // Only a result matching the selected date may replace it or enable record actions.
    var retainedReady by remember(ownerId) { mutableStateOf<HomeUiState.Ready?>(null) }
    SideEffect { if (currentReady != null) retainedReady = currentReady }
    val ready = currentReady ?: retainedReady
    val editor = (editorState as? MealUiState.Ready)?.takeIf {
        it.ownerId == ownerId && it.date == today
    }
    var deleteTargetId by rememberSaveable(ownerId, today) { mutableStateOf<String?>(null) }

    AppHeader("식단", back = actions::back)
    if (nutritionPublicationState.open && nutritionPublicationState.ownerId == ownerId) {
        NutritionPublicationDialog(
            state = nutritionPublicationState,
            priceTraceState = priceTraceState,
            actions = actions
        )
    }

    val snapshot = ready?.snapshot
    val totals = snapshot?.mealNutritionTotals?.get(snapshot.today)
    val loadError = (homeState as? HomeUiState.Error)?.takeIf {
        it.ownerId == ownerId && (it.date == null || it.date == today)
    }
    val loadingOverview = loadError == null && currentReady == null
    val hideOverviewMetrics = loadingOverview || loadError != null
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
    MealOverviewGlassSurface(Modifier.testTag("meal-day-overview")) {
        Text(
            if (selectedDate == LocalDate.now()) "오늘의 식단" else "이날의 식단",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Box(Modifier.fillMaxWidth()) {
            // Keep the normal, font-scale-aware metric layout as the loading placeholder's size.
            // Hidden values must also be absent from accessibility while a date is loading.
            Column(
                Modifier.fillMaxWidth()
                    .alpha(if (hideOverviewMetrics) 0f else 1f)
                    .then(if (hideOverviewMetrics) Modifier.clearAndSetSemantics {} else Modifier),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                MealDailyMetrics(
                    count = snapshot?.todayMeals?.size ?: 0,
                    calories = totals?.total("calories_kcal")?.describedValue() ?: "?"
                )
                MealMacroMetrics(
                    carbs = totals?.total("carbs_grams")?.describedValue() ?: "?",
                    protein = totals?.total("protein_grams")?.describedValue() ?: "?",
                    fat = totals?.total("fat_grams")?.describedValue() ?: "?"
                )
            }
            if (loadingOverview) {
                Column(
                    Modifier.matchParentSize().testTag("meal-day-overview-loading")
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small, Alignment.CenterVertically)
                ) {
                    ThinkingOrb()
                    Text("불러오는 중", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (loadError != null) {
                Text(
                    "식단을 불러오지 못했습니다.",
                    modifier = Modifier.align(Alignment.Center)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    Spacer(Modifier.height(AppSpacing.gap))
    if (editor?.editing != true) {
        editor?.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        val hasDraft = editor?.let(::hasMealRegistrationDraft) == true
        MealInputNotice(
            if (hasDraft) "작성 중인 식단이 있어요. 이어서 입력할 수 있어요."
            else "먹은 음식과 양을 기록해 보세요."
        )
        AppButton(onClick = actions::startDraft, Modifier.fillMaxWidth(), enabled = editor != null) {
            Text(if (hasDraft) "작성 중인 식단 이어서" else "식단 기록하기")
        }
    } else {
        MealEntryDialog(actions, editor, priceTraceState, mealLabel = "식단")
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

    Spacer(Modifier.height(AppSpacing.section))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("기록한 식단", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Text(snapshot?.today ?: today, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val recordActionsEnabled = currentReady != null && editor != null &&
        !editor.saving && !editor.recordActionSaving
    Box(Modifier.fillMaxWidth().testTag("meal-record-history")) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            if (snapshot == null) {
                Spacer(Modifier.height(FitnessSpacing.touch))
            } else if (snapshot.todayMeals.isEmpty()) {
                MealInputNotice("아직 기록한 식단이 없어요. 위에서 첫 식단을 기록해 보세요.")
            }
            snapshot?.todayMeals.orEmpty().forEach { meal ->
                key(meal.id) {
                    AppCard(Modifier.fillMaxWidth().testTag("meal-record-${meal.id}")) {
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
                                    enabled = meal.timeEditable && recordActionsEnabled
                                ) { Text("수정") }
                                AppOutlinedButton(
                                    onClick = { deleteTargetId = meal.id },
                                    modifier = Modifier.weight(1f),
                                    enabled = recordActionsEnabled,
                                    destructive = true
                                ) { Text("삭제") }
                            }
                        }
                    }
                }
            }
        }
        if (hideOverviewMetrics) {
            Box(Modifier.matchParentSize().testTag("meal-record-history-pending")
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center) {
                Surface(shape = FitnessShape.card, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)) {
                    Text(
                        if (loadError != null) "$today 식단을 불러오지 못했습니다."
                        else "$today 식단 불러오는 중",
                        Modifier.padding(AppSpacing.small).semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (loadError != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
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

    val deleteTarget = deleteTargetId?.takeIf { recordActionsEnabled }
        ?.let { id -> currentReady?.snapshot?.todayMeals?.firstOrNull { it.id == id } }
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
internal fun FoodMealEditor(actions: MealScreenActions, editor: MealUiState.Ready) {
    val focusManager = LocalFocusManager.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        Text("한 끼에 먹은 음식", style = MaterialTheme.typography.titleMedium)
        MealInputNotice("밥, 반찬, 음료를 함께 담으세요. 음식마다 전체 양과 먹은 비율을 따로 입력하세요.")
        AppOutlinedButton(
            onClick = actions::openNutritionEditor,
            modifier = Modifier.fillMaxWidth(),
            enabled = !editor.saving && !editor.draftLoading
        ) { Text("저장한 식단 구성 불러오기") }
        AppOutlinedButton(
            onClick = { if (editor.manualFoodEntry) actions.closeManualFood() else actions.openManualFood() },
            modifier = Modifier.fillMaxWidth(),
            selected = editor.manualFoodEntry,
            enabled = !editor.saving
        ) { Text(if (editor.manualFoodEntry) "영양정보 직접 입력 닫기" else "음식 직접 등록") }
        if (editor.manualFoodEntry) {
            ManualFoodEditor(editor.manualFoodDraft, actions::updateManualFood,
                actions::saveManualFood, actions::closeManualFood, editor.saving, editor.error)
        } else {
        // Keep search before the growing selection so adding foods preserves its position.
        MealCatalogSearch(actions, editor, Modifier.fillMaxWidth())
        if (editor.foodPortions.isNotEmpty()) {
            Text("담은 식품 ${editor.foodPortions.size}개", style = MaterialTheme.typography.titleMedium)
        }
        FoodPortionList(editor.foodPortions, actions::updateFoodQuantity,
            onRemove = { focusManager.clearFocus(); actions.removeFood(it) },
            modifier = Modifier.fillMaxWidth().testTag("meal-food-portions"), enabled = !editor.saving, amountLabel = "전체 양",
            itemContent = { portion ->
                val item = mealFoodItems(editor).first { it.food.id == portion.food.id }
                MealConsumedPercentControl(item.consumedPercent, { actions.updateFoodConsumedPercent(item.id, it) },
                    enabled = !editor.saving, tag = "meal-food-percent-${portion.food.id}")
                val calories = mealFoodNutritionTotals(listOf(item)).total(NutritionProfile.CALORIES_KCAL)
                Text("실제 섭취 · ${nutritionTotalText(calories)} kcal", style = MaterialTheme.typography.bodyMedium)
            })
        mealDraftNutritionTotals(editor.foodItems)?.let { totals ->
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(AppSpacing.card), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text("섭취 합계 · 음식 ${editor.foodItems.size}개", style = MaterialTheme.typography.titleSmall)
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
        val saveHint = foodMealRegistrationError(editor)
        saveHint?.let { MealInputNotice(it) }
        }
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
internal fun DiningOutEditor(
    actions: MealScreenActions,
    editor: MealUiState.Ready,
    priceTraceState: PriceTraceUiState,
    controls: DiningOutEditorState
) {
    val draft = editor.draft
    var showExtraNutrition by rememberSaveable(editor.ownerId, editor.date) {
        mutableStateOf(listOf(draft.sodium, draft.sugars, draft.saturatedFat).any { it.isNotBlank() })
    }
    val selectedMenuRequester = remember { BringIntoViewRequester() }
    val manualEntryRequester = remember { BringIntoViewRequester() }
    val compositionScope = rememberCoroutineScope()
    val nutritionError = diningOutRegistrationError(draft)
    val portionError = if (editor.diningPortion.trim().toDoubleOrNull()
        ?.let { it.isFinite() && it > 0.0 } == true) null else "먹은 양을 0보다 큰 숫자로 입력하세요."
    val saveHint = nutritionError ?: mealTimeError(draft.time) ?: portionError
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        MealInputNotice("한 가게에서 먹은 메뉴를 여러 개 담을 수 있어요. 메뉴마다 영양정보와 먹은 비율을 입력하세요.")
        SavedDiningOutMenuPicker(actions, editor, selectedMenuRequester)
        AppOutlinedButton(
            onClick = { controls.showPriceTrace = !controls.showPriceTrace },
            modifier = Modifier.fillMaxWidth(),
            enabled = !editor.saving
        ) { Text(if (controls.showPriceTrace) "식당 목록 닫기" else "식당 · 메뉴 검색") }
        if (controls.showPriceTrace) PriceTraceDiningOutPicker(actions, editor, priceTraceState, controls)
        DiningMenuDraftList(editor.diningMenus, actions::updateDiningMenu, actions::removeDiningMenu, enabled = !editor.saving,
            onAddAtStore = { menuId ->
                actions.startAnotherDiningMenu(menuId)
                compositionScope.launch { withFrameNanos { }; manualEntryRequester.bringIntoView() }
            })
        Text("추가할 메뉴", style = MaterialTheme.typography.titleSmall)
        AppTextField(draft.store, actions::updateStore, Modifier.fillMaxWidth(), { Text("상호명 (필수)") },
            enabled = !editor.saving,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        AppTextField(draft.branch, actions::updateBranch, Modifier.fillMaxWidth(), { Text("지점명 (선택)") },
            enabled = !editor.saving,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        AppTextField(draft.menu, actions::updateMenu, Modifier.fillMaxWidth().bringIntoViewRequester(manualEntryRequester)
            .testTag("meal-dining-menu-name"), { Text("먹은 메뉴 (필수)") },
            enabled = !editor.saving,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
        Text("전체 양과 1인분 영양정보", style = MaterialTheme.typography.titleSmall)
        MealInputNotice("영양정보는 1인분을 모두 먹었을 때(100%) 기준으로 입력하세요. 전체 양과 먹은 비율에 맞춰 섭취 합계가 자동 계산돼요.")
        MealNutritionField(editor.diningPortion, actions::updateDiningPortion, "전체 양", "인분", Modifier.fillMaxWidth(), !editor.saving)
        MealConsumedPercentControl(editor.diningConsumedPercent, actions::updateDiningConsumedPercent,
            enabled = !editor.saving, tag = "meal-dining-current-percent")
        if (portionError != null) MealInputNotice(portionError)
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
        saveHint?.let { MealInputNotice(it) }
        AppOutlinedButton(
            onClick = actions::saveReusableDiningOutMenu,
            modifier = Modifier.fillMaxWidth(),
            enabled = !editor.saving && nutritionError == null
        ) { Text("다음에도 쓸 메뉴로 저장") }
        AppButton(actions::addDiningMenu, Modifier.fillMaxWidth().testTag("meal-dining-add"),
            enabled = !editor.saving && diningMenuRegistrationError(currentDiningMenu(editor)) == null) { Text("이 메뉴를 끼니에 담기") }
        if (hasCurrentDiningMenu(editor)) {
            AppOutlinedButton(actions::clearCurrentDiningMenu, Modifier.fillMaxWidth(), enabled = !editor.saving) { Text("입력 중인 메뉴 취소") }
        }
        MealInputNotice("메뉴마다 영양정보와 먹은 비율을 입력해 담으세요. 아래 기록하기를 누르면 한 끼로 저장됩니다.")
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
        MealCatalogSearch(actions, editor, Modifier.fillMaxWidth())
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
private fun MealSearchStatus(editor: MealUiState.Ready) {
    val message = when {
        editor.searching -> "검색 중…"
        editor.query.isNotBlank() && editor.searchResults.isEmpty() ->
            "검색 결과가 없습니다. 영양정보를 직접 입력할 수 있습니다."
        else -> null
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant) }
}


private data class DiningProposalTarget(val merchant: Boolean,
    val locationId: String? = null, val merchantCandidateId: String? = null,
    val restaurantId: String? = null, val previous: DiningProposal? = null)

@Composable
internal fun DiningProposalControls(state: NutritionPublicationUiState, priceTrace: PriceTraceUiState.Ready?,
    actions: MealScreenActions, busy: Boolean) {
    val food = state.selectedFood ?: return
    val proposals = state.proposals.filter { it.nutritionFoodId == food.id }
    val privateMenu = food.id in state.privateFoodIds
    val enabled = state.remoteAvailable && !busy && privateMenu
    // Changing account/project/history invalidates an open confirmation, including publication.
    var target by remember(food.id, food.ownerId, proposals) { mutableStateOf<DiningProposalTarget?>(null) }
    var publishConfirmation by remember(food.id, food.ownerId, proposals) { mutableStateOf(false) }
    val merchant = proposals.latestDiningProposal(food.id, "merchant")
    val menu = proposals.latestDiningProposal(food.id, "menu")

    Text("등록 제안과 공개", fontWeight = FontWeight.Bold)
    Text("현재 PriceTrace에 이 가게 또는 메뉴가 없습니다. 등록을 제안할 수 있습니다. 관리자 승인 전까지 영양정보는 비공개로 유지됩니다.",
        style = MaterialTheme.typography.bodySmall)
    if (merchant == null && menu == null) {
        AppOutlinedButton(onClick = { target = DiningProposalTarget(merchant = true) }, enabled = enabled,
            modifier = Modifier.fillMaxWidth()) { Text("가게 등록 제안") }
    }
    if (menu == null) {
        priceTrace?.detail?.locations?.forEach { location ->
            AppOutlinedButton(onClick = { target = DiningProposalTarget(false, location.restaurantLocationId) },
                enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("${location.branchName.ifBlank { "본점" }} · 메뉴 등록 제안")
            }
        }
    }
    proposals.forEach { proposal ->
        Text((if (proposal.kind == "merchant") "가게" else "메뉴") + " · 요청 ${proposal.requestVersion} · " + when (proposal.status) {
            "pending" -> "관리자 검토 중 · 영양정보 비공개"
            "rejected" -> "등록 제안 거절 · 영양정보 비공개"
            "accepted" -> "승인됨 · 공개는 직접 선택해야 합니다"
            else -> "전송 확인 필요 · 새로고침으로 재시도"
        })
        proposal.reviewNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    listOfNotNull(merchant, menu).filter { it.status == "rejected" && it.candidateId != null }.forEach { rejected ->
        val request = remember(rejected.requestJson) { JSONObject(rejected.requestJson) }
        fun requestId(key: String) = request.optString(key).takeIf { !request.isNull(key) && it.isNotBlank() }
        AppOutlinedButton(onClick = { target = DiningProposalTarget(rejected.kind == "merchant",
            requestId("p_restaurant_location_id"), requestId("p_merchant_candidate_id"),
            requestId("p_restaurant_id"), rejected) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(if (rejected.kind == "merchant") "거절된 가게 제안 수정" else "거절된 메뉴 제안 수정")
        }
        if (rejected.kind == "menu") priceTrace?.detail?.locations?.forEach { location ->
            AppOutlinedButton(onClick = { target = DiningProposalTarget(false,
                location.restaurantLocationId, restaurantId = priceTrace.detail.restaurantId, previous = rejected) },
                enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("${location.branchName.ifBlank { "본점" }} · 지점 변경 후 재제출")
            }
        }
        if (rejected.kind == "menu" && merchant?.status == "accepted" && merchant.candidateId != null
            && merchant.candidateId != requestId("p_merchant_candidate_id")) {
            AppOutlinedButton(onClick = { target = DiningProposalTarget(false,
                merchantCandidateId = merchant.candidateId, previous = rejected) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth()) { Text("수정된 가게 제안에 메뉴 재제출") }
        }
    }
    if (merchant?.status == "accepted" && merchant.restaurantId != null && menu == null) {
        AppButton(onClick = { target = DiningProposalTarget(false, merchantCandidateId = merchant.candidateId) },
            enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("가게 승인됨 · 메뉴 등록 제안") }
    }
    if (menu?.canPublish == true && privateMenu) {
        AppButton(onClick = { publishConfirmation = true }, enabled = enabled,
            modifier = Modifier.fillMaxWidth()) { Text("승인됨 · PT에 공개 연결") }
    }
    if (proposals.isNotEmpty()) {
        AppOutlinedButton(onClick = actions::refreshDiningProposals,
            enabled = state.remoteAvailable && !busy, modifier = Modifier.fillMaxWidth()) { Text("제안 상태 새로고침") }
    }
    target?.let { selected ->
        val selectedRestaurantName = priceTrace?.detail?.takeIf {
            selected.previous == null || selected.restaurantId == it.restaurantId
        }?.restaurantName.orEmpty()
        DiningProposalConfirmation(food, selected, selectedRestaurantName,
            busy, onDismiss = { target = null }, onSubmit = { facts, menuName, verified ->
                if (selected.previous != null && selected.merchant)
                    actions.resubmitDiningMerchant(selected.previous.candidateId!!, facts, verified)
                else if (selected.previous != null) actions.resubmitDiningMenu(selected.previous.candidateId!!,
                    selected.restaurantId, selected.locationId, selected.merchantCandidateId, menuName, verified)
                else if (selected.merchant) actions.proposeDiningMerchant(facts)
                else actions.proposeDiningMenu(selected.locationId, selected.merchantCandidateId, menuName)
                target = null
            })
    }
    if (publishConfirmation) {
        AlertDialog(onDismissRequest = { if (!busy) publishConfirmation = false },
            title = { Text("승인된 메뉴에 공개 연결") },
            text = { Text("${food.displayName()}의 추정 영양정보를 승인된 PT 메뉴에 공개합니다. 식사 기록은 공개되지 않습니다.") },
            confirmButton = { TextButton(enabled = enabled, onClick = {
                actions.publishApprovedDiningProposal(); publishConfirmation = false
            }) { Text("공개 연결") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { publishConfirmation = false }) { Text("취소") } })
    }
}

@Composable
private fun DiningProposalConfirmation(food: NutritionFood, target: DiningProposalTarget,
    restaurantName: String, busy: Boolean, onDismiss: () -> Unit,
    onSubmit: (com.yeonsik.fitnessapp.integration.nutrition.DiningMerchantFacts, String, Boolean) -> Unit) {
    val request = remember(target) { target.previous?.let { JSONObject(it.requestJson) } }
    val merchant = request?.optJSONObject("p_merchant")
    var name by remember(target) { mutableStateOf(merchant?.optString("merchant_name") ?: food.brand.orEmpty()) }
    var branch by remember(target) { mutableStateOf(merchant?.optString("branch_name").orEmpty()) }
    var address by remember(target) { mutableStateOf(merchant?.optString("address").orEmpty()) }
    var phone by remember(target) { mutableStateOf(merchant?.optString("phone").orEmpty()) }
    var businessNumber by remember(target) { mutableStateOf(merchant?.optString("business_registration_number").orEmpty()) }
    var menuName by remember(target) { mutableStateOf(request?.optString("p_menu_name") ?: food.name) }
    var reconfirmed by remember(target, name, branch, address, phone, businessNumber, menuName) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (target.previous != null) "거절된 제안 수정·재확인"
            else if (target.merchant) "가게 등록 제안" else "메뉴 등록 제안") },
        text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text("직접 확인한 정보만 입력하세요. 모르는 항목은 비워두세요. 제안에는 영양 값과 식사 기록을 보내지 않습니다.")
                if (target.merchant) {
                    AppTextField(value = name, onValueChange = { name = it }, label = { Text("가게명 · 필수") })
                    AppTextField(value = branch, onValueChange = { branch = it }, label = { Text("지점명 · 선택") })
                    AppTextField(value = address, onValueChange = { address = it }, label = { Text("주소 · 선택") })
                    AppTextField(value = phone, onValueChange = { phone = it }, label = { Text("전화번호 · 선택") })
                    AppTextField(value = businessNumber, onValueChange = { businessNumber = it }, label = { Text("사업자등록번호 · 선택") })
                } else {
                    if (restaurantName.isNotBlank() && target.merchantCandidateId == null) Text(restaurantName)
                    if (target.previous != null && restaurantName.isBlank() && target.merchantCandidateId == null)
                        Text("이전에 제출한 가게·지점을 사용합니다. 지점을 바꾸려면 조회한 PT 지점에서 재제출을 선택하세요.")
                    AppTextField(value = menuName, onValueChange = { menuName = it }, label = { Text("메뉴명 · 필수") })
                }
                Text("확인한 정보를 PT에 제안합니다. 관리자 승인 후에도 공개 연결은 직접 선택해야 합니다.")
                if (target.previous != null) {
                    Text("이전 거절 이력은 보존됩니다. 수정한 정보로 새 검토 요청을 제출합니다.")
                    Row(Modifier.fillMaxWidth().toggleable(value = reconfirmed,
                        onValueChange = { reconfirmed = it }, role = Role.Checkbox),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = reconfirmed, onCheckedChange = null)
                        Text("수정한 정보를 직접 확인했습니다")
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy && (target.previous == null || reconfirmed)
            && (if (target.merchant) name.isNotBlank() else menuName.isNotBlank()),
            onClick = { onSubmit(com.yeonsik.fitnessapp.integration.nutrition.DiningMerchantFacts(
                name, branch, address, phone, businessNumber), menuName, reconfirmed) }) {
                Text(if (target.previous == null) "확인한 정보로 PT에 등록 제안" else "수정·재확인한 정보로 재제출") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("취소") } })
}
