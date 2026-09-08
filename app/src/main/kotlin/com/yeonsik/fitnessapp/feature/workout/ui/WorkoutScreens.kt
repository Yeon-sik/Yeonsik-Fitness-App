package com.yeonsik.fitnessapp.feature.workout.ui

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
internal fun WorkoutOverview(host: ScreenHost, ownerId: String, today: String, unit: MassUnit) {
    val state by host.homeViewModel().uiState.observeAsState(HomeUiState.Idle)
    LaunchedEffect(ownerId, today) { host.homeViewModel().enter(AccountScope(ownerId), today) }
    val ready = state as? HomeUiState.Ready
    if (ready == null || ready.snapshot.ownerId != ownerId) {
        StateMessage("운동", "오늘 기록을 불러오는 중입니다.")
        return
    }
    val snapshot = ready.snapshot
    val metrics = snapshot.dayMetrics[today]
    AppHeader("운동", today)
    FitnessFactRow(
        first = { FitnessFactCard("오늘 볼륨", MassFormatter.withUnit(metrics?.totalVolumeKg ?: 0.0, unit), "완료 세트 기준") },
        second = { FitnessFactCard("식사", "${snapshot.mealCounts[today] ?: 0}끼", "오늘") }
    )
    if (snapshot.inProgressSessionId != null) {
        AppButton(onClick = { host.continueWorkoutIfAvailable() }, Modifier.fillMaxWidth()) {
            Text("진행 중인 운동 이어가기")
        }
    }
    AppButton(onClick = { host.navigate(FitnessScreen.STRENGTH) }, Modifier.fillMaxWidth()) {
        Text("무산소 운동")
    }
    AppOutlinedButton(onClick = { host.navigate(FitnessScreen.CARDIO) }, Modifier.fillMaxWidth()) {
        Text("유산소 운동")
    }
    AppOutlinedButton(onClick = { host.showBodyMetricDialog() }, Modifier.fillMaxWidth()) { Text("체중 기록") }
    AppOutlinedButton(onClick = { host.openMealManagement() }, Modifier.fillMaxWidth()) { Text("식사 기록") }
}

@Composable
internal fun StrengthScreen(host: ScreenHost, ownerId: String) {
    val home by host.homeViewModel().uiState.observeAsState(HomeUiState.Idle)
    val routine by host.routineEntryViewModel().uiState.observeAsState(RoutineEntryUiState.Idle)
    val ready = home as? HomeUiState.Ready
    val routineReady = routine as? RoutineEntryUiState.Ready
    var newRoutineName by rememberSaveable { mutableStateOf("") }
    AppHeader("무산소", "루틴을 선택해 운동을 시작하세요.")
    if (ready == null || ready.snapshot.ownerId != ownerId || routineReady?.ownerId != ownerId) {
        Text("루틴을 불러오는 중입니다.")
        return
    }
    AppTextField(
        newRoutineName,
        { newRoutineName = it },
        Modifier.fillMaxWidth(),
        label = { Text("새 루틴 이름") }
    )
    AppOutlinedButton(
        onClick = {
            host.routineEntryViewModel().createRoutine(AccountScope(ownerId), newRoutineName)
            newRoutineName = ""
        },
        enabled = newRoutineName.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("루틴 만들기") }
    AppButton(onClick = { host.startEmptyWorkout() }, Modifier.fillMaxWidth()) { Text("루틴 없이 운동 시작") }
    AppOutlinedButton(onClick = { host.showPastWorkoutDialog() }, Modifier.fillMaxWidth()) { Text("지난 운동 수동 등록") }
    ready.snapshot.routines.forEach { routineRow ->
        AppCard(Modifier.fillMaxWidth().clickable {
            host.selectRoutine(routineRow.id)
            host.navigate(FitnessScreen.ROUTINE_DETAIL)
        }) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text(routineRow.name, fontWeight = FontWeight.Bold)
                Text("${routineRow.exerciseCount}개 종목", style = MaterialTheme.typography.bodySmall)
                AppButton(onClick = {
                    host.selectRoutine(routineRow.id)
                    host.startRoutineWorkout(ready.snapshot.routineExercises[routineRow.id].orEmpty())
                }) { Text("이 루틴으로 시작") }
            }
        }
    }
}

