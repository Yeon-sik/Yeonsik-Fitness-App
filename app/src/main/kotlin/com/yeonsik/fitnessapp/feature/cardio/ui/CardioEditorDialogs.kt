package com.yeonsik.fitnessapp.feature.cardio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.yeonsik.fitnessapp.cardio.CardioMetrics
import com.yeonsik.fitnessapp.core.ui.AppSpacing

interface CardioHeartRateEditorActions {
    fun updateInput(value: String)
    fun save(value: String)
    fun dismiss()
}

@Composable
fun CardioHeartRateEditorDialog(
    state: CardioHeartRateEditorUiState,
    ownerId: String,
    actions: CardioHeartRateEditorActions
) {
    val ready = state as? CardioHeartRateEditorUiState.Ready
    if (ready == null || ready.ownerId != ownerId) return

    val title = if (ready.finishAfterSave) {
        "${ready.session.activityLabel} 완료"
    } else {
        "평균 심박수 수정"
    }
    AlertDialog(
        onDismissRequest = actions::dismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                Text(
                    "${CardioMetrics.formatDistanceKilometers(ready.session.distanceMeters)}km · " +
                        CardioMetrics.formatElapsed(
                            ready.session.elapsedSeconds(System.currentTimeMillis())
                        )
                )
                OutlinedTextField(
                    value = ready.input,
                    onValueChange = actions::updateInput,
                    label = { Text("평균 심박수 bpm (선택)") },
                    supportingText = ready.errorMessage?.let { message ->
                        { Text(message) }
                    },
                    isError = ready.errorMessage != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("시계·밴드 등에서 확인한 평균값을 직접 입력하세요. 측정값이 없으면 비워둘 수 있습니다.")
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.save(ready.input) }) {
                Text(if (ready.finishAfterSave) "운동 완료" else "저장")
            }
        },
        dismissButton = {
            TextButton(onClick = actions::dismiss) { Text("취소") }
        }
    )
}

interface CardioCancelConfirmationActions {
    fun confirm()
    fun dismiss()
}

@Composable
fun CardioCancelConfirmationDialog(
    state: CardioCancelConfirmationUiState,
    ownerId: String,
    actions: CardioCancelConfirmationActions
) {
    val ready = state as? CardioCancelConfirmationUiState.Ready
    if (ready == null || ready.ownerId != ownerId) return

    AlertDialog(
        onDismissRequest = actions::dismiss,
        title = { Text("유산소 기록 취소") },
        text = { Text(ready.summary) },
        confirmButton = {
            TextButton(onClick = actions::confirm) { Text("기록 취소") }
        },
        dismissButton = {
            TextButton(onClick = actions::dismiss) { Text("돌아가기") }
        }
    )
}
