package com.yeonsik.fitnessapp.feature.routine.ui

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessButton
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import com.yeonsik.fitnessapp.core.ui.FitnessExerciseIllustration
import com.yeonsik.fitnessapp.core.ui.FitnessHeader
import com.yeonsik.fitnessapp.core.ui.FitnessOutlinedButton
import com.yeonsik.fitnessapp.core.ui.FitnessSection
import com.yeonsik.fitnessapp.core.ui.FitnessSemanticStatus
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessStatusMessage
import com.yeonsik.fitnessapp.core.ui.FitnessTextField
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitnessapp.feature.home.ui.HomeUiState
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitnessapp.state.FitnessScreen

interface RoutineDetailActions {
    fun back()
    fun rename(routineId: String, name: String)
    fun copy(routineId: String, name: String)
    fun delete(routineId: String)
    fun navigate(screen: FitnessScreen)
    fun startWorkout(
        routineId: String,
        title: String,
        exercises: List<RoutineExerciseInstance>
    )
}

@Composable
internal fun RoutineDetailScreen(
    home: HomeUiState,
    ownerId: String,
    selectedRoutineId: String?,
    actions: RoutineDetailActions
) {
    val ready = home as? HomeUiState.Ready
    if (ready == null || ready.snapshot.ownerId != ownerId) {
        FitnessHeader("루틴", back = actions::back)
        FitnessStatusMessage(
            status = FitnessSemanticStatus.INFO,
            title = "루틴을 불러오는 중입니다",
            message = "현재 계정의 루틴과 운동 종목을 준비하고 있습니다."
        )
        return
    }

    val routineId = selectedRoutineId ?: ready.snapshot.activeRoutineId
    val routine = ready.snapshot.routines.firstOrNull { it.id == routineId }
    val exercises = stableRoutineExercises(ready.snapshot.routineExercises[routineId].orEmpty())
    var editedName by rememberSaveable(routine?.id) { mutableStateOf(routine?.name.orEmpty()) }
    LaunchedEffect(routine?.id, routine?.name) {
        editedName = routine?.name.orEmpty()
    }

    FitnessHeader(
        title = "루틴",
        subtitle = routine?.name ?: "기본 루틴",
        back = actions::back
    )

    if (routine != null) {
        FitnessCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(FitnessSpacing.card),
                verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
            ) {
                FitnessTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("루틴 이름") }
                )
                RoutineDefinitionActions(routine.id, editedName, routine.name, actions)
            }
        }
    }

    FitnessSection("운동 종목") {
        if (exercises.isEmpty()) {
            FitnessStatusMessage(
                status = FitnessSemanticStatus.INFO,
                title = "아직 종목이 없습니다",
                message = "운동 종목을 추가하면 이 루틴으로 바로 운동을 시작할 수 있습니다."
            )
        } else {
            exercises.forEachIndexed { index, exercise ->
                RoutineExerciseRow(index + 1, exercise)
            }
        }
    }

    RoutineWorkoutActions(
        routineId = routine?.id ?: routineId.orEmpty(),
        title = routine?.name ?: "운동",
        exercises = exercises,
        actions = actions
    )
}

@Composable
private fun RoutineDefinitionActions(
    routineId: String,
    editedName: String,
    originalName: String,
    actions: RoutineDetailActions
) {
    val stack = LocalDensity.current.fontScale >= 1.3f
    val content: @Composable () -> Unit = {
        FitnessOutlinedButton(
            onClick = { actions.rename(routineId, editedName) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("이름 저장") }
        FitnessOutlinedButton(
            onClick = { actions.copy(routineId, "$originalName 복사") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("복사") }
        FitnessOutlinedButton(
            onClick = { actions.delete(routineId); actions.back() },
            modifier = Modifier.fillMaxWidth(),
            destructive = true
        ) { Text("루틴 삭제") }
    }
    if (stack) {
        Column(verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) { content() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
            FitnessOutlinedButton(
                onClick = { actions.rename(routineId, editedName) },
                modifier = Modifier.weight(1f)
            ) { Text("이름 저장") }
            FitnessOutlinedButton(
                onClick = { actions.copy(routineId, "$originalName 복사") },
                modifier = Modifier.weight(1f)
            ) { Text("복사") }
            FitnessOutlinedButton(
                onClick = { actions.delete(routineId); actions.back() },
                modifier = Modifier.weight(1f),
                destructive = true
            ) { Text("삭제") }
        }
    }
}

@Composable
private fun RoutineWorkoutActions(
    routineId: String,
    title: String,
    exercises: List<RoutineExerciseInstance>,
    actions: RoutineDetailActions
) {
    val stack = LocalDensity.current.fontScale >= 1.3f
    val add: @Composable () -> Unit = {
        FitnessOutlinedButton(
            onClick = { actions.navigate(FitnessScreen.ROUTINE_ADD) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("종목 추가") }
    }
    val start: @Composable () -> Unit = {
        FitnessButton(
            onClick = { actions.startWorkout(routineId, title, exercises) },
            enabled = exercises.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("이 루틴으로 시작") }
    }
    if (stack) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
        ) {
            add()
            start()
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
        ) {
            Box(Modifier.weight(1f)) { add() }
            Box(Modifier.weight(1f)) { start() }
        }
    }
}

@Composable
private fun RoutineExerciseRow(displayOrder: Int, exercise: RoutineExerciseInstance) {
    val activity = LocalActivity.current
    FitnessCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(FitnessSpacing.card),
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoutineExerciseImage(activity, exercise)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
            ) {
                Text(
                    text = "$displayOrder. ${exercise.nameKo}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val metadata = routineExerciseMetadata(exercise)
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineExerciseImage(activity: Activity?, exercise: RoutineExerciseInstance) {
    val modifier = Modifier.size(72.dp)
    val description = "${exercise.nameKo} 운동 이미지"
    val identity = exercise.familyIdentity
    if (activity == null) {
        RoutineImageFallback(modifier)
    } else if (identity != null) {
        FitnessExerciseIllustration(
            activity = activity,
            identity = identity,
            exactVariant = true,
            modifier = modifier,
            contentDescription = description,
            fallback = {
                FitnessExerciseIllustration(
                    activity = activity,
                    identity = identity,
                    modifier = modifier,
                    contentDescription = description
                ) { RoutineImageFallback() }
            }
        )
    } else {
        FitnessExerciseIllustration(
            activity = activity,
            exerciseId = exercise.exerciseId,
            modifier = modifier,
            contentDescription = description,
            fallback = { RoutineImageFallback() }
        )
    }
}

@Composable
private fun RoutineImageFallback(modifier: Modifier = Modifier.size(72.dp)) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "이미지 없음",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun stableRoutineExercises(
    exercises: List<RoutineExerciseInstance>
): List<RoutineExerciseInstance> = exercises
    .withIndex()
    .sortedWith(
        compareBy<IndexedValue<RoutineExerciseInstance>> { it.value.order }
            .thenBy { it.value.id }
            .thenBy { it.index }
    )
    .map { it.value }

internal fun routineExerciseMetadata(exercise: RoutineExerciseInstance): String = listOfNotNull(
    displayBodyPart(exercise.uiPart),
    exercise.primarySubPart.takeIf { it.isNotBlank() && it != "세부 부위 없음" },
    exercise.equipment.takeIf { it.isNotBlank() },
    FitnessRecordContract.displayRecordTypeKo(exercise.recordType)
).joinToString(" · ")

private fun displayBodyPart(value: String): String? {
    val normalized = value.trim().takeIf { it.isNotEmpty() } ?: return null
    return BodyPart.fromId(normalized)?.labelKo() ?: normalized
}
