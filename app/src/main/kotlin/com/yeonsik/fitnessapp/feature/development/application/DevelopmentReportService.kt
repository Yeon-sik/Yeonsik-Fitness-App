package com.yeonsik.fitnessapp.feature.development.application

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.development.DevelopmentGoal
import com.yeonsik.fitnessapp.development.DevelopmentInsightRules
import com.yeonsik.fitnessapp.development.DevelopmentReport
import com.yeonsik.fitnessapp.development.PaperAdviceAssessment
import com.yeonsik.fitnessapp.feature.body.api.BodyMetricsReadApi
import com.yeonsik.fitnessapp.feature.development.api.DevelopmentReadApi
import com.yeonsik.fitnessapp.feature.development.api.DevelopmentReportApi
import com.yeonsik.fitnessapp.feature.meal.api.MealReadApi
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutReadApi
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.LinkedHashMap

/** Composes development-owned report facts from the owning feature read APIs. */
class DevelopmentReportService(
    private val workouts: WorkoutReadApi,
    private val meals: MealReadApi,
    private val body: BodyMetricsReadApi,
    private val development: DevelopmentReadApi
) : DevelopmentReportApi {
    fun buildPaperAdviceAssessment(scope: AccountScope, referenceDate: LocalDate): PaperAdviceAssessment =
        PaperAdviceSnapshotAssembler(workouts, meals, body, development, scope.ownerId)
            .assess(referenceDate)

    override fun buildReport(scope: AccountScope, referenceDate: LocalDate): DevelopmentReport {
        require(scope.ownerId.isNotBlank()) { "Development owner is required." }
        val safeReferenceDate = referenceDate ?: LocalDate.now()
        val weekStart = safeReferenceDate.with(DayOfWeek.MONDAY)
        val recentWindowEnd = safeReferenceDate
        val recentWindowStart = recentWindowEnd.minusDays(13)
        val profile = body.bodyProfile(scope)
        val goal = development.developmentGoal(scope)
        val latestWeight = body.latestBodyMetricOnOrBefore(scope, safeReferenceDate.toString())?.weightKg
        val weekProgress = workouts.weekProgress(scope, weekStart.toString(), safeReferenceDate.toString())
        val setsByBodyPart = emptyBodyPartCounts()
        workouts.strengthSetsByBodyPart(scope, recentWindowStart.toString(), recentWindowEnd.toString())
            .forEach { row ->
                val normalized = normalizeReportBodyPart(row.uiPart)
                if (normalized != null) {
                    setsByBodyPart[normalized] = setsByBodyPart.getValue(normalized) + row.setCount
                }
            }
        val focusPart = if (goal.isConfigured) goal.focusBodyPart else null
        val focusSets = focusPart?.let { setsByBodyPart[it] ?: 0 } ?: 0
        val focusLastTrainedDate = focusPart?.let {
            workouts.latestDetailedTrainingDate(
                scope,
                safeReferenceDate.toString(),
                bodyPartAliases(it).toList()
            )
        }
        val mealRecordedDays = meals.recordedDays(
            scope, recentWindowStart.toString(), recentWindowEnd.toString()
        )
        val checkInStats = development.checkInStats(
            scope, recentWindowStart.toString(), recentWindowEnd.toString()
        )
        val recentWorkoutRecordedDays = workouts.completedRecordedDays(
            scope, recentWindowStart.toString(), recentWindowEnd.toString()
        )
        val recentWeightRecordedDays = body.recordedDays(
            scope, recentWindowStart.toString(), recentWindowEnd.toString()
        )
        val daysWithAnyData = linkedSetOf<String>().apply {
            addAll(workouts.completedDates(scope, recentWindowStart.toString(), recentWindowEnd.toString()))
            addAll(meals.dates(scope, recentWindowStart.toString(), recentWindowEnd.toString()))
            addAll(body.dates(scope, recentWindowStart.toString(), recentWindowEnd.toString()))
            addAll(development.checkInDates(scope, recentWindowStart.toString(), recentWindowEnd.toString()))
        }.size
        val coverage = DevelopmentReport.DataCoverage(
            14,
            daysWithAnyData,
            recentWorkoutRecordedDays,
            mealRecordedDays,
            checkInStats.recordedDays,
            recentWeightRecordedDays
        )
        val insights = DevelopmentInsightRules.build(
            DevelopmentInsightRules.Input(
                safeReferenceDate,
                recentWindowStart,
                recentWindowEnd,
                goal.isConfigured,
                goal.weeklySessionsTarget ?: 0,
                weekProgress.completedDays,
                weekProgress.completedSessions,
                goal.focusBodyPartLabelKo(),
                focusSets,
                focusLastTrainedDate,
                mealRecordedDays,
                checkInStats.lowEnergyOrReadinessDays,
                daysWithAnyData,
                checkInStats.recordedDays,
                recentWeightRecordedDays,
                latestWeight
            )
        )
        return DevelopmentReport(
            safeReferenceDate,
            weekStart,
            recentWindowStart,
            recentWindowEnd,
            profile,
            goal,
            latestWeight,
            weekProgress.completedDays,
            weekProgress.completedSessions,
            setsByBodyPart,
            focusSets,
            focusLastTrainedDate,
            mealRecordedDays,
            checkInStats.recordedDays,
            checkInStats.lowEnergyOrReadinessDays,
            coverage,
            insights
        )
    }

    private fun emptyBodyPartCounts(): LinkedHashMap<String, Int> = linkedMapOf(
        DevelopmentGoal.BODY_PART_CHEST to 0,
        DevelopmentGoal.BODY_PART_BACK to 0,
        DevelopmentGoal.BODY_PART_LEGS to 0,
        DevelopmentGoal.BODY_PART_SHOULDERS to 0,
        DevelopmentGoal.BODY_PART_ARMS to 0,
        DevelopmentGoal.BODY_PART_ABS to 0
    )

    private fun normalizeReportBodyPart(value: String?): String? = when (value?.trim()?.lowercase()) {
        "가슴", "chest" -> DevelopmentGoal.BODY_PART_CHEST
        "등", "back" -> DevelopmentGoal.BODY_PART_BACK
        "하체", "legs" -> DevelopmentGoal.BODY_PART_LEGS
        "어깨", "shoulders" -> DevelopmentGoal.BODY_PART_SHOULDERS
        "복근", "복부", "abs" -> DevelopmentGoal.BODY_PART_ABS
        "팔", "이두", "삼두", "arms", "biceps", "triceps" -> DevelopmentGoal.BODY_PART_ARMS
        else -> null
    }

    private fun bodyPartAliases(bodyPart: String): Array<String> = when (bodyPart) {
        DevelopmentGoal.BODY_PART_ARMS -> arrayOf("arms", "팔", "biceps", "이두", "triceps", "삼두")
        DevelopmentGoal.BODY_PART_CHEST -> arrayOf("chest", "가슴")
        DevelopmentGoal.BODY_PART_BACK -> arrayOf("back", "등")
        DevelopmentGoal.BODY_PART_LEGS -> arrayOf("legs", "하체")
        DevelopmentGoal.BODY_PART_SHOULDERS -> arrayOf("shoulders", "어깨")
        DevelopmentGoal.BODY_PART_ABS -> arrayOf("abs", "복근", "복부")
        else -> arrayOf(bodyPart.lowercase())
    }
}
