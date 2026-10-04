package com.yeonsik.fitnessapp.feature.workout.ui

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
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
import com.yeonsik.fitnessapp.exercise.ExercisePrimaryMuscleLabel
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
        val bodyParts = routineBodyPartLabels(exercises)
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
                            if (bodyParts.isEmpty()) "부위 미설정"
                            else "${bodyParts.joinToString(" · ")} 운동",
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

private fun routineBodyPartLabels(exercises: List<RoutineExerciseInstance>): List<String> {
    val parts = exercises.mapNotNull { exercise ->
        when (BodyPart.fromId(exercise.uiPart)) {
            BodyPart.CHEST -> "가슴"
            BodyPart.BACK -> "등"
            BodyPart.LEGS -> "하체"
            BodyPart.SHOULDERS -> "어깨"
            BodyPart.ABS -> "복근"
            BodyPart.ARMS -> ExercisePrimaryMuscleLabel
                .forPrimarySubPart(exercise.primarySubPart, exercise.uiPart)
                .takeIf { it == "삼두" || it == "이두" }
            null -> null
        }
    }.toSet()

    return ROUTINE_BODY_PART_ORDER.filter { it in parts }
}

private val ROUTINE_BODY_PART_ORDER = listOf("가슴", "등", "하체", "어깨", "복근", "삼두", "이두")

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
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 180.dp)
    )
    Text(
        workoutExerciseMetadata(detail.activeExercise),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
    WorkoutExerciseNavigation(orderedExercises, activeIndex, actions::openExercise)
    FitnessStatusBadge(
        status = if (ready.readOnly) FitnessSemanticStatus.SUCCESS else FitnessSemanticStatus.INFO,
        label = if (ready.readOnly) "완료된 기록 · 읽기 전용" else "진행 중 · 입력 가능",
        modifier = Modifier.fillMaxWidth()
    )
    if (ready.readOnly) {
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
    FitnessSection("세트 기록") {
        detail.sets.forEach { set ->
            drafts.SaveableStateProvider("$ownerId:${detail.recordId}:${set.id}") {
                WorkoutSetEditor(
                    actions,
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
        }, Modifier.fillMaxWidth()) {
            Text("+ 세트 추가", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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

private const val EXERCISE_RECORDS_TAB = "records"
private const val EXERCISE_TRENDS_TAB = "trends"

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
            WorkoutExerciseSetTableRow(rows.upperLabel, rows.upperValues)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            WorkoutExerciseSetTableRow(rows.lowerLabel, rows.lowerValues)
        }
    }
}

@Composable
private fun WorkoutExerciseSetTableRow(
    label: String,
    values: List<WorkoutSetTableCell>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.width(50.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        values.forEach { cell ->
            Text(
                cell.visibleValue,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = cell.spokenValue
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
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
    val activity = LocalActivity.current
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
        loadState != com.yeonsik.fitness.shared.feature.exercise.model.LoadState.BODYWEIGHT
    val mainFieldLabel = when (recordType) {
        FitnessRecordContract.REPS_ONLY -> "횟수"
        FitnessRecordContract.TIME -> "시간(초)"
        FitnessRecordContract.ASSISTED_WEIGHT_REPS -> "보조(${inputUnit.symbol()})"
        FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS ->
            if (showMassInput) "추가(${inputUnit.symbol()})" else "체중"
        else -> "중량(${inputUnit.symbol()})"
    }
    val hasSecondField = recordType != FitnessRecordContract.REPS_ONLY &&
        recordType != FitnessRecordContract.TIME
    val secondFieldLabel = if (recordType == FitnessRecordContract.WEIGHT_TIME) "시간(초)" else "횟수"
    val hasRirField = FitnessRecordContract.supportsRir(recordType)
    val saveCurrentSet: () -> Unit = {
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
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tableWidth = maxWidth.coerceAtLeast(320.dp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Column(Modifier.width(tableWidth)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WorkoutSetFieldColumn("세트", Modifier.width(40.dp)) {
                            Box(Modifier.fillMaxWidth().heightIn(min = FitnessSpacing.touch), contentAlignment = Alignment.Center) {
                                Text(set.setIndex.toString(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        WorkoutSetFieldColumn(mainFieldLabel, Modifier.weight(1f)) {
                            when (recordType) {
                                FitnessRecordContract.REPS_ONLY -> WorkoutSetValueField(reps, { reps = it }, mainFieldLabel, numberOptions, nextAction)
                                FitnessRecordContract.TIME -> WorkoutSetValueField(duration, { duration = it }, mainFieldLabel, numberOptions, nextAction)
                                FitnessRecordContract.ASSISTED_WEIGHT_REPS -> WorkoutSetValueField(assisted, { assisted = it }, mainFieldLabel, decimalOptions, nextAction)
                                FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS -> if (showMassInput) {
                                    WorkoutSetValueField(added, { added = it }, mainFieldLabel, decimalOptions, nextAction)
                                } else WorkoutSetStaticValue("체중")
                                else -> if (showMassInput) {
                                    WorkoutSetValueField(weight, { weight = it }, mainFieldLabel, decimalOptions, nextAction)
                                } else WorkoutSetStaticValue("체중")
                            }
                        }
                        if (hasSecondField) {
                            WorkoutSetFieldColumn(secondFieldLabel, Modifier.width(48.dp)) {
                                if (recordType == FitnessRecordContract.WEIGHT_TIME) {
                                    WorkoutSetValueField(duration, { duration = it }, secondFieldLabel, numberOptions, nextAction)
                                } else {
                                    WorkoutSetValueField(reps, { reps = it }, secondFieldLabel, numberOptions, nextAction)
                                }
                            }
                        }
                        if (hasRirField) {
                            WorkoutSetFieldColumn("RIR", Modifier.width(44.dp)) {
                                WorkoutSetValueField(rir, { rir = it }, "RIR", numberOptions, nextAction)
                            }
                        }
                        WorkoutSetFieldColumn("휴식", Modifier.width(56.dp)) {
                            WorkoutSetValueField(
                                rest,
                                { rest = it },
                                "휴식 초",
                                KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                KeyboardActions(onDone = { focus.clearFocus() })
                            )
                        }
                        WorkoutSetFieldColumn("완료", Modifier.width(48.dp)) {
                            Checkbox(
                                checked = completed,
                                onCheckedChange = { completed = it },
                                modifier = Modifier.size(FitnessSpacing.touch)
                                    .semantics { contentDescription = "${set.setIndex}세트 완료" }
                            )
                        }
                    }
                }
            }
        }

        if (allowedLoadStates.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                allowedLoadStates.forEach { state ->
                    val selected = loadState == state
                    Text(
                        loadStateLabelKo(state),
                        modifier = Modifier
                            .clip(FitnessShape.button)
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                if (selected) LocalFitnessColors.current.action else MaterialTheme.colorScheme.outlineVariant,
                                FitnessShape.button
                            )
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { loadState = state }
                            )
                            .heightIn(min = FitnessSpacing.touch)
                            .padding(horizontal = AppSpacing.small),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = saveCurrentSet) { Text("저장") }
            TextButton(onClick = {
                actions.deleteSet(recordId, set.id) {
                    if (it) actions.refresh() else actions.toast("세트를 삭제하지 못했습니다.")
                }
            }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun WorkoutSetFieldColumn(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        content()
    }
}

@Composable
private fun WorkoutSetValueField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = FitnessSpacing.touch)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FitnessShape.input)
            .padding(horizontal = 2.dp, vertical = AppSpacing.small)
            .semantics { contentDescription = label },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                if (value.isEmpty()) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                innerTextField()
            }
        }
    )
}

@Composable
private fun WorkoutSetStaticValue(value: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = FitnessSpacing.touch), contentAlignment = Alignment.Center) {
        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1)
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
                                Text(exercise.name, style = MaterialTheme.typography.titleSmall)
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
