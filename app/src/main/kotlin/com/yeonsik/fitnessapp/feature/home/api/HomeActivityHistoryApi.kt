package com.yeonsik.fitnessapp.feature.home.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityKind

interface HomeActivityReadSource {
    val kind: HomeActivityKind
    fun firstRecordedDate(scope: AccountScope): String?
    fun recordedDates(scope: AccountScope, startDate: String, endDate: String): Set<String>
}

interface HomeActivityHistoryApi {
    fun firstRecordedDate(scope: AccountScope): String?
    fun recordedKindsByDate(
        scope: AccountScope, startDate: String, endDate: String
    ): Map<String, Set<HomeActivityKind>>
}
