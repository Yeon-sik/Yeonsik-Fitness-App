package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.data.NutritionCalculator
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.data.NutritionTotals
import com.yeonsik.fitnessapp.feature.nutrition.model.FoodPortionDraft
import org.json.JSONArray
import org.json.JSONObject

internal fun mealConsumedFraction(percent: String): Double? =
    percent.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 && it <= 100.0 }
        ?.div(100.0)

internal fun mealConsumedPercentError(percent: String): String? = when {
    percent.isBlank() -> "먹은 비율을 입력하세요."
    mealConsumedFraction(percent) == null -> "먹은 비율은 0보다 크고 100 이하인 숫자로 입력하세요."
    else -> null
}

/** Scale the meal quantity once; catalog nutrition always retains its full baseline. */
internal fun mealConsumedQuantity(quantity: String, percent: String): Double? {
    val amount = quantity.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val fraction = mealConsumedFraction(percent) ?: return null
    return (amount * fraction).takeIf { it.isFinite() && it > 0.0 }
}

internal fun mealFoodItems(editor: MealUiState.Ready): List<MealFoodDraftItem> =
    editor.foodItems.ifEmpty { editor.foodPortions.map { MealFoodDraftItem(it.food.id, it.food, it.quantity) } }

internal fun mealFoodNutritionTotals(items: List<MealFoodDraftItem>): NutritionTotals =
    NutritionTotals.builder().apply {
        items.forEach { portion ->
            val amount = mealConsumedQuantity(portion.quantity, portion.consumedPercent)
            val profile = amount?.let { runCatching { NutritionCalculator.forQuantity(portion.food, it) }.getOrNull() }
            add(profile ?: NutritionProfile.empty())
        }
    }.build()

internal fun currentDiningMenu(editor: MealUiState.Ready) = MealDiningDraftItem("current-menu", editor.draft,
    editor.diningPortion, editor.diningConsumedPercent, editor.selectedFood?.id)

internal fun hasCurrentDiningMenu(editor: MealUiState.Ready): Boolean = editor.draft.let {
    listOf(it.menu, it.calories, it.carbs, it.protein, it.fat, it.sodium, it.sugars, it.saturatedFat).any(String::isNotBlank)
}

internal fun diningMealMenus(editor: MealUiState.Ready): List<MealDiningDraftItem> =
    editor.diningMenus + listOfNotNull(currentDiningMenu(editor).takeIf { hasCurrentDiningMenu(editor) })

internal fun diningMenuRegistrationError(menu: MealDiningDraftItem): String? =
    diningOutRegistrationError(menu.draft) ?: mealConsumedPercentError(menu.consumedPercent) ?:
        if (mealQuantityValue(menu.quantity)?.let { it <= 100 } == true) null
        else "메뉴 전체 양은 0보다 크고 100 이하인 인분 수로 입력하세요."

internal fun diningMealRegistrationError(editor: MealUiState.Ready): String? {
    val menus = diningMealMenus(editor)
    if (menus.isEmpty()) return "먹은 메뉴를 하나 이상 담으세요."
    menus.forEach { menu -> diningMenuRegistrationError(menu)?.let { return "${menu.draft.menu.ifBlank { "입력 중인 메뉴" }} · $it" } }
    return mealTimeError(editor.draft.time)
}

internal fun diningMenuNutritionProfile(menu: MealDiningDraftItem): NutritionProfile {
    val amount = mealConsumedQuantity(menu.quantity, menu.consumedPercent)
    val draft = menu.draft
    return NutritionProfile.builder().apply {
        if (amount != null) {
            listOf(NutritionProfile.CALORIES_KCAL to draft.calories, NutritionProfile.PROTEIN_GRAMS to draft.protein,
                NutritionProfile.CARBS_GRAMS to draft.carbs, NutritionProfile.FAT_GRAMS to draft.fat,
                NutritionProfile.SODIUM_MG to draft.sodium, NutritionProfile.SUGARS_GRAMS to draft.sugars,
                NutritionProfile.SATURATED_FAT_GRAMS to draft.saturatedFat).forEach { (key, raw) ->
                raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                    ?.let { (it * amount).takeIf(Double::isFinite) }?.let { value(key, it) }
            }
        }
    }.build()
}

internal fun diningNutritionTotals(editor: MealUiState.Ready): NutritionTotals =
    NutritionTotals.builder().apply { diningMealMenus(editor).forEach { add(diningMenuNutritionProfile(it)) } }.build()

internal fun encodeDiningMenu(menu: MealDiningDraftItem): String = JSONObject().apply {
    put("id", menu.id); put("quantity", menu.quantity); put("percent", menu.consumedPercent)
    put("food_id", menu.nutritionFoodId ?: JSONObject.NULL)
    val d = menu.draft
    put("draft", JSONArray(listOf(d.store, d.branch, d.menu, d.calories, d.carbs, d.protein, d.fat,
        d.sodium, d.sugars, d.saturatedFat, d.time, d.restaurantId, d.restaurantLocationId, d.restaurantMenuId,
        d.catalogProductId, d.sourceNamespace, d.sourceLocationCode)))
}.toString()

internal fun decodeDiningMenu(raw: String): MealDiningDraftItem? = runCatching {
    val json = JSONObject(raw)
    val d = json.getJSONArray("draft")
    require(d.length() == 17)
    MealDiningDraftItem(json.getString("id"), DiningOutDraft(d.getString(0), d.getString(1), d.getString(2),
        d.getString(3), d.getString(4), d.getString(5), d.getString(6), d.getString(7), d.getString(8), d.getString(9),
        d.getString(10), d.getString(11), d.getString(12), d.getString(13), d.getString(14), d.getString(15), d.getString(16)),
        json.getString("quantity"), json.getString("percent"), json.optString("food_id").takeUnless { it == "null" || it.isBlank() })
}.getOrNull()
