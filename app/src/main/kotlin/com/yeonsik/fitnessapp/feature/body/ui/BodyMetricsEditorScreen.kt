package com.yeonsik.fitnessapp.feature.body.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.data.MassUnit

interface BodyMetricsEditorActions {
    fun save(recordId: String?, date: String, weightKg: Double, memo: String)
    fun delete(recordId: String)
    fun dismiss()
    fun notify(message: String)
}

@Composable
fun BodyMetricsEditorDialog(
    state: BodyMetricsEditorUiState,
    ownerId: String,
    unit: MassUnit,
    actions: BodyMetricsEditorActions
) {
    val ready = state as? BodyMetricsEditorUiState.Ready
    if (ready == null || ready.ownerId != ownerId) return

    val editor = ready.editor
    var date by rememberSaveable(ready.requestId) { mutableStateOf(editor.date) }
    var weight by rememberSaveable(ready.requestId) {
        mutableStateOf(if (editor.exists()) MassFormatter.formatInput(editor.weightKg, unit) else "")
    }
    var memo by rememberSaveable(ready.requestId) { mutableStateOf(editor.memo) }

    AlertDialog(
        onDismissRequest = actions::dismiss,
        title = { Text(if (editor.exists()) "체중 수정" else "체중 기록") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("날짜 (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("체중 ${unit.symbol()}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모 (선택)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = weight.trim().toDoubleOrNull()
                if (date.trim().isEmpty()) {
                    actions.notify("날짜를 입력하세요.")
                } else if (parsed == null) {
                    actions.notify("체중을 입력하세요.")
                } else {
                    actions.save(editor.recordId, date.trim(), unit.toKg(parsed), memo)
                }
            }) { Text("저장") }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (editor.exists()) {
                    OutlinedButton(onClick = { actions.delete(editor.recordId) }) {
                        Text("이 기록 삭제")
                    }
                }
                TextButton(onClick = actions::dismiss) { Text("취소") }
            }
        }
    )
}
