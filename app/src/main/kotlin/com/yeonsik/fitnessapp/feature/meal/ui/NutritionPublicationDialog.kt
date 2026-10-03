package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.feature.nutrition.ui.NutritionEntryFrame
import com.yeonsik.fitnessapp.feature.nutrition.ui.NutritionFormSection

@Composable
internal fun NutritionPublicationDialog(
    state: NutritionPublicationUiState,
    priceTraceState: PriceTraceUiState,
    actions: MealScreenActions
) {
    val prices = (priceTraceState as? PriceTraceUiState.Ready)?.takeIf { it.ownerId == state.ownerId }
    val food = state.selectedFood
    val detail = prices?.detail
    val restaurantScope = detail?.restaurantId ?: prices?.selectedRestaurantId.orEmpty()
    val previousTarget = state.lastTarget?.takeIf {
        it.foodId == food?.id && it.nutritionOwnerId == state.nutritionOwnerId && it.restaurantId == restaurantScope
    }
    var menuQuery by rememberSaveable(state.ownerId, state.nutritionOwnerId) { mutableStateOf("") }
    var locationId by rememberSaveable(state.ownerId, state.nutritionOwnerId, food?.id, restaurantScope) { mutableStateOf(previousTarget?.locationId.orEmpty()) }
    var menuId by rememberSaveable(state.ownerId, state.nutritionOwnerId, food?.id, restaurantScope) { mutableStateOf(previousTarget?.menuId.orEmpty()) }
    var productId by rememberSaveable(state.ownerId, state.nutritionOwnerId, food?.id, restaurantScope) { mutableStateOf(previousTarget?.catalogProductId.orEmpty()) }
    var branchExpanded by remember(food?.id, restaurantScope) { mutableStateOf(false) }
    var confirmation by remember(food?.id, restaurantScope, locationId, menuId, productId) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val query = menuQuery.trim()
    val visibleMenus = remember(state.menus, query) {
        state.menus.filter { query.isEmpty() || it.displayName().contains(query, ignoreCase = true) }
    }
    val location = detail?.locations?.singleOrNull { it.restaurantLocationId == locationId }
        ?: detail?.locations?.singleOrNull()
    val menu = detail?.menus?.singleOrNull { it.restaurantMenuId == menuId && it.catalogProductId == productId }
    val canConnect = food != null && location != null && menu != null &&
        location.restaurantLocationId.isNotBlank() && menu.restaurantMenuId.isNotBlank() && menu.catalogProductId.isNotBlank()
    val enabled = !state.busy && prices?.loading != true
    val verified = state.verifiedTarget?.let {
        it.nutritionOwnerId == state.nutritionOwnerId && it.restaurantId == detail?.restaurantId &&
            it.matches(food?.id, location?.restaurantLocationId, menu?.restaurantMenuId, menu?.catalogProductId)
    } == true
    val recovery = state.needsVerification && state.lastTarget?.matches(
        food?.id, location?.restaurantLocationId, menu?.restaurantMenuId, menu?.catalogProductId
    ) == true
    val phase = state.progressLabel
    fun verify() {
        if (canConnect && enabled) actions.verifyNutritionMenu(
            location.restaurantLocationId, menu.restaurantMenuId, menu.catalogProductId
        )
    }

    NutritionEntryFrame(
        title = "외식 영양정보 관리", onClose = actions::closeNutritionPublication,
        subtitle = "내 외식 메뉴를 식당 메뉴에 연결",
        footer = {
            if (phase != null) {
                PublicationProgress(phase, Modifier.testTag("publication-progress"))
                if (state.working) Text("창을 닫아도 처리는 계속돼요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    when {
                        verified -> "선택한 메뉴의 공개 연결을 확인했어요."
                        recovery -> "다시 공개하기 전에 공개 상태를 확인해 주세요."
                        canConnect -> "${location.branchName.ifBlank { "본점" }} · ${menu.menuName}"
                        food == null -> "저장한 외식 메뉴를 먼저 선택해 주세요."
                        else -> "연결할 식당의 지점과 메뉴를 선택해 주세요."
                    },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            AppButton(
                onClick = {
                    focus.clearFocus()
                    when {
                        verified -> actions.closeNutritionPublication()
                        recovery -> verify()
                        else -> confirmation = true
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("publication-primary"),
                enabled = enabled && (verified || canConnect)
            ) { Text(if (verified) "완료" else if (recovery) "공개 상태 확인" else "공개 연결하기") }
        }
    ) {
        NutritionFormSection("1. 저장한 외식 메뉴", Modifier.fillMaxWidth(),
            "공개할 영양정보를 선택하세요. 메뉴가 많으면 목록 안에서 스크롤할 수 있어요.") {
            if (state.menus.isNotEmpty()) {
                AppTextField(menuQuery, { menuQuery = it }, Modifier.fillMaxWidth(),
                    label = { Text("저장된 외식 메뉴 검색") }, enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (query.isEmpty()) "저장 메뉴 ${state.menus.size}개" else "검색 결과 ${visibleMenus.size} / ${state.menus.size}개",
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = actions::openNutritionPublication, enabled = !state.busy) { Text("새로고침") }
            }
            when {
                state.loading && state.menus.isEmpty() -> Text("메뉴 목록을 준비하고 있어요.")
                state.menus.isEmpty() && state.failure != NutritionPublicationFailure.LOAD -> {
                    Text("아직 저장한 외식 메뉴가 없어요.", fontWeight = FontWeight.SemiBold)
                    Text("외식 입력에서 ‘내 외식 목록에 저장’을 누르면 여기에 표시돼요.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppOutlinedButton(onClick = actions::createDiningOutMenu, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()) { Text("외식 메뉴 등록하기") }
                }
                visibleMenus.isEmpty() && state.menus.isNotEmpty() -> {
                    Text("‘$query’에 맞는 저장 메뉴가 없어요.")
                    TextButton(onClick = { menuQuery = "" }, enabled = !state.busy) { Text("검색어 지우기") }
                }
                visibleMenus.isNotEmpty() -> {
                    PublicationChoiceList(
                        items = visibleMenus, itemKey = { it.id }, selectedKey = food?.id,
                        onSelect = { actions.selectNutritionPublicationMenu(it.id) }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 288.dp).testTag("saved-dining-menus")
                    ) { item ->
                        Text(item.displayName(), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${item.basisLabel()} · ${item.nutritionLabel()}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (visibleMenus.size > 3) Text("목록을 위아래로 스크롤해 메뉴를 더 볼 수 있어요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.failure == NutritionPublicationFailure.LOAD) {
                PublicationFeedback(state.error ?: "저장된 메뉴를 불러오지 못했어요.", error = true)
                TextButton(onClick = actions::openNutritionPublication, enabled = !state.busy) { Text("메뉴 다시 불러오기") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AppOutlinedButton(onClick = actions::syncNutritionPublicationCatalog, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()) { Text(if (state.failure == NutritionPublicationFailure.SYNC) "동기화 다시 시도" else "영양정보 동기화") }
            Text("현재 영양정보 계정의 식품·메뉴 전체를 서버와 동기화해요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.failure == NutritionPublicationFailure.SYNC) {
                PublicationFeedback(state.error ?: "동기화를 완료하지 못했어요.", error = true)
            }
        }

        food?.let { selected ->
            NutritionFormSection("선택한 영양정보", Modifier.fillMaxWidth(), selected.displayName()) {
                Text(selected.extendedNutritionLabel(), style = MaterialTheme.typography.bodyMedium)
                Text("${selected.basisLabel()} 기준 · 외식 추정값",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (query.isNotEmpty() && visibleMenus.none { it.id == selected.id }) {
                    Text("검색 결과에 없어도 이 메뉴의 선택은 유지돼요.", style = MaterialTheme.typography.bodySmall)
                }
            }
            NutritionFormSection("2. 식당 메뉴 연결", Modifier.fillMaxWidth(),
                "식당을 검색한 뒤 지점과 기존 메뉴를 선택하세요.") {
                AppTextField(prices?.query.orEmpty(), actions::updatePriceTraceQuery, Modifier.fillMaxWidth(),
                    label = { Text("연결할 식당 검색") }, enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (enabled && !prices?.query.isNullOrBlank()) { focus.clearFocus(); actions.searchPriceTraceRestaurants() }
                    }))
                AppOutlinedButton(
                    onClick = { focus.clearFocus(); actions.searchPriceTraceRestaurants() },
                    enabled = enabled && !prices?.query.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("식당 검색") }
                when {
                    prices?.loading == true -> PublicationProgress(
                        if (prices.selectedRestaurantId == null) "식당 검색 중" else "지점과 메뉴를 불러오는 중")
                    prices?.error != null -> {
                        PublicationFeedback(prices.error, error = true)
                        TextButton(onClick = {
                            prices.selectedRestaurantId?.let(actions::loadPriceTraceRestaurant)
                                ?: actions.searchPriceTraceRestaurants()
                        }, enabled = !state.busy) { Text("식당 정보 다시 시도") }
                    }
                    prices?.searchedQuery != null && prices.restaurants.isEmpty() && detail == null ->
                        Text("‘${prices.searchedQuery}’에 맞는 식당이 없어요. 다른 이름으로 검색해 주세요.",
                            style = MaterialTheme.typography.bodyMedium)
                }
                if (!prices?.restaurants.isNullOrEmpty()) {
                    PublicationChoiceList(
                        items = prices.restaurants, itemKey = { it.restaurantId }, selectedKey = prices.selectedRestaurantId,
                        onSelect = { focus.clearFocus(); actions.loadPriceTraceRestaurant(it.restaurantId) }, enabled = enabled,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).testTag("publication-restaurants")
                    ) {
                        Text(it.restaurantName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("지점 ${it.locations.size}개", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                detail?.let { restaurant ->
                    Text(restaurant.restaurantName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    when {
                        restaurant.locations.isEmpty() -> Text("등록된 지점이 없어 연결할 수 없어요. 식당 정보가 등록된 뒤 다시 시도해 주세요.")
                        restaurant.menus.isEmpty() -> Text("등록된 메뉴가 없어 연결할 수 없어요. 식당 메뉴가 등록된 뒤 다시 시도해 주세요.")
                        else -> {
                            Box(Modifier.fillMaxWidth()) {
                                AppOutlinedButton(
                                    onClick = { branchExpanded = true }, enabled = enabled,
                                    modifier = Modifier.fillMaxWidth().testTag("publication-branch")
                                ) { Text("지점 · ${location?.branchName?.ifBlank { "본점" } ?: "선택해 주세요"}") }
                                DropdownMenu(expanded = branchExpanded, onDismissRequest = { branchExpanded = false },
                                    modifier = Modifier.heightIn(max = 280.dp)) {
                                    restaurant.locations.forEach { branch ->
                                        DropdownMenuItem(text = { Text(branch.branchName.ifBlank { "본점" }) },
                                            onClick = { locationId = branch.restaurantLocationId; branchExpanded = false })
                                    }
                                }
                            }
                            Text("메뉴 ${restaurant.menus.size}개", style = MaterialTheme.typography.labelLarge)
                            PublicationChoiceList(
                                items = restaurant.menus, itemKey = { it.restaurantMenuId + ":" + it.catalogProductId },
                                selectedKey = menu?.let { it.restaurantMenuId + ":" + it.catalogProductId },
                                onSelect = { menuId = it.restaurantMenuId; productId = it.catalogProductId }, enabled = enabled,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).testTag("publication-menu-targets")
                            ) { Text(it.menuName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    TextButton(onClick = { actions.loadPriceTraceRestaurant(restaurant.restaurantId) }, enabled = enabled) { Text("지점·메뉴 새로고침") }
                }
            }
        }
        if (state.failure in listOf(NutritionPublicationFailure.SELECTION, NutritionPublicationFailure.PUBLISH, NutritionPublicationFailure.VERIFY)) {
            PublicationFeedback(state.error ?: "선택한 메뉴의 연결 상태를 확인해 주세요.", error = true)
            if (state.failure == NutritionPublicationFailure.SELECTION) {
                TextButton(onClick = actions::openNutritionPublication, enabled = !state.busy) { Text("선택 정보 다시 불러오기") }
            }
        }
        state.notice?.let { PublicationFeedback(it, error = false) }
        if (canConnect) {
            TextButton(onClick = ::verify, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("공개 상태 확인") }
        }
        Text("공개하면 연결된 식당 메뉴에서 누구나 이 영양정보를 확인할 수 있어요. 외식 영양 값은 추정값이므로 공개 전에 확인해 주세요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (confirmation && canConnect) {
        AlertDialog(
            onDismissRequest = { confirmation = false },
            title = { Text("공개 연결 확인") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${food.displayName()}의 추정 영양정보를 공개합니다.")
                    Text("${detail.restaurantName} · ${location.branchName.ifBlank { "본점" }} · ${menu.menuName}",
                        fontWeight = FontWeight.SemiBold)
                    Text("공개하면 연결된 메뉴에서 누구나 이 영양정보를 확인할 수 있어요.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (enabled) actions.publishNutritionMenu(location.restaurantLocationId, menu.restaurantMenuId, menu.catalogProductId)
                    confirmation = false
                }, enabled = enabled) { Text("공개 연결") }
            },
            dismissButton = { TextButton(onClick = { confirmation = false }) { Text("취소") } }
        )
    }
}

/** Finite height is supplied by the caller so this list can live inside the form scroll. */
@Composable
private fun <T> PublicationChoiceList(
    items: List<T>,
    itemKey: (T) -> String,
    selectedKey: String?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemContent: @Composable ColumnScope.(T) -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    LazyColumn(modifier.clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
        // An index suffix prevents malformed duplicate server IDs from crashing the form.
        itemsIndexed(items, key = { index, item -> itemKey(item) + "#" + index }) { index, item ->
            val selected = itemKey(item) == selectedKey
            Row(
                Modifier.fillMaxWidth()
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(item) })
                    .heightIn(min = 64.dp).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { itemContent(item) }
                Text(if (selected) "선택됨" else "선택", style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (index < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun PublicationProgress(label: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PublicationFeedback(message: String, error: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium) {
        Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
