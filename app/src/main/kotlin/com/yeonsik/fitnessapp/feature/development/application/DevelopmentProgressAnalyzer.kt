package com.yeonsik.fitnessapp.feature.development.application

import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutProgressSession
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetType
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.feature.development.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs

/** Describes observed work. It does not diagnose plateaus or prescribe automatic progression. */
internal object DevelopmentProgressAnalyzer {
    fun build(
        referenceDate: LocalDate,
        sessions: List<WorkoutProgressSession>,
        completedDates: List<String>
    ): DevelopmentTrainingOverview {
        val start = referenceDate.minusDays(55)
        val valid = sessions.filter { it.date >= start.toString() && it.date <= referenceDate.toString() }
        val exercises = valid.filter { it.performanceKey != null }
            .groupBy { requireNotNull(it.performanceKey) }
            .map { (key, rows) ->
                val history = rows.groupBy { it.recordId }.values.map { occurrences ->
                    val latest = occurrences.maxBy { it.performedAt }
                    DevelopmentPerformanceSample(latest, occurrences.size != 1)
                }.sortedWith(compareBy<DevelopmentPerformanceSample> { it.session.date }
                    .thenBy { it.session.performedAt }.thenBy { it.session.recordId }).takeLast(8)
                val comparison = compare(history.getOrNull(history.lastIndex - 1), history.last())
                DevelopmentExerciseProgress(key, history.last().session.exerciseName, history, comparison,
                    history.map { valueForTrend(it, comparison) })
            }
            .sortedWith(compareByDescending<DevelopmentExerciseProgress> { it.history.last().session.date }
                .thenByDescending { it.history.size }.thenBy { it.name })
        return DevelopmentTrainingOverview(start, referenceDate, exercises,
            bodyParts(valid, referenceDate), completedDates.toSet(),
            valid.filter { it.performanceKey == null }.map { it.exerciseOccurrenceId }.distinct().size,
            valid.filter { it.performanceKey == null }.maxWithOrNull(
                compareBy<WorkoutProgressSession> { it.date }.thenBy { it.performedAt }))
    }

    internal fun compare(
        previous: DevelopmentPerformanceSample?,
        current: DevelopmentPerformanceSample
    ): DevelopmentExerciseComparison {
        fun unavailable(state: DevelopmentComparisonState, reason: String? = null) =
            DevelopmentExerciseComparison(state, previous, current, reason = reason)
        if (current.ambiguousOccurrence)
            return unavailable(DevelopmentComparisonState.UNAVAILABLE, "같은 세션에 동일 운동이 여러 번 있어 어느 구성을 비교할지 확정하기 어려워요.")
        if (current.normalSets.isEmpty())
            return unavailable(DevelopmentComparisonState.UNAVAILABLE, "최근 운동에 비교할 일반 본세트가 없어요.")
        val state = current.session.loadState
        if (state?.isBandSemantics() == true)
            return unavailable(DevelopmentComparisonState.UNAVAILABLE, "밴드의 저항 조건을 확인할 수 없어 수치로 비교하지 않아요.")
        val type = current.session.recordType
        val timed = type == FitnessRecordContract.TIME || type == FitnessRecordContract.WEIGHT_TIME
        val unloaded = type == FitnessRecordContract.REPS_ONLY || type == FitnessRecordContract.TIME ||
            state == LoadState.BODYWEIGHT
        if (totalPerformance(current, timed) == null || !unloaded && current.normalSets.any { it.loadKg == null })
            return unavailable(DevelopmentComparisonState.UNAVAILABLE, "최근 본세트의 수행값이나 중량이 누락되어 있어요.")
        if (previous == null) return unavailable(DevelopmentComparisonState.FIRST_RECORD)
        if (previous.ambiguousOccurrence || previous.normalSets.isEmpty())
            return unavailable(DevelopmentComparisonState.UNAVAILABLE, "이전 운동의 일반 본세트 구성을 확인해 주세요.")
        if (totalPerformance(previous, timed) == null || !unloaded && previous.normalSets.any { it.loadKg == null })
            return unavailable(DevelopmentComparisonState.UNAVAILABLE, "이전 본세트의 수행값이나 중량이 누락되어 있어요.")
        if (current.normalSets.size != previous.normalSets.size)
            return unavailable(DevelopmentComparisonState.DIFFERENT_CONDITIONS, "이전과 최근의 일반 본세트 수가 달라요.")
        val currentLoad = uniformLoad(current)
        val previousLoad = uniformLoad(previous)
        val sameLoad = unloaded || currentLoad != null && previousLoad != null &&
            abs(currentLoad - previousLoad) < 0.000001
        if (sameLoad) {
            val metric = if (timed) DevelopmentComparisonMetric.DURATION else DevelopmentComparisonMetric.REPETITIONS
            val old = totalPerformance(previous, timed)
            val now = totalPerformance(current, timed)
            if (old == null || now == null) return unavailable(DevelopmentComparisonState.UNAVAILABLE,
                "비교할 세트의 수행값이 누락되어 있어요.")
            return DevelopmentExerciseComparison(DevelopmentComparisonState.COMPARABLE, previous, current,
                metric, old, now, currentLoad.takeUnless { unloaded })
        }
        // Only compare uniform loads with exactly the same repetitions in each normal set.
        if (!timed && currentLoad != null && previousLoad != null &&
            current.normalSets.all { it.repetitions > 0 } &&
            current.normalSets.map { it.repetitions } == previous.normalSets.map { it.repetitions }) {
            return DevelopmentExerciseComparison(DevelopmentComparisonState.COMPARABLE, previous, current,
                DevelopmentComparisonMetric.LOAD, previousLoad, currentLoad)
        }
        return unavailable(DevelopmentComparisonState.DIFFERENT_CONDITIONS, "세트별 중량이나 반복수 구성이 달라 기록을 나란히 보여드려요.")
    }

