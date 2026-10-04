package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

interface WorkoutDeleteConfirmationActions {
    fun confirm()
    fun dismiss()
}

@Composable
fun WorkoutDeleteConfirmationDialog(
    state: WorkoutDeleteConfirmationUiState,
    ownerId: String,
    actions: WorkoutDeleteConfirmationActions
) {
    val ready = state as? WorkoutDeleteConfirmationUiState.Ready
    if (ready == null || ready.ownerId != ownerId) return

    AlertDialog(
        onDismissRequest = actions::dismiss,
        title = { Text(if (ready.cancelWorkout) "운동을 취소할까요?" else "운동 기록 삭제") },
        text = {
            Text(
                if (ready.cancelWorkout) "입력한 종목과 세트가 모두 삭제되고, 운동 기록이 남지 않습니다."
                else "이 운동 기록과 세부 운동/세트 기록을 삭제 표시합니다.\n삭제된 기록은 기록 탭에서 더 이상 보이지 않습니다."
            )
        },
        confirmButton = {
            TextButton(onClick = actions::confirm) {
                Text(
                    if (ready.cancelWorkout) "기록 없이 취소" else "삭제",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = actions::dismiss) {
                Text(if (ready.cancelWorkout) "계속 운동" else "취소")
            }
        }
    )
}
