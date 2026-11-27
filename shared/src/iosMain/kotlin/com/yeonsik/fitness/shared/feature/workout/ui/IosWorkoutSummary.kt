package com.yeonsik.fitness.shared.feature.workout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.settings.IosDisplaySettings
import com.yeonsik.fitness.shared.feature.settings.IosFitnessTheme
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessSemanticStatus
import com.yeonsik.fitnessapp.core.ui.FitnessStatusBadge
import platform.Foundation.NSBundle
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode
import platform.UIKit.UIViewController

fun iosSummarySet(id: String, index: Int, weightKg: Double?, inputWeight: Double?, unit: MassUnit,
                  reps: Int?, rir: Int?, completed: Boolean) =
    WorkoutSet(id, index, weightKg ?: 0.0, reps ?: 0, rir, null, completed, 0, 0.0, 0.0, 0.0,
        LoadState.EXTERNAL_LOAD, inputWeight, unit)

fun iosSummaryExercise(id: String, exerciseId: String, order: Int, name: String, bodyPart: String?,
                       primaryPart: String?, equipment: String?, sets: List<WorkoutSet>): WorkoutSessionExercise {
    val completed = sets.filter { it.isCompleted }
    return WorkoutSessionExercise(id, exerciseId, order, name, BodyPart.fromId(bodyPart)?.labelKo.orEmpty(),
        equipment.orEmpty(), "weight_reps", "중량 · 반복", null, completed.size, sets.size, completed,
        primarySubPart = primaryPart, totalVolumeKg = completed.sumOf { it.weightKg * it.actualReps })
}

@Composable
private fun SummaryExerciseImage(exercise: WorkoutSessionExercise) {
    val path = remember(exercise.exerciseId) {
        NSBundle.mainBundle.pathForResource(exercise.exerciseId, "png", "WorkoutSummary")
    }
    val modifier = Modifier.size(72.dp)
    if (path == null) {
        FitnessStatusBadge(FitnessSemanticStatus.UNKNOWN, "운동 이미지 없음", modifier)
    } else {
        UIKitView(
            factory = { UIImageView().apply { contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit } },
            update = { it.image = UIImage.imageWithContentsOfFile(path) },
            modifier = modifier.semantics { contentDescription = "${exercise.name} 운동 이미지" },
            properties = UIKitInteropProperties(interactionMode = null)
        )
    }
}

fun iosCompletedSnapshot(id: String, routineId: String, title: String, startedAt: String, completedAt: String,
                         durationSeconds: Int, exercises: List<WorkoutSessionExercise>, recent: List<WorkoutVolumePoint>,
                         previous: WorkoutRoutineComparison?): WorkoutSessionSnapshot {
    val totals = workoutRoutineTotals(exercises)
    return WorkoutSessionSnapshot(id, title, "completed", startedAt, durationSeconds, totals.volumeKg,
        exercises.sumOf { it.completedSetCount }, exercises, recent, routineId, previous, completedAt, totals)
}

fun iosPreviousRoutine(date: String, recordId: String, exercises: List<WorkoutSessionExercise>): WorkoutRoutineComparison {
    val totals = workoutRoutineTotals(exercises)
    return WorkoutRoutineComparison(date, totals.volumeKg, exercises.sumOf { it.completedSetCount }, totals, recordId)
}

fun makeWorkoutSummaryViewController(
    session: WorkoutSessionSnapshot,
    onExercise: (String) -> Unit,
    onRecords: () -> Unit,
    onSaveRoutine: (String) -> Unit
): UIViewController = ComposeUIViewController {
    IosFitnessTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FitnessSpacing.page),
                verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
                CompletedWorkoutSummary(session, IosDisplaySettings.unit, onExercise, onRecords,
                    onSaveAsRoutine = { name, _ -> onSaveRoutine(name) },
                    exerciseImage = { SummaryExerciseImage(it) })
            }
        }
    }
}
