package com.yeonsik.fitnessapp.feature.workout.ui

import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.feature.home.ui.*
import com.yeonsik.fitnessapp.feature.routine.ui.*
import com.yeonsik.fitnessapp.data.MassUnit
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.feature.workout.model.*
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyIdentity
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.WorkoutSetPresentation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun WorkoutOverview(
    state: HomeUiState,
    ownerId: String,
    today: String,
    unit: MassUnit,
    actions: WorkoutOverviewActions
) {
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
        AppButton(onClick = actions::continueWorkout, Modifier.fillMaxWidth()) {
            Text("진행 중인 운동 이어가기")
        }
    }
    AppButton(onClick = { actions.navigate(FitnessScreen.STRENGTH) }, Modifier.fillMaxWidth()) {
        Text("무산소 운동")
    }
    AppOutlinedButton(onClick = { actions.navigate(FitnessScreen.CARDIO) }, Modifier.fillMaxWidth()) {
        Text("유산소 운동")
    }
    AppOutlinedButton(onClick = actions::showBodyMetric, Modifier.fillMaxWidth()) { Text("체중 기록") }
    AppOutlinedButton(onClick = actions::openMeals, Modifier.fillMaxWidth()) { Text("식사 기록") }
}

@Composable
internal fun StrengthScreen(
    home: HomeUiState,
    routine: RoutineEntryUiState,
    ownerId: String,
    actions: StrengthActions
) {
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
            actions.createRoutine(newRoutineName)
            newRoutineName = ""
        },
        enabled = newRoutineName.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("루틴 만들기") }
    AppButton(onClick = actions::startEmptyWorkout, Modifier.fillMaxWidth()) { Text("루틴 없이 운동 시작") }
    AppOutlinedButton(onClick = actions::showPastWorkout, Modifier.fillMaxWidth()) { Text("지난 운동 수동 등록") }
    ready.snapshot.routines.forEach { routineRow ->
        AppCard(Modifier.fillMaxWidth().clickable {
            actions.selectRoutine(routineRow.id)
            actions.navigate(FitnessScreen.ROUTINE_DETAIL)
        }) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text(routineRow.name, fontWeight = FontWeight.Bold)
                Text("${routineRow.exerciseCount}개 종목", style = MaterialTheme.typography.bodySmall)
                AppButton(onClick = {
                    actions.selectRoutine(routineRow.id)
                    actions.startRoutineWorkout(
                        routineRow.id,
                        routineRow.name,
                        ready.snapshot.routineExercises[routineRow.id].orEmpty()
                    )
                }) { Text("이 루틴으로 시작") }
            }
        }
    }
}

@Composable
internal fun WorkoutSessionScreen(
    state: WorkoutSessionUiState,
    ownerId: String,
    unit: MassUnit,
    onExercise: (String) -> Unit
) {
    val ready = state as? WorkoutSessionUiState.Ready
    if (ready == null || ready.ownerId != ownerId) {
        FitnessStatusMessage(
            status = when (state) {
                is WorkoutSessionUiState.Error -> FitnessSemanticStatus.ERROR
                is WorkoutSessionUiState.Missing -> FitnessSemanticStatus.WARNING
                else -> FitnessSemanticStatus.INFO
            },
            title = "운동 진행",
            message = when (state) {
                is WorkoutSessionUiState.Error -> state.message
                is WorkoutSessionUiState.Missing -> "운동 기록을 찾지 못했습니다."
                else -> "운동을 불러오는 중입니다."
            }
        )
        return
    }
    AppWorkoutSessionContent(ready.session, unit, onExercise)
}

