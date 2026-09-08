package com.yeonsik.fitnessapp.feature.body.api

import com.yeonsik.fitnessapp.data.BodyMetricEntry
import com.yeonsik.fitnessapp.development.BodyProfile

/** Public body boundary. Storage entities and database handles do not cross this interface. */
interface BodyMetricsRepositoryApi {
    fun setUserId(userId: String)
    fun addBodyMetric(date: String, weightKg: Double, memo: String): String
    fun bodyMetricForDate(date: String): BodyMetricEntry?
    fun bodyMetricEntryById(id: String): BodyMetricEntry?
    fun bodyMetricEntriesForDate(date: String?): List<BodyMetricEntry>
    fun updateBodyMetric(id: String, date: String, weightKg: Double, memo: String)
    fun deleteBodyMetric(id: String)
    fun latestBodyMetricOnOrBefore(date: String): BodyMetricEntry?
    fun bodyMetricsForDate(date: String?): List<String>
    fun bodyProfile(): BodyProfile
    fun saveBodyProfile(profile: BodyProfile)
}
