package com.yeonsik.fitnessapp.feature.workout.ui

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
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitness.shared.feature.workout.model.*

internal fun workoutSetMarker(sets: List<WorkoutSet>, set: WorkoutSet): String {
    if (set.setType == WorkoutSetType.WARMUP) return "W"
    val groups = workoutSetGroups(sets).filter { it.root.setType != WorkoutSetType.WARMUP }
    val group = groups.firstOrNull { group -> group.sets.any { it.id == set.id } }
    val ordinal = groups.indexOfFirst { it.root.id == group?.root?.id } + 1
    return if (group?.isDropGroup == true) "${ordinal.coerceAtLeast(1)}D" else ordinal.coerceAtLeast(1).toString()
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
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("운동 연결 설정") },
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
