package com.yeonsik.fitnessapp.feature.development.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutProgressSession
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.development.DevelopmentGoal
import com.yeonsik.fitnessapp.development.DevelopmentReport
import com.yeonsik.fitnessapp.feature.development.model.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Plain content: persistent selection and reads belong to the ViewModel; disclosure is local. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DevelopmentOverviewContent(
    state: DevelopmentUiState.Ready,
    paperAdviceState: PaperAdviceUiState,
    unit: MassUnit,
    actions: DevelopmentScreenActions,
    modifier: Modifier = Modifier,
    startingWorkout: Boolean = false
) {
    var settingsOpen by rememberSaveable(state.ownerId) { mutableStateOf(false) }
    var pickerOpen by rememberSaveable(state.ownerId) { mutableStateOf(false) }
    var contextOpen by rememberSaveable(state.ownerId) { mutableStateOf(false) }
    var selectedExerciseKey by rememberSaveable(state.ownerId, state.report.referenceDate) { mutableStateOf<String?>(null) }
    var selectedAdviceId by rememberSaveable(state.ownerId, state.report.referenceDate) { mutableStateOf<String?>(null) }
    val training = state.training
    val followed = state.followedKeys.mapNotNull { key -> training?.exercises?.find { it.key == key } }
    val guidance = state.guidance
    val nextTraining = guidance?.training?.firstOrNull { it.exerciseKey in state.followedKeys }
        ?: guidance?.training?.firstOrNull()
    val selectedExercise = training?.exercises?.find { it.key == selectedExerciseKey }
    val selectedAdvice = (guidance?.nutrition.orEmpty() + listOfNotNull(guidance?.recovery))
        .find { it.id == selectedAdviceId }
    val closeDetail = { selectedExerciseKey = null; selectedAdviceId = null }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
                Text("발전", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
                Text("기록을 바탕으로 다음 할 일을 정리해요", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { settingsOpen = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("development-settings"),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Icon(FitnessSettingsIcon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(FitnessSpacing.micro))
                Text("설정")
            }
        }
        WeeklyTrainingSummary(state.report)
        if (state.trainingError != null) {
            FitnessStatusMessage(FitnessSemanticStatus.ERROR, "운동 변화를 불러오지 못했어요", state.trainingError)
            FitnessOutlinedButton(actions::refresh, Modifier.fillMaxWidth()) { Text("다시 불러오기") }
        } else if (training != null) {
            if (nextTraining != null) {
                SetSuggestionCard(nextTraining, startingWorkout,
                    onDetails = { selectedExerciseKey = nextTraining.exerciseKey },
                    onStart = actions::startSuggestedWorkout,
                    onPrepare = {
                        actions.openRoutine(training.exercises.find { it.key == nextTraining.exerciseKey }
                            ?.comparison?.current?.session?.routineId)
                    })
            } else if (training.exercises.isEmpty()) {
                DevelopmentGroupCard(FitnessStrengthIcon, "다음 운동", Modifier.fillMaxWidth(),
                    subtitle = "운동을 완료하면 세트 구성을 제안해요") {
                    Text(if (training.unlinkedExerciseCount > 0)
                        "기록에 운동 종목을 연결해 주세요. 같은 운동끼리 비교할 수 있어요."
                        else "운동을 시작하고 세트를 기록해 보세요. 다음에는 이곳에서 이어갈 구성을 확인할 수 있어요.",
                        style = MaterialTheme.typography.bodyMedium)
                    FitnessButton({ actions.openRoutine(null) }, Modifier.fillMaxWidth()) {
                        Icon(FitnessPlayIcon, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(FitnessSpacing.small))
                        Text("운동 선택하기")
                    }
                    training.unlinkedEvidence?.let { evidence ->
                        DevelopmentActionRow(FitnessEvidenceIcon, "최근 운동 기록 열기",
                            { actions.openExerciseRecord(evidence.recordId, evidence.exerciseOccurrenceId) },
                            subtitle = evidence.exerciseName)
                    }
                }
            }
        }
        state.guidanceError?.let {
            FitnessStatusMessage(FitnessSemanticStatus.ERROR, "다음 행동을 정리하지 못했어요", it)
            FitnessOutlinedButton(actions::refresh, Modifier.fillMaxWidth()) { Text("다시 불러오기") }
        }
        guidance?.recovery?.let { advice ->
            AdviceSummaryCard(advice, onAction = actions::showRecoveryCheckIn, onDetails = { selectedAdviceId = advice.id })
        }
        if (guidance != null && guidance.recovery == null && guidance.recoveryError == null) {
            DevelopmentActionRow(FitnessRecoveryIcon, "오늘 컨디션 기록하기",
                actions::showRecoveryCheckIn, Modifier.fillMaxWidth().testTag("development-recovery"),
                subtitle = "수면·에너지 기록을 운동량 판단에 참고해요")
        }
        guidance?.nutrition?.firstOrNull()?.let { advice ->
            AdviceSummaryCard(advice, onAction = actions::openMeals, onDetails = { selectedAdviceId = advice.id })
        }
        guidance?.nutritionError?.let {
            FitnessStatusMessage(FitnessSemanticStatus.ERROR, "식단 제안을 불러오지 못했어요", it)
            TextButton(onClick = actions::refresh) { Text("다시 불러오기") }
        }
        guidance?.recoveryError?.let {
            FitnessStatusMessage(FitnessSemanticStatus.ERROR, "회복 기록을 불러오지 못했어요", it)
            TextButton(onClick = actions::refresh) { Text("다시 불러오기") }
        }
        if (training != null && training.exercises.isNotEmpty()) {
            DevelopmentGroupCard(FitnessTrendIcon, "운동 변화", Modifier.fillMaxWidth(),
                subtitle = "최근 기록을 같은 조건끼리 비교해요",
                trailingContent = {
                    TextButton(onClick = { pickerOpen = true },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("development-picker"),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Text("운동 선택")
                    }
                }) {
                state.trackingMessage?.let { DetailText(it) }
                if (followed.isEmpty()) {
                    DevelopmentActionRow(FitnessStrengthIcon, "비교할 운동 고르기", { pickerOpen = true },
                        subtitle = "최대 5개 운동의 변화를 모아볼 수 있어요")
                }
                followed.forEach { progress ->
                    key(progress.key) {
                        ExerciseProgressRow(progress, unit) { selectedExerciseKey = progress.key }
                    }
                }
            }
        }
        DevelopmentActionRow(FitnessWorkingSetsIcon,
            if (contextOpen) "훈련 분포·기록 참고 접기" else "훈련 분포·기록 참고 보기",
            { contextOpen = !contextOpen }, Modifier.fillMaxWidth().testTag("development-context")
                .semantics { stateDescription = if (contextOpen) "펼쳐짐" else "접힘" },
            subtitle = "부위별 세트 · 체중 · 기록 현황",
            trailingIcon = if (contextOpen) FitnessCollapseIcon else FitnessExpandIcon)
        if (contextOpen) {
            if (training != null) {
                WeeklyBodyPartCard(training)
                training.unlinkedEvidence?.let { evidence ->
                    DetailText("종목 식별이 불명확한 운동 ${training.unlinkedExerciseCount}건은 비교에서 제외했어요.")
                    TextButton(onClick = { actions.openExerciseRecord(evidence.recordId, evidence.exerciseOccurrenceId) }) {
                        Text("비교에 연결할 기록 확인 · ${evidence.exerciseName}")
                    }
                }
            }
            DevelopmentGroupCard(FitnessBodyMetricIcon, "기록 현황", Modifier.fillMaxWidth(), subtitle = "최근 14일 기준") {
                DevelopmentFactRow("최근 체중", state.report.latestWeightKg?.let { MassFormatter.withUnit(it, unit) } ?: "미기록")
                DevelopmentFactRow("식사 기록", "${state.report.mealRecordedDays}일")
                DevelopmentFactRow("체중 기록", "${state.report.dataCoverage.weightRecordedDays}일")
                DevelopmentFactRow("컨디션 기록", "${state.report.checkInRecordedDays}일")
                DetailText("기록이 없는 날은 식사를 거르거나 컨디션이 나빴던 날로 판단하지 않아요.")
                DevelopmentActionRow(FitnessBodyMetricIcon, "체중·신체 정보 기록", actions::showBodyProfile)
            }
            if (paperAdviceState == PaperAdviceUiState.Idle || paperAdviceState is PaperAdviceUiState.Error) {
                DevelopmentActionRow(FitnessEvidenceIcon, "연구 근거로 추가 점검", actions::requestPaperAdvice,
                    subtitle = "기록과 연구 자료를 함께 살펴봐요")
            }
            if (paperAdviceState != PaperAdviceUiState.Idle) PaperAdviceSection(paperAdviceState, state.ownerId)
        }
    }
    if (settingsOpen) {
        DevelopmentSettingsDialog(state.report, onDismiss = { settingsOpen = false }, onAction = { action ->
            settingsOpen = false
            action()
        }, actions = actions)
    }
    if (pickerOpen && training != null) {
        FollowedExerciseDialog(training.exercises, state.followedKeys, state.ownerId,
            onDismiss = { pickerOpen = false }, onSave = { keys ->
                actions.followExercises(keys)
                pickerOpen = false
            })
    }
    if (selectedExercise != null || selectedAdvice != null) {
        ModalBottomSheet(onDismissRequest = closeDetail,
            modifier = Modifier.testTag("development-detail").semantics { paneTitle = "발전 상세" },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = FitnessSpacing.card),
                verticalAlignment = Alignment.CenterVertically) {
                Text("판단 근거와 기록", Modifier.weight(1f).semantics { heading() },
                    fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = closeDetail,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("development-detail-close"),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text("닫기", fontSize = 16.sp)
                }
            }
            key(selectedExercise?.key, selectedAdvice?.id) {
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = FitnessSpacing.card).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
                    if (selectedExercise != null) {
                        ExerciseProgressDetail(selectedExercise, unit,
                            guidance?.training?.find { it.exerciseKey == selectedExercise.key }, startingWorkout,
                            onOpenRecord = { session ->
                                closeDetail()
                                actions.openExerciseRecord(session.recordId, session.exerciseOccurrenceId)
                            }, onStart = { proposal -> closeDetail(); actions.startSuggestedWorkout(proposal) },
                            onRoutine = { closeDetail(); actions.openRoutine(selectedExercise.comparison.current.session.routineId) })
                    } else if (selectedAdvice != null) {
                        AdviceDetail(selectedAdvice, onAction = {
                            closeDetail()
                            when (selectedAdvice.action) {
                                DevelopmentAdviceAction.MEALS -> actions.openMeals()
                                DevelopmentAdviceAction.RECOVERY -> actions.showRecoveryCheckIn()
                                DevelopmentAdviceAction.BODY -> actions.showBodyProfile()
                            }
                        })
                        if (selectedAdvice.action == DevelopmentAdviceAction.MEALS) {
                            guidance?.nutrition.orEmpty().filter { it.id != selectedAdvice.id }.forEach { advice ->
                                HorizontalDivider()
                                DevelopmentGroupCard(FitnessMealIcon, advice.title, Modifier.fillMaxWidth()) {
                                    Text(advice.summary, style = MaterialTheme.typography.bodyMedium)
                                    advice.evidence.forEach { DetailText(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyTrainingSummary(report: DevelopmentReport) {
    FitnessCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("이번 주 · ${report.currentWeekCompletedWorkoutSessions}회 운동",
                fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
            DetailText("${report.currentWeekStart.format(shortDate)}–${report.referenceDate.format(shortDate)} · " +
                "운동한 날 ${report.currentWeekCompletedWorkoutDays}일")
        }
    }
}

@Composable
private fun SetSuggestionCard(
    suggestion: DevelopmentSetSuggestion, starting: Boolean, onDetails: () -> Unit,
    onStart: (com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetCountProposal) -> Unit,
    onPrepare: () -> Unit,
    modifier: Modifier = Modifier
) {
    DevelopmentGroupCard(FitnessStrengthIcon, "다음 운동",
        modifier.fillMaxWidth().testTag("development-next-workout"), subtitle = suggestion.exerciseName) {
        SetCountPanel(suggestion)
        DetailText(suggestion.reason)
        val proposal = suggestion.proposal
        if (proposal != null) {
            FitnessButton({ onStart(proposal) }, Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .testTag("development-start"), enabled = !starting) {
                Icon(FitnessPlayIcon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(FitnessSpacing.small))
                Text(if (starting) "운동 준비 중…" else "이 구성으로 루틴 시작", Modifier.weight(1f))
            }
            SuggestionDetailsButton(onDetails, Modifier.fillMaxWidth())
        } else {
            DetailText("세트 자동 적용 없이 루틴에서 준비해 주세요.")
            if (LocalDensity.current.fontScale > 1.3f) {
                PrepareRoutineButton(onPrepare, starting, Modifier.fillMaxWidth())
                SuggestionDetailsButton(onDetails, Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrepareRoutineButton(onPrepare, starting, Modifier.weight(1f))
                    SuggestionDetailsButton(onDetails, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PrepareRoutineButton(onPrepare: () -> Unit, starting: Boolean, modifier: Modifier) {
    FitnessOutlinedButton(onPrepare, modifier.heightIn(min = 48.dp).testTag("development-prepare"),
        enabled = !starting) {
        Text("루틴에서 준비", fontSize = 14.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun SuggestionDetailsButton(onDetails: () -> Unit, modifier: Modifier) {
    TextButton(onClick = onDetails, modifier = modifier.heightIn(min = 48.dp)
        .testTag("development-suggestion-detail"),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        Text("근거·기록 보기", fontSize = 14.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun SetCountPanel(suggestion: DevelopmentSetSuggestion, modifier: Modifier = Modifier) {
    if (suggestion.suggestedSets == suggestion.baselineSets || suggestion.suggestedSets == null) {
        Surface(modifier.fillMaxWidth(), shape = FitnessShape.input,
            color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Column(Modifier.heightIn(min = 64.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(developmentSetSummary(suggestion), fontSize = 18.sp, lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("워밍업을 뺀 일반 세트 기준", fontSize = 14.sp, lineHeight = 22.sp)
            }
        }
        return
    }
    Surface(modifier.fillMaxWidth(), shape = FitnessShape.input, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(FitnessSpacing.gap), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
                Icon(FitnessWorkingSetsIcon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(setAdjustmentLabel(suggestion), style = MaterialTheme.typography.labelLarge)
            }
            if (LocalDensity.current.fontScale > 1.3f) {
                Text("지난 운동 ${suggestion.baselineSets}세트", fontSize = 16.sp, lineHeight = 24.sp)
                Text("다음 운동 제안 ${suggestion.suggestedSets}세트", fontSize = 18.sp, lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold)
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
                    DetailText("지난 운동")
                    Text(if (suggestion.baselineSets > 0) "${suggestion.baselineSets}세트" else "기록 확인",
                        style = MaterialTheme.typography.titleLarge)
                }
                Icon(FitnessActionNextIcon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(Modifier.weight(1f), shape = FitnessShape.input,
                    color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Column(Modifier.padding(FitnessSpacing.small), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
                        Text("다음 운동 제안", style = MaterialTheme.typography.labelMedium)
                        Text(suggestion.suggestedSets?.let { "${it}세트" } ?: "구성 확인",
                            style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            DetailText("워밍업을 뺀 일반 세트예요 · 시작 후 수정 가능")
        }
    }
}

@Composable
private fun AdviceSummaryCard(advice: DevelopmentAdvice, onAction: () -> Unit,
    modifier: Modifier = Modifier, onDetails: () -> Unit) {
    val recovery = advice.action == DevelopmentAdviceAction.RECOVERY
    val icon = if (recovery) FitnessRecoveryIcon else FitnessMealIcon
    val startsMealLog = advice.id == "nutrition-start"
    DevelopmentGroupCard(icon, if (recovery) "오늘 컨디션" else "식사 보완", modifier.fillMaxWidth(),
        onClick = if (startsMealLog) onAction else onDetails) {
        Text(if (startsMealLog) "식사 기록부터 시작해요" else advice.title,
            fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
        DetailText(advice.summary)
        DevelopmentDisclosureHint(when {
            startsMealLog -> "식사 기록하기"
            recovery -> "컨디션과 운동량 살펴보기"
            else -> "식사 보완점 살펴보기"
        })
    }
}

@Composable
private fun AdviceDetail(advice: DevelopmentAdvice, onAction: () -> Unit) {
    val icon = when (advice.action) {
        DevelopmentAdviceAction.MEALS -> FitnessMealIcon
        DevelopmentAdviceAction.RECOVERY -> FitnessRecoveryIcon
        DevelopmentAdviceAction.BODY -> FitnessBodyMetricIcon
    }
    DevelopmentGroupCard(icon, advice.title, Modifier.fillMaxWidth()) {
        Text(advice.summary, style = MaterialTheme.typography.bodyLarge)
        FitnessButton(onAction, Modifier.fillMaxWidth()) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(FitnessSpacing.small))
            Text(advice.actionLabel, Modifier.weight(1f))
        }
    }
    DevelopmentGroupCard(FitnessEvidenceIcon, "이렇게 판단했어요", Modifier.fillMaxWidth()) {
        advice.evidence.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun ExerciseProgressRow(progress: DevelopmentExerciseProgress, unit: MassUnit,
    modifier: Modifier = Modifier, onDetails: () -> Unit) {
    DevelopmentActionRow(FitnessStrengthIcon, progress.name, onDetails,
        modifier.fillMaxWidth().testTag("development-exercise-${progress.key}"),
        subtitle = "${developmentComparisonValues(progress.comparison, unit)}\n" +
            "${comparisonHeadline(progress.comparison, unit)}\n" +
            "${shortDateLabel(progress.comparison.current.session.date)} · ${sessionContext(progress.comparison.current.session)}")
}

@Composable
private fun ExerciseProgressDetail(
    progress: DevelopmentExerciseProgress, unit: MassUnit, suggestion: DevelopmentSetSuggestion?, starting: Boolean,
    onOpenRecord: (WorkoutProgressSession) -> Unit,
    onStart: (com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetCountProposal) -> Unit,
    onRoutine: () -> Unit
) {
    var historyOpen by rememberSaveable(progress.key) { mutableStateOf(false) }
    val comparison = progress.comparison
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
        DevelopmentIconBadge(FitnessStrengthIcon)
        Column(Modifier.weight(1f)) {
            Text(progress.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            DetailText(sessionContext(comparison.current.session))
        }
    }
    suggestion?.let {
        SetCountPanel(it)
        Text(it.reason, style = MaterialTheme.typography.bodyLarge)
        DevelopmentGroupCard(FitnessEvidenceIcon, "제안 이유", Modifier.fillMaxWidth()) {
            it.evidence.forEach { item -> Text(item, style = MaterialTheme.typography.bodyMedium) }
            DetailText("최근 기록을 바탕으로 한 앱의 조정 제안이에요. 몸 상태에 맞게 바꿔도 괜찮아요.")
        }
        val proposal = it.proposal
        if (proposal != null) {
            DetailText("이 운동의 일반 본세트만 새 세션에 준비해요. 이전 중량·반복수를 입력값으로 가져오며, 운동 중 수정할 수 있어요.")
            FitnessButton({ onStart(proposal) }, Modifier.fillMaxWidth(), enabled = !starting) {
                Icon(FitnessPlayIcon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(FitnessSpacing.small))
                Text(if (starting) "운동 준비 중…" else "이 구성으로 루틴 시작", Modifier.weight(1f))
            }
        } else {
            DevelopmentActionRow(FitnessStrengthIcon, "루틴에서 운동 준비", onRoutine)
        }
        HorizontalDivider()
    }
    Text(comparisonHeadline(comparison, unit), style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold)
    if (comparison.state == DevelopmentComparisonState.COMPARABLE) DetailText(comparisonBasis(comparison, unit))
    comparison.reason?.let { DetailText(it) }
    comparison.previous?.let { sample ->
        EvidenceRow("이전", sample, unit) { onOpenRecord(sample.session) }
    }
    EvidenceRow("최근", comparison.current, unit) { onOpenRecord(comparison.current.session) }
    val normal = comparison.current.normalSets
    val rir = normal.count { it.rir != null }
    val rest = normal.count { it.restSeconds != null }
    DetailText("비교한 일반 세트 ${normal.size}개 · 여유 반복수 ${rir}개 · 휴식 ${rest}개 기록")
    DetailText("여유 반복수는 세트를 마쳤을 때 더 할 수 있었다고 느낀 횟수예요.")
    if (progress.trendValues.count { it != null } >= 3) {
        val model = remember(progress, unit) {
            fitnessTrendPresentation(progress.history.mapIndexedNotNull { index, sample ->
                val value = progress.trendValues[index]?.let {
                    if (comparison.metric == DevelopmentComparisonMetric.LOAD) unit.fromKg(it) else it
                } ?: return@mapIndexedNotNull null
                FitnessTrendPoint(shortDateLabel(sample.session.date), value, sample.session.date)
            }, scalePolicy = FitnessTrendScalePolicy.ZERO_BASED)
        }
        FitnessTrendChart(model, unit = when (comparison.metric) {
            DevelopmentComparisonMetric.LOAD -> unit.symbol()
            DevelopmentComparisonMetric.DURATION -> "초"
            else -> "회"
        })
        DetailText("조건이 같은 기록만 모아 표시해요. 기록 사이의 날짜 간격은 다를 수 있습니다.")
    }
    DetailText("같은 운동 변형·부하 방식·그룹끼리 비교해요. 수치 변화는 근육 성장이나 회복 정도를 뜻하지 않습니다.")
    DevelopmentActionRow(FitnessEvidenceIcon,
        if (historyOpen) "최근 기록 접기" else "최근 기록 ${progress.history.size}회 보기",
        { historyOpen = !historyOpen }, Modifier.semantics { stateDescription = if (historyOpen) "펼쳐짐" else "접힘" },
        trailingIcon = if (historyOpen) FitnessCollapseIcon else FitnessExpandIcon)
    if (historyOpen) {
        DetailText("최근 8주 중 최대 8회 · 일반 본세트 기준")
        progress.history.asReversed().forEach { sample ->
            EvidenceRow("기록", sample, unit) { onOpenRecord(sample.session) }
        }
    }
}

private fun setAdjustmentLabel(suggestion: DevelopmentSetSuggestion): String = when (suggestion.adjustment) {
    DevelopmentSetAdjustment.MAINTAIN -> "세트 수 유지"
    DevelopmentSetAdjustment.CONSIDER_ADD -> "1세트 추가 검토"
    DevelopmentSetAdjustment.CONSIDER_REDUCE -> "1세트 줄이기 검토"
    DevelopmentSetAdjustment.REVIEW -> "최근 구성부터 확인해요"
}

@Composable
private fun EvidenceRow(label: String, sample: DevelopmentPerformanceSample, unit: MassUnit, onOpen: () -> Unit) {
    DevelopmentActionRow(FitnessEvidenceIcon, "$label · ${shortDateLabel(sample.session.date)} · 기록 열기", onOpen,
        Modifier.fillMaxWidth(), subtitle = sampleSummary(sample, unit))
}

@Composable
private fun WeeklyBodyPartCard(training: DevelopmentTrainingOverview) {
    val maximum = training.bodyParts.maxOfOrNull { maxOf(it.currentSets, it.previousSets) }?.coerceAtLeast(1) ?: 1
    DevelopmentGroupCard(FitnessWorkingSetsIcon, "부위별 세트", Modifier.fillMaxWidth(),
        subtitle = "이번 주와 지난주 같은 요일까지") {
            if (training.bodyParts.all { it.currentSets == 0 && it.previousSets == 0 }) {
                Text("비교할 본세트 기록이 아직 없어요.")
            } else training.bodyParts.forEach { part ->
                Column(verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
                    DevelopmentFactRow(if (part.bodyPart == "other") "기타" else DevelopmentGoal.bodyPartLabelKo(part.bodyPart),
                        "이번 ${part.currentSets} · 지난 ${part.previousSets}세트")
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
                        DetailText("이번 주")
                        LinearProgressIndicator(progress = { part.currentSets.toFloat() / maximum },
                            modifier = Modifier.weight(1f).clearAndSetSemantics {}, color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
                        DetailText("지난주")
                        LinearProgressIndicator(progress = { part.previousSets.toFloat() / maximum },
                            modifier = Modifier.weight(1f).clearAndSetSemantics {}, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                    DetailText("이번 주 ${part.currentDays}일 수행")
                }
            }
            DetailText("운동의 대표 부위로 분류해요. 워밍업은 빼고, 드롭 묶음은 1세트로 세어요. 세트 수는 근육 자극량이나 회복률을 뜻하지 않아요.")
    }
}

@Composable
private fun FollowedExerciseDialog(
    exercises: List<DevelopmentExerciseProgress>, selected: List<String>, ownerId: String,
    onDismiss: () -> Unit, onSave: (List<String>) -> Unit
) {
    var pending by rememberSaveable(ownerId, selected) { mutableStateOf(ArrayList(selected)) }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(FitnessTrendIcon, null) },
        title = { Text("변화를 볼 운동 선택") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
            Text("${pending.size}/5개 선택 · 최근 8주에 기록한 운동이에요.", style = MaterialTheme.typography.bodyMedium)
            DetailText("선택한 운동의 중량과 반복수 변화를 발전 탭에 모아 보여줘요.")
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                exercises.forEach { progress ->
                    val checked = progress.key in pending
                    val enabled = checked || pending.size < 5
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = enabled,
                        role = Role.Checkbox, onValueChange = { value ->
                            pending = ArrayList(if (value) pending + progress.key else pending - progress.key)
                        }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked, onCheckedChange = null, enabled = enabled)
                        Column(Modifier.weight(1f).padding(vertical = FitnessSpacing.small)) {
                            Text(progress.name, style = MaterialTheme.typography.bodyLarge)
                            DetailText(sessionContext(progress.comparison.current.session))
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(pending.toList()) }) { Text("선택 저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

@Composable
private fun DevelopmentSettingsDialog(
    report: DevelopmentReport, onDismiss: () -> Unit,
    onAction: (() -> Unit) -> Unit, actions: DevelopmentScreenActions
) {
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(FitnessSettingsIcon, null) },
        title = { Text("발전 설정") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
            DetailText(if (report.goal.isConfigured) "${report.goal.objectiveLabelKo()} · 주 ${report.goal.weeklySessionsTarget}회"
                else "운동 기록만 있어도 발전 탭을 사용할 수 있어요.")
            DevelopmentActionRow(FitnessMealIcon, "영양·단백질 기준", { onAction(actions::showNutritionGoal) },
                subtitle = "홈의 단백질 기준도 여기서 수정해요")
            DevelopmentActionRow(FitnessBodyMetricIcon, "체중·신체 정보", { onAction(actions::showBodyProfile) })
            DevelopmentActionRow(FitnessRecoveryIcon, "오늘 컨디션", { onAction(actions::showRecoveryCheckIn) })
            DevelopmentActionRow(FitnessTrendIcon, "기존 운동 목표", { onAction(actions::showGoal) },
                subtitle = "목표 설정은 선택 사항이에요")
            DevelopmentActionRow(FitnessRefreshIcon, "기록 다시 불러오기", { onAction(actions::refresh) })
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

@Composable
private fun DetailText(text: String) {
    Text(text, fontSize = 14.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val shortDate = DateTimeFormatter.ofPattern("M/d", Locale.KOREA)
private fun shortDateLabel(date: String): String = runCatching {
    java.time.LocalDate.parse(date).format(shortDate)
}.getOrDefault(date)

private fun sessionContext(session: WorkoutProgressSession): String = listOfNotNull(
    when (session.loadState) {
        LoadState.EXTERNAL_LOAD -> "외부 중량"
        LoadState.ADDED_WEIGHT -> "추가 중량"
        LoadState.ASSISTED -> "보조 중량"
        LoadState.BODYWEIGHT -> "맨몸"
        LoadState.BAND_ASSISTED -> "밴드 보조"
        LoadState.BAND_RESISTED -> "밴드 저항"
        null -> if (session.recordType == FitnessRecordContract.TIME) "시간 기록" else "반복수 기록"
    }, session.groupType?.label).joinToString(" · ")

private fun sampleSummary(sample: DevelopmentPerformanceSample, unit: MassUnit): String {
    if (sample.ambiguousOccurrence) return "같은 세션에 동일 운동이 여러 번 있어요. 기록에서 구성을 확인하세요."
    if (sample.normalSets.isEmpty()) return "비교할 일반 본세트가 없어요."
    val timed = sample.session.recordType == FitnessRecordContract.TIME ||
        sample.session.recordType == FitnessRecordContract.WEIGHT_TIME
    return sample.normalSets.joinToString(" · ") { set ->
        val load = set.loadKg?.let { MassFormatter.withUnit(it, unit) + " × " }.orEmpty()
        load + (if (timed) "${set.durationSeconds}초" else "${set.repetitions}회") +
            (set.rir?.let { " (여유 $it)" }.orEmpty())
    }
}

private fun comparisonHeadline(comparison: DevelopmentExerciseComparison, unit: MassUnit): String {
    return when (comparison.state) {
        DevelopmentComparisonState.FIRST_RECORD -> "다음 비교의 기준이 생겼어요"
        DevelopmentComparisonState.DIFFERENT_CONDITIONS -> "조건이 달라 직접 비교하기 어려워요"
        DevelopmentComparisonState.UNAVAILABLE -> "비교할 조건을 확인해 주세요"
        DevelopmentComparisonState.COMPARABLE -> {
            val delta = requireNotNull(comparison.currentValue) - requireNotNull(comparison.previousValue)
            if (delta == 0.0) return "비교한 수행 기록을 유지했어요"
            val direction = if (delta > 0) "늘었어요" else "줄었어요"
            when (comparison.metric) {
                DevelopmentComparisonMetric.REPETITIONS -> "총 반복수가 ${kotlin.math.abs(delta).toInt()}회 $direction"
                DevelopmentComparisonMetric.DURATION -> "기록된 수행 시간이 ${kotlin.math.abs(delta).toInt()}초 $direction"
                DevelopmentComparisonMetric.LOAD -> {
                    val label = if (comparison.current.session.loadState == LoadState.ASSISTED) "보조 중량" else "기록 중량"
                    "${label}이 ${MassFormatter.withUnit(kotlin.math.abs(delta), unit)} $direction"
                }
                null -> "최근 기록을 확인하세요"
            }
        }
    }
}

private fun comparisonBasis(comparison: DevelopmentExerciseComparison, unit: MassUnit): String {
    val sets = comparison.current.normalSets.size
    return when (comparison.metric) {
        DevelopmentComparisonMetric.LOAD -> "같은 세트별 반복수 · 일반 본세트 ${sets}개 기준"
        else -> (comparison.referenceLoadKg?.let { "같은 ${MassFormatter.withUnit(it, unit)} · " }.orEmpty()) +
            "일반 본세트 ${sets}개의 합계 기준"
    }
}

