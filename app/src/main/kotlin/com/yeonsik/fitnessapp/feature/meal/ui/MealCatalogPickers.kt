package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.yeonsik.fitnessapp.feature.nutrition.ui.*

@Composable
internal fun MealCatalogSearch(actions: MealScreenActions, editor: MealUiState.Ready, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    val enabled = !editor.saving && !editor.draftLoading
    val dining = editor.diningOut
    val noun = if (dining) "외식 메뉴" else "식품"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(editor.query, actions::searchFood, Modifier.fillMaxWidth(),
            label = { Text(if (dining) "저장된 외식 메뉴 검색" else "식품 이름 검색") }, enabled = enabled,
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
                FoodSearchResults(editor.searchResults, {
                    focus.clearFocus()
                    if (dining) actions.useDiningOutFood(it) else actions.selectFood(it)
                }, Modifier.fillMaxWidth().testTag(if (dining) "meal-dining-results" else "meal-food-results"), enabled = enabled,
                    includedFoodIds = if (dining) setOfNotNull(editor.selectedFood?.id) else editor.foodPortions.map { it.food.id }.toSet(),
                    actionLabel = if (dining) "불러오기" else "추가", includedLabel = if (dining) "선택됨" else "구성에 있음")
            }
        }
        if (editor.query.isNotBlank()) TextButton({ actions.searchFood(""); focus.clearFocus() }, enabled = enabled) { Text("검색 지우기") }
    }
}

