package com.yeonsik.fitnessapp.feature.nutrition.model

import com.yeonsik.fitnessapp.data.NutritionCalculator
import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.NutritionUnit
import org.json.JSONObject

/** The entered unit is separate from the catalog's nutrition calculation basis. */
enum class PortionInputMode { BASIS, COUNT, GRAMS }

internal fun packageAmountInBasis(food: NutritionFood): Double? {
    if (!food.isPackagedFood()) return null
    val amount = food.packageAmount?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val unit = food.packageUnit ?: return null
    return runCatching { NutritionUnit.convert(amount, unit, food.basisUnit) }.getOrNull()
        ?.takeIf { it.isFinite() && it > 0.0 }
}

internal fun recipeGramsPerServing(food: NutritionFood): Double? {
    if (food.kind != NutritionFood.KIND_RECIPE || food.basisUnit != NutritionUnit.SERVING) return null
    return runCatching {
        val source = JSONObject(food.sourceReference ?: return null)
        val grams = source.optDouble("recipe_weight_grams", Double.NaN)
        val servings = source.optDouble("recipe_servings", Double.NaN)
        (grams / servings).takeIf { grams > 0 && servings > 0 && it.isFinite() && it > 0 }
    }.getOrNull()
}

fun FoodPortionDraft.availableModes(): List<PortionInputMode> = buildList {
    if (packageAmountInBasis(food) != null) add(PortionInputMode.COUNT)
    add(PortionInputMode.BASIS)
    if (recipeGramsPerServing(food) != null) add(PortionInputMode.GRAMS)
}

fun FoodPortionDraft.unitLabel(): String = when (mode) {
    PortionInputMode.COUNT -> "개"
    PortionInputMode.GRAMS -> "g"
    PortionInputMode.BASIS -> NutritionUnit.display(food.basisUnit)
}

fun FoodPortionDraft.modeLabel(inputMode: PortionInputMode): String = when (inputMode) {
    PortionInputMode.COUNT -> "개수"
    PortionInputMode.GRAMS -> "무게"
    PortionInputMode.BASIS -> when (food.basisUnit) {
        NutritionUnit.SERVING -> "인분"
        NutritionUnit.MILLILITER -> "용량"
        else -> "무게"
    }
}

fun FoodPortionDraft.withMode(next: PortionInputMode): FoodPortionDraft? {
    if (next !in availableModes()) return null
    val basisAmount = amount ?: return null
    val entered = when (next) {
        PortionInputMode.BASIS -> basisAmount
        PortionInputMode.COUNT -> basisAmount / (packageAmountInBasis(food) ?: return null)
        PortionInputMode.GRAMS -> basisAmount * (recipeGramsPerServing(food) ?: return null)
    }
    return entered.takeIf { it.isFinite() && it > 0.0 }
        ?.let { copy(quantity = NutritionCalculator.trim(it), mode = next) }
}

fun initialFoodPortion(food: NutritionFood, preferCount: Boolean = false): FoodPortionDraft =
    if (preferCount && packageAmountInBasis(food) != null) FoodPortionDraft(food, "1", PortionInputMode.COUNT)
    else FoodPortionDraft(food, NutritionCalculator.trim(food.basisAmount))
