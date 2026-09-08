package com.yeonsik.fitnessapp.feature.routine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
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
import com.yeonsik.fitnessapp.app.navigation.*
import com.yeonsik.fitnessapp.cardio.*
import com.yeonsik.fitnessapp.config.*
import com.yeonsik.fitnessapp.core.account.*
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.cardio.model.*
import com.yeonsik.fitnessapp.feature.cardio.ui.*
import com.yeonsik.fitnessapp.feature.development.ui.*
import com.yeonsik.fitnessapp.feature.exercise.ui.*
import com.yeonsik.fitnessapp.feature.home.ui.*
import com.yeonsik.fitnessapp.feature.meal.ui.*
import com.yeonsik.fitnessapp.feature.routine.ui.*
import com.yeonsik.fitnessapp.feature.supplement.ui.*
import com.yeonsik.fitnessapp.feature.workout.model.*
import com.yeonsik.fitnessapp.feature.workout.ui.*
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.*
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
internal fun RoutineDetailScreen(host: ScreenHost, ownerId: String) {
    val home by host.homeViewModel().uiState.observeAsState(HomeUiState.Idle)
    val routineState by host.routineEntryViewModel().uiState.observeAsState(RoutineEntryUiState.Idle)
    val ready = home as? HomeUiState.Ready
    AppHeader("루틴", back = { host.back() })
    if (ready == null || ready.snapshot.ownerId != ownerId) {
        Text("루틴을 불러오는 중입니다.")
        return
    }
    val routineId = host.selectedRoutineId() ?: ready.snapshot.activeRoutineId
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
                    host.routineEntryViewModel().renameRoutine(
                        AccountScope(ownerId), routine.id, editedName
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("이름 저장") }
            AppOutlinedButton(
                onClick = {
                    host.routineEntryViewModel().copyRoutine(
                        AccountScope(ownerId), routine.id, "${routine.name} 복사"
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("복사") }
        }
        AppOutlinedButton(
            onClick = {
                host.routineEntryViewModel().deleteRoutine(AccountScope(ownerId), routine.id)
                host.back()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("루틴 삭제") }
    }
    val exercises = ready.snapshot.routineExercises[routineId].orEmpty()
    exercises.forEach { AppDataRow("${it.order + 1}. ${it.nameKo}", it.equipment) }
    AppOutlinedButton(onClick = { host.navigate(FitnessScreen.ROUTINE_ADD) }, Modifier.fillMaxWidth()) { Text("종목 추가") }
    AppButton(onClick = { host.startRoutineWorkout(exercises) }, enabled = exercises.isNotEmpty(),
        modifier = Modifier.fillMaxWidth()) { Text("이 루틴으로 시작") }
}