@Composable
internal fun WorkoutSessionScreen(host: ScreenHost, ownerId: String, unit: MassUnit) {
    val state by host.workoutSessionViewModel().uiState.observeAsState(WorkoutSessionUiState.Idle)
    val ready = state as? WorkoutSessionUiState.Ready
    if (ready == null || ready.ownerId != ownerId) {
        StateMessage("운동 진행", "운동을 불러오는 중입니다.")
        return
    }
    // Back/add/finish stay in the existing fixed Activity bars, with their original callbacks.
    AppWorkoutSessionContent(ready.session, unit) { host.openWorkoutExerciseDetail(it) }
}

@Composable
internal fun WorkoutDetailScreen(host: ScreenHost, ownerId: String, unit: MassUnit) {
    val drafts = rememberSaveableStateHolder()
    val state by host.workoutExerciseDetailViewModel().uiState.observeAsState(WorkoutExerciseDetailUiState.Idle)
    val ready = state as? WorkoutExerciseDetailUiState.Ready
    AppHeader(ready?.detail?.activeExercise?.name ?: "운동 종목", back = { host.back() })
    if (ready == null || ready.ownerId != ownerId) {
        Text("세트를 불러오는 중입니다.")
        return
    }
    val detail = ready.detail
    detail.sets.forEach { set ->
        drafts.SaveableStateProvider("$ownerId:${detail.recordId}:${set.id}") {
            WorkoutSetEditor(host, ownerId, detail.recordId, detail.activeExercise.recordType, detail.allowedLoadStates[detail.activeExercise.recordType].orEmpty(), set, unit)
        }
    }
    AppOutlinedButton(onClick = {
        val next = (detail.sets.maxOfOrNull { it.setIndex } ?: 0) + 1
        host.workoutExerciseDetailViewModel().addTypedSet(
            AccountScope(ownerId), detail.recordId, detail.activeExercise.id, next,
            WorkoutSetInput(null, null, null, null, null, null, null, 90, false, null, null, unit)
        ) { ok -> if (ok) host.refreshWorkoutExerciseDetail() else host.toast("세트를 추가하지 못했습니다.") }
    }, Modifier.fillMaxWidth()) { Text("세트 추가") }
    AppOutlinedButton(onClick = { host.openWorkoutExerciseReplacementPicker(detail.activeExercise.id) }, Modifier.fillMaxWidth()) { Text("종목 교체") }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun WorkoutSetEditor(
    host: ScreenHost,
    ownerId: String,
    recordId: String,
    rawRecordType: String,
    allowedLoadStates: List<com.yeonsik.fitnessapp.exercise.LoadState>,
    set: WorkoutSet,
    unit: MassUnit
) {
    val recordType = FitnessRecordContract.normalizeRecordType(rawRecordType)
    var weight by rememberSaveable(set.id) { mutableStateOf(MassFormatter.formatInput(set.weightKg, unit)) }
    var reps by rememberSaveable(set.id) { mutableStateOf(set.actualReps.takeIf { it > 0 }?.toString().orEmpty()) }
    var duration by rememberSaveable(set.id) { mutableStateOf(set.durationSeconds.takeIf { it > 0 }?.toString().orEmpty()) }
    var assisted by rememberSaveable(set.id) { mutableStateOf(MassFormatter.formatInput(set.assistedWeightKg, unit)) }
    var added by rememberSaveable(set.id) { mutableStateOf(MassFormatter.formatInput(set.addedWeightKg, unit)) }
    var rir by rememberSaveable(set.id) { mutableStateOf(set.rir?.toString().orEmpty()) }
    var rest by rememberSaveable(set.id) { mutableStateOf(set.restSeconds?.toString().orEmpty()) }
    var loadState by rememberSaveable(set.id) { mutableStateOf(set.loadState) }
    var completed by rememberSaveable(set.id) { mutableStateOf(set.isCompleted) }
    val focus = LocalFocusManager.current
    val decimalOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    val numberOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
    val nextAction = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.card), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text("${set.setIndex}세트", fontWeight = FontWeight.Bold)
            if (allowedLoadStates.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    allowedLoadStates.forEach { state ->
                        AppOutlinedButton(onClick = { loadState = state }, selected = loadState == state) {
                            Text(state.id())
                        }
                    }
                }
            }
            when (recordType) {
                FitnessRecordContract.REPS_ONLY -> AppTextField(reps, { reps = it }, Modifier.fillMaxWidth(), { Text("횟수") }, keyboardOptions = numberOptions, keyboardActions = nextAction)
                FitnessRecordContract.TIME -> AppTextField(duration, { duration = it }, Modifier.fillMaxWidth(), { Text("시간 초") }, keyboardOptions = numberOptions, keyboardActions = nextAction)
                FitnessRecordContract.WEIGHT_TIME -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                    AppTextField(weight, { weight = it }, Modifier.weight(1f), { Text("중량 ${unit.symbol()}") }, keyboardOptions = decimalOptions, keyboardActions = nextAction)
                    AppTextField(duration, { duration = it }, Modifier.weight(1f), { Text("시간 초") }, keyboardOptions = numberOptions, keyboardActions = nextAction)
                }
                FitnessRecordContract.ASSISTED_WEIGHT_REPS -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                    AppTextField(assisted, { assisted = it }, Modifier.weight(1f), { Text("보조 중량 ${unit.symbol()}") }, keyboardOptions = decimalOptions, keyboardActions = nextAction)
                    AppTextField(reps, { reps = it }, Modifier.weight(1f), { Text("횟수") }, keyboardOptions = numberOptions, keyboardActions = nextAction)
                }
                FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                    AppTextField(added, { added = it }, Modifier.weight(1f), { Text("추가 중량 ${unit.symbol()}") }, keyboardOptions = decimalOptions, keyboardActions = nextAction)
                    AppTextField(reps, { reps = it }, Modifier.weight(1f), { Text("횟수") }, keyboardOptions = numberOptions, keyboardActions = nextAction)
                }
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                    AppTextField(weight, { weight = it }, Modifier.weight(1f), { Text("중량 ${unit.symbol()}") }, keyboardOptions = decimalOptions, keyboardActions = nextAction)
                    AppTextField(reps, { reps = it }, Modifier.weight(1f), { Text("횟수") }, keyboardOptions = numberOptions, keyboardActions = nextAction)
                }
            }
            if (FitnessRecordContract.supportsRir(recordType)) {
                AppTextField(rir, { rir = it }, Modifier.fillMaxWidth(), { Text("RIR") }, keyboardOptions = numberOptions, keyboardActions = nextAction)
            }
            AppTextField(rest, { rest = it }, Modifier.fillMaxWidth(), { Text("휴식 초") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
            Row(Modifier.fillMaxWidth().heightIn(min = AppSpacing.touch)
                .toggleable(completed, role = Role.Checkbox, onValueChange = { completed = it }),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(completed, onCheckedChange = null)
                Text("완료", Modifier.padding(start = AppSpacing.small),
                    color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                AppButton(onClick = {
                    val enteredWeight = weight.toDoubleOrNull()
                    val enteredAssisted = assisted.toDoubleOrNull()
                    val enteredAdded = added.toDoubleOrNull()
                    val input = WorkoutSetInput(
                        if (recordType == FitnessRecordContract.WEIGHT_REPS || recordType == FitnessRecordContract.WEIGHT_TIME) enteredWeight?.let { MassUnit.toKg(it, unit) } else set.weightKg.takeIf { it > 0.0 },
                        if (recordType == FitnessRecordContract.TIME || recordType == FitnessRecordContract.WEIGHT_TIME) set.actualReps.takeIf { it > 0 } else reps.toIntOrNull(),
                        if (recordType == FitnessRecordContract.TIME || recordType == FitnessRecordContract.WEIGHT_TIME) duration.toIntOrNull() else set.durationSeconds.takeIf { it > 0 },
                        set.distanceMeters.takeIf { it > 0.0 },
                        if (recordType == FitnessRecordContract.ASSISTED_WEIGHT_REPS) enteredAssisted?.let { MassUnit.toKg(it, unit) } else set.assistedWeightKg.takeIf { it > 0.0 },
                        if (recordType == FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS) enteredAdded?.let { MassUnit.toKg(it, unit) } else set.addedWeightKg.takeIf { it > 0.0 },
                        if (FitnessRecordContract.supportsRir(recordType)) rir.toIntOrNull() else set.rir,
                        rest.toIntOrNull(), completed, loadState,
                        when (recordType) {
                            FitnessRecordContract.ASSISTED_WEIGHT_REPS -> enteredAssisted
                            FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS -> enteredAdded
                            FitnessRecordContract.WEIGHT_REPS, FitnessRecordContract.WEIGHT_TIME -> enteredWeight
                            else -> set.inputLoadValue
                        },
                        unit
                    )
                    host.workoutExerciseDetailViewModel().updateTypedSet(AccountScope(ownerId), recordId, set.id, input) { ok ->
                        if (ok) {
                            if (completed) host.startRestTimer(rest.toIntOrNull())
                            host.refreshWorkoutExerciseDetail()
                        } else host.toast("세트를 저장하지 못했습니다.")
                    }
                }) { Text("저장") }
                AppOutlinedButton(destructive = true, onClick = {
                    host.workoutExerciseDetailViewModel().deleteSet(AccountScope(ownerId), recordId, set.id) {
                        if (it) host.refreshWorkoutExerciseDetail() else host.toast("세트를 삭제하지 못했습니다.")
                    }
                }) { Text("삭제") }
            }
        }
    }
}

