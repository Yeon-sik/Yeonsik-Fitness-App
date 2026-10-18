package com.yeonsik.fitnessapp.feature.development.application

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.development.DevelopmentGoal
import com.yeonsik.fitnessapp.development.DevelopmentInsightRules
import com.yeonsik.fitnessapp.development.DevelopmentReport
import com.yeonsik.fitnessapp.development.PaperAdviceAssessment
import com.yeonsik.fitness.shared.feature.body.api.BodyMetricsReadApi
import com.yeonsik.fitnessapp.feature.development.api.DevelopmentReadApi
import com.yeonsik.fitnessapp.feature.development.api.DevelopmentReportApi
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentWeekProgress
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentTrainingOverview
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentGuidance
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentAdvice
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentAdviceAction
import com.yeonsik.fitnessapp.feature.nutrition.analysis.api.NutritionAnalysisApi
import com.yeonsik.fitness.shared.feature.meal.api.MealReadApi
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutReadApi
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.LinkedHashMap

/** Composes development-owned report facts from the owning feature read APIs. */
class DevelopmentReportService @JvmOverloads constructor(
    private val workouts: WorkoutReadApi,
    private val meals: MealReadApi,
    private val body: BodyMetricsReadApi,
    private val development: DevelopmentReadApi,
    private val nutritionAnalysis: NutritionAnalysisApi? = null
) : DevelopmentReportApi {
    override fun buildGuidance(scope: AccountScope, referenceDate: LocalDate,
                               training: DevelopmentTrainingOverview): DevelopmentGuidance {
        require(scope.ownerId.isNotBlank())
        val checkIn = runCatching {
            development.checkInSummary(scope, referenceDate.minusDays(6).toString(), referenceDate.toString())
        }
        // Completed calendar days; today's partially logged meals never masquerade as a daily trend.
        val nutrition = runCatching {
            nutritionAnalysis?.analyze(scope, referenceDate.minusDays(7).toString(), referenceDate.minusDays(1).toString())
                ?.let { report ->
                    val weight = body.latestBodyMetricOnOrBefore(scope, referenceDate.toString())
                    val recentWeight = weight?.takeIf {
                        runCatching { LocalDate.parse(it.date) >= referenceDate.minusDays(13) }.getOrDefault(false)
                    }?.weightKg?.takeIf { it.isFinite() && it > 0 }
                    val resistanceSessions = workouts.completedResistanceSessions(scope,
                        referenceDate.minusDays(13).toString(), referenceDate.toString())
                    DevelopmentNutritionAdvisor.advise(report, recentWeight, resistanceSessions)
                }.orEmpty()
        }
        val facts = checkIn.getOrNull()
        val shortSleep = facts != null && facts.sleepRecordedDays >= 3 &&
            facts.averageSleepHours?.let { it < 7.0 } == true
        val lowReadiness = (facts?.lowEnergyOrReadinessDays ?: 0) >= 2
        val recoveryAdvice = if (shortSleep || lowReadiness) DevelopmentAdvice(
            "recovery-review", "세트 추가 전에 컨디션을 살펴보세요",
            "최근 낮은 컨디션이나 짧은 수면 기록이 있어요. 오늘 느낌을 확인하고 운동량을 결정하세요.",
            listOf("최근 7일 낮은 에너지·준비도 ${facts?.lowEnergyOrReadinessDays ?: 0}일",
                "수면 기록 ${facts?.sleepRecordedDays ?: 0}일 · 평균 ${facts?.averageSleepHours?.let { "%.1f시간".format(it) } ?: "미기록"}",
                "자가 기록을 참고한 조정 후보예요. 수면과 수행 변화의 원인을 확정하지 않아요."),
            DevelopmentAdviceAction.RECOVERY, "오늘 컨디션 확인", 30
        ) else null
        return DevelopmentGuidance(referenceDate, DevelopmentSetAdvisor.suggest(training, facts),
            nutrition.getOrDefault(emptyList()), recoveryAdvice,
            if (nutrition.isFailure) "식단 제안을 불러오지 못했어요." else null,
            if (checkIn.isFailure) "회복 기록을 불러오지 못해 운동 제안에 반영하지 않았어요." else null)
    }

    override fun buildTrainingOverview(scope: AccountScope, referenceDate: LocalDate): DevelopmentTrainingOverview {
        require(scope.ownerId.isNotBlank()) { "Development owner is required." }
        val start = referenceDate.minusDays(55).toString()
        val end = referenceDate.toString()
        return DevelopmentProgressAnalyzer.build(referenceDate,
            workouts.progressSessions(scope, start, end), workouts.completedDates(scope, start, end))
    }

    override fun buildPaperAdviceAssessment(scope: AccountScope, referenceDate: LocalDate): PaperAdviceAssessment =
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
        val workoutProgress = workouts.weekProgress(scope, weekStart.toString(), safeReferenceDate.toString())
        val weekProgress = DevelopmentWeekProgress(
            workoutProgress.completedSessions,
            workoutProgress.completedDays
        )
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
