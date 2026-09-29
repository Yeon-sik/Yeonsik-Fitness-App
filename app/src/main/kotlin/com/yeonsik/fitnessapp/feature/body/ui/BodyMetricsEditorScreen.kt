package com.yeonsik.fitnessapp.feature.body.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

interface BodyMetricsEditorActions {
    fun save(recordId: String?, date: String, weightKg: Double, memo: String)
    fun delete(recordId: String)
    fun dismiss()
    fun notify(message: String)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    var showDatePicker by rememberSaveable(ready.requestId) { mutableStateOf(false) }
    var weight by rememberSaveable(ready.requestId) {
        mutableStateOf(if (editor.exists()) MassFormatter.formatInput(editor.weightKg, unit) else "")
    }
    var weightSaveAttempted by rememberSaveable(ready.requestId) { mutableStateOf(false) }
    val parsedWeight = weight.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
    val weightErrorMessage = bodyMetricsWeightErrorMessage(weight, weightSaveAttempted)

    AlertDialog(
        onDismissRequest = actions::dismiss,
        title = { Text(if (editor.exists()) "체중 수정" else "체중 기록") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("날짜 선택  ·  $date") }
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("체중 ${unit.symbol()}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = weightErrorMessage != null,
                    supportingText = { weightErrorMessage?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val selectedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                if (selectedDate == null) {
                    actions.notify("달력에서 날짜를 선택하세요.")
                } else if (parsedWeight == null) {
                    weightSaveAttempted = true
                } else {
                    actions.save(
                        editor.recordId,
                        selectedDate.toString(),
                        unit.toKg(parsedWeight),
                        editor.memo
                    )
                }
            }) { Text("저장") }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (editor.exists()) {
                    OutlinedButton(onClick = { editor.recordId?.let(actions::delete) }) {
                        Text("이 기록 삭제")
                    }
                }
                TextButton(onClick = actions::dismiss) { Text("취소") }
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateToUtcMillis(date)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            date = dateFromUtcMillis(selectedMillis)
                        }
                        showDatePicker = false
                    }
                ) { Text("선택") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소") }
            }
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}

internal fun bodyMetricsWeightErrorMessage(value: String, saveAttempted: Boolean): String? {
    val input = value.trim()
    val parsed = input.toDoubleOrNull()
    return when {
        parsed != null && parsed.isFinite() -> null
        input.isEmpty() && !saveAttempted -> null
        input.isEmpty() -> "체중을 입력하세요."
        ',' in input -> "소수점은 쉼표(,) 대신 마침표(.)를 사용해 주세요. 예: 90.9"
        else -> "체중을 숫자로 입력해 주세요."
    }
}

internal fun dateToUtcMillis(date: String): Long? = runCatching {
    LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}.getOrNull()

internal fun dateFromUtcMillis(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
