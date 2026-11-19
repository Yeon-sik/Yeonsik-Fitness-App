package com.yeonsik.fitness.shared.feature.body.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens
import com.yeonsik.fitness.shared.feature.body.application.BodyMetricsApplicationService
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
fun BodyMetricsEditorDialog(
    controller: BodyMetricsEditorController,
    account: AccountScope,
    unit: MassUnit,
    formatInput: (Double, MassUnit) -> String,
    onRecordsChanged: () -> Unit,
    notify: (String) -> Unit
) {
    val state by controller.state.collectAsState()
    LaunchedEffect(state, account) {
        val result = state as? BodyMetricsEditorState.Finished ?: return@LaunchedEffect
        if (result.account == account) {
            notify(result.notice)
            if (result.recordsChanged) onRecordsChanged()
        }
        controller.acknowledge(result)
    }

    val ready = state as? BodyMetricsEditorState.Ready ?: return
    if (ready.account != account) return
    BodyMetricsEditorContent(
        editor = ready.editor,
        requestId = ready.requestId,
        unit = unit,
        formatInput = formatInput,
        onSave = { date, weightKg -> controller.save(ready, date, weightKg) },
        onDelete = { controller.delete(ready) },
        onDismiss = { controller.dismiss(ready) },
        notify = notify
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BodyMetricsEditorContent(
    editor: BodyMetricsApplicationService.Editor,
    requestId: Long,
    unit: MassUnit,
    formatInput: (Double, MassUnit) -> String,
    onSave: (String, Double) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    notify: (String) -> Unit
) {
    var date by rememberSaveable(requestId) { mutableStateOf(editor.date) }
    var showDatePicker by rememberSaveable(requestId) { mutableStateOf(false) }
    var weight by rememberSaveable(requestId) {
        mutableStateOf(if (editor.exists()) formatInput(editor.weightKg, unit) else "")
    }
    var weightSaveAttempted by rememberSaveable(requestId) { mutableStateOf(false) }
    val parsedWeight = weight.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
    val weightErrorMessage = bodyMetricsWeightErrorMessage(weight, weightSaveAttempted)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.exists()) "체중 수정" else "체중 기록") },
        text = {
            if (!showDatePicker) BodyDialogBackGesture(onDismiss)
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FitnessComposeTokens.FORM_ITEM_GAP_DP.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth().testTag("weight-date")
                ) { Text("날짜 선택  ·  $date") }
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("체중 ${unit.symbol()}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = weightErrorMessage != null,
                    supportingText = { weightErrorMessage?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth().testTag("weight-input")
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val selectedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                if (selectedDate == null) {
                    notify("달력에서 날짜를 선택하세요.")
                } else if (parsedWeight == null) {
                    weightSaveAttempted = true
                } else {
                    onSave(selectedDate.toString(), unit.toKg(parsedWeight))
                }
            }, modifier = Modifier.testTag("weight-save")) { Text("저장") }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (editor.exists()) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.testTag("weight-delete")) {
                        Text("이 기록 삭제")
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("weight-cancel")) { Text("취소") }
            }
        }
    )

    if (showDatePicker) {
        val closeDatePicker = { showDatePicker = false }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateToUtcMillis(date)
        )
        DatePickerDialog(
            onDismissRequest = closeDatePicker,
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    modifier = Modifier.testTag("weight-date-confirm"),
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            date = dateFromUtcMillis(selectedMillis)
                        }
                        closeDatePicker()
                    }
                ) { Text("선택") }
            },
            dismissButton = {
                TextButton(
                    onClick = closeDatePicker,
                    modifier = Modifier.testTag("weight-date-cancel")
                ) { Text("취소") }
            }
        ) {
            BodyDialogBackGesture(closeDatePicker)
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}

@Composable
internal expect fun BodyDialogBackGesture(onBack: () -> Unit)

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
    LocalDate.parse(date).toEpochDays() * 86_400_000L
}.getOrNull()

internal fun dateFromUtcMillis(millis: Long): String =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date.toString()
