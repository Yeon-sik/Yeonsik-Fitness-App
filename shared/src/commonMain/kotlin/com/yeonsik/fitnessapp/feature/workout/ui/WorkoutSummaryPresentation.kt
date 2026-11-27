package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitness.shared.feature.workout.model.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitness.shared.feature.workout.ui.WorkoutDisplayNumbers
import kotlin.math.abs

fun stableWorkoutSessionExercises(
    exercises: List<WorkoutSessionExercise>
): List<WorkoutSessionExercise> = exercises
    .withIndex()
    .sortedWith(
        compareBy<IndexedValue<WorkoutSessionExercise>> { it.value.orderIndex }
            .thenBy { it.value.id }
            .thenBy { it.index }
    )
    .map { it.value }

data class WorkoutSummaryMuscleDistribution(
    val label: String,
    val completedSetCount: Int,
    val fraction: Float
)

/**
 * Creates a presentation-only distribution from the exercise snapshot. The workout owner has
 * already persisted the exercise focus and completed sets; this helper does not infer anatomy or
 * recalculate volume.
 */
fun workoutSummaryMuscleDistribution(
    exercises: List<WorkoutSessionExercise>
): List<WorkoutSummaryMuscleDistribution> {
    val counts = LinkedHashMap<String, Int>()
    stableWorkoutSessionExercises(exercises).forEach { exercise ->
        val completed = exercise.completedSets.count { it.isCompleted }
        if (completed <= 0) return@forEach
        val label = exercise.primarySubPart
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "세부 부위 없음" }
            ?: exercise.uiPart.trim().takeIf { it.isNotEmpty() }
            ?: "기타"
        counts[label] = (counts[label] ?: 0) + completed
    }
    val total = counts.values.sum()
    if (total <= 0) return emptyList()
    return counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { (label, count) ->
            WorkoutSummaryMuscleDistribution(label, count, count.toFloat() / total.toFloat())
        }
}

fun formatWorkoutElapsedSeconds(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val remainder = safeSeconds % 60
    fun two(value: Int) = value.toString().padStart(2, '0')
    val clock = "${two(minutes)}:${two(remainder)}"
    return if (hours > 0) "$hours:$clock" else clock
}

fun workoutExerciseGroupLabel(exercises: List<WorkoutExercise>, exercise: WorkoutExercise): String {
    val groupId = exercise.supersetGroupId ?: return ""
    val ordered = exercises.sortedBy { it.orderIndex }
    val groups = ordered.filter { it.groupType == exercise.groupType }.mapNotNull { it.supersetGroupId }.distinct()
    val members = ordered.filter { it.supersetGroupId == groupId }
    val index = members.indexOfFirst { it.id == exercise.id }
    if (members.size < 2 || index < 0) return ""
    val member = if (index < 26) ('A'.code + index).toChar().toString() else (index + 1).toString()
    return "${exercise.groupType.marker}${groups.indexOf(groupId) + 1}-$member"
}

fun workoutSessionGroupLabels(exercises: List<WorkoutSessionExercise>): Map<String, String> {
    val references = exercises.map { WorkoutExercise(it.id, it.exerciseId, it.orderIndex, it.name,
        it.uiPart, it.equipment, it.recordType, it.familyIdentity, it.supersetGroupId, it.groupType) }
    return references.associate { it.id to workoutExerciseGroupLabel(references, it) }
}

/** One aggregate comparison per routine screen; never repeated beside individual sets. */
@Composable
fun WorkoutRoutineComparisonCard(session: WorkoutSessionSnapshot, unit: MassUnit) {
    if (session.routineId.isNullOrBlank()) return
    val current = session.routineTotals ?: workoutRoutineTotals(session.exercises)
    val previous = session.previousRoutine
    val prior = previous?.totals
    FitnessCard(Modifier.fillMaxWidth().testTag("workout-routine-comparison")) {
        Column(Modifier.padding(FitnessSpacing.card), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("지난 동일 루틴 대비", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (session.status == "in_progress") "진행 중 누적 · 워밍업 제외" else "최종 기록 · 워밍업 제외",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (previous == null) {
                Text("첫 기록입니다. 다음 운동부터 이전 완료 기록과 비교합니다.")
                Text("본세트 ${current.workingSetCount}개 · ${WorkoutDisplayNumbers.withUnit(current.volumeKg, unit)}")
            } else {
                val previousVolume = prior?.volumeKg ?: previous.totalVolumeKg
                val previousSets = prior?.workingSetCount ?: previous.completedSetCount
                val delta = current.volumeKg - previousVolume
                val difference = when {
                    delta > 0 -> "+${WorkoutDisplayNumbers.withUnit(delta, unit)}"
                    delta < 0 -> "−${WorkoutDisplayNumbers.withUnit(abs(delta), unit)}"
                    else -> "차이 없음"
                }
                val percent = if (previousVolume > 0) " · ${WorkoutDisplayNumbers.signedPercent(delta / previousVolume * 100)}" else ""
                Text("이전 완료 · ${previous.date}", style = MaterialTheme.typography.bodySmall)
                Text("본세트 ${current.workingSetCount} / 지난번 ${previousSets}세트")
                Text("총 볼륨 ${WorkoutDisplayNumbers.withUnit(current.volumeKg, unit)} / 지난번 ${WorkoutDisplayNumbers.withUnit(previousVolume, unit)}")
                Text("차이 $difference$percent", modifier = Modifier.testTag("workout-routine-volume-difference"))
                if (prior != null) {
                    Text("총 반복 ${current.repetitions} / 지난번 ${prior.repetitions}회")
                    if (current.durationSeconds > 0 || prior.durationSeconds > 0)
                        Text("시간 종목 ${current.durationSeconds} / 지난번 ${prior.durationSeconds}초")
                    if (current.exerciseKeys != prior.exerciseKeys)
                        Text("종목 구성 변경 · 합계 차이는 참고값입니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (current.dropStepCount > 0) Text("드롭 단계 ${current.dropStepCount}개 포함 · 묶음은 본세트 1개로 집계",
                style = MaterialTheme.typography.bodySmall)
            Text("볼륨은 외부 중량 × 반복수의 합계입니다. 맨몸·보조·밴드·시간 종목은 kg 볼륨에 합산하지 않습니다.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
