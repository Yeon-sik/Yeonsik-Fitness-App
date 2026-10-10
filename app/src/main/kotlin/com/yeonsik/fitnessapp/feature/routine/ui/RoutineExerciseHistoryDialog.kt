package com.yeonsik.fitnessapp.feature.routine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.FitnessHeader
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutExerciseHistoryContent

@Composable
internal fun RoutineExerciseHistoryDialog(
    state: RoutineExerciseHistoryUiState,
    unit: MassUnit,
    onClose: () -> Unit,
    onRetry: () -> Unit
) {
    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("routine-exercise-history")
            .semantics { paneTitle = "종목 기록" }, color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
                FitnessHeader("종목 기록", subtitle = state.exercise.nameKo, back = onClose)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when {
                        state.loading -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("종목 기록을 불러오는 중입니다.")
                        }
                        state.error != null -> {
                            Text(state.error, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = onRetry) { Text("다시 불러오기") }
                        }
                        state.detail != null -> WorkoutExerciseHistoryContent(state.detail, unit)
                        else -> Text("이 종목의 완료된 운동 기록이 없습니다.")
                    }
                }
            }
        }
    }
}
