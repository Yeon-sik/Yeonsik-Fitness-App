package com.yeonsik.fitnessapp.feature.workout.ui

import android.app.Activity
import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.feature.home.ui.*
import com.yeonsik.fitnessapp.feature.routine.ui.*
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.feature.routine.ui.routineDominantMuscleLabel
import com.yeonsik.fitnessapp.exercise.ExerciseIllustrationLookup
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.exercise.model.EquipmentType
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitness.shared.feature.exercise.model.ExerciseFamilyIdentity
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.WorkoutSetPresentation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
internal fun WorkoutOverview(
    state: HomeUiState,
    ownerId: String,
    today: String,
    unit: MassUnit,
    actions: WorkoutOverviewActions,
    entranceState: TopLevelEntranceState = rememberTopLevelEntranceState("WORKOUT"),
    entranceToken: Long? = null,
    isActualActive: Boolean = true,
    inProgressIsCardio: Boolean? = null
) {
    val ready = state as? HomeUiState.Ready
    val entrance = rememberTopLevelEntranceMotion(
        entranceState, entranceToken, isActualActive,
        contentReady = ready != null && ready.snapshot.ownerId == ownerId
    )
    if (ready == null || ready.snapshot.ownerId != ownerId) {
        StateMessage("운동", "오늘 기록을 불러오는 중입니다.")
        return
    }
    val snapshot = ready.snapshot
    val metrics = snapshot.dayMetrics[today]
    val strengthInProgress = snapshot.inProgressSessionId != null && inProgressIsCardio == false
    val cardioInProgress = snapshot.inProgressSessionId != null && inProgressIsCardio == true
    var order = 0
    TopLevelEntranceContent(entrance, order++) { AppHeader("운동", today) }
    TopLevelEntranceContent(entrance, order++) {
        FitnessFactRow(
            first = { FitnessFactCard("오늘 볼륨", MassFormatter.withUnit(metrics?.totalVolumeKg ?: 0.0, unit), "완료 세트 기준") },
            second = { FitnessFactCard("식단", "${snapshot.mealCounts[today] ?: 0}끼", "오늘") }
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        WorkoutOverviewAction(
            "무산소 운동",
            FitnessStrengthIcon,
            { if (strengthInProgress) actions.continueWorkout() else actions.navigate(FitnessScreen.STRENGTH) },
            entrance, order++, Modifier.weight(1f),
            supportingText = if (strengthInProgress) "운동 진행중 >" else null
        )
        WorkoutOverviewAction(
            "유산소 운동",
            FitnessCardioIcon,
            { if (cardioInProgress) actions.continueWorkout() else actions.navigate(FitnessScreen.CARDIO) },
            entrance, order++, Modifier.weight(1f),
            supportingText = if (cardioInProgress) "운동 진행중 >" else null
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        WorkoutOverviewAction(
            "체중 기록", FitnessBodyMetricIcon, actions::showBodyMetric, entrance, order++, Modifier.weight(1f)
        )
        WorkoutOverviewAction(
            "식단 기록", FitnessMealIcon, actions::openMeals, entrance, order, Modifier.weight(1f)
        )
    }
}

@Composable
private fun WorkoutOverviewAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    entrance: TopLevelEntranceMotion,
    order: Int,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    val colors = LocalFitnessColors.current
    TopLevelEntranceContent(entrance, order, modifier.aspectRatio(1f)) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = FitnessShape.card,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.action,
                contentColor = colors.onAction
            ),
            contentPadding = PaddingValues(horizontal = FitnessSpacing.micro * 4, vertical = AppSpacing.card)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                    Icon(
                        FitnessActionNextIcon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.micro),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (supportingText != null) {
                        Text(
                            supportingText,
                            modifier = Modifier.weight(1f).alignByBaseline(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 20.sp),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
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
    FitnessHeader("무산소", "루틴을 선택해 운동을 시작하세요.",
        back = { actions.navigate(FitnessScreen.WORKOUT) })
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
    val bodyPartColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0xFFFFD666)
    } else {
        Color(0xFF8A6900)
    }
    ready.snapshot.routines.forEach { routineRow ->
        val exercises = ready.snapshot.routineExercises[routineRow.id].orEmpty()
        val muscleLabel = routineDominantMuscleLabel(exercises)
        AppCard(Modifier.fillMaxWidth().clickable {
            actions.selectRoutine(routineRow.id)
            actions.navigate(FitnessScreen.ROUTINE_DETAIL)
        }) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = AppSpacing.touch)
                    .padding(AppSpacing.card),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            routineRow.name.ifBlank { "나만의 루틴" },
                            modifier = Modifier.alignByBaseline(),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            muscleLabel,
                            modifier = Modifier.alignByBaseline(),
                            style = MaterialTheme.typography.bodySmall,
                            color = bodyPartColor
                        )
                    }
                    Text(
                        "${routineRow.exerciseCount}개 종목",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        routineLastWorkoutLabel(
                            ready.snapshot.latestRoutineDates[routineRow.id],
                            ready.snapshot.today
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "›",
                    modifier = Modifier.padding(start = AppSpacing.small),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun routineLastWorkoutLabel(date: String?, today: String): String {
    if (date.isNullOrBlank()) return "마지막 운동 기록 없음"
    val daysAgo = runCatching {
        ChronoUnit.DAYS.between(LocalDate.parse(date), LocalDate.parse(today))
    }.getOrNull()
    val relative = when {
        daysAgo == null || daysAgo < 0 -> date
        daysAgo == 0L -> "오늘"
        else -> "${daysAgo}일 전"
    }
    return "마지막 운동 · $relative"
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
                is WorkoutSessionUiState.Cancelling -> "운동을 취소하는 중입니다."
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
    val titleStyle = MaterialTheme.typography.headlineLarge
    FitnessHeader(ready?.detail?.activeExercise?.name ?: "운동 종목", back = actions::back,
        titleStyle = titleStyle.copy(fontSize = titleStyle.fontSize * 0.7f,
            lineHeight = titleStyle.lineHeight * 0.7f))
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
    var selectedRecordTab by rememberSaveable(detail.activeExercise.id) {
        mutableStateOf(EXERCISE_RECORDS_TAB)
    }
    if (ready.readOnly) {
        WorkoutExerciseRecordTabs(
            selected = selectedRecordTab,
            onSelected = { selectedRecordTab = it }
        )
    }
    WorkoutExerciseImage(
        exerciseId = detail.activeExercise.exerciseId,
        identity = detail.activeExercise.familyIdentity,
        contentDescription = "${detail.activeExercise.name} 운동 이미지",
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        workoutExerciseMetadata(detail.activeExercise),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
    if (!ready.readOnly) {
        WorkoutSetRecordCard(detail, ownerId, unit, actions, drafts)
    }
    WorkoutExerciseNavigation(orderedExercises, activeIndex, actions::openExercise)
    FitnessStatusBadge(
        status = if (ready.readOnly) FitnessSemanticStatus.SUCCESS else FitnessSemanticStatus.INFO,
        label = if (ready.readOnly) "완료된 기록 · 읽기 전용" else "진행 중 · 입력 가능",
        modifier = Modifier.fillMaxWidth()
    )
    if (ready.readOnly) {
        if (detail.activeExercise.exerciseId == "manual") {
            detail.activeExercise.familyIdentity?.let { identity ->
                Text("연결된 정식 운동: ${identity.presetNameKo}",
                    style = MaterialTheme.typography.bodySmall)
            }
            FitnessOutlinedButton(onClick = { actions.linkManualExercise(detail.activeExercise.id) },
                modifier = Modifier.fillMaxWidth()) { Text("정식 운동에 연결") }
        }
        when (selectedRecordTab) {
            EXERCISE_TRENDS_TAB -> WorkoutExerciseTrendsTab(detail, unit)
            else -> WorkoutExerciseRecordsTab(detail, unit)
        }
        return
    }
    detail.lastHistory?.let { history ->
        WorkoutPreviousHistory(detail, history, unit, readOnly = false, actions = actions)
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
    var confirmDelete by rememberSaveable(detail.recordId, detail.activeExercise.id) {
        mutableStateOf(false)
    }
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

/** One box per exercise occurrence. Adding a set appends a row inside this box. */
@Composable
private fun WorkoutSetRecordCard(
    detail: WorkoutExerciseDetail,
    ownerId: String,
    unit: MassUnit,
    actions: WorkoutDetailActions,
    drafts: SaveableStateHolder
) {
    val restSeconds = detail.exerciseRestSeconds ?: 90
    val allowedLoadStates = detail.allowedLoadStates[detail.activeExercise.id].orEmpty()
    val recordType = FitnessRecordContract.normalizeRecordType(detail.activeExercise.recordType)
    val hasLoadState = allowedLoadStates.isNotEmpty() || detail.sets.any { it.loadState != null }
    val columns = remember(recordType, hasLoadState) { workoutSetInputColumns(recordType, hasLoadState) }
    val orderedSets = remember(detail.sets) { detail.sets.sortedBy { it.setIndex } }
    var restDraft by rememberSaveable(ownerId, detail.recordId, detail.activeExercise.id, restSeconds) {
        mutableStateOf(restSeconds.toString())
    }
    var applyingRest by remember { mutableStateOf(false) }
    var addingSet by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val applyRest: () -> Unit = {
        val seconds = restDraft.toIntOrNull()?.takeIf { it >= 0 }
        if (seconds == null) actions.toast("휴식 시간을 0 이상의 초로 입력하세요.")
        else if (!applyingRest) {
            applyingRest = true
            actions.updateExerciseRestSeconds(detail.recordId, detail.activeExercise.id, seconds) { ok ->
                applyingRest = false
                if (ok) { focus.clearFocus(); actions.refresh() }
                else actions.toast("운동 휴식 시간을 저장하지 못했습니다.")
            }
        }
    }
    FitnessCard(Modifier.fillMaxWidth().testTag("workout-set-record-box")) {
        Column(Modifier.padding(FitnessSpacing.small),
            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
                Text("세트 기록", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("휴식", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.width(64.dp)) {
                    WorkoutSetValueField(
                        value = restDraft, onValueChange = { restDraft = it }, label = "운동 휴식 시간",
                        suffix = "초",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { applyRest() }),
                        enabled = !applyingRest,
                        modifier = Modifier.testTag("exercise-rest-seconds")
                    )
                }
                TextButton(onClick = applyRest, enabled = !applyingRest,
                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text("적용") }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                val tableWidth = maxWidth.coerceAtLeast(columns.minimumWidth * fontScale)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Column(
                        Modifier.width(tableWidth),
                        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
                    ) {
                        WorkoutSetTableHeader(columns)
                        orderedSets.forEach { set ->
                            key(set.id) {
                                drafts.SaveableStateProvider("$ownerId:${detail.recordId}:${set.id}") {
                                    WorkoutSetEditor(actions, detail.recordId, detail.activeExercise.recordType,
                                        allowedLoadStates, set, unit, restSeconds, columns)
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    addingSet = true
                    val next = (detail.sets.maxOfOrNull { it.setIndex } ?: 0) + 1
                    actions.addSet(detail.recordId, detail.activeExercise.id, next,
                        WorkoutSetInput(null, null, null, null, null, null, null, null, false, null, null, unit)) { ok ->
                        addingSet = false
                        if (ok) actions.refresh() else actions.toast("세트를 추가하지 못했습니다.")
                    }
                },
                enabled = !addingSet,
                modifier = Modifier.fillMaxWidth().heightIn(min = FitnessSpacing.touch)
                    .testTag("workout-add-set")
                    .semantics { contentDescription = "세트 추가" },
                shape = FitnessShape.input,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text("세트 추가")
            }
        }
    }
}

private const val EXERCISE_RECORDS_TAB = "records"
private const val EXERCISE_TRENDS_TAB = "trends"

/** Read-only history content shared by workout detail and routine browsing. */
@Composable
internal fun WorkoutExerciseHistoryContent(detail: WorkoutExerciseDetail, unit: MassUnit) {
    var tab by rememberSaveable(detail.activeExercise.id) { mutableStateOf(EXERCISE_RECORDS_TAB) }
    WorkoutExerciseRecordTabs(tab) { tab = it }
    if (tab == EXERCISE_RECORDS_TAB) WorkoutExerciseRecordsTab(detail, unit)
    else WorkoutExerciseTrendsTab(detail, unit)
}

@Composable
private fun WorkoutExerciseRecordTabs(
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(
        Modifier
            .padding(horizontal = 24.dp, vertical = FitnessSpacing.small)
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(FitnessSpacing.micro)
            .selectableGroup()
    ) {
        listOf(EXERCISE_RECORDS_TAB to "종목 기록", EXERCISE_TRENDS_TAB to "종목 변화")
            .forEach { (tab, label) ->
                val isSelected = selected == tab
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = FitnessSpacing.touch)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onSelected(tab) }
                        )
                        .padding(vertical = FitnessSpacing.micro),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
    }
}

@Composable
private fun WorkoutExerciseRecordsTab(detail: WorkoutExerciseDetail, unit: MassUnit) {
    FitnessSection("운동 기록") {
        val latestFirst = detail.recentHistories.asReversed()
        if (latestFirst.isEmpty()) {
            FitnessStatusMessage(
                status = FitnessSemanticStatus.UNKNOWN,
                title = "운동 기록 없음",
                message = "이 종목의 완료된 세트 기록이 없습니다."
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
                contentPadding = PaddingValues(horizontal = AppSpacing.small)
            ) {
                items(latestFirst, key = { it.recordId }) { history ->
                    AppCard(Modifier.width(320.dp)) {
                        Column(
                            Modifier.padding(AppSpacing.card),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
                        ) {
                            Text(
                                history.date.ifBlank { "날짜 미상" },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "총 볼륨 ${MassFormatter.withUnit(history.totalVolumeKg, unit)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            WorkoutExerciseSetTable(
                                sets = history.sets,
                                recordType = detail.activeExercise.recordType,
                                unit = unit,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
    FitnessSection("개인 최고 기록") {
        val bests = detail.personalBests
        val bestOneRepMax = bests?.estimatedOneRepMaxKg?.takeIf { it.isFinite() && it > 0.0 }
        val bestVolume = bests?.totalVolumeKg?.takeIf { it.isFinite() && it > 0.0 }
        if (bestOneRepMax == null && bestVolume == null) {
            FitnessStatusMessage(
                status = FitnessSemanticStatus.UNKNOWN,
                title = "개인 최고 기록 없음",
                message = "중량 기반 1RM 또는 운동 볼륨을 계산할 수 있는 완료 기록이 없습니다."
            )
        } else {
            FitnessFactRow(
                first = {
                    FitnessFactCard(
                        "최고 추정 1RM",
                        bestOneRepMax?.let { MassFormatter.withUnit(it, unit) } ?: "기록 없음",
                        bests.estimatedOneRepMaxDate ?: ""
                    )
                },
                second = {
                    FitnessFactCard(
                        "최고 종목 볼륨",
                        bestVolume?.let { MassFormatter.withUnit(it, unit) } ?: "기록 없음",
                        bests.totalVolumeDate ?: ""
                    )
                }
            )
        }
    }
}

@Composable
private fun WorkoutExerciseTrendsTab(detail: WorkoutExerciseDetail, unit: MassUnit) {
    FitnessSection("최근 5회 추세") {
        val points = workoutExerciseTrendPoints(detail.recentHistories)
        if (points.isEmpty()) {
            FitnessStatusMessage(
                status = FitnessSemanticStatus.UNKNOWN,
                title = "추세 데이터 없음",
                message = "완료된 운동 기록이 쌓이면 1RM과 종목 볼륨 추세를 표시합니다."
            )
        } else {
            WorkoutExerciseDualTrendChart(points, unit, Modifier.fillMaxWidth())
            if (points.size < 2) {
                Text(
                    "기록이 더 쌓이면 추세선이 이어집니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WorkoutExerciseSetTable(
    sets: List<WorkoutSet>,
    recordType: String,
    unit: MassUnit,
    modifier: Modifier = Modifier
) {
    val completedSets = sets.filter { it.isCompleted }.sortedBy { it.setIndex }
    if (completedSets.isEmpty()) {
        Text("완료 세트 없음", modifier, style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
        Text(
            "${completedSets.size}세트",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        workoutHistorySetBatches(completedSets).forEachIndexed { batchIndex, batch ->
            if (batchIndex > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            val rows = workoutSetTableRows(recordType, batch, unit)
            WorkoutExerciseSetTableRow(rows.upperLabel, rows.upperValues,
                if (FitnessRecordContract.normalizeRecordType(recordType) == FitnessRecordContract.TIME)
                    MaterialTheme.colorScheme.onSurface else workoutWeightTextColor(MaterialTheme.colorScheme.surface))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            WorkoutExerciseSetTableRow(rows.lowerLabel, rows.lowerValues)
        }
    }
}

@Composable
private fun WorkoutExerciseSetTableRow(
    label: String,
    values: List<WorkoutSetTableCell>,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.width(50.dp),
            style = MaterialTheme.typography.labelSmall,
            color = valueColor,
            maxLines = 1
        )
        values.forEach { cell ->
            Text(
                cell.visibleValue,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = cell.spokenValue
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFeatureSettings = "tnum"),
                color = valueColor,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        repeat((WORKOUT_HISTORY_SET_COLUMNS - values.size).coerceAtLeast(0)) {
            Spacer(Modifier.weight(1f))
        }
    }
}

internal fun workoutWeightTextColor(surface: Color): Color =
    if (surface.luminance() > 0.5f) Color(0xFF9A6700) else Color(0xFFFACC15)

@Composable
private fun WorkoutExerciseDualTrendChart(
    points: List<WorkoutExerciseTrendPoint>,
    unit: MassUnit,
    modifier: Modifier = Modifier
) {
    val oneRepMaxValues = points.mapNotNull { it.estimatedOneRepMaxKg }
    val volumeValues = points.map { it.totalVolumeKg }.filter { it.isFinite() && it > 0.0 }
    val oneRepColor = MaterialTheme.colorScheme.primary
    val volumeColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val spokenSummary = points.joinToString(separator = "; ") { point ->
        val e1rm = point.estimatedOneRepMaxKg?.let { MassFormatter.withUnit(it, unit) } ?: "1RM 미기록"
        "${point.date}, $e1rm, 볼륨 ${MassFormatter.withUnit(point.totalVolumeKg, unit)}"
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        Column(verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
            WorkoutExerciseTrendLegend(
                color = oneRepColor,
                label = "1RM · ${metricRange(oneRepMaxValues, unit)}",
                circular = true
            )
            WorkoutExerciseTrendLegend(
                color = volumeColor,
                label = "볼륨 · ${metricRange(volumeValues, unit)}",
                circular = false
            )
        }
        Canvas(
            modifier = Modifier.fillMaxWidth().height(160.dp).semantics {
                contentDescription = "최근 운동 선 그래프. 원은 추정 1RM, 사각형은 운동 볼륨입니다. $spokenSummary"
            }
        ) {
            val left = 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val plotHeight = (bottom - top).coerceAtLeast(1f)
            val plotWidth = (right - left).coerceAtLeast(1f)
            for (step in 0..2) {
                val y = top + plotHeight * step / 2f
                drawLine(
                    color = outlineColor,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            fun drawSeries(values: List<Double?>, color: Color, circular: Boolean) {
                val numeric = values.filterNotNull().filter { it.isFinite() }
                if (numeric.isEmpty()) return
                val minimum = numeric.minOrNull() ?: return
                val maximum = numeric.maxOrNull() ?: return
                val range = (maximum - minimum).takeIf { it > 0.0 } ?: 1.0
                val plotted = values.mapIndexedNotNull { index, value ->
                    value?.takeIf { it.isFinite() }?.let {
                        val ratio = if (maximum == minimum) 0.5f
                        else ((it - minimum) / range).toFloat().coerceIn(0f, 1f)
                        val x = if (points.size == 1) left + plotWidth / 2f
                        else left + plotWidth * index / (points.size - 1).toFloat()
                        index to Offset(x, bottom - plotHeight * ratio)
                    }
                }
                plotted.zipWithNext().forEach { (start, end) ->
                    if (end.first == start.first + 1) {
                        drawLine(color, start.second, end.second, strokeWidth = 2.dp.toPx())
                    }
                }
                plotted.forEach { (_, point) ->
                    if (circular) {
                        drawCircle(color, radius = 4.dp.toPx(), center = point)
                    } else {
                        val side = 8.dp.toPx()
                        drawRect(color, topLeft = Offset(point.x - side / 2f, point.y - side / 2f), size = Size(side, side))
                    }
                }
            }

            drawSeries(points.map { it.estimatedOneRepMaxKg }, oneRepColor, circular = true)
            drawSeries(points.map { it.totalVolumeKg.takeIf { volume -> volume.isFinite() && volume > 0.0 } }, volumeColor, circular = false)
        }
        Row(Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Text(
                    point.date.takeLast(5),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun WorkoutExerciseTrendLegend(color: Color, label: String, circular: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(if (circular) androidx.compose.foundation.shape.CircleShape else FitnessShape.input)
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun metricRange(values: List<Double>, unit: MassUnit): String =
    if (values.isEmpty()) {
        "계산 불가"
    } else {
        "${MassFormatter.withUnit(values.minOrNull() ?: 0.0, unit)}–${MassFormatter.withUnit(values.maxOrNull() ?: 0.0, unit)}"
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
    val context = LocalContext.current
    val resolution = remember(context, exerciseId, identity) {
        if (identity != null) ExerciseIllustrationLookup.resolveExact(context, identity)
        else ExerciseIllustrationLookup.resolveExactForStorageExerciseId(context, exerciseId)
    }
    if (resolution.isPlaceholder) {
        FitnessStatusBadge(
            status = FitnessSemanticStatus.UNKNOWN,
            label = "운동 이미지 없음",
            modifier = modifier
        )
        return
    }
    key(resolution.illustrationKey, resolution.drawables.toList()) {
        var frame by remember { mutableIntStateOf(0) }
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner, resolution) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (resolution.drawables.size > 1) {
                    while (isActive) {
                        delay((resolution.durationsMs.getOrNull(frame) ?: 1000).coerceAtLeast(1).toLong())
                        frame = (frame + 1) % resolution.drawables.size
                    }
                }
            }
        }
        val canvasBounds = remember(context.resources, resolution.illustrationKey, resolution.drawables.toList()) {
            workoutExerciseCanvasBounds(context.resources, resolution.drawables.toList())
        }
        // Trim only empty canvas shared by every frame, using one transform for
        // A/B. Per-pose cropping would move the camera and body between frames.
        val referencePainter = painterResource(resolution.drawables[0])
        val aspectRatio = (referencePainter.intrinsicSize.width * canvasBounds.width /
            (referencePainter.intrinsicSize.height * canvasBounds.height))
            .takeIf { it.isFinite() && it > 0f } ?: 1f
        val framePainter = painterResource(resolution.drawables[frame])
        val scenePainter = remember(framePainter, canvasBounds) {
            WorkoutExerciseCanvasPainter(framePainter, canvasBounds)
        }
        BoxWithConstraints(modifier) {
            val imageHeight = (maxWidth / aspectRatio).coerceAtMost(220.dp)
            Image(scenePainter, contentDescription,
                Modifier.fillMaxWidth().height(imageHeight).testTag("workout-exercise-frame-$frame"),
                contentScale = ContentScale.Fit)
        }
    }
}

/** A tiny sampled alpha scan runs once per scene, not on animation frames or set writes. */
private fun workoutExerciseCanvasBounds(resources: Resources, drawables: List<Int>): Rect {
    val fullCanvas = Rect(0f, 0f, 1f, 1f)
    var union: Rect? = null
    for (drawable in drawables) {
        val options = BitmapFactory.Options().apply { inSampleSize = 8; inScaled = false }
        val bitmap = BitmapFactory.decodeResource(resources, drawable, options) ?: return fullCanvas
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        pixels.forEachIndexed { index, pixel ->
            if (pixel ushr 24 != 0) {
                val x = index % width
                val y = index / width
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        if (right < left || bottom < top) return fullCanvas
        // One sampled pixel of breathing room keeps antialiased edges intact.
        val bounds = Rect(
            (left - 1).coerceAtLeast(0).toFloat() / width,
            (top - 1).coerceAtLeast(0).toFloat() / height,
            (right + 2).coerceAtMost(width).toFloat() / width,
            (bottom + 2).coerceAtMost(height).toFloat() / height
        )
        val previous = union
        union = if (previous == null) bounds else Rect(
            minOf(previous.left, bounds.left), minOf(previous.top, bounds.top),
            maxOf(previous.right, bounds.right), maxOf(previous.bottom, bounds.bottom)
        )
    }
    return union ?: fullCanvas
}

private class WorkoutExerciseCanvasPainter(private val source: Painter, private val bounds: Rect) : Painter() {
    override val intrinsicSize = Size(source.intrinsicSize.width * bounds.width,
        source.intrinsicSize.height * bounds.height)

    override fun DrawScope.onDraw() {
        val canvasSize = Size(size.width / bounds.width, size.height / bounds.height)
        clipRect {
            translate(-canvasSize.width * bounds.left, -canvasSize.height * bounds.top) {
                with(source) { draw(canvasSize) }
            }
        }
    }
}

private fun workoutExerciseMetadata(exercise: WorkoutExercise): String = listOfNotNull(
    exercise.uiPart.takeIf { it.isNotBlank() }?.let {
        BodyPart.fromId(it)?.labelKo() ?: it
    },
    exercise.equipment.takeIf { it.isNotBlank() }?.let {
        EquipmentType.fromId(it)?.labelKo() ?: it
    },
    FitnessRecordContract.displayRecordTypeKo(exercise.recordType)
).joinToString(" · ")

@Composable
private fun WorkoutSetEditor(
    actions: WorkoutDetailActions,
    recordId: String,
    rawRecordType: String,
    allowedLoadStates: List<com.yeonsik.fitness.shared.feature.exercise.model.LoadState>,
    set: WorkoutSet,
    unit: MassUnit,
    exerciseRestSeconds: Int,
    columns: WorkoutSetInputColumns
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
    var loadState by rememberSaveable(set.id) { mutableStateOf(set.loadState) }
    var completed by rememberSaveable(set.id) { mutableStateOf(set.isCompleted) }
    var showSetMenu by remember(set.id) { mutableStateOf(false) }
    var showLoadMenu by remember(set.id) { mutableStateOf(false) }
    var saving by remember(set.id) { mutableStateOf(false) }
    var draftRevision by rememberSaveable(set.id) { mutableIntStateOf(0) }
    var savedRevision by rememberSaveable(set.id) { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    val decimalOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    val numberOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
    val nextAction = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
    val showMassInput = recordType != FitnessRecordContract.REPS_ONLY &&
        recordType != FitnessRecordContract.TIME &&
        loadState != com.yeonsik.fitness.shared.feature.exercise.model.LoadState.BODYWEIGHT
    val mainFieldLabel = when (recordType) {
        FitnessRecordContract.REPS_ONLY -> "횟수"
        FitnessRecordContract.TIME -> "시간(초)"
        FitnessRecordContract.ASSISTED_WEIGHT_REPS -> "보조(${inputUnit.symbol()})"
        FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS ->
            if (showMassInput) "추가(${inputUnit.symbol()})" else "체중"
        else -> "중량(${inputUnit.symbol()})"
    }
    val secondFieldLabel = columns.secondLabel.orEmpty()
    val saveCurrentSet: (Boolean) -> Unit = save@ { nextCompleted ->
        if (saving) return@save
        saving = true
        val revisionToSave = draftRevision
        val enteredWeight = weight.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        val enteredAssisted = assisted.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        val enteredAdded = added.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        val selectedState = loadState
        val enteredLoad = when {
            selectedState == LoadState.ADDED_WEIGHT ||
                (recordType == FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS && selectedState == null) -> enteredAdded
            selectedState == LoadState.ASSISTED || selectedState == LoadState.BAND_ASSISTED ||
                recordType == FitnessRecordContract.ASSISTED_WEIGHT_REPS -> enteredAssisted
            selectedState == LoadState.EXTERNAL_LOAD || selectedState == LoadState.BAND_RESISTED ||
                recordType == FitnessRecordContract.WEIGHT_REPS ||
                recordType == FitnessRecordContract.WEIGHT_TIME -> enteredWeight
            else -> null
        }
        val canonicalWeight = when {
            selectedState == LoadState.EXTERNAL_LOAD || selectedState == LoadState.BAND_RESISTED ||
                recordType == FitnessRecordContract.WEIGHT_REPS ||
                recordType == FitnessRecordContract.WEIGHT_TIME -> enteredWeight?.let { MassUnit.toKg(it, inputUnit) }
            else -> null
        }
        val canonicalAssisted = when {
            selectedState == LoadState.ASSISTED || selectedState == LoadState.BAND_ASSISTED ||
                recordType == FitnessRecordContract.ASSISTED_WEIGHT_REPS -> enteredAssisted?.let { MassUnit.toKg(it, inputUnit) }
            else -> null
        }
        val canonicalAdded = when {
            selectedState == LoadState.ADDED_WEIGHT ||
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
            set.restSeconds, nextCompleted, loadState,
            enteredLoad,
            enteredLoad?.let { inputUnit }
        )
        actions.updateSet(recordId, set.id, input) { ok ->
            saving = false
            if (ok) {
                val newlyCompleted = nextCompleted && !completed
                completed = nextCompleted
                savedRevision = revisionToSave
                if (newlyCompleted) actions.startRestTimer(exerciseRestSeconds)
                actions.refresh()
            } else actions.toast("세트를 저장하지 못했습니다.")
        }
    }
    val doneAction = KeyboardActions(onDone = { saveCurrentSet(completed); focus.clearFocus() })
    val mainOptions = if (columns.secondLabel == null && !columns.hasRir)
        numberOptions.copy(imeAction = ImeAction.Done) else numberOptions

    WorkoutSetTableRow(
        columns = columns,
        modifier = Modifier.testTag("workout-set-${set.id}"),
        setContent = {
            Box(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { showSetMenu = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = FitnessSpacing.touch)
                        .semantics { contentDescription = "${set.setIndex}세트 설정" },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(set.setIndex.toString(), style = MaterialTheme.typography.bodyMedium)
                    WorkoutSetMenuIndicator()
                }
                DropdownMenu(expanded = showSetMenu, onDismissRequest = { showSetMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("입력 저장") },
                        enabled = !saving,
                        onClick = { showSetMenu = false; saveCurrentSet(completed) }
                    )
                    DropdownMenuItem(
                        text = { Text("세트 삭제", color = MaterialTheme.colorScheme.error) },
                        enabled = !saving,
                        onClick = {
                            showSetMenu = false
                            actions.deleteSet(recordId, set.id) {
                                if (it) actions.refresh() else actions.toast("세트를 삭제하지 못했습니다.")
                            }
                        }
                    )
                }
            }
        },
        mainContent = {
            when (recordType) {
                FitnessRecordContract.REPS_ONLY ->
                    WorkoutSetValueField(reps, { reps = it; draftRevision++ }, mainFieldLabel, mainOptions,
                        if (mainOptions.imeAction == ImeAction.Done) doneAction else nextAction)
                FitnessRecordContract.TIME ->
                    WorkoutSetValueField(duration, { duration = it; draftRevision++ }, mainFieldLabel, mainOptions,
                        if (mainOptions.imeAction == ImeAction.Done) doneAction else nextAction)
                FitnessRecordContract.ASSISTED_WEIGHT_REPS ->
                    WorkoutSetValueField(assisted, { assisted = it; draftRevision++ }, mainFieldLabel, decimalOptions, nextAction,
                        suffix = inputUnit.symbol())
                FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS -> if (showMassInput) {
                    WorkoutSetValueField(added, { added = it; draftRevision++ }, mainFieldLabel, decimalOptions, nextAction,
                        suffix = inputUnit.symbol())
                } else WorkoutSetStaticValue("체중")
                else -> if (showMassInput) {
                    WorkoutSetValueField(weight, { weight = it; draftRevision++ }, mainFieldLabel, decimalOptions, nextAction,
                        suffix = inputUnit.symbol())
                } else WorkoutSetStaticValue("체중")
            }
        },
        secondContent = {
            val options = if (columns.hasRir) numberOptions else numberOptions.copy(imeAction = ImeAction.Done)
            val keyboardActions = if (columns.hasRir) nextAction else doneAction
            if (recordType == FitnessRecordContract.WEIGHT_TIME) {
                WorkoutSetValueField(duration, { duration = it; draftRevision++ }, secondFieldLabel, options, keyboardActions)
            } else {
                WorkoutSetValueField(reps, { reps = it; draftRevision++ }, secondFieldLabel, options, keyboardActions)
            }
        },
        rirContent = { WorkoutSetValueField(rir, { rir = it; draftRevision++ }, "RIR",
            numberOptions.copy(imeAction = ImeAction.Done), doneAction) },
        completedContent = {
            Checkbox(
                checked = completed && draftRevision == savedRevision,
                onCheckedChange = saveCurrentSet,
                enabled = !saving,
                modifier = Modifier.size(FitnessSpacing.touch)
                    .semantics {
                        contentDescription = "${set.setIndex}세트 완료"
                        stateDescription = when {
                            saving -> "저장 중"
                            draftRevision != savedRevision -> "입력 변경됨, 체크하면 저장"
                            completed -> "저장 및 완료됨"
                            else -> "체크하면 저장"
                        }
                    }
            )
        },
        loadContent = {
            Box(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { showLoadMenu = true },
                    enabled = allowedLoadStates.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = FitnessSpacing.touch)
                        .testTag("workout-set-load-state-${set.id}")
                        .semantics { contentDescription = "${set.setIndex}세트 부하 방식" },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(loadState?.let(::loadStateLabelKo) ?: "미지정",
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                }
                DropdownMenu(expanded = showLoadMenu, onDismissRequest = { showLoadMenu = false }) {
                    allowedLoadStates.forEach { state ->
                        DropdownMenuItem(
                            text = { Text(loadStateLabelKo(state)) },
                            onClick = {
                                if (loadState != state) { loadState = state; draftRevision++ }
                                showLoadMenu = false
                            },
                            leadingIcon = { RadioButton(selected = loadState == state, onClick = null) }
                        )
                    }
                }
            }
        }
    )
}

private data class WorkoutSetInputColumns(
    val mainLabel: String,
    val secondLabel: String?,
    val hasRir: Boolean,
    val hasLoadState: Boolean
) {
    val minimumWidth get() = 156.dp +
        (if (secondLabel != null) 50.dp else 0.dp) + (if (hasRir) 50.dp else 0.dp) +
        (if (hasLoadState) 66.dp else 0.dp)
}

private fun workoutSetInputColumns(recordType: String, hasLoadState: Boolean) = WorkoutSetInputColumns(
    mainLabel = when (recordType) {
        FitnessRecordContract.REPS_ONLY -> "횟수"
        FitnessRecordContract.TIME -> "시간(초)"
        FitnessRecordContract.ASSISTED_WEIGHT_REPS -> "보조 중량"
        FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS -> "추가 중량"
        else -> "중량"
    },
    secondLabel = when (recordType) {
        FitnessRecordContract.REPS_ONLY, FitnessRecordContract.TIME -> null
        FitnessRecordContract.WEIGHT_TIME -> "시간(초)"
        else -> "횟수"
    },
    hasRir = FitnessRecordContract.supportsRir(recordType),
    hasLoadState = hasLoadState
)

@Composable
private fun WorkoutSetTableHeader(columns: WorkoutSetInputColumns) {
    Column(Modifier.fillMaxWidth().testTag("workout-set-table-header")) {
        WorkoutSetTableRow(
            columns = columns,
            setContent = { WorkoutSetHeaderLabel("세트") },
            mainContent = { WorkoutSetHeaderLabel(columns.mainLabel) },
            secondContent = { WorkoutSetHeaderLabel(columns.secondLabel.orEmpty()) },
            rirContent = { WorkoutSetHeaderLabel("RIR") },
            completedContent = { WorkoutSetHeaderLabel("완료") },
            loadContent = { WorkoutSetHeaderLabel("부하") }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Header and editable rows share one column layout and one horizontal scroll. */
@Composable
private fun WorkoutSetTableRow(
    columns: WorkoutSetInputColumns,
    modifier: Modifier = Modifier,
    setContent: @Composable () -> Unit,
    mainContent: @Composable () -> Unit,
    secondContent: @Composable () -> Unit,
    rirContent: @Composable () -> Unit,
    completedContent: @Composable () -> Unit,
    loadContent: @Composable () -> Unit
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp * fontScale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(48.dp * fontScale), contentAlignment = Alignment.Center) { setContent() }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { mainContent() }
        if (columns.secondLabel != null) {
            Box(Modifier.width(48.dp * fontScale), contentAlignment = Alignment.Center) { secondContent() }
        }
        if (columns.hasRir) {
            Box(Modifier.width(48.dp * fontScale), contentAlignment = Alignment.Center) { rirContent() }
        }
        Box(Modifier.width(48.dp * fontScale), contentAlignment = Alignment.Center) { completedContent() }
        if (columns.hasLoadState) {
            Box(Modifier.width(64.dp * fontScale), contentAlignment = Alignment.Center) { loadContent() }
        }
    }
}

@Composable
private fun WorkoutSetHeaderLabel(label: String) {
    Text(
        label,
        modifier = Modifier.fillMaxWidth().padding(vertical = FitnessSpacing.small)
            .semantics { heading() },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun WorkoutSetMenuIndicator() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.padding(start = 2.dp).size(8.dp).clearAndSetSemantics {}) {
        val middle = Offset(size.width / 2f, size.height * 0.7f)
        drawLine(color, Offset(0f, size.height * 0.3f), middle, strokeWidth = 1.5.dp.toPx())
        drawLine(color, middle, Offset(size.width, size.height * 0.3f), strokeWidth = 1.5.dp.toPx())
    }
}

@Composable
private fun WorkoutSetValueField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    suffix: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth()
            .heightIn(min = FitnessSpacing.touch)
            .border(if (focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                FitnessShape.input)
            .padding(horizontal = 4.dp, vertical = AppSpacing.small)
            .semantics { contentDescription = label },
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) Text("—", Modifier.clearAndSetSemantics {},
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    innerTextField()
                }
                if (suffix != null) {
                    Text(suffix, Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

@Composable
private fun WorkoutSetStaticValue(value: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = FitnessSpacing.touch)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FitnessShape.input)
        .padding(horizontal = 4.dp, vertical = AppSpacing.small), contentAlignment = Alignment.Center) {
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

private fun loadStateLabelKo(state: LoadState): String = when (state) {
    LoadState.BODYWEIGHT -> "체중"
    LoadState.EXTERNAL_LOAD -> "외부 중량"
    LoadState.ADDED_WEIGHT -> "추가 중량"
    LoadState.ASSISTED -> "보조"
    LoadState.BAND_ASSISTED -> "밴드 보조"
    LoadState.BAND_RESISTED -> "밴드 저항"
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
    val activity = LocalActivity.current
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
                    Column(
                        Modifier.fillMaxWidth().padding(AppSpacing.card * 0.6f),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.small * 0.6f)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap * 0.6f),
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
                                        exactVariant = true,
                                        fallback = { Text("이미지 없음") }
                                    )
                                }
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.small * 0.6f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        exercise.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "총 볼륨",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            MassFormatter.withUnit(exercise.totalVolumeKg, unit),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Text(
                                    listOfNotNull(
                                        exercise.uiPart.takeIf { it.isNotBlank() },
                                        exercise.primarySubPart?.takeIf { it.isNotBlank() },
                                        exercise.equipment.takeIf { it.isNotBlank() },
                                        exercise.recordTypeLabel.takeIf { it.isNotBlank() }
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    workoutExerciseVolumeChangeLabel(
                                        exercise.previousTotalVolumeKg,
                                        exercise.totalVolumeKg,
                                        unit
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.micro),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.semantics {
                                        contentDescription =
                                            "완료 세트 ${exercise.completedSetCount}/${exercise.totalSetCount}, 완료 상태"
                                    }
                                ) {
                                    Text(
                                        FitnessSemanticStatus.SUCCESS.glyph(),
                                        color = LocalFitnessColors.current.success,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        "완료 세트 ${exercise.completedSetCount}/${exercise.totalSetCount}",
                                        color = LocalFitnessColors.current.success,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        WorkoutExerciseSetTable(
                            sets = exercise.completedSets,
                            recordType = exercise.recordType,
                            unit = unit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    val previousRoutine = session.previousRoutine
    FitnessSection("같은 루틴 비교") {
        when {
            session.routineId.isNullOrBlank() -> FitnessStatusMessage(
                status = FitnessSemanticStatus.UNKNOWN,
                title = "루틴 식별 정보 없음",
                message = "이 기록에는 이름이 아닌 stable routine ID가 없어 동일 루틴 비교를 표시하지 않습니다."
            )
            previousRoutine == null -> FitnessStatusMessage(
                status = FitnessSemanticStatus.INFO,
                title = "비교할 이전 기록 없음",
                message = "같은 routine_id로 저장된 이전 완료 기록이 없습니다."
            )
            else -> {
                val previous = previousRoutine
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
                value = MassUnit.fromKg(it.volumeKg, unit),
                detailLabel = it.date
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
    session: com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot,
    unit: MassUnit,
    onExercise: (String) -> Unit
) {
    val orderedExercises = stableWorkoutSessionExercises(session.exercises)
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
    val isInProgress = session.status == "in_progress"
    val displayedDurationSeconds = if (isInProgress) {
        workoutElapsedSeconds(
            session.startedAt,
            session.durationSeconds,
            session.status,
            nowMillis
        )
    } else {
        session.durationSeconds
    }
    FitnessHeader("운동 진행", session.title)
    FitnessStatusBadge(
        status = when (session.status) {
            "completed" -> FitnessSemanticStatus.SUCCESS
            "in_progress" -> FitnessSemanticStatus.INFO
            else -> FitnessSemanticStatus.UNKNOWN
        },
        label = when (session.status) {
            "completed" -> workoutCompletionStatusLabel(session.completedAt)
            "in_progress" -> "진행 중"
            else -> "상태 미상"
        },
        modifier = Modifier.fillMaxWidth()
    )
    FitnessFactRow(
        first = { FitnessFactCard("완료 세트", session.completedSetCount.toString(), "현재 운동") },
        second = {
            FitnessFactCard(
                if (isInProgress) "경과 시간" else "운동 시간",
                formatWorkoutElapsedSeconds(displayedDurationSeconds),
                if (isInProgress) "현재 운동" else "기록된 운동 시간"
            )
        }
    )
    FitnessProgressBar(sessionProgress, title = "세션 세트 진행")
    FitnessFactCard(
        "총 볼륨",
        MassFormatter.withUnit(session.totalVolumeKg, unit),
        "저장 기준 kg · 표시 ${unit.symbol()}"
    )
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
                    Modifier.padding(FitnessSpacing.card * 0.6f),
                    verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small * 0.6f)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap * 0.6f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WorkoutExerciseImage(
                            exerciseId = exercise.exerciseId,
                            identity = exercise.familyIdentity,
                            contentDescription = "${exercise.name} 운동 이미지",
                            modifier = Modifier.size(64.dp)
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
                        ) {
                            Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                exercise.recordTypeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            WorkoutExerciseProgressCompact(exercise)
                        }
                    }
                    WorkoutExerciseSetTable(
                        sets = exercise.completedSets,
                        recordType = exercise.recordType,
                        unit = unit,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                            value = MassUnit.fromKg(it.volumeKg, unit),
                            detailLabel = it.date
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

@Composable
private fun WorkoutExerciseProgressCompact(exercise: WorkoutSessionExercise) {
    val progress = workoutExerciseProgress(exercise)
    val fraction = progress.fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val status = when {
        exercise.totalSetCount > 0 && exercise.completedSetCount >= exercise.totalSetCount ->
            FitnessSemanticStatus.SUCCESS
        exercise.completedSetCount > 0 -> FitnessSemanticStatus.INFO
        else -> FitnessSemanticStatus.UNKNOWN
    }
    val statusColor = when (status) {
        FitnessSemanticStatus.SUCCESS -> LocalFitnessColors.current.success
        FitnessSemanticStatus.WARNING -> LocalFitnessColors.current.warning
        FitnessSemanticStatus.ERROR -> MaterialTheme.colorScheme.error
        FitnessSemanticStatus.INFO -> MaterialTheme.colorScheme.primary
        FitnessSemanticStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier.fillMaxWidth().semantics {
            contentDescription = "종목 진행, 세트 ${progress.label}"
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        },
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.micro),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(status.glyph(), color = statusColor, style = MaterialTheme.typography.labelSmall)
            Text("세트 ${progress.label}", color = statusColor, style = MaterialTheme.typography.labelSmall)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = statusColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
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
