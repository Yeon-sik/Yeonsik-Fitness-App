package com.yeonsik.fitnessapp.feature.nutrition.api

import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandRow

/** PriceTrace 읽기 전용 경계. UI에는 URL, key, transport를 전달하지 않는다. */
interface PriceTraceBrandCatalog {
    fun brandCatalogScope(): Long
    fun isBrandCatalogConfigured(): Boolean
    fun observeBrandCatalogScope(listener: Runnable): AutoCloseable = AutoCloseable {}
    @Throws(Exception::class)
    fun searchPriceTraceBrands(query: String, limit: Int, offset: Int): List<PriceTraceBrandRow>
}
