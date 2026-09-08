package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.BuildConfig
import com.yeonsik.fitnessapp.app.navigation.*
import com.yeonsik.fitnessapp.cardio.*
import com.yeonsik.fitnessapp.config.*
import com.yeonsik.fitnessapp.core.account.*
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.cardio.model.*
import com.yeonsik.fitnessapp.feature.cardio.ui.*
import com.yeonsik.fitnessapp.feature.development.ui.*
import com.yeonsik.fitnessapp.feature.exercise.ui.*
import com.yeonsik.fitnessapp.feature.home.ui.*
import com.yeonsik.fitnessapp.feature.meal.ui.*
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService
import com.yeonsik.fitnessapp.feature.routine.ui.*
import com.yeonsik.fitnessapp.feature.supplement.ui.*
import com.yeonsik.fitnessapp.feature.workout.model.*
import com.yeonsik.fitnessapp.feature.workout.ui.*
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.*
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
internal fun MealScreen(host: ScreenHost, ownerId: String, today: String, unit: MassUnit) {
    val homeState by host.homeViewModel().uiState.observeAsState(HomeUiState.Idle)
    val editorState by host.mealViewModel().uiState.observeAsState(MealUiState.Idle)
    LaunchedEffect(ownerId, today) {
        host.homeViewModel().enter(AccountScope(ownerId), today)
        host.mealViewModel().enter(AccountScope(ownerId), today)
    }
    val ready = homeState as? HomeUiState.Ready
    val editor = editorState as? MealUiState.Ready
    LaunchedEffect(editor?.notice) {
        if (editor?.notice != null) {
            host.homeViewModel().enter(AccountScope(ownerId), today)
        }
    }

    AppHeader("식사", today, back = { host.back() })
    if (ready == null || ready.snapshot.ownerId != ownerId) {
        Text("식사 기록을 불러오는 중입니다.")
        return
    }
    val snapshot = ready.snapshot
    val totals = snapshot.mealNutritionTotals[today]
    FitnessFactRow(
        first = { FitnessFactCard("식사", "${snapshot.todayMeals.size}끼", today) },
        second = { FitnessFactCard("열량", totals?.total("calories_kcal")?.describedValue() ?: "?", "kcal") }
    )
    snapshot.todayMeals.forEach { meal ->
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text(meal.previewTitle, fontWeight = FontWeight.Bold)
                Text(meal.previewSubtitle())
                Text("${meal.calories} kcal · 단백질 ${meal.proteinGrams}g")
            }
        }
    }

    if (editor == null || editor.ownerId != ownerId || editor.date != today) {
        Text("식사 입력을 준비하는 중입니다.")
    } else if (!editor.editing) {
        editor.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        AppButton(onClick = { host.mealViewModel().startDraft() }, Modifier.fillMaxWidth()) {
            Text("새 끼니 기록")
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
            AppOutlinedButton(
                onClick = { host.mealViewModel().chooseFood() },
                modifier = Modifier.weight(1f),
                selected = !editor.diningOut
            ) { Text("식단") }
            AppOutlinedButton(
                onClick = { host.mealViewModel().chooseDiningOut() },
                modifier = Modifier.weight(1f),
                selected = editor.diningOut
            ) { Text("외식") }
        }
        if (editor.diningOut) {
            DiningOutEditor(host, ownerId, editor)
        } else {
            FoodMealEditor(host, ownerId, editor)
        }
    }

    AppOutlinedButton(onClick = { host.showBodyMetricDialog(today, null) }, Modifier.fillMaxWidth()) {
        Text("오늘 체중 · ${snapshot.todayWeight?.let { MassFormatter.withUnit(it.weightKg, unit) } ?: "미기록"}")
    }
}

