package com.yeonsik.fitnessapp.feature.body.model

data class BodyReadEntry(
    val id: String,
    val date: String,
    val weightKg: Double,
    val memo: String
)

data class BodyWeightWindow(
    val averageKg: Double?,
    val recordedDays: Int
)
