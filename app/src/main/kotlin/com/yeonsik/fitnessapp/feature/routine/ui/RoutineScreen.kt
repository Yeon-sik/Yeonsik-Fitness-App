package com.yeonsik.fitnessapp.feature.routine.ui

import com.yeonsik.fitnessapp.core.ui.FitnessAlertDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.feature.home.ui.HomeUiState
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutExerciseImage
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutRoutineExerciseAnchor
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutRoutineTitleAnchor
import com.yeonsik.fitnessapp.feature.workout.ui.workoutSharedElement
import com.yeonsik.fitnessapp.state.FitnessScreen

interface RoutineDetailActions {
    fun back()
    fun rename(routineId: String, name: String)
    fun copy(routineId: String, name: String)
    fun delete(routineId: String)
    fun navigate(screen: FitnessScreen)
    fun openExerciseHistory(exercise: RoutineExerciseInstance)
    fun closeExerciseHistory()
    fun startWorkout(routineId: String, title: String, exercises: List<RoutineExerciseInstance>)
}

/** The preparation page owns scrolling so its start action remains reachable. */
@Composable
internal fun RoutineDetailScreen(
    home: HomeUiState,
    ownerId: String,
    selectedRoutineId: String?,
    actions: RoutineDetailActions,
    historyState: RoutineExerciseHistoryUiState? = null,
    unit: MassUnit = MassUnit.KG,
    starting: Boolean = false,
    inProgressSessionId: String? = null
) {
    val ready = (home as? HomeUiState.Ready)?.takeIf { it.snapshot.ownerId == ownerId }
    if (ready == null) {
        FitnessHeader("루틴", back = actions::back)
        FitnessStatusMessage(FitnessSemanticStatus.INFO, "루틴을 불러오는 중입니다",
            "현재 계정의 루틴과 운동 종목을 준비하고 있습니다.")
        return
    }
    val routineId = selectedRoutineId ?: ready.snapshot.activeRoutineId
    val routine = ready.snapshot.routines.firstOrNull { it.id == routineId }
    val exercises = stableRoutineExercises(ready.snapshot.routineExercises[routineId].orEmpty())
    val closeHistory by rememberUpdatedState(actions::closeExerciseHistory)
    DisposableEffect(ownerId, routineId) { onDispose { closeHistory() } }
    if (historyState?.ownerId == ownerId) {
        RoutineExerciseHistoryDialog(historyState, unit, actions::closeExerciseHistory,
            onRetry = { actions.openExerciseHistory(historyState.exercise) })
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .testTag("routine-preparation-scroll"), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    FitnessHeader(routine?.name ?: "루틴", "${exercises.size}개 종목", back = actions::back,
                        titleModifier = Modifier.workoutSharedElement(routineId?.let(::WorkoutRoutineTitleAnchor), "title"))
                }
                if (routine != null) RoutineManagementMenu(routine.id, routine.name, actions, enabled = !starting)
            }
            FitnessSection("운동 종목") {
                if (exercises.isEmpty()) {
                    FitnessStatusMessage(FitnessSemanticStatus.INFO, "아직 종목이 없습니다",
                        "운동 종목을 추가하면 이 루틴으로 바로 운동을 시작할 수 있습니다.")
                }
                exercises.forEachIndexed { index, exercise ->
                    key(exercise.id) {
                        RoutineExerciseRow(index, routineId.orEmpty(), exercise) { actions.openExerciseHistory(exercise) }
                    }
                }
                FitnessOutlinedButton(onClick = { actions.navigate(FitnessScreen.ROUTINE_ADD) },
                    enabled = !starting, modifier = Modifier.fillMaxWidth()) { Text("종목 추가") }
            }
            Spacer(Modifier.height(FitnessSpacing.gap))
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(top = FitnessSpacing.gap)
                .semantics { liveRegion = LiveRegionMode.Polite }) {
                FitnessButton(
                    onClick = { actions.startWorkout(routineId.orEmpty(), routine?.name ?: "운동", exercises) },
                    enabled = !starting && routine != null && exercises.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("routine-start-workout")
                ) {
                    if (starting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = LocalContentColor.current)
                        Spacer(Modifier.width(FitnessSpacing.small))
                    }
                    Text(when {
                        starting -> "시작하는 중…"
                        inProgressSessionId != null || ready.snapshot.inProgressSessionId != null -> "진행 중인 운동 이어가기"
                        else -> "이 루틴으로 시작"
                    })
                }
            }
        }
    }
}

