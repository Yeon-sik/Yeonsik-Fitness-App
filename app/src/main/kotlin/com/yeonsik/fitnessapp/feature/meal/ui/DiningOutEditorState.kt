package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

/** Composition-owned controls; meal inputs and searches remain in MealViewModel. */
@Stable
internal class DiningOutEditorState(
    showPriceTrace: MutableState<Boolean>,
    editMenu: MutableState<Boolean>,
    editNutrition: MutableState<Boolean>,
    additional: MutableState<Boolean>,
    locationId: MutableState<String>,
    menuId: MutableState<String>,
    productId: MutableState<String>,
    menuQuery: MutableState<String>
) {
    var showPriceTrace by showPriceTrace
    var editMenu by editMenu
    var editNutrition by editNutrition
    var additional by additional
    var locationId by locationId
    var menuId by menuId
    var productId by productId
    var menuQuery by menuQuery
}

/** Remember before opening the Dialog so its parent registry can restore controls. */
@Composable
internal fun rememberDiningOutEditorState(editor: MealUiState.Ready, prices: PriceTraceUiState): DiningOutEditorState {
    val priceState = (prices as? PriceTraceUiState.Ready)?.takeIf { it.ownerId == editor.ownerId }
    val restaurant = priceState?.detail?.restaurantId ?: priceState?.selectedRestaurantId.orEmpty()
    val matchingDraft = editor.draft.takeIf { it.restaurantId == restaurant }
    val show = rememberSaveable(editor.ownerId, editor.date) { mutableStateOf(false) }
    val menuEditor = rememberSaveable(editor.ownerId, editor.date, editor.selectedFood?.id, editor.draft.restaurantMenuId) { mutableStateOf(editor.selectedFood == null) }
    val nutritionEditor = rememberSaveable(editor.ownerId, editor.date, editor.selectedFood?.id, editor.draft.restaurantMenuId) { mutableStateOf(editor.selectedFood == null) }
    val more = rememberSaveable(editor.ownerId, editor.date) { mutableStateOf(false) }
    val location = rememberSaveable(editor.ownerId, editor.date, restaurant) { mutableStateOf(matchingDraft?.restaurantLocationId.orEmpty()) }
    val menu = rememberSaveable(editor.ownerId, editor.date, restaurant) { mutableStateOf(matchingDraft?.restaurantMenuId.orEmpty()) }
    val product = rememberSaveable(editor.ownerId, editor.date, restaurant) { mutableStateOf(matchingDraft?.catalogProductId.orEmpty()) }
    val query = rememberSaveable(editor.ownerId, editor.date, restaurant) { mutableStateOf("") }
    return remember(show, menuEditor, nutritionEditor, more, location, menu, product, query) {
        DiningOutEditorState(show, menuEditor, nutritionEditor, more, location, menu, product, query)
    }
}
