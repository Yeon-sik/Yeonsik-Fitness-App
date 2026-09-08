package com.yeonsik.fitnessapp.feature.body.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.body.model.BodyReadEntry

/** Read-only body port used by cross-feature read models. */
interface BodyMetricsReadApi {
    fun bodyMetrics(scope: AccountScope, date: String): List<BodyReadEntry>
}