@Composable
private fun RoutineManagementMenu(routineId: String, name: String, actions: RoutineDetailActions, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    var rename by rememberSaveable(routineId) { mutableStateOf(false) }
    var deleting by rememberSaveable(routineId) { mutableStateOf(false) }
    var draft by rememberSaveable(routineId, name) { mutableStateOf(name) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) { Text("더보기") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("이름 변경") }, onClick = { expanded = false; rename = true })
            DropdownMenuItem(text = { Text("복사") }, onClick = { expanded = false; actions.copy(routineId, "$name 복사") })
            DropdownMenuItem(text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; deleting = true })
        }
    }
    if (rename) FitnessAlertDialog(onDismissRequest = { rename = false }, title = { Text("루틴 이름 변경") },
        text = { FitnessTextField(draft, { draft = it }, label = { Text("루틴 이름") }) },
        confirmButton = { TextButton(onClick = { actions.rename(routineId, draft.trim()); rename = false },
            enabled = draft.isNotBlank()) { Text("이름 저장") } },
        dismissButton = { TextButton(onClick = { rename = false }) { Text("취소") } })
    if (deleting) FitnessAlertDialog(onDismissRequest = { deleting = false }, title = { Text("루틴을 삭제할까요?") },
        text = { Text("$name 루틴을 삭제합니다. 이미 저장된 운동 기록은 유지됩니다.") },
        confirmButton = { TextButton(onClick = { deleting = false; actions.delete(routineId); actions.back() }) {
            Text("루틴 삭제", color = MaterialTheme.colorScheme.error)
        } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("취소") } })
}

@Composable
private fun RoutineExerciseRow(index: Int, routineId: String, exercise: RoutineExerciseInstance, onClick: () -> Unit) {
    val anchor = WorkoutRoutineExerciseAnchor(routineId, index)
    FitnessCard(Modifier.fillMaxWidth().testTag("routine-exercise-${exercise.id}"), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(FitnessSpacing.card * 0.6f),
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap * 0.6f),
            verticalAlignment = Alignment.CenterVertically) {
            WorkoutExerciseImage(exercise.exerciseId, exercise.familyIdentity, "${exercise.nameKo} 운동 이미지",
                Modifier.size(64.dp), sharedAnchor = anchor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
                Text(exercise.nameKo, Modifier.workoutSharedElement(anchor, "title"), style = MaterialTheme.typography.titleSmall)
                Text(routineExerciseMetadata(exercise), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${index + 1}번째 종목 · 기록 보기", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal fun stableRoutineExercises(exercises: List<RoutineExerciseInstance>): List<RoutineExerciseInstance> = exercises
    .withIndex().sortedWith(compareBy<IndexedValue<RoutineExerciseInstance>> { it.value.order }
        .thenBy { it.value.id }.thenBy { it.index }).map { it.value }

internal fun routineExerciseMetadata(exercise: RoutineExerciseInstance): String = listOfNotNull(
    displayBodyPart(exercise.uiPart), exercise.primarySubPart.takeIf { it.isNotBlank() && it != "세부 부위 없음" },
    exercise.equipment.takeIf { it.isNotBlank() }, FitnessRecordContract.displayRecordTypeKo(exercise.recordType)
).joinToString(" · ")

private fun displayBodyPart(value: String): String? {
    val normalized = value.trim().takeIf { it.isNotEmpty() } ?: return null
    return BodyPart.fromId(normalized)?.labelKo() ?: normalized
}
