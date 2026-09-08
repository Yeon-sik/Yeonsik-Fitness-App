package com.yeonsik.fitnessapp.feature.supplement.model

data class SupplementProgress(
    val planned: Int,
    val taken: Int,
    val skipped: Int
) {
    val unrecorded: Int get() = maxOf(0, planned - taken - skipped)
}

data class SupplementAdherence(
    val planned: Int,
    val taken: Int,
    val skipped: Int
) {
    val unrecorded: Int get() = maxOf(0, planned - taken - skipped)
    fun adherencePercent(): Int = if (planned == 0) 0 else kotlin.math.round(taken * 100f / planned).toInt()
}
