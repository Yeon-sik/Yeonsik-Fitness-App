package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.feature.meal.model.PriceTraceDiningTarget

/** Only ordinary Meal entry uses this state; publication retains its directory/detail state. */
data class MealDiningSearchState(
    val query: String = "",
    val rows: List<PriceTraceDiningTarget> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
    val pendingTarget: PriceTraceDiningTarget? = null
)
