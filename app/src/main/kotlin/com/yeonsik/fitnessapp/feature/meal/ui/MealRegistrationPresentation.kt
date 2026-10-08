package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.data.MealEntryPolicy
import com.yeonsik.fitnessapp.data.NutritionUnit
import com.yeonsik.fitnessapp.data.MealCompositionItem
import com.yeonsik.fitnessapp.data.NutritionCalculator
import com.yeonsik.fitnessapp.data.NutritionTotals
import java.math.BigDecimal

internal fun mealQuantityValue(value: String): Double? =
    value.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

internal fun mealQuantityError(value: String): String? = when {
    value.isBlank() -> "먹은 양을 입력하세요."
    mealQuantityValue(value) == null -> "먹은 양은 0보다 큰 숫자로 입력하세요."
    else -> null
}

internal fun mealTimeError(value: String): String? =
    if (runCatching { MealEntryPolicy.requireMealTime(value) }.isSuccess) null
    else "먹은 시간을 선택하세요."

internal fun mealNutritionInputError(
    value: String,
    label: String,
    required: Boolean = true
): String? {
    if (value.isBlank()) return if (required) "${label} 값을 입력하세요." else null
    val number = value.trim().toDoubleOrNull()
    return if (number == null || !number.isFinite() || number < 0.0) {
        "${label}은 0 이상의 숫자로 입력하세요."
    } else if (label == "칼로리" && number > Int.MAX_VALUE) {
        "칼로리 값이 너무 큽니다. 입력한 값을 확인하세요."
    } else null
}

internal fun diningOutRegistrationError(draft: DiningOutDraft): String? {
    if (draft.store.isBlank()) return "상호명을 입력하세요."
    if (draft.menu.isBlank()) return "먹은 메뉴를 입력하세요."
    val required = listOf(
        "칼로리" to draft.calories,
        "탄수화물" to draft.carbs,
        "단백질" to draft.protein,
        "지방" to draft.fat
    )
    val missing = required.filter { it.second.isBlank() }.map { it.first }
    if (missing.isNotEmpty()) return "${missing.joinToString("·")} 값을 입력하면 저장할 수 있어요."
    required.forEach { (label, value) ->
        mealNutritionInputError(value, label)?.let { return it }
    }
    listOf(
        "나트륨" to draft.sodium,
        "당류" to draft.sugars,
        "포화지방" to draft.saturatedFat
    ).forEach { (label, value) ->
        mealNutritionInputError(value, label, required = false)?.let { return it }
    }
    return null
}

internal fun mealQuantityText(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

internal fun mealQuantityUnit(unit: String): String = when (NutritionUnit.normalize(unit)) {
    NutritionUnit.SERVING -> "인분"
    "portion" -> "인분"
    "pack" -> "팩"
    else -> NutritionUnit.display(unit)
}

internal fun hasMealRegistrationDraft(editor: MealUiState.Ready): Boolean =
    editor.foodItems.isNotEmpty() || editor.selectedFood != null || editor.query.isNotBlank() ||
        listOf(
            editor.draft.store, editor.draft.branch, editor.draft.menu,
            editor.draft.calories, editor.draft.carbs, editor.draft.protein,
            editor.draft.fat, editor.draft.sodium, editor.draft.sugars,
            editor.draft.saturatedFat
        ).any { it.isNotBlank() }

internal fun foodMealRegistrationError(editor: MealUiState.Ready): String? {
    if (editor.foodItems.isEmpty()) return "한 끼에 먹은 음식을 하나 이상 담으세요."
    editor.foodItems.forEach { item ->
        mealQuantityError(item.quantity)?.let { return "${item.food.displayName()} · $it" }
    }
    return mealTimeError(editor.draft.time)
}

internal fun mealDraftNutritionTotals(items: List<MealFoodDraftItem>): NutritionTotals? {
    if (items.isEmpty()) return null
    val foods = items.map { item ->
        val quantity = mealQuantityValue(item.quantity) ?: return null
        runCatching { MealCompositionItem.from(item.food, quantity) }.getOrNull() ?: return null
    }
    return NutritionCalculator.sum(foods)
}