@Composable
internal fun WorkoutSummaryScreen(host: ScreenHost, ownerId: String, unit: MassUnit) {
    val state by host.workoutSessionViewModel().uiState.observeAsState(WorkoutSessionUiState.Idle)
    val ready = state as? WorkoutSessionUiState.Ready
    AppHeader("운동 요약", back = { host.back() })
    if (ready == null || ready.ownerId != ownerId) {
        Text("요약을 불러오는 중입니다.")
        return
    }
    Text(ready.session.title, fontWeight = FontWeight.Bold)
    FitnessFactRow(
        first = { FitnessFactCard("완료 세트", ready.session.completedSetCount.toString(), "완료 기록") },
        second = { FitnessFactCard("총 볼륨", MassFormatter.withUnit(ready.session.totalVolumeKg, unit), "완료 기록") }
    )
    FitnessSection("운동 종목") {
        ready.session.exercises.forEach { AppDataRow(it.name, "${it.completedSetCount}/${it.totalSetCount} 세트") }
    }
}

@Composable
private fun AppWorkoutSessionContent(
    session: com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionSnapshot,
    unit: MassUnit,
    onExercise: (String) -> Unit
) {
    AppHeader("운동 진행", session.title)
    FitnessFactRow(
        first = { FitnessFactCard("완료 세트", session.completedSetCount.toString(), "현재 운동") },
        second = { FitnessFactCard("볼륨", MassFormatter.withUnit(session.totalVolumeKg, unit), "현재 운동") }
    )
    FitnessSection("운동 종목") {
        if (session.exercises.isEmpty()) AppCard(Modifier.fillMaxWidth()) {
            Text("종목을 추가해 운동을 기록하세요.", Modifier.padding(AppSpacing.card))
        }
        session.exercises.forEach { exercise ->
            AppCard(Modifier.fillMaxWidth().clickable { onExercise(exercise.id) }) {
                Row(Modifier.padding(AppSpacing.card), horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text(exercise.recordTypeLabel, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${exercise.completedSetCount}/${exercise.totalSetCount}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
