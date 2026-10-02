package com.yeonsik.fitnessapp.feature.home.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.api.BodyMetricsReadApi
import com.yeonsik.fitness.shared.feature.meal.api.MealReadApi
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutReadApi
import com.yeonsik.fitnessapp.feature.home.api.HomeActivityHistoryApi
import com.yeonsik.fitnessapp.feature.home.api.HomeActivityReadSource
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityKind
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityDayDetails
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityRecordSummary

class WorkoutHomeActivityReadSource(private val read: WorkoutReadApi) : HomeActivityReadSource {
    override val kind = HomeActivityKind.EXERCISE
    override fun firstRecordedDate(scope: AccountScope) = read.earliestCompletedDate(scope)
    override fun recordedDates(scope: AccountScope, startDate: String, endDate: String) =
        read.completedDates(scope, startDate, endDate).toSet()

    override fun recordsForDate(scope: AccountScope, date: String): List<HomeActivityRecordSummary> =
        read.completedSessionSummaries(scope, date, date)
            .filter { it.date == date }
            .map { summary ->
                val bodyParts = summary.projectionMuscleLabels.ifEmpty { summary.muscleLabels }
                    .distinct()
                HomeActivityRecordSummary(
                    kind = kind,
                    name = summary.title.ifBlank {
                        when (summary.workoutType) {
                            "cardio" -> "유산소"
                            "strength" -> "근력 운동"
                            else -> "운동 기록"
                        }
                    },
                    category = bodyParts.joinToString(" · ").takeIf(String::isNotBlank)
                )
            }
}

class BodyHomeActivityReadSource(private val read: BodyMetricsReadApi) : HomeActivityReadSource {
    override val kind = HomeActivityKind.WEIGHT
    override fun firstRecordedDate(scope: AccountScope) = read.earliestRecordedDate(scope)
    override fun recordedDates(scope: AccountScope, startDate: String, endDate: String) =
        read.dates(scope, startDate, endDate).toSet()

    override fun recordsForDate(scope: AccountScope, date: String): List<HomeActivityRecordSummary> =
        read.bodyMetrics(scope, date).filter { it.date == date }.map {
            HomeActivityRecordSummary(kind = kind, weightKg = it.weightKg)
        }
}

class MealHomeActivityReadSource(private val read: MealReadApi) : HomeActivityReadSource {
    override val kind = HomeActivityKind.MEAL
    override fun firstRecordedDate(scope: AccountScope) = read.earliestRecordedDate(scope)
    override fun recordedDates(scope: AccountScope, startDate: String, endDate: String) =
        read.dates(scope, startDate, endDate).toSet()

    override fun recordsForDate(scope: AccountScope, date: String): List<HomeActivityRecordSummary> =
        read.meals(scope, date).filter { it.date == date }.map {
            HomeActivityRecordSummary(
                kind = kind,
                name = it.previewTitle.ifBlank { it.menu }.ifBlank { "식단 기록" },
                category = it.mealLabel.takeIf(String::isNotBlank)
            )
        }
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

    override fun detailsForDate(scope: AccountScope, date: String): HomeActivityDayDetails {
        require(runCatching { java.time.LocalDate.parse(date) }.isSuccess) {
            "Invalid activity detail date."
        }
        return HomeActivityDayDetails(
            date,
            sources.flatMap { it.recordsForDate(scope, date) }
        )
    }
}
