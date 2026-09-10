package com.yeonsik.fitnessapp.feature.routine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.BuildConfig
import com.yeonsik.fitnessapp.cardio.*
import com.yeonsik.fitnessapp.config.*
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.cardio.model.*
import com.yeonsik.fitnessapp.feature.cardio.ui.*
import com.yeonsik.fitnessapp.feature.development.ui.*
import com.yeonsik.fitnessapp.feature.exercise.ui.*
import com.yeonsik.fitnessapp.feature.home.ui.*
import com.yeonsik.fitnessapp.feature.meal.ui.*
import com.yeonsik.fitnessapp.feature.routine.ui.*
import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitnessapp.feature.supplement.ui.*
import com.yeonsik.fitnessapp.feature.workout.model.*
import com.yeonsik.fitnessapp.feature.workout.ui.*
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.*
import kotlinx.coroutines.delay
import java.time.LocalDate

interface RoutineDetailActions {
    fun back()
    fun rename(routineId: String, name: String)
    fun copy(routineId: String, name: String)
    fun delete(routineId: String)
    fun navigate(screen: FitnessScreen)
    fun startWorkout(
        routineId: String,
        title: String,
        exercises: List<RoutineExerciseInstance>
    )
}

@Composable
internal fun RoutineDetailScreen(
    home: HomeUiState,
    ownerId: String,
    selectedRoutineId: String?,
    actions: RoutineDetailActions
) {
    val ready = home as? HomeUiState.Ready
    AppHeader("루틴", back = { actions.back() })
    if (ready == null || ready.snapshot.ownerId != ownerId) {
        Text("루틴을 불러오는 중입니다.")
        return
    }
    val routineId = selectedRoutineId ?: ready.snapshot.activeRoutineId
    val routine = ready.snapshot.routines.firstOrNull { it.id == routineId }
    var editedName by rememberSaveable(routine?.id) { mutableStateOf(routine?.name.orEmpty()) }
    LaunchedEffect(routine?.id, routine?.name) { editedName = routine?.name.orEmpty() }
    Text(routine?.name ?: "기본 루틴", fontWeight = FontWeight.Bold)
    if (routine != null) {
        AppTextField(
            editedName,
            { editedName = it },
            Modifier.fillMaxWidth(),
            label = { Text("루틴 이름") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
            AppOutlinedButton(
                onClick = {
                    actions.rename(routine.id, editedName)
                },
                modifier = Modifier.weight(1f)
            ) { Text("이름 저장") }
            AppOutlinedButton(
                onClick = {
                    actions.copy(routine.id, "${routine.name} 복사")
                },
                modifier = Modifier.weight(1f)
            ) { Text("복사") }
        }
        AppOutlinedButton(
            onClick = {
                actions.delete(routine.id)
                actions.back()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("루틴 삭제") }
    }
    val exercises = ready.snapshot.routineExercises[routineId].orEmpty()
    exercises.forEach { AppDataRow("${it.order + 1}. ${it.nameKo}", it.equipment) }
    AppOutlinedButton(onClick = { actions.navigate(FitnessScreen.ROUTINE_ADD) }, Modifier.fillMaxWidth()) { Text("종목 추가") }
    AppButton(
        onClick = {
            actions.startWorkout(routine?.id ?: routineId.orEmpty(), routine?.name ?: "운동", exercises)
        },
        enabled = exercises.isNotEmpty(),
        modifier = Modifier.fillMaxWidth()) { Text("이 루틴으로 시작") }
}
