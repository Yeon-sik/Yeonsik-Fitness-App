package com.yeonsik.fitnessapp.feature.development.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.data.AthleteDailyCheckIn
import com.yeonsik.fitnessapp.data.AthleteNutritionGoal

@Composable
internal fun RecoveryNutritionGoalEditorDialog(
    state: RecoveryEditorUiState,
    ownerId: String,
    actions: DevelopmentEditorActions
) {
    val ready = state as? RecoveryEditorUiState.Ready ?: return
    if (ready.ownerId != ownerId || ready.editor != RecoveryEditorKind.NUTRITION_GOAL) return

    val current = ready.nutritionGoal
    var phase by rememberSaveable(ready.requestId, "phase") {
        mutableStateOf(current?.phase ?: AthleteNutritionGoal.PHASE_MAINTENANCE)
    }
    var calories by rememberSaveable(ready.requestId, "calories") {
        mutableStateOf(current?.caloriesKcal?.toString().orEmpty())
    }
    var protein by rememberSaveable(ready.requestId, "protein") {
        mutableStateOf(current?.proteinGrams?.toString().orEmpty())
    }
    var carbs by rememberSaveable(ready.requestId, "carbs") {
        mutableStateOf(current?.carbsGrams?.toString().orEmpty())
    }
    var fat by rememberSaveable(ready.requestId, "fat") {
        mutableStateOf(current?.fatGrams?.toString().orEmpty())
    }
    var fiber by rememberSaveable(ready.requestId, "fiber") {
        mutableStateOf(current?.fiberGrams?.toString().orEmpty())
    }
    var sodium by rememberSaveable(ready.requestId, "sodium") {
        mutableStateOf(current?.sodiumMg?.toString().orEmpty())
    }
    var water by rememberSaveable(ready.requestId, "water") {
        mutableStateOf(current?.waterMl?.toString().orEmpty())
    }
    var phaseExpanded by rememberSaveable(ready.requestId, "phase-expanded") { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = actions::dismissRecovery,
        title = { Text("영양 목표 설정") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                Text(
                    "앱이 목표값을 추정하지 않습니다. 직접 입력한 값만 저장합니다.",
                    style = MaterialTheme.typography.bodySmall
                )
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { phaseExpanded = true }) {
                        Text("단계 · ${AthleteNutritionGoal.phaseLabel(phase)}")
                    }
                    DropdownMenu(
                        expanded = phaseExpanded,
                        onDismissRequest = { phaseExpanded = false }
                    ) {
                        AthleteNutritionGoal.PHASES.forEach { value ->
                            DropdownMenuItem(
                                text = { Text(AthleteNutritionGoal.phaseLabel(value)) },
                                onClick = {
                                    phase = value
                                    phaseExpanded = false
                                }
                            )
                        }
                    }
                }
                GoalNumberField("열량 kcal", calories) { calories = it }
                GoalNumberField("단백질 g", protein) { protein = it }
                GoalNumberField("탄수화물 g", carbs) { carbs = it }
                GoalNumberField("지방 g", fat) { fat = it }
                GoalNumberField("식이섬유 g", fiber) { fiber = it }
                GoalNumberField("나트륨 mg", sodium) { sodium = it }
                GoalNumberField("수분 목표 ml", water) { water = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    actions.saveNutritionGoal(
                        AthleteNutritionGoal(
                            phase,
                            calories.requiredPositiveDouble("열량"),
                            protein.requiredPositiveDouble("단백질"),
                            carbs.requiredPositiveDouble("탄수화물"),
                            fat.requiredPositiveDouble("지방"),
                            fiber.requiredPositiveDouble("식이섬유"),
                            sodium.requiredPositiveDouble("나트륨"),
                            water.requiredPositiveInt("수분 목표")
                        )
                    )
                } catch (error: IllegalArgumentException) {
                    actions.notify(error.message ?: "영양 목표를 저장하지 못했습니다.")
                }
            }) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = actions::dismissRecovery) { Text("취소") }
        }
    )
}

