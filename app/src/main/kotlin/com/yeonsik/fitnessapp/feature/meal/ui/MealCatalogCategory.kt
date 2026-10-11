package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.data.NutritionFood

/** Filters what can be added, never the contents or ownership of the meal. */
enum class MealCatalogCategory(val label: String) {
    PRODUCT("제품"), INGREDIENT("재료"), RECIPE("내 요리"), DINING_OUT("외식");

    fun includes(food: NutritionFood): Boolean = when (this) {
        PRODUCT -> food.isPackagedFood()
        INGREDIENT -> food.kind == NutritionFood.KIND_INGREDIENT
        RECIPE -> food.kind == NutritionFood.KIND_RECIPE
        DINING_OUT -> food.isDiningOutMenu()
    }
}
