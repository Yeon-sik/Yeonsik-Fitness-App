package com.yeonsik.fitnessapp.feature.development.application

import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetCountProposal
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetType
import com.yeonsik.fitness.shared.feature.workout.model.proposalSignature
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.feature.development.model.*
import java.time.LocalDate

/**
 * Conservative, versioned app heuristics, not an individual optimal-volume prediction.
 * ACSM 2026 emphasizes individualization: https://doi.org/10.1249/MSS.0000000000003897
 * One adjustment at a time; an absent RIR/check-in is never treated as good recovery.
 */
internal object DevelopmentSetAdvisor {
    private const val VERSION = "local-sets-v1"

    fun suggest(
        overview: DevelopmentTrainingOverview,
        checkIn: DevelopmentCheckInSummary?
    ): List<DevelopmentSetSuggestion> = overview.exercises.map { progress ->
        suggest(progress, overview.windowEnd, checkIn)
    }.sortedWith(compareByDescending<DevelopmentSetSuggestion> { it.priority }
        .thenByDescending { suggestion ->
            overview.exercises.find { it.key == suggestion.exerciseKey }?.history?.lastOrNull()?.session?.date
        })

    private fun suggest(
        progress: DevelopmentExerciseProgress,
        date: LocalDate,
        recovery: DevelopmentCheckInSummary?
    ): DevelopmentSetSuggestion {
        val current = progress.history.last()
        val baseline = current.normalSets.size
        val evidence = mutableListOf("최근 완료 운동 ${current.session.date} · 일반 본세트 ${baseline}개")
        val recent = progress.history.filter {
            it.session.date >= date.minusDays(27).toString() &&
                it.session.routineId == current.session.routineId
        }.takeLast(3)
        evidence += "최근 28일 같은 루틴에서 확인한 기록 ${recent.size}회"
        fun result(adjustment: DevelopmentSetAdjustment, count: Int?, reason: String, priority: Int = 0): DevelopmentSetSuggestion {
            val proposal = count?.takeIf { it in 1..12 && current.session.routineId != null &&
                current.session.performanceKey != null && current.session.groupType == null &&
                !current.ambiguousOccurrence && current.normalSets.isNotEmpty() &&
                current.session.sets.none { row -> row.type == WorkoutSetType.DROP } &&
                progress.comparison.state != DevelopmentComparisonState.UNAVAILABLE &&
                current.session.recordType == FitnessRecordContract.WEIGHT_REPS &&
                current.normalSets.all { row -> row.loadKg != null && row.repetitions > 0 }
            }?.let {
                WorkoutSetCountProposal(current.session.recordId, current.session.exerciseOccurrenceId,
                    current.session.date, date.toString(), requireNotNull(current.session.routineId),
                    requireNotNull(current.session.performanceKey), current.session.proposalSignature(), it, VERSION)
            }
            return DevelopmentSetSuggestion(progress.key, progress.name, adjustment, baseline, count,
                reason, evidence.toList(), proposal, priority, VERSION)
        }
        if (current.session.date < date.minusDays(13).toString())
            return result(DevelopmentSetAdjustment.REVIEW, null, "최근 운동과 간격이 있어요. 마지막 기록과 현재 컨디션을 먼저 확인하세요.")
        if (baseline == 0 || current.ambiguousOccurrence ||
            progress.comparison.state == DevelopmentComparisonState.UNAVAILABLE)
            return result(DevelopmentSetAdjustment.REVIEW, null,
                progress.comparison.reason ?: "비교할 일반 본세트 구성을 확인해 주세요.")
        if (current.session.groupType != null || current.session.sets.any { it.type == WorkoutSetType.DROP })
            return result(DevelopmentSetAdjustment.REVIEW, null, "묶음·드롭세트는 전체 구성을 함께 확인한 뒤 조정하세요.")
        if (recent.size < 3)
            return result(DevelopmentSetAdjustment.MAINTAIN, baseline, "최근 수행량을 기준으로 시작해 보세요. 비교 기록이 쌓이면 추가 조정을 제안해요.")

        val pairs = recent.zipWithNext { before, after -> DevelopmentProgressAnalyzer.compare(before, after) }
        val comparable = pairs.all { it.state == DevelopmentComparisonState.COMPARABLE &&
            it.metric == DevelopmentComparisonMetric.REPETITIONS } &&
            pairs.map { it.referenceLoadKg }.distinct().size == 1
        if (!comparable)
            return result(DevelopmentSetAdjustment.MAINTAIN, baseline, "중량이나 구성이 달라졌어요. 최근 세트 수를 유지하며 같은 조건의 기록을 더 확인하세요.")
        val deltas = pairs.map { requireNotNull(it.currentValue) - requireNotNull(it.previousValue) }
        evidence += "같은 중량·일반 본세트 수의 최근 반복수 합계: " +
            recent.joinToString(" → ") { sample -> sample.normalSets.sumOf { it.repetitions }.toString() }
        val lowRecovery = (recovery?.lowEnergyOrReadinessDays ?: 0) >= 2
        val shortSleep = recovery != null && recovery.sleepRecordedDays >= 3 &&
            recovery.averageSleepHours?.let { it < 7.0 } == true
        if (recovery != null) {
            evidence += "최근 7일 낮은 에너지·준비도 기록 ${recovery.lowEnergyOrReadinessDays}일"
            if (shortSleep) evidence += "최근 7일 수면 기록 ${recovery.sleepRecordedDays}일의 평균이 7시간 미만"
        }
        if (deltas.all { it < 0 } && lowRecovery && baseline > 1) {
            return result(DevelopmentSetAdjustment.CONSIDER_REDUCE, baseline - 1,
                "반복수 감소와 낮은 컨디션 기록이 함께 있어요. 다음 운동은 일반 본세트 1개 줄이기를 검토하세요.", 30)
        }
        if (deltas.any { it > 0 })
            return result(DevelopmentSetAdjustment.MAINTAIN, baseline,
                "현재 세트 수에서 반복수가 늘어난 기록이 있어요. 세트를 더하기보다 현재 구성을 이어가세요.", 10)
        if (lowRecovery || shortSleep)
            return result(DevelopmentSetAdjustment.MAINTAIN, baseline,
                "최근 컨디션을 함께 살펴보세요. 세트 추가는 보류하고 수행 변화를 확인하세요.", 20)
        val rows = recent.flatMap { it.normalSets }
        val rirCoverage = rows.count { it.rir != null }
        val reserveRecorded = rirCoverage == rows.size && rows.all { (it.rir ?: 0) >= 2 }
        evidence += "여유 반복수 기록 ${rirCoverage}/${rows.size}개 세트"
        // Flat performance alone cannot establish a plateau or an insufficient training dose.
        val recentReadinessRecorded = recovery?.latestReadinessScore?.let { it >= 3 } == true
        if (deltas.all { it == 0.0 } && reserveRecorded && recentReadinessRecorded && baseline < 6) {
            return result(DevelopmentSetAdjustment.CONSIDER_ADD, baseline + 1,
                "최근 수행은 비슷하고 여유 반복수가 기록돼 있어요. 일반 본세트 1개 추가를 검토할 수 있어요.", 15)
        }
        return result(DevelopmentSetAdjustment.MAINTAIN, baseline,
            "최근 수행량을 유지하며 반복수와 여유 반복수를 함께 확인하세요. 한두 번의 기록으로 정체를 판단하지 않아요.")
    }
}
