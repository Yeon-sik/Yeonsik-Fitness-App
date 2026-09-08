package com.yeonsik.fitnessapp.feature.body.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.development.BodyProfile
import com.yeonsik.fitnessapp.feature.body.model.BodyReadEntry
import com.yeonsik.fitnessapp.feature.body.model.BodyWeightWindow

/** Read-only body port used by cross-feature read models. */
interface BodyMetricsReadApi {
    fun bodyProfile(scope: AccountScope): BodyProfile
    fun bodyMetrics(scope: AccountScope, date: String): List<BodyReadEntry>
    fun latestBodyMetricOnOrBefore(scope: AccountScope, date: String): BodyReadEntry?
    fun recordedDays(scope: AccountScope, startDate: String, endDate: String): Int
    fun dates(scope: AccountScope, startDate: String, endDate: String): List<String>
    fun weightWindow(scope: AccountScope, startDate: String, endDate: String): BodyWeightWindow
}
