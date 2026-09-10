package com.yeonsik.fitnessapp.feature.body.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.BodyRoomDao
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.development.BodyProfile
import com.yeonsik.fitnessapp.feature.body.api.BodyMetricsReadApi
import com.yeonsik.fitnessapp.feature.body.model.BodyReadEntry
import com.yeonsik.fitnessapp.feature.body.model.BodyWeightWindow
import org.json.JSONObject

/** Body-owned read adapter for the Home projection. */
class BodyMetricsReadRepository(roomDatabase: FitnessRoomDatabase) : BodyMetricsReadApi {
    private val bodyDao: BodyRoomDao = roomDatabase.bodyRoomDao()

    override fun bodyProfile(scope: AccountScope): BodyProfile =
        bodyDao.bodyProfile(scope.ownerId)?.let { profile ->
            BodyProfile(profile.heightCm, profile.createdAt, profile.updatedAt)
        } ?: BodyProfile.empty()

    override fun bodyMetrics(scope: AccountScope, date: String): List<BodyReadEntry> =
        bodyDao.visibleWeightsForDate(scope.ownerId, date).map(::toReadEntry)

    override fun latestBodyMetricOnOrBefore(scope: AccountScope, date: String): BodyReadEntry? =
        bodyDao.latestVisibleWeightOnOrBefore(scope.ownerId, date)?.let(::toReadEntry)

    override fun recordedDays(scope: AccountScope, startDate: String, endDate: String): Int =
        bodyDao.visibleWeightRecordedDays(scope.ownerId, startDate, endDate)

    override fun dates(scope: AccountScope, startDate: String, endDate: String): List<String> =
        bodyDao.visibleWeightDates(scope.ownerId, startDate, endDate)

    override fun weightWindow(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): BodyWeightWindow {
        val rows = bodyDao.visibleWeightsBetween(scope.ownerId, startDate, endDate)
        if (rows.isEmpty()) return BodyWeightWindow(null, 0)
        val dailyAverages = rows.groupBy { it.date }.values.map { entries ->
            entries.map { it.weightKg }.average()
        }
        return BodyWeightWindow(dailyAverages.average(), dailyAverages.size)
    }

    private fun toReadEntry(record: com.yeonsik.fitnessapp.core.database.WeightRecordEntity) =
        BodyReadEntry(
            record.id,
            record.date,
            record.weightKg,
            try { JSONObject(record.metadata).optString("memo", "") } catch (_: Exception) { "" }
        )
}
