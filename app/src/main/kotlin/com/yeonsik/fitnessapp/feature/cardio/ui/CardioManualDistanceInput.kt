package com.yeonsik.fitnessapp.feature.cardio.ui

/** Parses an optional equipment reading without converting missing or invalid values to zero. */
internal object CardioManualDistanceInput {
    fun parseKilometers(input: String): Double? {
        val normalized = input.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val meters = normalized.toDoubleOrNull()?.times(1000.0)
        require(meters != null && meters.isFinite() && meters > 0.0) {
            "기구 거리는 0보다 큰 숫자로 입력하세요."
        }
        return meters
    }
}
