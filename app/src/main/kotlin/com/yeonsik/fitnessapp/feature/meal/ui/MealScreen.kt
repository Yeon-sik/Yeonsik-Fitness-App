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
import com.yeonsik.fitnessapp.feature.routine.ui.*
import com.yeonsik.fitnessapp.feature.supplement.ui.*
import com.yeonsik.fitnessapp.feature.workout.model.*
import com.yeonsik.fitnessapp.feature.workout.ui.*
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.*
import kotlinx.coroutines.delay
import java.time.LocalDate

interface MealScreenActions {
    fun back()
    fun startDraft()
    fun closeDraft()
    fun chooseFood()
    fun chooseDiningOut()
    fun searchFood(query: String)
    fun selectFood(food: NutritionFood)
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
}

@Composable
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

    AppHeader("식사", today, back = actions::back)
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
        AppButton(onClick = actions::startDraft, Modifier.fillMaxWidth()) {
            Text("새 끼니 기록")
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
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
}

@Composable
private fun FoodMealEditor(actions: MealScreenActions, editor: MealUiState.Ready) {
    AppTextField(
        editor.query,
        actions::searchFood,
        Modifier.fillMaxWidth(),
        label = { Text("식품 검색") }
    )
    editor.searchResults.forEach { food ->
        AppCard(Modifier.fillMaxWidth().clickable { actions.selectFood(food) }) {
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
private fun DiningOutEditor(
    actions: MealScreenActions,
    editor: MealUiState.Ready,
    priceTraceState: PriceTraceUiState
) {
    val draft = editor.draft
    var showPriceTrace by rememberSaveable { mutableStateOf(false) }
    Text("외식 직접 등록", style = MaterialTheme.typography.titleMedium)
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
    AppTextField(draft.sodium, actions::updateSodium, Modifier.fillMaxWidth(), { Text("나트륨 mg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.sugars, actions::updateSugars, Modifier.fillMaxWidth(), { Text("당류 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.saturatedFat, actions::updateSaturatedFat, Modifier.fillMaxWidth(), { Text("포화지방 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
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
