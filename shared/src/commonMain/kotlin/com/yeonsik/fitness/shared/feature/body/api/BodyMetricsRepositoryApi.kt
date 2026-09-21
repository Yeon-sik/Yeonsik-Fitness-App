package com.yeonsik.fitness.shared.feature.body.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.model.BodyProfile
import com.yeonsik.fitness.shared.feature.body.model.BodyReadEntry

/**
 * Owner-scoped body write/read port.
 *
 * Implementations own persistence details.  Callers must carry the scope captured at the
 * beginning of the user action; no Room, Android, or external-contract type crosses this port.
 */
interface BodyMetricsRepositoryApi : BodyMetricsReadApi {
    fun addBodyMetric(scope: AccountScope, date: String, weightKg: Double, memo: String): String

    fun bodyMetricForDate(scope: AccountScope, date: String): BodyReadEntry? =
        bodyMetrics(scope, date).firstOrNull()

    fun bodyMetricEntryById(scope: AccountScope, id: String): BodyReadEntry?

    fun bodyMetricEntriesForDate(scope: AccountScope, date: String?): List<BodyReadEntry>

    fun updateBodyMetric(
        scope: AccountScope,
        id: String,
        date: String,
        weightKg: Double,
        memo: String
    )

    fun deleteBodyMetric(scope: AccountScope, id: String)

    fun saveBodyProfile(scope: AccountScope, profile: BodyProfile)
}
