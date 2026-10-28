package com.yeonsik.fitnessapp.feature.meal.model

import com.yeonsik.fitnessapp.data.NutritionFood

/** Foods come from the consumed snapshots, including foods no longer in the catalog. */
data class MealRecordEditSnapshot(
    val recordId: String,
    val date: String,
    val title: String,
    val time: String,
    val updatedAt: String,
    val items: List<MealRecordEditItem>,
    val photoEstimate: PhotoEstimateMealInput? = null
)

data class MealRecordEditItem(
    val itemId: String?,
    val food: NutritionFood,
    val quantity: Double,
    val consumedFraction: Double = 1.0,
    val diningOut: Boolean = false,
    val sourceFoodId: String? = null
)

/** Original item ids retain provenance; new additions have no original id. */
data class MealRecordEditRequest(
    val recordId: String,
    val expectedUpdatedAt: String,
    val foodItemIds: List<String?>,
    val menuItemIds: List<String?>
)
