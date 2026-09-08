package com.yeonsik.fitnessapp.feature.body.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.BodyRoomDao
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.feature.body.api.BodyMetricsReadApi
import com.yeonsik.fitnessapp.feature.body.model.BodyReadEntry
import org.json.JSONObject

/** Body-owned read adapter for the Home projection. */
class BodyMetricsReadRepository(roomDatabase: FitnessRoomDatabase) : BodyMetricsReadApi {
    private val bodyDao: BodyRoomDao = roomDatabase.bodyRoomDao()

    override fun bodyMetrics(scope: AccountScope, date: String): List<BodyReadEntry> =
        bodyDao.visibleWeightsForDate(scope.ownerId, date).map { record ->
            BodyReadEntry(
                record.id,
                record.date,
                record.weightKg,
                try { JSONObject(record.metadata).optString("memo", "") } catch (_: Exception) { "" }
            )
        }
}
