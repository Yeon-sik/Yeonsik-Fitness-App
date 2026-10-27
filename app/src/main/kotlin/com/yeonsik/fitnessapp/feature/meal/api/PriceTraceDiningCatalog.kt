package com.yeonsik.fitnessapp.feature.meal.api

import com.yeonsik.fitnessapp.feature.meal.model.PriceTraceDiningTarget

/** Public, read-only menu query; matching and identity remain owned by PriceTrace. */
fun interface PriceTraceDiningCatalog {
    @Throws(Exception::class)
    fun searchDiningTargets(query: String): List<PriceTraceDiningTarget>
}