@Composable
internal fun PriceTraceDiningOutPicker(
    actions: MealScreenActions,
    editor: MealUiState.Ready,
    priceTraceState: PriceTraceUiState,
    controls: DiningOutEditorState,
    modifier: Modifier = Modifier
) {
    val state = (priceTraceState as? PriceTraceUiState.Ready)?.takeIf { it.ownerId == editor.ownerId }
    val query = state?.query ?: editor.priceTraceQuery
    val detail = state?.detail
    val restaurantScope = detail?.restaurantId ?: state?.selectedRestaurantId.orEmpty()
    val locationId = controls.locationId
    val menuId = controls.menuId
    val productId = controls.productId
    val menuQuery = controls.menuQuery
    var branchExpanded by remember(restaurantScope) { mutableStateOf(false) }
    val location = detail?.locations?.singleOrNull { it.restaurantLocationId == locationId }
        ?: detail?.locations?.singleOrNull().takeIf { locationId.isBlank() }
    val menu = detail?.menus?.singleOrNull { it.restaurantMenuId == menuId && it.catalogProductId == productId }
    val enabled = !editor.saving && !editor.draftLoading && state?.loading != true
    val focus = LocalFocusManager.current
    val search = { if (enabled && query.isNotBlank()) { focus.clearFocus(); actions.searchPriceTraceRestaurants() } }
    NutritionFormSection("식당 · 메뉴 검색", modifier.fillMaxWidth(), "지점을 선택한 뒤 먹은 메뉴를 불러오세요.") {
        AppTextField(query, actions::updatePriceTraceQuery, Modifier.fillMaxWidth(), label = { Text("식당 이름 검색") },
            enabled = !editor.saving && !editor.draftLoading, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }))
        AppButton(search, Modifier.fillMaxWidth(), enabled = enabled && query.isNotBlank()) { Text("식당 검색") }
        when {
            state?.loading == true -> MealCatalogProgress(if (state.selectedRestaurantId == null) "식당 검색 중" else "지점과 메뉴를 불러오는 중")
            state?.error != null -> {
                MealCatalogFeedback(state.error)
                AppOutlinedButton({ state.selectedRestaurantId?.let(actions::loadPriceTraceRestaurant) ?: actions.searchPriceTraceRestaurants() },
                    Modifier.fillMaxWidth(), enabled = enabled) { Text("식당 정보 다시 시도") }
            }
            state?.searchedQuery != null && state.restaurants.isEmpty() && detail == null ->
                Text("‘${state.searchedQuery}’에 맞는 식당이 없어요. 다른 이름으로 검색하거나 메뉴를 직접 입력하세요.", style = MaterialTheme.typography.bodyMedium)
        }
        if (!state?.restaurants.isNullOrEmpty()) {
            Text("식당 ${state.restaurants.size}개 · 목록 안에서 스크롤", style = MaterialTheme.typography.labelLarge)
            NutritionChoiceList(state.restaurants, { it.restaurantId }, state.selectedRestaurantId,
                { focus.clearFocus(); actions.loadPriceTraceRestaurant(it.restaurantId) },
                Modifier.fillMaxWidth().heightIn(max = 200.dp).testTag("meal-restaurants"), enabled = enabled) {
                Text(it.restaurantName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("지점 ${it.locations.size}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        detail?.let { restaurant ->
            Text(restaurant.restaurantName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when {
                restaurant.locations.isEmpty() -> Text("등록된 지점이 없어요. 메뉴를 직접 입력하거나 지점 정보를 다시 불러오세요.")
                restaurant.menus.isEmpty() -> Text("등록된 메뉴가 없어요. 메뉴를 직접 입력하거나 메뉴 정보를 다시 불러오세요.")
                else -> {
                    Box(Modifier.fillMaxWidth()) {
                        AppOutlinedButton({ branchExpanded = true }, Modifier.fillMaxWidth().testTag("meal-branch"), enabled = enabled) {
                            Text("지점 · ${location?.branchName?.ifBlank { "본점" } ?: "선택해 주세요"}")
                        }
                        DropdownMenu(branchExpanded, { branchExpanded = false }, Modifier.heightIn(max = 280.dp)) {
                            restaurant.locations.forEach { branch ->
                                DropdownMenuItem(text = { Text(branch.branchName.ifBlank { "본점" }) }, enabled = enabled,
                                    onClick = { controls.locationId = branch.restaurantLocationId; branchExpanded = false })
                            }
                        }
                    }
                    AppTextField(menuQuery, { controls.menuQuery = it }, Modifier.fillMaxWidth(), label = { Text("메뉴 이름으로 좁히기") },
                        enabled = enabled, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
                    val menus = restaurant.menus.filter { it.menuName.contains(menuQuery.trim(), ignoreCase = true) }
                    Text("메뉴 ${menus.size}개 · 전체 ${restaurant.menus.size}개", style = MaterialTheme.typography.labelLarge)
                    if (menus.isEmpty()) {
                        Text("검색에 맞는 메뉴가 없어요.", style = MaterialTheme.typography.bodyMedium)
                        TextButton({ controls.menuQuery = "" }, enabled = enabled) { Text("메뉴 검색 지우기") }
                    } else NutritionChoiceList(menus, { it.restaurantMenuId + ":" + it.catalogProductId },
                        menu?.let { it.restaurantMenuId + ":" + it.catalogProductId },
                        { focus.clearFocus(); controls.menuId = it.restaurantMenuId; controls.productId = it.catalogProductId },
                        Modifier.fillMaxWidth().heightIn(max = 240.dp).testTag("meal-menu-targets"), enabled = enabled) {
                        Text(it.menuName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (menu != null) Text("선택 메뉴 · ${menu.menuName}", style = MaterialTheme.typography.bodyMedium)
                    AppButton({
                        if (location != null && menu != null && enabled) {
                            focus.clearFocus()
                            actions.applyPriceTraceSelection(restaurant.restaurantId, restaurant.restaurantName,
                                location.restaurantLocationId, location.branchName, menu.restaurantMenuId, menu.menuName, menu.catalogProductId)
                        }
                    }, Modifier.fillMaxWidth().testTag("meal-load-restaurant-menu"),
                        enabled = enabled && location != null && menu != null && restaurant.restaurantId.isNotBlank() &&
                            location.restaurantLocationId.isNotBlank() && menu.restaurantMenuId.isNotBlank() && menu.catalogProductId.isNotBlank()) {
                        Text("이 메뉴 불러오기")
                    }
                    Text("식당과 메뉴 이름을 불러옵니다. 1인분 영양정보는 아래에서 확인해 입력하세요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton({ actions.loadPriceTraceRestaurant(restaurant.restaurantId) }, enabled = enabled) { Text("지점·메뉴 새로고침") }
        }
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
