package com.yeonsik.fitnessapp.feature.exercise.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.exercise.model.EquipmentType
import com.yeonsik.fitness.shared.feature.workout.model.ManualWorkoutExercise
import com.yeonsik.fitnessapp.core.ui.FitnessOutlinedButton
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessTextField
import com.yeonsik.fitnessapp.data.FitnessRecordContract

/** Local input draft only. The route delegates the confirmed command to the picker ViewModel. */
@Composable
internal fun ManualWorkoutExerciseDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (ManualWorkoutExercise) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var partId by rememberSaveable { mutableStateOf("") }
    var equipmentId by rememberSaveable { mutableStateOf("") }
    var recordType by rememberSaveable { mutableStateOf("") }
    val part = BodyPart.fromId(partId)
    val equipment = EquipmentType.fromId(equipmentId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("운동 직접 추가") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
                Text("수동 운동으로 기록합니다. 완료 후 정식 운동에 연결할 수 있습니다.")
                FitnessTextField(name, { name = it }, label = { Text("운동명") },
                    modifier = Modifier.fillMaxWidth().testTag("manual-exercise-name"))
                ManualExerciseChoice("부위", partId,
                    BodyPart.values().map { it.id() to it.labelKo() }) { partId = it }
                ManualExerciseChoice("장비", equipmentId,
                    EquipmentType.values().map { it.id() to it.labelKo() }) { equipmentId = it }
                ManualExerciseChoice("기록 방식", recordType, ManualWorkoutExercise.RECORD_TYPES.map {
                    it to FitnessRecordContract.displayRecordTypeKo(it)
                }) { recordType = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ManualWorkoutExercise(name, part!!, equipment!!, recordType)) },
                enabled = name.isNotBlank() && part != null && equipment != null && recordType.isNotBlank()) {
                Text("운동 추가")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun ManualExerciseChoice(label: String, selected: String,
                                 choices: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        FitnessOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${choices.firstOrNull { it.first == selected }?.second ?: "선택"}")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(id) })
            }
        }
    }
}
