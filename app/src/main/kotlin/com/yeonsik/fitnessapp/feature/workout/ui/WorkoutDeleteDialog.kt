package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.material3.AlertDialog
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
        title = { Text("운동 기록 삭제") },
        text = {
            Text("이 운동 기록과 세부 운동/세트 기록을 삭제 표시합니다.\n삭제된 기록은 기록 탭에서 더 이상 보이지 않습니다.")
        },
        confirmButton = {
            TextButton(onClick = actions::confirm) { Text("삭제") }
        },
        dismissButton = {
            TextButton(onClick = actions::dismiss) { Text("취소") }
        }
    )
}
