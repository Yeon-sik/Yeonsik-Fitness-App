package com.yeonsik.fitnessapp.feature.home.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.api.BodyMetricsReadApi
import com.yeonsik.fitness.shared.feature.meal.api.MealReadApi
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutReadApi
import com.yeonsik.fitnessapp.feature.home.api.HomeActivityHistoryApi
import com.yeonsik.fitnessapp.feature.home.api.HomeActivityReadSource
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityKind

class WorkoutHomeActivityReadSource(private val read: WorkoutReadApi) : HomeActivityReadSource {
    override val kind = HomeActivityKind.EXERCISE
    override fun firstRecordedDate(scope: AccountScope) = read.earliestCompletedDate(scope)
    override fun recordedDates(scope: AccountScope, startDate: String, endDate: String) =
        read.completedDates(scope, startDate, endDate).toSet()
}

class BodyHomeActivityReadSource(private val read: BodyMetricsReadApi) : HomeActivityReadSource {
    override val kind = HomeActivityKind.WEIGHT
    override fun firstRecordedDate(scope: AccountScope) = read.earliestRecordedDate(scope)
    override fun recordedDates(scope: AccountScope, startDate: String, endDate: String) =
        read.dates(scope, startDate, endDate).toSet()
}

class MealHomeActivityReadSource(private val read: MealReadApi) : HomeActivityReadSource {
    override val kind = HomeActivityKind.MEAL
    override fun firstRecordedDate(scope: AccountScope) = read.earliestRecordedDate(scope)
    override fun recordedDates(scope: AccountScope, startDate: String, endDate: String) =
        read.dates(scope, startDate, endDate).toSet()
}

/** Read composition only; the owning features retain all visibility/completion decisions. */
class HomeActivityHistoryRepository(private val sources: List<HomeActivityReadSource>) : HomeActivityHistoryApi {
    override fun firstRecordedDate(scope: AccountScope): String? =
        sources.mapNotNull { it.firstRecordedDate(scope) }.minOrNull()

    override fun recordedKindsByDate(
        scope: AccountScope, startDate: String, endDate: String
    ): Map<String, Set<HomeActivityKind>> {
        require(startDate <= endDate) { "Invalid activity date range." }
        val kindsByDate = linkedMapOf<String, MutableSet<HomeActivityKind>>()
        sources.forEach { source ->
            source.recordedDates(scope, startDate, endDate).forEach { date ->
                if (date in startDate..endDate) kindsByDate.getOrPut(date) { linkedSetOf() }.add(source.kind)
            }
        }
        return kindsByDate.mapValues { (_, kinds) -> kinds.toSet() }
    }
}