@Composable
internal fun RecoveryCheckInEditorDialog(
    state: RecoveryEditorUiState,
    ownerId: String,
    actions: DevelopmentEditorActions
) {
    val ready = state as? RecoveryEditorUiState.Ready ?: return
    if (ready.ownerId != ownerId || ready.editor != RecoveryEditorKind.CHECK_IN) return

    val current = ready.checkIn
    var water by rememberSaveable(ready.requestId, "check-in-water") {
        mutableStateOf(current.waterMl.toString())
    }
    var sleep by rememberSaveable(ready.requestId, "check-in-sleep") {
        mutableStateOf(current.sleepHours?.toString().orEmpty())
    }
    var energy by rememberSaveable(ready.requestId, "check-in-energy") {
        mutableStateOf(current.energyScore?.toString().orEmpty())
    }
    var hunger by rememberSaveable(ready.requestId, "check-in-hunger") {
        mutableStateOf(current.hungerScore?.toString().orEmpty())
    }
    var digestion by rememberSaveable(ready.requestId, "check-in-digestion") {
        mutableStateOf(current.digestionScore?.toString().orEmpty())
    }
    var readiness by rememberSaveable(ready.requestId, "check-in-readiness") {
        mutableStateOf(current.trainingReadinessScore?.toString().orEmpty())
    }
    var note by rememberSaveable(ready.requestId, "check-in-note") {
        mutableStateOf(current.note)
    }

    AlertDialog(
        onDismissRequest = actions::dismissRecovery,
        title = { Text("회복 체크인 · ${ready.date}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                Text(
                    "입력하지 않은 점수는 미기록으로 유지됩니다.",
                    style = MaterialTheme.typography.bodySmall
                )
                GoalNumberField("수분 섭취 ml", water) { water = it }
                GoalNumberField("수면 시간(시간, 선택)", sleep) { sleep = it }
                GoalNumberField("에너지 점수 1~5(선택)", energy) { energy = it }
                GoalNumberField("허기 점수 1~5(선택)", hunger) { hunger = it }
                GoalNumberField("소화 점수 1~5(선택)", digestion) { digestion = it }
                GoalNumberField("훈련 준비도 1~5(선택)", readiness) { readiness = it }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("메모(선택)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    actions.saveRecoveryCheckIn(
                        AthleteDailyCheckIn(
                            current.id,
                            ready.date,
                            water.requiredNonNegativeInt("수분 섭취량"),
                            sleep.optionalDouble("수면 시간"),
                            energy.optionalScore("에너지"),
                            hunger.optionalScore("허기"),
                            digestion.optionalScore("소화"),
                            readiness.optionalScore("훈련 준비도"),
                            note
                        )
                    )
                } catch (error: IllegalArgumentException) {
                    actions.notify(error.message ?: "회복 체크인을 저장하지 못했습니다.")
                }
            }) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = actions::dismissRecovery) { Text("취소") }
        }
    )
}

@Composable
private fun GoalNumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

private fun String.requiredPositiveDouble(label: String): Double {
    val value = trim().toDoubleOrNull()
        ?: throw IllegalArgumentException("$label 목표를 숫자로 입력해 주세요.")
    if (!value.isFinite() || value <= 0.0) {
        throw IllegalArgumentException("$label 목표는 0보다 커야 합니다.")
    }
    return value
}

private fun String.requiredPositiveInt(label: String): Int {
    val value = trim().toIntOrNull()
        ?: throw IllegalArgumentException("$label 목표를 숫자로 입력해 주세요.")
    if (value <= 0) throw IllegalArgumentException("$label 목표는 0보다 커야 합니다.")
    return value
}

private fun String.requiredNonNegativeInt(label: String): Int {
    val value = trim().toIntOrNull()
        ?: throw IllegalArgumentException("${label}을 숫자로 입력해 주세요.")
    if (value < 0) throw IllegalArgumentException("${label}은 0 이상이어야 합니다.")
    return value
}

private fun String.optionalDouble(label: String): Double? {
    if (trim().isEmpty()) return null
    val value = trim().toDoubleOrNull()
        ?: throw IllegalArgumentException("${label}을 숫자로 입력해 주세요.")
    return value
}

private fun String.optionalScore(label: String): Int? {
    if (trim().isEmpty()) return null
    val value = trim().toIntOrNull()
        ?: throw IllegalArgumentException("$label 점수를 숫자로 입력해 주세요.")
    if (value !in 1..5) throw IllegalArgumentException("$label 점수는 1~5 범위여야 합니다.")
    return value
}