@Composable
internal fun WorkoutDetailScreen(
    state: WorkoutExerciseDetailUiState,
    ownerId: String,
    unit: MassUnit,
    actions: WorkoutDetailActions
) {
    val drafts = rememberSaveableStateHolder()
    val ready = state as? WorkoutExerciseDetailUiState.Ready
    FitnessHeader(ready?.detail?.activeExercise?.name ?: "운동 종목", back = actions::back)
    if (ready == null || ready.ownerId != ownerId) {
        FitnessStatusMessage(
            status = when (state) {
                is WorkoutExerciseDetailUiState.Error -> FitnessSemanticStatus.ERROR
                is WorkoutExerciseDetailUiState.Missing -> FitnessSemanticStatus.WARNING
                else -> FitnessSemanticStatus.INFO
            },
            title = "운동 세트",
            message = when (state) {
                is WorkoutExerciseDetailUiState.Error -> state.message
                is WorkoutExerciseDetailUiState.Missing -> "운동 종목을 찾지 못했습니다."
                else -> "세트를 불러오는 중입니다."
            }
        )
        return
    }
    val detail = ready.detail
    val orderedExercises = stableWorkoutExercises(detail.exercises)
    val activeIndex = orderedExercises.indexOfFirst { it.id == detail.activeExercise.id }
    WorkoutExerciseImage(
        exerciseId = detail.activeExercise.exerciseId,
        identity = detail.activeExercise.familyIdentity,
        contentDescription = "${detail.activeExercise.name} 운동 이미지",
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 180.dp)
    )
    Text(
        workoutExerciseMetadata(detail.activeExercise),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    WorkoutExerciseNavigation(orderedExercises, activeIndex, actions::openExercise)
    FitnessStatusBadge(
        status = if (ready.readOnly) FitnessSemanticStatus.SUCCESS else FitnessSemanticStatus.INFO,
        label = if (ready.readOnly) "완료된 기록 · 읽기 전용" else "진행 중 · 입력 가능",
        modifier = Modifier.fillMaxWidth()
    )
    detail.lastHistory?.let { history ->
        WorkoutPreviousHistory(detail, history, unit, ready.readOnly, actions)
    }
    if (detail.recentVolumes.isNotEmpty()) {
        FitnessSection("최근 종목 볼륨") {
            FitnessTrendChart(
                model = fitnessTrendPresentation(
                    detail.recentVolumes.map {
                        FitnessTrendPoint(
                            label = it.label.ifBlank { it.date },
                            value = MassUnit.fromKg(it.volumeKg, unit)
                        )
                    },
                    minimumPoints = 2
                ),
                unit = unit.symbol(),
                emptyLabel = "최근 종목 기록이 없습니다.",
                insufficientLabel = "최근 종목 기록이 부족합니다."
            )
        }
    }
    if (detail.bests.isNotEmpty()) {
        FitnessSection("개인 최고 기록") {
            detail.bests.forEach { best ->
                FitnessDataRow(
                    title = "${best.loadState?.id() ?: "상태 미상"} · ${best.maxWeightDate.ifBlank { "날짜 미상" }}",
                    detail = "최고 ${MassFormatter.withUnit(best.maxWeightKg, unit)} · ${best.repsAtMaxWeight}회 · " +
                        "E1RM ${MassFormatter.withUnit(best.bestE1rmKg, unit)} · " +
                        "최고 볼륨 ${MassFormatter.withUnit(best.bestSessionVolumeKg, unit)}"
                )
            }
        }
    }
    if (ready.readOnly) {
        WorkoutReadOnlyDetail(detail, unit)
        return
    }
    detail.sets.forEach { set ->
        drafts.SaveableStateProvider("$ownerId:${detail.recordId}:${set.id}") {
            WorkoutSetEditor(
                actions,
                ownerId,
                detail.recordId,
                detail.activeExercise.recordType,
                detail.allowedLoadStates[detail.activeExercise.id].orEmpty(),
                set,
                unit
            )
        }
    }
    FitnessOutlinedButton(onClick = {
        val next = (detail.sets.maxOfOrNull { it.setIndex } ?: 0) + 1
        actions.addSet(
            detail.recordId,
            detail.activeExercise.id,
            next,
            WorkoutSetInput(null, null, null, null, null, null, null, 90, false, null, null, unit)
        ) { ok -> if (ok) actions.refresh() else actions.toast("세트를 추가하지 못했습니다.") }
    }, Modifier.fillMaxWidth()) { Text("세트 추가") }
    var confirmDelete by rememberSaveable(detail.recordId, detail.activeExercise.id) {
        mutableStateOf(false)
    }
    FitnessOutlinedButton(
        onClick = { actions.replaceExercise(detail.activeExercise.id) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("종목 교체") }
    FitnessOutlinedButton(
        onClick = { confirmDelete = true },
        modifier = Modifier.fillMaxWidth(),
        destructive = true
    ) { Text("종목 삭제") }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("종목을 삭제할까요?") },
            text = { Text("이 종목의 세트 기록도 현재 세션에서 함께 삭제됩니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.deleteExercise(detail.recordId, detail.activeExercise.id) { ok ->
                        if (ok) actions.back() else actions.toast("종목을 삭제하지 못했습니다.")
                    }
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun WorkoutReadOnlyDetail(
    detail: WorkoutExerciseDetail,
    unit: MassUnit
) {
    val completedSets = detail.sets.filter { it.isCompleted }
    FitnessSection("완료 세트") {
        if (completedSets.isEmpty()) {
            FitnessStatusMessage(
                status = FitnessSemanticStatus.UNKNOWN,
                title = "완료 세트 없음",
                message = "이 기록에는 완료된 세트가 없습니다."
            )
        } else {
            completedSets.forEach { set ->
                FitnessCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(FitnessSpacing.card),
                        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
                    ) {
                        Text("${set.setIndex}세트", fontWeight = FontWeight.Bold)
                        Text(
                            WorkoutSetPresentation.completedSetSummary(
                                detail.activeExercise.recordType,
                                set.weightKg,
                                set.actualReps,
                                set.durationSeconds,
                                set.assistedWeightKg,
                                set.addedWeightKg,
                                set.loadState,
                                unit
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                        detail.volumeBySetId[set.id]?.takeIf { it.isFinite() }?.let { volume ->
                            Text("볼륨 ${MassFormatter.withUnit(volume, unit)}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutPreviousHistory(
    detail: WorkoutExerciseDetail,
    history: WorkoutExerciseHistory,
    unit: MassUnit,
    readOnly: Boolean,
    actions: WorkoutDetailActions
) {
    FitnessSection("지난 운동 기록") {
        FitnessCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(FitnessSpacing.card),
                verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
            ) {
                Text(history.date.ifBlank { "날짜 미상" }, style = MaterialTheme.typography.titleMedium)
                Text("총 볼륨 ${MassFormatter.withUnit(history.totalVolumeKg, unit)}")
                history.sets.forEach { set ->
                    Text(
                        "${set.setIndex}세트 · " + WorkoutSetPresentation.completedSetSummary(
                            detail.activeExercise.recordType,
                            set.weightKg,
                            set.actualReps,
                            set.durationSeconds,
                            set.assistedWeightKg,
                            set.addedWeightKg,
                            set.loadState,
                            unit
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!readOnly) {
                    FitnessOutlinedButton(
                        onClick = {
                            actions.applyPreviousHistory(
                                detail.recordId,
                                detail.activeExercise.id,
                                detail.sets,
                                history
                            ) { ok ->
                                if (ok) actions.refresh()
                                else actions.toast("지난 기록을 적용하지 못했습니다.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("지난 기록 적용") }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun WorkoutExerciseNavigation(
    exercises: List<WorkoutExercise>,
    activeIndex: Int,
    onExercise: (String) -> Unit
) {
    if (activeIndex < 0 || exercises.size < 2) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
    ) {
        FitnessOutlinedButton(
            onClick = { onExercise(exercises[activeIndex - 1].id) },
            enabled = activeIndex > 0
        ) { Text("이전 종목") }
        FitnessOutlinedButton(
            onClick = { onExercise(exercises[activeIndex + 1].id) },
            enabled = activeIndex < exercises.lastIndex
        ) { Text("다음 종목") }
    }
}

@Composable
private fun WorkoutExerciseImage(
    exerciseId: String,
    identity: ExerciseFamilyIdentity?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current as? Activity
    if (activity == null) {
        FitnessStatusBadge(
            status = FitnessSemanticStatus.UNKNOWN,
            label = "운동 이미지 없음",
            modifier = modifier
        )
        return
    }
    if (identity != null) {
        FitnessExerciseIllustration(
            activity = activity,
            identity = identity,
            exactVariant = true,
            modifier = modifier,
            contentDescription = contentDescription,
            fallback = {
                FitnessExerciseIllustration(
                    activity = activity,
                    identity = identity,
                    modifier = modifier,
                    contentDescription = contentDescription,
                    fallback = {
                        FitnessStatusBadge(
                            status = FitnessSemanticStatus.UNKNOWN,
                            label = "운동 이미지 없음",
                            modifier = modifier
                        )
                    }
                )
            }
        )
    } else {
        FitnessExerciseIllustration(
            activity = activity,
            exerciseId = exerciseId,
            modifier = modifier,
            contentDescription = contentDescription,
            fallback = {
                FitnessStatusBadge(
                    status = FitnessSemanticStatus.UNKNOWN,
                    label = "운동 이미지 없음",
                    modifier = modifier
                )
            }
        )
    }
}

private fun workoutExerciseMetadata(exercise: WorkoutExercise): String = listOfNotNull(
    exercise.uiPart.takeIf { it.isNotBlank() },
    exercise.equipment.takeIf { it.isNotBlank() },
    FitnessRecordContract.displayRecordTypeKo(exercise.recordType)
).joinToString(" · ")

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun WorkoutSetEditor(
    actions: WorkoutDetailActions,
    ownerId: String,
    recordId: String,
    rawRecordType: String,
    allowedLoadStates: List<com.yeonsik.fitnessapp.exercise.LoadState>,
    set: WorkoutSet,
    unit: MassUnit
) {
    val recordType = FitnessRecordContract.normalizeRecordType(rawRecordType)
    val inputUnit = editableMassUnit(set.inputLoadUnit, unit)
    val initialLoad = initialMassInputValue(
        recordType,
        set.weightKg,
        set.assistedWeightKg,
        set.addedWeightKg,
        set.inputLoadValue,
        set.inputLoadUnit,
        inputUnit
    )
    var weight by rememberSaveable(set.id, inputUnit) { mutableStateOf(initialLoad) }
    var reps by rememberSaveable(set.id) { mutableStateOf(set.actualReps.takeIf { it > 0 }?.toString().orEmpty()) }
    var duration by rememberSaveable(set.id) { mutableStateOf(set.durationSeconds.takeIf { it > 0 }?.toString().orEmpty()) }
    var assisted by rememberSaveable(set.id, inputUnit) { mutableStateOf(initialLoad) }
    var added by rememberSaveable(set.id, inputUnit) { mutableStateOf(initialLoad) }
    var rir by rememberSaveable(set.id) { mutableStateOf(set.rir?.toString().orEmpty()) }
    var rest by rememberSaveable(set.id) { mutableStateOf(set.restSeconds?.toString().orEmpty()) }
    var loadState by rememberSaveable(set.id) { mutableStateOf(set.loadState) }
    var completed by rememberSaveable(set.id) { mutableStateOf(set.isCompleted) }
    val focus = LocalFocusManager.current
    val decimalOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    val numberOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
    val nextAction = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
    val showMassInput = recordType != FitnessRecordContract.REPS_ONLY &&
        recordType != FitnessRecordContract.TIME &&
        loadState != com.yeonsik.fitnessapp.exercise.LoadState.BODYWEIGHT
    val massLabel = when {
        loadState == com.yeonsik.fitnessapp.exercise.LoadState.ADDED_WEIGHT -> "추가 중량 ${inputUnit.symbol()}"
        loadState == com.yeonsik.fitnessapp.exercise.LoadState.ASSISTED ||
            loadState == com.yeonsik.fitnessapp.exercise.LoadState.BAND_ASSISTED -> "보조 중량 ${inputUnit.symbol()}"
        else -> "중량 ${inputUnit.symbol()}"
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.card), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text("${set.setIndex}세트", fontWeight = FontWeight.Bold)
            if (set.inputLoadUnit != null) {
                Text(
                    "저장 당시 입력 단위: ${set.inputLoadUnit.symbol()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                FitnessRecordContract.WEIGHT_TIME -> WorkoutInputPair(
                    first = {
                        if (showMassInput) AppTextField(weight, { weight = it }, Modifier.fillMaxWidth(), { Text(massLabel) }, keyboardOptions = decimalOptions, keyboardActions = nextAction)
                    },
                    second = { AppTextField(duration, { duration = it }, Modifier.fillMaxWidth(), { Text("시간 초") }, keyboardOptions = numberOptions, keyboardActions = nextAction) }
                )
                FitnessRecordContract.ASSISTED_WEIGHT_REPS -> WorkoutInputPair(
                    first = { AppTextField(assisted, { assisted = it }, Modifier.fillMaxWidth(), { Text("보조 중량 ${inputUnit.symbol()}") }, keyboardOptions = decimalOptions, keyboardActions = nextAction) },
                    second = { AppTextField(reps, { reps = it }, Modifier.fillMaxWidth(), { Text("횟수") }, keyboardOptions = numberOptions, keyboardActions = nextAction) }
                )
                FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS -> WorkoutInputPair(
                    first = {
                        if (showMassInput) AppTextField(added, { added = it }, Modifier.fillMaxWidth(), { Text("추가 중량 ${inputUnit.symbol()}") }, keyboardOptions = decimalOptions, keyboardActions = nextAction)
                        else Text("체중", style = MaterialTheme.typography.bodyLarge)
                    },
                    second = { AppTextField(reps, { reps = it }, Modifier.fillMaxWidth(), { Text("횟수") }, keyboardOptions = numberOptions, keyboardActions = nextAction) }
                )
                else -> WorkoutInputPair(
                    first = {
                        if (showMassInput) AppTextField(weight, { weight = it }, Modifier.fillMaxWidth(), { Text(massLabel) }, keyboardOptions = decimalOptions, keyboardActions = nextAction)
                        else Text("체중", style = MaterialTheme.typography.bodyLarge)
                    },
                    second = { AppTextField(reps, { reps = it }, Modifier.fillMaxWidth(), { Text("횟수") }, keyboardOptions = numberOptions, keyboardActions = nextAction) }
                )
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                AppButton(onClick = {
                    val enteredWeight = weight.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                    val enteredAssisted = assisted.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                    val enteredAdded = added.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                    val selectedState = loadState
                    val enteredLoad = when {
                        selectedState == com.yeonsik.fitnessapp.exercise.LoadState.ADDED_WEIGHT ||
                            (recordType == FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS && selectedState == null) -> enteredAdded
                        selectedState == com.yeonsik.fitnessapp.exercise.LoadState.ASSISTED ||
                            selectedState == com.yeonsik.fitnessapp.exercise.LoadState.BAND_ASSISTED ||
                            recordType == FitnessRecordContract.ASSISTED_WEIGHT_REPS -> enteredAssisted
                        selectedState == com.yeonsik.fitnessapp.exercise.LoadState.EXTERNAL_LOAD ||
                            selectedState == com.yeonsik.fitnessapp.exercise.LoadState.BAND_RESISTED ||
                            recordType == FitnessRecordContract.WEIGHT_REPS ||
                            recordType == FitnessRecordContract.WEIGHT_TIME -> enteredWeight
                        else -> null
                    }
                    val canonicalWeight = when {
                        selectedState == com.yeonsik.fitnessapp.exercise.LoadState.EXTERNAL_LOAD ||
                            selectedState == com.yeonsik.fitnessapp.exercise.LoadState.BAND_RESISTED ||
                            recordType == FitnessRecordContract.WEIGHT_REPS ||
                            recordType == FitnessRecordContract.WEIGHT_TIME -> enteredWeight?.let { MassUnit.toKg(it, inputUnit) }
                        else -> null
                    }
                    val canonicalAssisted = when {
                        selectedState == com.yeonsik.fitnessapp.exercise.LoadState.ASSISTED ||
                            selectedState == com.yeonsik.fitnessapp.exercise.LoadState.BAND_ASSISTED ||
                            recordType == FitnessRecordContract.ASSISTED_WEIGHT_REPS -> enteredAssisted?.let { MassUnit.toKg(it, inputUnit) }
                        else -> null
                    }
                    val canonicalAdded = when {
                        selectedState == com.yeonsik.fitnessapp.exercise.LoadState.ADDED_WEIGHT ||
                            recordType == FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS -> enteredAdded?.let { MassUnit.toKg(it, inputUnit) }
                        else -> null
                    }
                    val input = WorkoutSetInput(
                        canonicalWeight,
                        if (recordType == FitnessRecordContract.TIME || recordType == FitnessRecordContract.WEIGHT_TIME) set.actualReps.takeIf { it > 0 } else reps.toIntOrNull(),
                        if (recordType == FitnessRecordContract.TIME || recordType == FitnessRecordContract.WEIGHT_TIME) duration.toIntOrNull() else set.durationSeconds.takeIf { it > 0 },
                        set.distanceMeters.takeIf { it > 0.0 },
                        canonicalAssisted,
                        canonicalAdded,
                        if (FitnessRecordContract.supportsRir(recordType)) rir.toIntOrNull() else set.rir,
                        rest.toIntOrNull(), completed, loadState,
                        enteredLoad,
                        enteredLoad?.let { inputUnit }
                    )
                    actions.updateSet(recordId, set.id, input) { ok ->
                        if (ok) {
                            if (completed) actions.startRestTimer(rest.toIntOrNull())
                            actions.refresh()
                        } else actions.toast("세트를 저장하지 못했습니다.")
                    }
                }) { Text("저장") }
                AppOutlinedButton(destructive = true, onClick = {
                    actions.deleteSet(recordId, set.id) {
                        if (it) actions.refresh() else actions.toast("세트를 삭제하지 못했습니다.")
                    }
                }) { Text("삭제") }
            }
        }
    }
}

@Composable
private fun WorkoutInputPair(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit
) {
    if (LocalDensity.current.fontScale >= 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
            first()
            second()
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.weight(1f)) { first() }
            Box(Modifier.weight(1f)) { second() }
        }
    }
}

@Composable
internal fun WorkoutSummaryScreen(
    state: WorkoutSessionUiState,
    ownerId: String,
    unit: MassUnit,
    onBack: () -> Unit,
    onExercise: (String) -> Unit,
    onRecords: () -> Unit = {},
    onSaveAsRoutine: (String, List<WorkoutSessionExercise>) -> Unit = { _, _ -> }
) {
    val ready = state as? WorkoutSessionUiState.Ready
    AppHeader("운동 요약", back = onBack)
    if (ready == null || ready.ownerId != ownerId) {
        Text("요약을 불러오는 중입니다.")
        return
    }

    val session = ready.session
    if (session.status != "completed") {
        FitnessStatusMessage(
            status = FitnessSemanticStatus.WARNING,
            title = "완료된 운동만 요약할 수 있습니다",
            message = "진행 중이거나 상태를 확인할 수 없는 기록은 저장된 완료 요약으로 표시하지 않습니다."
        )
        return
    }

    val orderedExercises = stableWorkoutSessionExercises(session.exercises)
    val activity = LocalContext.current as? Activity
    var routineName by rememberSaveable(session.recordId) {
        mutableStateOf("${session.title} 루틴")
    }

    Text(session.title, fontWeight = FontWeight.Bold)
    FitnessStatusBadge(
        status = FitnessSemanticStatus.SUCCESS,
        label = "완료된 운동 요약",
        modifier = Modifier.fillMaxWidth()
    )
    FitnessFactRow(
        first = { FitnessFactCard("완료 세트", session.completedSetCount.toString(), "저장된 세트") },
        second = { FitnessFactCard("총 볼륨", MassFormatter.withUnit(session.totalVolumeKg, unit), "저장된 완료 기록") }
    )

    FitnessFactRow(
        first = { FitnessFactCard("운동 종목", orderedExercises.size.toString(), "저장된 snapshot") },
        second = { FitnessFactCard("운동 시간", formatWorkoutElapsedSeconds(session.durationSeconds), "완료 시각 기준") }
    )

    FitnessSection("근육/부위 분포") {
        val distribution = workoutSummaryMuscleDistribution(orderedExercises)
        if (distribution.isEmpty()) {
            FitnessStatusMessage(
                status = FitnessSemanticStatus.UNKNOWN,
                title = "분포 데이터 없음",
                message = "완료된 세트에 연결된 운동 부위 정보가 없습니다."
            )
        } else {
            distribution.forEach { group ->
                FitnessProgressBar(
                    fitnessProgressPresentation(
                        ratio = group.fraction.toDouble(),
                        label = "${group.completedSetCount}세트"
                    ),
                    title = group.label
                )
            }
        }
    }

    FitnessSection("운동 종목") {
        if (orderedExercises.isEmpty()) {
            FitnessStatusMessage(
                status = FitnessSemanticStatus.UNKNOWN,
                title = "운동 종목 없음",
                message = "저장된 운동 snapshot에 종목이 없습니다."
            )
        } else {
            orderedExercises.forEach { exercise ->
                AppCard(Modifier.fillMaxWidth().clickable { onExercise(exercise.id) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(AppSpacing.card),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (activity != null) {
                            val identity = exercise.familyIdentity
                            if (identity != null) {
                                FitnessExerciseIllustration(
                                    activity = activity,
                                    identity = identity,
                                    exactVariant = true,
                                    modifier = Modifier.size(72.dp),
                                    contentDescription = exercise.name,
                                    fallback = { Text("이미지 없음") }
                                )
                            } else {
                                FitnessExerciseIllustration(
                                    activity = activity,
                                    exerciseId = exercise.exerciseId,
                                    modifier = Modifier.size(72.dp),
                                    contentDescription = exercise.name,
                                    fallback = { Text("이미지 없음") }
                                )
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    exercise.uiPart.takeIf { it.isNotBlank() },
                                    exercise.primarySubPart?.takeIf { it.isNotBlank() },
                                    exercise.equipment.takeIf { it.isNotBlank() },
                                    exercise.recordTypeLabel.takeIf { it.isNotBlank() }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall
                            )
                            FitnessStatusBadge(
                                status = FitnessSemanticStatus.SUCCESS,
                                label = "완료 세트 ${exercise.completedSetCount}/${exercise.totalSetCount}",
                                modifier = Modifier.fillMaxWidth()
                            )
                            completedSetSummaryLines(exercise, unit).forEach { summary ->
                                Text(summary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    FitnessSection("같은 루틴 비교") {
        when {
            session.routineId.isNullOrBlank() -> FitnessStatusMessage(
                status = FitnessSemanticStatus.UNKNOWN,
                title = "루틴 식별 정보 없음",
                message = "이 기록에는 이름이 아닌 stable routine ID가 없어 동일 루틴 비교를 표시하지 않습니다."
            )
            session.previousRoutine == null -> FitnessStatusMessage(
                status = FitnessSemanticStatus.INFO,
                title = "비교할 이전 기록 없음",
                message = "같은 routine_id로 저장된 이전 완료 기록이 없습니다."
            )
            else -> {
                val previous = session.previousRoutine
                FitnessFactRow(
                    first = {
                        FitnessFactCard(
                            "이전 볼륨",
                            MassFormatter.withUnit(previous.totalVolumeKg, unit),
                            previous.date
                        )
                    },
                    second = {
                        FitnessFactCard(
                            "볼륨 변화",
                            workoutSummaryChangeLabel(previous.totalVolumeKg, session.totalVolumeKg),
                            "현재 대비"
                        )
                    }
                )
                Text("이전 완료 세트 ${previous.completedSetCount}세트", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    FitnessSection("볼륨 추세") {
        val trendPoints = session.recentVolumes.map {
            FitnessTrendPoint(
                label = it.label.ifBlank { it.date },
                value = MassUnit.fromKg(it.volumeKg, unit)
            )
        } + FitnessTrendPoint(
            label = session.title.ifBlank { "현재" },
            value = MassUnit.fromKg(session.totalVolumeKg, unit)
        )
        FitnessTrendChart(
            model = fitnessTrendPresentation(trendPoints, minimumPoints = 2),
            unit = unit.symbol(),
            emptyLabel = "완료 운동 볼륨이 없습니다.",
            insufficientLabel = "비교할 완료 운동 볼륨 기록이 부족합니다."
        )
    }

    FitnessStatusMessage(
        status = FitnessSemanticStatus.INFO,
        title = "저장된 운동 snapshot",
        message = "이 요약은 완료 시 저장된 종목·세트·볼륨 snapshot으로 재구성됩니다. 이후 운동 마스터 변경은 과거 요약에 반영되지 않습니다."
    )

    FitnessSection("루틴으로 저장") {
        FitnessTextField(
            value = routineName,
            onValueChange = { routineName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("새 루틴 이름") }
        )
        FitnessButton(
            onClick = { onSaveAsRoutine(routineName, orderedExercises) },
            enabled = routineName.isNotBlank() && orderedExercises.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("이 운동을 루틴으로 저장") }
    }

    FitnessOutlinedButton(onClick = onRecords, modifier = Modifier.fillMaxWidth()) {
        Text("기록에서 보기")
    }
}

@Composable
private fun AppWorkoutSessionContent(
    session: com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionSnapshot,
    unit: MassUnit,
    onExercise: (String) -> Unit
) {
    val orderedExercises = stableWorkoutSessionExercises(session.exercises)
    val currentExercise = currentWorkoutExercise(orderedExercises)
    val sessionProgress = workoutSessionProgress(orderedExercises)
    var nowMillis by remember(session.recordId, session.status, session.startedAt) {
        mutableStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(session.recordId, session.status, session.startedAt) {
        if (session.status != "in_progress") return@LaunchedEffect
        while (isActive) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }
    FitnessHeader("운동 진행", session.title)
    FitnessStatusBadge(
        status = when (session.status) {
            "completed" -> FitnessSemanticStatus.SUCCESS
            "in_progress" -> FitnessSemanticStatus.INFO
            else -> FitnessSemanticStatus.UNKNOWN
        },
        label = when (session.status) {
            "completed" -> "완료된 운동"
            "in_progress" -> "진행 중"
            else -> "상태 미상"
        },
        modifier = Modifier.fillMaxWidth()
    )
    FitnessFactRow(
        first = { FitnessFactCard("완료 세트", session.completedSetCount.toString(), "현재 운동") },
        second = {
            FitnessFactCard(
                "경과 시간",
                formatWorkoutElapsedSeconds(
                    workoutElapsedSeconds(
                        session.startedAt,
                        session.durationSeconds,
                        session.status,
                        nowMillis
                    )
                ),
                "운동 시간"
            )
        }
    )
    FitnessProgressBar(sessionProgress, title = "세션 세트 진행")
    FitnessFactCard(
        "총 볼륨",
        MassFormatter.withUnit(session.totalVolumeKg, unit),
        "저장 기준 kg · 표시 ${unit.symbol()}"
    )
    currentExercise?.let { exercise ->
        FitnessSection("현재 종목") {
            FitnessCard(
                Modifier.fillMaxWidth(),
                onClick = { onExercise(exercise.id) }
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WorkoutExerciseImage(
                        exerciseId = exercise.exerciseId,
                        identity = exercise.familyIdentity,
                        contentDescription = "${exercise.name} 현재 종목 이미지",
                        modifier = Modifier.size(72.dp)
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text(exercise.recordTypeLabel, style = MaterialTheme.typography.bodySmall)
                        FitnessProgressBar(workoutExerciseProgress(exercise), title = "종목 진행")
                    }
                }
            }
        }
    }
    FitnessSection("운동 종목") {
        if (orderedExercises.isEmpty()) {
            FitnessStatusMessage(
                status = FitnessSemanticStatus.UNKNOWN,
                title = "종목 없음",
                message = "종목을 추가해 운동을 기록하세요."
            )
        }
        orderedExercises.forEach { exercise ->
            FitnessCard(
                Modifier.fillMaxWidth(),
                onClick = { onExercise(exercise.id) }
            ) {
                Column(
                    Modifier.padding(FitnessSpacing.card),
                    verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WorkoutExerciseImage(
                            exerciseId = exercise.exerciseId,
                            identity = exercise.familyIdentity,
                            contentDescription = "${exercise.name} 운동 이미지",
                            modifier = Modifier.size(64.dp)
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            Text(exercise.recordTypeLabel, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    FitnessStatusBadge(
                        status = when {
                            exercise.totalSetCount > 0 && exercise.completedSetCount >= exercise.totalSetCount -> FitnessSemanticStatus.SUCCESS
                            exercise.completedSetCount > 0 -> FitnessSemanticStatus.INFO
                            else -> FitnessSemanticStatus.UNKNOWN
                        },
                        label = "세트 ${workoutExerciseProgress(exercise).label}",
                        modifier = Modifier.fillMaxWidth()
                    )
                    FitnessProgressBar(workoutExerciseProgress(exercise), title = "종목 진행")
                    completedSetSummaryLines(exercise, unit).forEach { summary ->
                        Text(summary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (session.recentVolumes.isNotEmpty()) {
        FitnessSection("이전 운동 볼륨") {
            FitnessTrendChart(
                model = fitnessTrendPresentation(
                    session.recentVolumes.map {
                        FitnessTrendPoint(
                            label = it.label.ifBlank { it.date },
                            value = MassUnit.fromKg(it.volumeKg, unit)
                        )
                    },
                    minimumPoints = 2
                ),
                unit = unit.symbol(),
                emptyLabel = "이전 운동 볼륨이 없습니다.",
                insufficientLabel = "이전 운동 볼륨 기록이 부족합니다."
            )
        }
    }
}

internal fun completedSetSummaryLines(
    exercise: WorkoutSessionExercise,
    unit: MassUnit
): List<String> = exercise.completedSets.map { set ->
    "${set.setIndex}세트 " + WorkoutSetPresentation.completedSetSummary(
        exercise.recordType,
        set.weightKg,
        set.actualReps,
        set.durationSeconds,
        set.assistedWeightKg,
        set.addedWeightKg,
        set.loadState,
        unit
    )
}