@Composable
private fun FoodMealEditor(host: ScreenHost, ownerId: String, editor: MealUiState.Ready) {
    AppTextField(
        editor.query,
        { host.mealViewModel().search(it) },
        Modifier.fillMaxWidth(),
        label = { Text("식품 검색") }
    )
    editor.searchResults.forEach { food ->
        AppCard(Modifier.fillMaxWidth().clickable { host.mealViewModel().selectFood(food) }) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text(food.displayName(), fontWeight = FontWeight.Bold)
                Text("${food.basisLabel()} · ${food.extendedNutritionLabel()}")
            }
        }
    }
    editor.selectedFood?.let { food ->
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text("선택 · ${food.displayName()}", fontWeight = FontWeight.Bold)
                Text(food.basisLabel())
            }
        }
        AppTextField(
            editor.quantity,
            { host.mealViewModel().updateQuantity(it) },
            Modifier.fillMaxWidth(),
            label = { Text("섭취량 ${food.basisUnit}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
    AppTextField(
        editor.draft.time,
        { host.mealViewModel().updateTime(it) },
        Modifier.fillMaxWidth(),
        label = { Text("식사 시각 HH:mm") }
    )
    editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        AppOutlinedButton(
            onClick = { host.mealViewModel().closeDraft() },
            modifier = Modifier.weight(1f),
            enabled = !editor.saving
        ) { Text("취소") }
        AppButton(
            onClick = { host.mealViewModel().saveFood(AccountScope(ownerId)) { } },
            modifier = Modifier.weight(1f),
            enabled = !editor.saving && editor.selectedFood != null
        ) { Text(if (editor.saving) "저장 중" else "끼니 기록하기") }
    }
}

@Composable
private fun DiningOutEditor(host: ScreenHost, ownerId: String, editor: MealUiState.Ready) {
    val draft = editor.draft
    var showPriceTrace by rememberSaveable { mutableStateOf(false) }
    Text("외식 직접 등록", style = MaterialTheme.typography.titleMedium)
    AppOutlinedButton(
        onClick = { showPriceTrace = !showPriceTrace },
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (showPriceTrace) "PriceTrace 선택 닫기" else "PriceTrace 식당·메뉴 선택") }
    if (showPriceTrace) {
        PriceTraceDiningOutPicker(host, editor)
    }
    AppTextField(draft.store, { host.mealViewModel().updateStore(it) }, Modifier.fillMaxWidth(), { Text("상호명") })
    AppTextField(draft.branch, { host.mealViewModel().updateBranch(it) }, Modifier.fillMaxWidth(), { Text("지점명 (선택)") })
    AppTextField(draft.menu, { host.mealViewModel().updateMenu(it) }, Modifier.fillMaxWidth(), { Text("메뉴명") })
    AppTextField(draft.time, { host.mealViewModel().updateTime(it) }, Modifier.fillMaxWidth(), { Text("식사 시각 HH:mm") })
    AppTextField(draft.calories, { host.mealViewModel().updateCalories(it) }, Modifier.fillMaxWidth(), { Text("칼로리 kcal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.carbs, { host.mealViewModel().updateCarbs(it) }, Modifier.fillMaxWidth(), { Text("탄수화물 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.protein, { host.mealViewModel().updateProtein(it) }, Modifier.fillMaxWidth(), { Text("단백질 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.fat, { host.mealViewModel().updateFat(it) }, Modifier.fillMaxWidth(), { Text("지방 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.sodium, { host.mealViewModel().updateSodium(it) }, Modifier.fillMaxWidth(), { Text("나트륨 mg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.sugars, { host.mealViewModel().updateSugars(it) }, Modifier.fillMaxWidth(), { Text("당류 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.saturatedFat, { host.mealViewModel().updateSaturatedFat(it) }, Modifier.fillMaxWidth(), { Text("포화지방 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        AppOutlinedButton(
            onClick = { host.mealViewModel().closeDraft() },
            modifier = Modifier.weight(1f),
            enabled = !editor.saving
        ) { Text("취소") }
        AppButton(
            onClick = { host.mealViewModel().save(AccountScope(ownerId)) { } },
            modifier = Modifier.weight(1f),
            enabled = !editor.saving
        ) { Text(if (editor.saving) "저장 중" else "외식만 기록") }
    }
}

@Composable
private fun PriceTraceDiningOutPicker(host: ScreenHost, editor: MealUiState.Ready) {
    var query by rememberSaveable { mutableStateOf("") }
    var restaurants by remember { mutableStateOf<List<NutritionIntegrationService.RestaurantSummary>>(emptyList()) }
    var detail by remember { mutableStateOf<NutritionIntegrationService.RestaurantDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    AppTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("PriceTrace 식당 검색") })
    AppButton(
        onClick = {
            host.searchPriceTraceRestaurants(query, object : ScreenHost.RestaurantSearchCallback {
                override fun onComplete(value: List<NutritionIntegrationService.RestaurantSummary>) {
                    mainHandler.post { restaurants = value; detail = null; error = null }
                }

                override fun onError(value: Exception) {
                    mainHandler.post { error = value.message ?: "PriceTrace 식당을 찾지 못했습니다." }
                }
            })
        },
        enabled = query.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("검색") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    restaurants.forEach { restaurant ->
        AppOutlinedButton(
            onClick = {
                host.loadPriceTraceRestaurant(restaurant.restaurantId, object : ScreenHost.RestaurantLoadCallback {
                    override fun onComplete(value: NutritionIntegrationService.RestaurantDetail) {
                        mainHandler.post { detail = value; error = null }
                    }

                    override fun onError(value: Exception) {
                        mainHandler.post { error = value.message ?: "PriceTrace 메뉴를 불러오지 못했습니다." }
                    }
                })
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(restaurant.restaurantName) }
    }
    detail?.let { restaurant ->
        Text("${restaurant.restaurantName} 메뉴", fontWeight = FontWeight.Bold)
        restaurant.menus.forEach { menu ->
            restaurant.locations.forEach { location ->
                AppOutlinedButton(
                    onClick = {
                        host.mealViewModel().applyPriceTraceSelection(
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
