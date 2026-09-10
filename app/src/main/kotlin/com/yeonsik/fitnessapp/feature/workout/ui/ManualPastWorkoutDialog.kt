package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitnessapp.feature.routine.model.RoutineSummary
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

interface ManualPastWorkoutActions {
    fun start(
        date: String,
        title: String,
        routineId: String?,
        exercises: List<RoutineExerciseInstance>,
        startedAt: String,
        endedAt: String
    )

    fun dismiss()
    fun notify(message: String)
}

@Composable
fun ManualPastWorkoutDialog(
    state: ManualPastWorkoutUiState,
    ownerId: String,
    routines: List<RoutineSummary>,
    routineExercises: Map<String, List<RoutineExerciseInstance>>,
    initialRoutineId: String?,
    actions: ManualPastWorkoutActions
) {
    val ready = state as? ManualPastWorkoutUiState.Ready
    if (ready == null || ready.ownerId != ownerId) return

    var date by rememberSaveable(ready.ownerId) {
        mutableStateOf(LocalDate.now().minusDays(1).toString())
    }
    var startTime by rememberSaveable(ready.ownerId) { mutableStateOf("18:00") }
    var duration by rememberSaveable(ready.ownerId) { mutableStateOf("60") }
    var selectedRoutineId by rememberSaveable(ready.ownerId) {
        mutableStateOf(initialRoutineId?.takeIf { id -> routines.any { it.id == id } })
    }
    var routineExpanded by remember { mutableStateOf(false) }
    val selectedRoutine = routines.firstOrNull { it.id == selectedRoutineId }

    AlertDialog(
        onDismissRequest = actions::dismiss,
        title = { Text("지난 운동 수동 등록") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("운동 날짜 (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("시작 시각 (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("운동 시간 (분)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { routineExpanded = true }) {
                        Text(
                            selectedRoutine?.let { "${it.name} · ${it.exerciseCount}종목" }
                                ?: "루틴 없이 운동"
                        )
                    }
                    DropdownMenu(
                        expanded = routineExpanded,
                        onDismissRequest = { routineExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("루틴 없이 운동") },
                            onClick = {
                                selectedRoutineId = null
                                routineExpanded = false
                            }
                        )
                        routines.forEach { routine ->
                            DropdownMenuItem(
                                text = { Text("${routine.name} · ${routine.exerciseCount}종목") },
                                onClick = {
                                    selectedRoutineId = routine.id
                                    routineExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val selectedDate = LocalDate.parse(date.trim())
                    val selectedTime = LocalTime.parse(
                        startTime.trim(),
                        DateTimeFormatter.ofPattern("H:mm")
                    )
                    val durationMinutes = duration.trim().toInt()
                    if (durationMinutes <= 0 || durationMinutes > 1440) {
                        throw IllegalArgumentException("운동 시간은 1~1440분으로 입력하세요.")
                    }
                    val startedAt = selectedDate.atTime(selectedTime).atOffset(ZoneOffset.ofHours(9))
                    val endedAt = startedAt.plusMinutes(durationMinutes.toLong())
                    if (endedAt.isAfter(OffsetDateTime.now(ZoneOffset.ofHours(9)))) {
                        throw IllegalArgumentException("종료 시각이 현재보다 늦을 수 없습니다.")
                    }
                    actions.start(
                        selectedDate.toString(),
                        selectedRoutine?.name ?: "루틴 없이 운동",
                        selectedRoutine?.id,
                        selectedRoutine?.let { routineExercises[it.id].orEmpty() }.orEmpty(),
                        startedAt.toString(),
                        endedAt.toString()
                    )
                } catch (_: DateTimeParseException) {
                    actions.notify("날짜는 YYYY-MM-DD, 시작 시각은 HH:mm 형식으로 입력하세요.")
                } catch (_: NumberFormatException) {
                    actions.notify("운동 시간은 숫자로 입력하세요.")
                } catch (error: IllegalArgumentException) {
                    actions.notify(error.message ?: "지난 운동을 등록하지 못했습니다.")
                }
            }) { Text("세트 입력으로 이동") }
        },
        dismissButton = { TextButton(onClick = actions::dismiss) { Text("취소") } }
    )
}