    private fun uniformLoad(sample: DevelopmentPerformanceSample): Double? {
        val loads = sample.normalSets.map { it.loadKg }
        val first = loads.firstOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
        return first.takeIf { loads.all { load -> load != null && abs(load - first) < 0.000001 } }
    }

    private fun totalPerformance(sample: DevelopmentPerformanceSample, timed: Boolean): Double? {
        val values = sample.normalSets.map { if (timed) it.durationSeconds else it.repetitions }
        if (values.isEmpty() || values.any { it <= 0 }) return null
        return values.sumOf { it.toDouble() }
    }

    private fun valueForTrend(
        sample: DevelopmentPerformanceSample,
        comparison: DevelopmentExerciseComparison
    ): Double? {
        if (comparison.state != DevelopmentComparisonState.COMPARABLE || sample.ambiguousOccurrence ||
            sample.normalSets.size != comparison.current.normalSets.size) return null
        return when (comparison.metric) {
            DevelopmentComparisonMetric.LOAD -> uniformLoad(sample)?.takeIf {
                sample.normalSets.map { row -> row.repetitions } ==
                    comparison.current.normalSets.map { row -> row.repetitions }
            }
            DevelopmentComparisonMetric.REPETITIONS, DevelopmentComparisonMetric.DURATION -> {
                val reference = comparison.referenceLoadKg
                if (reference != null && uniformLoad(sample)?.let { abs(it - reference) < 0.000001 } != true) null
                else totalPerformance(sample, comparison.metric == DevelopmentComparisonMetric.DURATION)
            }
            null -> null
        }
    }

    private fun bodyParts(rows: List<WorkoutProgressSession>, date: LocalDate): List<DevelopmentBodyPartProgress> {
        val weekStart = date.with(DayOfWeek.MONDAY).toString()
        val previousStart = date.with(DayOfWeek.MONDAY).minusWeeks(1).toString()
        val previousEnd = date.minusWeeks(1).toString()
        // Load-state partitions and drop steps must not count the same root set twice.
        val occurrences = rows.groupBy { it.recordId to it.exerciseOccurrenceId }.values
        data class Work(val part: String, val date: String, val count: Int)
        val work = occurrences.map { group ->
            val first = group.first()
            val roots = group.flatMap { it.sets }.filter { it.type != WorkoutSetType.WARMUP }
                .map { it.dropParentSetId ?: it.id }.distinct().size
            Work(normalizePart(first.bodyPart), first.date, roots)
        }
        return listOf("chest", "back", "legs", "shoulders", "arms", "abs", "other").mapNotNull { part ->
            val current = work.filter { it.part == part && it.date >= weekStart && it.date <= date.toString() }
            val previous = work.filter { it.part == part && it.date >= previousStart && it.date <= previousEnd }
            if (part == "other" && current.isEmpty() && previous.isEmpty()) null
            else DevelopmentBodyPartProgress(part, current.sumOf { it.count }, previous.sumOf { it.count },
                current.filter { it.count > 0 }.map { it.date }.distinct().size)
        }
    }

    private fun normalizePart(part: String): String = when (part.trim().lowercase()) {
        "가슴", "chest" -> "chest"
        "등", "back" -> "back"
        "하체", "legs" -> "legs"
        "어깨", "shoulders" -> "shoulders"
        "팔", "arms", "이두", "biceps", "삼두", "triceps" -> "arms"
        "복부", "복근", "abs" -> "abs"
        else -> "other"
    }
}
