package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.data.NutritionFood
import org.json.JSONObject

/** Filters what can be added, never the contents or ownership of the meal. */
enum class MealCatalogCategory(val label: String) {
    ALL("모두"), PRODUCT("제품"), INGREDIENT("재료"), RECIPE("내 요리"), DINING_OUT("외식");

    fun includes(food: NutritionFood): Boolean = when (this) {
        ALL -> !food.isDiningOutComponent()
        PRODUCT -> food.isPackagedFood()
        INGREDIENT -> food.kind == NutritionFood.KIND_INGREDIENT
        RECIPE -> food.kind == NutritionFood.KIND_RECIPE
        DINING_OUT -> food.isDiningOutMenu()
    }
}

internal fun mealCatalogLabel(food: NutritionFood): String = when {
    food.isDiningOutMenu() -> MealCatalogCategory.DINING_OUT.label
    food.isPackagedFood() -> MealCatalogCategory.PRODUCT.label
    food.kind == NutritionFood.KIND_RECIPE -> MealCatalogCategory.RECIPE.label
    food.kind == NutritionFood.KIND_INGREDIENT -> MealCatalogCategory.INGREDIENT.label
    else -> "음식"
}

/** Typing a store narrows by its name; ordinary menu search also matches dishes and branches. */
internal fun matchesDiningMenuQuery(food: NutritionFood, query: String, storeOnly: Boolean = false): Boolean {
    val source = food.sourceReference?.let { runCatching { JSONObject(it) }.getOrNull() }
    val stores = listOf(food.brand.orEmpty(), source?.optString("restaurant_name").orEmpty())
    val values = if (storeOnly) stores else stores + listOf(food.displayName(), food.name,
        source?.optString("branch_name").orEmpty())
    return values.any { it.contains(query.trim(), ignoreCase = true) }
}
