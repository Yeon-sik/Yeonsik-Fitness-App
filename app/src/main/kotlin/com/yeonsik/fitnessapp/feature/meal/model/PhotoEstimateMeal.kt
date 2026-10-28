package com.yeonsik.fitnessapp.feature.meal.model

import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Values describe the entire consumed meal, never a per-plate or catalog portion. */
data class PhotoEstimateMealInput(
    val calories: Double,
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fatGrams: Double? = null
) {
    init {
        require(calories.isFinite() && calories >= 0 && calories <= Int.MAX_VALUE) {
            "칼로리는 0 이상 ${Int.MAX_VALUE} 이하의 숫자로 입력하세요."
        }
        require(listOfNotNull(proteinGrams, carbsGrams, fatGrams).all { it.isFinite() && it >= 0 }) {
            "영양성분은 0 이상의 숫자로 입력하세요."
        }
    }
}

object PhotoEstimateMeal {
    const val METHOD = "photo_estimate"
    const val LABEL = "사진 기반 추정"

    @JvmStatic fun title(date: String): String = LocalDate.parse(date).format(DateTimeFormatter.BASIC_ISO_DATE)

    @JvmStatic fun isPhotoEstimate(metadata: String?): Boolean =
        runCatching { JSONObject(metadata ?: "{}").optString("recording_method") == METHOD }.getOrDefault(false)

    /** The legacy SQL protein column is NOT NULL; the explicit marker retains missingness. */
    fun recordedProtein(metadata: String?, legacyValue: Double?): Double? =
        if (isPhotoEstimate(metadata) && !JSONObject(metadata!!).optBoolean("protein_known", false)) null
        else legacyValue
}
