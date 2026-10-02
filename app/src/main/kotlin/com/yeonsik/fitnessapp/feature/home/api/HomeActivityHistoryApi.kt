package com.yeonsik.fitnessapp.feature.home.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityKind
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityDayDetails
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityRecordSummary

interface HomeActivityReadSource {
    val kind: HomeActivityKind
    fun firstRecordedDate(scope: AccountScope): String?
    fun recordedDates(scope: AccountScope, startDate: String, endDate: String): Set<String>
    fun recordsForDate(scope: AccountScope, date: String): List<HomeActivityRecordSummary> = emptyList()
}

interface HomeActivityHistoryApi {
    fun firstRecordedDate(scope: AccountScope): String?
    fun recordedKindsByDate(
        scope: AccountScope, startDate: String, endDate: String
    ): Map<String, Set<HomeActivityKind>>

    fun detailsForDate(scope: AccountScope, date: String): HomeActivityDayDetails =
        HomeActivityDayDetails(date, emptyList())
}
