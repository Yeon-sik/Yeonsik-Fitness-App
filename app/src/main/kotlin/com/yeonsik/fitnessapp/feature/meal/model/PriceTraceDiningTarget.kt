package com.yeonsik.fitnessapp.feature.meal.model

/** External PriceTrace identities and names captured by an explicit menu selection. */
data class PriceTraceDiningTarget(
    val restaurantId: String,
    val restaurantName: String,
    val locations: List<PriceTraceDiningLocation>,
    val restaurantMenuId: String,
    val menuName: String,
    val catalogProductId: String
)

data class PriceTraceDiningLocation(val restaurantLocationId: String, val branchName: String?)
