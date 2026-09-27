package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
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
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.cardio.model.*
import com.yeonsik.fitnessapp.feature.cardio.ui.*
import com.yeonsik.fitnessapp.feature.development.ui.*
import com.yeonsik.fitnessapp.feature.exercise.ui.*
import com.yeonsik.fitnessapp.feature.home.ui.*
import com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionAnalysisReport
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
    fun showBodyMetric()
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
    ownerId: String,
    today: String,
    unit: MassUnit,
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

    AppHeader("식사", today, back = actions::back)
    if (ready == null || ready.snapshot.ownerId != ownerId || ready.snapshot.today != today) {
        Text("식사 기록을 불러오는 중입니다.")
        return
    }
    val snapshot = ready.snapshot
    val totals = snapshot.mealNutritionTotals[today]
    val selectedDate = runCatching { LocalDate.parse(today) }.getOrNull()
    if (selectedDate != null) {
        MealOverviewGlassSurface {
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
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                TextButton(
                    onClick = { actions.selectDate(selectedDate.minusDays(1).toString()) },
                    modifier = Modifier.heightIn(min = AppSpacing.touch)
                ) { Text("‹ 이전") }
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
                    modifier = Modifier.heightIn(min = AppSpacing.touch),
                    enabled = selectedDate.isBefore(LocalDate.now())
                ) { Text("다음 ›") }
            }
            TextButton(
                onClick = { actions.selectDate(LocalDate.now().toString()) },
                modifier = Modifier.align(Alignment.End).heightIn(min = AppSpacing.touch),
                enabled = !selectedDate.isEqual(LocalDate.now())
            ) { Text("오늘로 이동") }
        }
    }
    Spacer(Modifier.height(AppSpacing.gap))
    MealOverviewGlassSurface {
        Text(
            if (selectedDate == LocalDate.now()) "오늘의 식사" else "이날의 식사",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        MealDailyMetrics(
            count = snapshot.todayMeals.size,
            calories = totals?.total("calories_kcal")?.describedValue() ?: "?"
        )
    }
    Spacer(Modifier.height(AppSpacing.gap))
    snapshot.todayMeals.forEach { meal ->
        AppCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(AppSpacing.card),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
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

    editor?.takeIf { it.ownerId == ownerId && it.date == today }?.let { mealEditor ->
        NutritionAnalysisSection(
            report = mealEditor.nutritionAnalysis,
            loading = mealEditor.nutritionAnalysisLoading,
            error = mealEditor.nutritionAnalysisError
        )
    }

    if (editor == null || editor.ownerId != ownerId || editor.date != today) {
        Text("식사 입력을 준비하는 중입니다.")
    } else if (!editor.editing) {
        editor.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        AppButton(onClick = actions::startDraft, Modifier.fillMaxWidth()) {
            Text("새 끼니 기록")
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(editorStartRequester),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)
        ) {
            AppOutlinedButton(
                onClick = actions::chooseFood,
                modifier = Modifier.weight(1f),
                selected = !editor.diningOut
            ) { Text("식단") }
            AppOutlinedButton(
                onClick = actions::chooseDiningOut,
                modifier = Modifier.weight(1f),
                selected = editor.diningOut
            ) { Text("외식") }
        }
        if (editor.diningOut) {
            DiningOutEditor(actions, editor, priceTraceState)
        } else {
            FoodMealEditor(actions, editor)
        }
    }

    AppOutlinedButton(onClick = actions::showBodyMetric, Modifier.fillMaxWidth()) {
        Text("오늘 체중 · ${snapshot.todayWeight?.let { MassFormatter.withUnit(it.weightKg, unit) } ?: "미기록"}")
    }

    val deleteTarget = deleteTargetId?.let { id -> snapshot.todayMeals.firstOrNull { it.id == id } }
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("식사 기록 삭제") },
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
            title = { Text("식사 수정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(recordEditor.title, fontWeight = FontWeight.Bold)
                    AppTextField(
                        value = editTime,
                        onValueChange = { editTime = it },
                        label = { Text("식사 시각 HH:mm") }
                    )
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
private fun NutritionAnalysisSection(
    report: NutritionAnalysisReport?,
    loading: Boolean,
    error: String?
) {
    MealOverviewGlassSurface {
        Text("영양 분석", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        when {
            loading -> Text("영양 분석을 계산하는 중입니다.")
            error != null -> Text(error, color = MaterialTheme.colorScheme.error)
            report == null -> Text("영양 분석 데이터가 없습니다.")
            else -> {
                Text(
                    "${report.recordedMealCount}끼 · ${report.recordedDays}일 기록 · ${report.calendarDayCount}일 범위",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                report.target?.phase?.let { phase ->
                    Text("목표 단계 · ${AthleteNutritionGoal.phaseLabel(phase)}")
                } ?: Text("영양 목표 미설정")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("목표 대비 상태", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                NutritionAnalysisMetricRows(report)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("단백질 분포", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                if (report.proteinDistribution.entries.isEmpty()) {
                    Text("기록된 식사가 없습니다.")
                } else {
                    report.proteinDistribution.entries.forEach { entry ->
                        val share = entry.shareOfKnownTotalPercent?.let {
                            " · ${NutritionCalculator.trim(it)}%"
                        }.orEmpty()
                        Text(
                            "${entry.title} · ${entry.protein.displayValue()}g$share · " +
                                entry.protein.provenanceLabel(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                val detailedKeys = report.metrics.keys.filterNot {
                    NutritionProfile.PRIMARY_DISPLAY_ORDER.contains(it)
                }
                if (detailedKeys.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("상세 영양소", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    detailedKeys.forEach { key ->
                        val metric = report.metrics.getValue(key)
                        Text(
                            "${NutritionProfile.labelOf(key).ifBlank { key }} · " +
                                "${metric.displayValue()} ${NutritionProfile.unitOf(key)} · " +
                                metric.provenanceLabel(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NutritionAnalysisMetricRows(report: NutritionAnalysisReport) {
    val keys = NutritionProfile.PRIMARY_DISPLAY_ORDER.filter { it in report.metrics }
    if (LocalDensity.current.fontScale >= 1.35f) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            keys.forEach { key -> NutritionAnalysisMetricTile(report, key, Modifier.fillMaxWidth()) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            keys.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    row.forEach { key ->
                        NutritionAnalysisMetricTile(report, key, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NutritionAnalysisMetricTile(
    report: NutritionAnalysisReport,
    key: String,
    modifier: Modifier = Modifier
) {
    val metric = report.metrics.getValue(key)
    val comparison = report.comparison(key)
    Column(
        modifier.clip(FitnessShape.card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(AppSpacing.small),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
    ) {
        Text(NutritionProfile.labelOf(key), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${metric.displayValue()} ${metric.unit}",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        comparison?.targetValue?.let { value ->
            Text("목표 ${NutritionCalculator.trim(value)} ${metric.unit} · ${comparison.status.label()}",
                style = MaterialTheme.typography.bodySmall)
        }
        Text(metric.provenanceLabel(), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FoodMealEditor(actions: MealScreenActions, editor: MealUiState.Ready) {
    val selectedFoodRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(editor.selectedFood?.id) {
        if (editor.selectedFood != null) selectedFoodRequester.bringIntoView()
    }
    AppTextField(
        editor.query,
        actions::searchFood,
        Modifier.fillMaxWidth(),
        label = { Text("식품 검색") }
    )
    editor.searchResults.forEach { food ->
        AppCard(Modifier.fillMaxWidth().clickable { actions.selectFood(food) }) {
            Column(Modifier.padding(AppSpacing.card)) {
                val displayName = if (food.isPackagedFood()) food.packagedProductLabel() else food.displayName()
                val variantLabel = if (food.isPackagedFood()) food.packagedVariantLabel() else food.basisLabel()
                Text(displayName, fontWeight = FontWeight.Bold)
                Text("${NutritionFood.kindLabel(food.kind)} · $variantLabel · ${food.extendedNutritionLabel()}")
            }
        }
    }
    editor.selectedFood?.let { food ->
        AppCard(Modifier.fillMaxWidth().bringIntoViewRequester(selectedFoodRequester)) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text("선택 · ${food.displayName()}", fontWeight = FontWeight.Bold)
                Text(food.basisLabel())
            }
        }
        AppTextField(
            editor.quantity,
            actions::updateQuantity,
            Modifier.fillMaxWidth(),
            label = { Text("섭취량 ${food.basisUnit}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
    AppTextField(
        editor.draft.time,
        actions::updateTime,
        Modifier.fillMaxWidth(),
        label = { Text("식사 시각 HH:mm") }
    )
    editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        AppOutlinedButton(
            onClick = actions::closeDraft,
            modifier = Modifier.weight(1f),
            enabled = !editor.saving
        ) { Text("취소") }
        AppButton(
            onClick = actions::saveFood,
            modifier = Modifier.weight(1f),
            enabled = !editor.saving && editor.selectedFood != null
        ) { Text(if (editor.saving) "저장 중" else "끼니 기록하기") }
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
    var showPriceTrace by rememberSaveable { mutableStateOf(false) }
    val selectedMenuRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(editor.selectedFood?.id) {
        if (editor.selectedFood != null) selectedMenuRequester.bringIntoView()
    }
    Text("외식 직접 등록", style = MaterialTheme.typography.titleMedium)
    SavedDiningOutMenuPicker(actions, editor, selectedMenuRequester)
    AppOutlinedButton(
        onClick = { showPriceTrace = !showPriceTrace },
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (showPriceTrace) "PriceTrace 선택 닫기" else "PriceTrace 식당·메뉴 선택") }
    if (showPriceTrace) {
        PriceTraceDiningOutPicker(actions, editor, priceTraceState)
    }
    AppTextField(draft.store, actions::updateStore, Modifier.fillMaxWidth(), { Text("상호명") })
    AppTextField(draft.branch, actions::updateBranch, Modifier.fillMaxWidth(), { Text("지점명 (선택)") })
    AppTextField(draft.menu, actions::updateMenu, Modifier.fillMaxWidth(), { Text("메뉴명") })
    AppTextField(draft.time, actions::updateTime, Modifier.fillMaxWidth(), { Text("식사 시각 HH:mm") })
    AppTextField(draft.calories, actions::updateCalories, Modifier.fillMaxWidth(), { Text("칼로리 kcal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.carbs, actions::updateCarbs, Modifier.fillMaxWidth(), { Text("탄수화물 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.protein, actions::updateProtein, Modifier.fillMaxWidth(), { Text("단백질 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.fat, actions::updateFat, Modifier.fillMaxWidth(), { Text("지방 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.sodium, actions::updateSodium, Modifier.fillMaxWidth(), { Text("나트륨 mg (선택)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.sugars, actions::updateSugars, Modifier.fillMaxWidth(), { Text("당류 g (선택)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.saturatedFat, actions::updateSaturatedFat, Modifier.fillMaxWidth(), { Text("포화지방 g (선택)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        AppOutlinedButton(
            onClick = actions::closeDraft,
            modifier = Modifier.weight(1f),
            enabled = !editor.saving
        ) { Text("취소") }
        AppButton(
            onClick = actions::saveDiningOut,
            modifier = Modifier.weight(1f),
            enabled = !editor.saving
        ) { Text(if (editor.saving) "저장 중" else "외식만 기록") }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SavedDiningOutMenuPicker(
    actions: MealScreenActions,
    editor: MealUiState.Ready,
    requester: BringIntoViewRequester
) {
    Text("저장된 외식 메뉴 재사용", style = MaterialTheme.typography.titleMedium)
    AppTextField(
        editor.query,
        actions::searchFood,
        Modifier.fillMaxWidth(),
        label = { Text("저장된 외식 메뉴 검색") }
    )
    editor.searchResults.forEach { food ->
        AppCard(Modifier.fillMaxWidth().clickable { actions.useDiningOutFood(food) }) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text(food.displayName(), fontWeight = FontWeight.Bold)
                Text(food.extendedNutritionLabel())
            }
        }
    }
    editor.selectedFood?.let { food ->
        AppCard(Modifier.fillMaxWidth().bringIntoViewRequester(requester)) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text("재사용 · ${food.displayName()}", fontWeight = FontWeight.Bold)
                Text(food.extendedNutritionLabel())
            }
        }
    }
    AppOutlinedButton(
        onClick = actions::saveReusableDiningOutMenu,
        modifier = Modifier.fillMaxWidth(),
        enabled = !editor.saving
    ) { Text("Nutrition 재사용 메뉴로 저장") }
}

@Composable
private fun PriceTraceDiningOutPicker(
    actions: MealScreenActions,
    editor: MealUiState.Ready,
    priceTraceState: PriceTraceUiState
) {
    val state = priceTraceState as? PriceTraceUiState.Ready
    val query = state?.query ?: editor.priceTraceQuery

    AppTextField(query, actions::updatePriceTraceQuery, Modifier.fillMaxWidth(), label = { Text("PriceTrace 식당 검색") })
    AppButton(
        onClick = actions::searchPriceTraceRestaurants,
        enabled = query.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("검색") }
    if (state?.loading == true) Text("PriceTrace 정보를 불러오는 중입니다.")
    state?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state?.restaurants.orEmpty().forEach { restaurant ->
        AppOutlinedButton(
            onClick = { actions.loadPriceTraceRestaurant(restaurant.restaurantId) },
            modifier = Modifier.fillMaxWidth()
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${restaurant.restaurantName} · ${location.branchName.ifBlank { "본점" }} · ${menu.menuName}")
                }
            }
        }
    }
}
