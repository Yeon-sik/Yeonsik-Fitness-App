package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitnessapp.core.ui.FitnessAlertDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitness.shared.feature.workout.model.*
import java.util.Locale
import kotlin.math.abs

internal fun workoutSetMarker(sets: List<WorkoutSet>, set: WorkoutSet): String {
    if (set.setType == WorkoutSetType.WARMUP) return "W"
    val groups = workoutSetGroups(sets).filter { it.root.setType != WorkoutSetType.WARMUP }
    val group = groups.firstOrNull { group -> group.sets.any { it.id == set.id } }
    val ordinal = groups.indexOfFirst { it.root.id == group?.root?.id } + 1
    return if (group?.isDropGroup == true) "${ordinal.coerceAtLeast(1)}D" else ordinal.coerceAtLeast(1).toString()
}

internal fun workoutExerciseGroupLabel(exercises: List<WorkoutExercise>, exercise: WorkoutExercise): String {
    val groupId = exercise.supersetGroupId ?: return ""
    val ordered = exercises.sortedBy { it.orderIndex }
    val groups = ordered.filter { it.groupType == exercise.groupType }.mapNotNull { it.supersetGroupId }.distinct()
    val members = ordered.filter { it.supersetGroupId == groupId }
    val index = members.indexOfFirst { it.id == exercise.id }
    if (members.size < 2 || index < 0) return ""
    val member = if (index < 26) ('A'.code + index).toChar().toString() else (index + 1).toString()
    return "${exercise.groupType.marker}${groups.indexOf(groupId) + 1}-$member"
}

internal fun workoutSessionGroupLabels(exercises: List<WorkoutSessionExercise>): Map<String, String> {
    val references = exercises.map { WorkoutExercise(it.id, it.exerciseId, it.orderIndex, it.name,
        it.uiPart, it.equipment, it.recordType, it.familyIdentity, it.supersetGroupId, it.groupType) }
    return references.associate { it.id to workoutExerciseGroupLabel(references, it) }
}

@Composable
internal fun WorkoutExerciseGroupDialog(detail: WorkoutExerciseDetail, actions: WorkoutDetailActions, onDismiss: () -> Unit) {
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTypeId by rememberSaveable(detail.recordId, detail.activeExercise.id, detail.activeExercise.groupType) {
        mutableStateOf(detail.activeExercise.groupType.id)
    }
    val selectedType = WorkoutExerciseGroupType.fromId(selectedTypeId)
    val currentMembers = detail.exercises.filter { it.supersetGroupId != null &&
        it.supersetGroupId == detail.activeExercise.supersetGroupId }.sortedBy { it.orderIndex }
    val connect: (String?) -> Unit = { partner ->
        saving = true
        error = null
        actions.linkExerciseGroup(detail.recordId, detail.activeExercise.id, partner, selectedType) { ok ->
            saving = false
            if (ok) { actions.refresh(); onDismiss() }
            else error = "운동 연결을 저장하지 못했습니다. 다시 시도하세요."
        }
    }
    FitnessAlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("운동 연결 설정") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutExerciseGroupType.entries.forEach { type ->
                    Row(Modifier.fillMaxWidth().heightIn(min = FitnessSpacing.touch)
                        .selectable(selected = selectedType == type, enabled = !saving, role = Role.RadioButton,
                            onClick = { selectedTypeId = type.id })
                        .testTag("exercise-group-type-${type.id}"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedType == type, onClick = null)
                        Column(Modifier.weight(1f)) {
                            Text("${type.marker} · ${type.label}")
                            Text(if (type == WorkoutExerciseGroupType.COMPOUND) "같은 부위의 운동을 이어 수행" else "두 운동을 이어 수행",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("${detail.activeExercise.name}와 이어 수행할 종목을 고르세요. 묶음의 마지막 종목을 마치면 휴식합니다.")
                if (currentMembers.isNotEmpty()) Text("현재 연결: ${currentMembers.joinToString(" → ") { it.name }}",
                    style = MaterialTheme.typography.bodySmall)
                if (currentMembers.size >= 2 && selectedType != detail.activeExercise.groupType) {
                    TextButton({ connect(currentMembers.first { it.id != detail.activeExercise.id }.id) }, enabled = !saving,
                        modifier = Modifier.testTag("exercise-group-change-type")) { Text("현재 묶음을 ${selectedType.label}로 변경") }
                }
                val partners = detail.exercises.filter { it.id != detail.activeExercise.id }
                if (partners.isEmpty()) Text("현재 운동에 종목을 하나 더 추가하면 연결할 수 있습니다.")
                partners.forEach { exercise ->
                    TextButton({ connect(exercise.id) }, enabled = !saving,
                        modifier = Modifier.fillMaxWidth().testTag("exercise-group-partner-${exercise.id}")) {
                        Text(listOf(workoutExerciseGroupLabel(detail.exercises, exercise), exercise.name).filter { it.isNotBlank() }.joinToString(" · "))
                    }
                }
                if (detail.activeExercise.supersetGroupId != null) TextButton({ connect(null) }, enabled = !saving,
                    modifier = Modifier.testTag("exercise-group-unlink")) { Text("일반 운동으로 전환 (연결 해제)") }
                Text("연결을 바꾸거나 해제해도 각 세트의 유형·중량·횟수·완료 기록은 유지됩니다.", style = MaterialTheme.typography.bodySmall)
                if (currentMembers.size > 2) Text("연결을 해제하면 이 종목만 빠지고 나머지 종목의 연결은 유지됩니다.",
                    style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onDismiss, enabled = !saving) { Text("닫기") } })
}

/** One aggregate comparison per routine screen; never repeated beside individual sets. */
@Composable
internal fun WorkoutRoutineComparisonCard(session: WorkoutSessionSnapshot, unit: MassUnit) {
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
                Text("본세트 ${current.workingSetCount}개 · ${MassFormatter.withUnit(current.volumeKg, unit)}")
            } else {
                val previousVolume = prior?.volumeKg ?: previous.totalVolumeKg
                val previousSets = prior?.workingSetCount ?: previous.completedSetCount
                val delta = current.volumeKg - previousVolume
                val difference = when {
                    delta > 0 -> "+${MassFormatter.withUnit(delta, unit)}"
                    delta < 0 -> "−${MassFormatter.withUnit(abs(delta), unit)}"
                    else -> "차이 없음"
                }
                val percent = if (previousVolume > 0) " · ${String.format(Locale.ROOT, "%+.1f%%", delta / previousVolume * 100)}" else ""
                Text("이전 완료 · ${previous.date}", style = MaterialTheme.typography.bodySmall)
                Text("본세트 ${current.workingSetCount} / 지난번 ${previousSets}세트")
                Text("총 볼륨 ${MassFormatter.withUnit(current.volumeKg, unit)} / 지난번 ${MassFormatter.withUnit(previousVolume, unit)}")
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
