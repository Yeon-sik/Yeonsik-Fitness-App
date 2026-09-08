package com.yeonsik.fitnessapp.feature.development.model

data class DevelopmentWeekProgress(
    val completedSessions: Int,
    val completedDays: Int
)

data class DevelopmentBodyPartSets(
    val uiPart: String?,
    val setCount: Int
)

data class DevelopmentCheckInStats(
    val recordedDays: Int,
    val lowEnergyOrReadinessDays: Int
)

data class DevelopmentCheckInSummary(
    val averageSleepHours: Double?,
    val sleepRecordedDays: Int,
    val lowEnergyOrReadinessDays: Int,
    val latestEnergyScore: Int?,
    val latestReadinessScore: Int?
)
