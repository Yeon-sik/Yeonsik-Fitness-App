package com.yeonsik.fitnessapp.app.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.BuildConfig
import com.yeonsik.fitnessapp.cardio.CardioRouteProjection
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.core.ui.FitnessFactCard
import com.yeonsik.fitnessapp.core.ui.FitnessFactRow
import com.yeonsik.fitnessapp.core.ui.FitnessSection
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.data.MassUnit
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot
import com.yeonsik.fitnessapp.feature.cardio.ui.CardioRouteMap
import com.yeonsik.fitnessapp.feature.cardio.ui.CardioSessionUiState
import com.yeonsik.fitnessapp.feature.development.ui.DevelopmentUiState
import com.yeonsik.fitnessapp.feature.exercise.ui.ExercisePickerUiState
import com.yeonsik.fitnessapp.feature.home.ui.HomeUiState
import com.yeonsik.fitnessapp.feature.meal.ui.MealUiState
import com.yeonsik.fitnessapp.feature.routine.ui.RoutineEntryUiState
import com.yeonsik.fitnessapp.feature.supplement.ui.SupplementUiState
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSet
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSetInput
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutExerciseDetailUiState
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutSessionUiState
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.FitnessUi
import com.yeonsik.fitnessapp.ui.ScreenHost
import kotlinx.coroutines.delay
import java.time.LocalDate

/** The single Compose navigation destination host for every application screen. */
object ComposeAppScreen {
    @JvmStatic
    fun install(view: ComposeView, host: ScreenHost, screen: FitnessScreen,
                ownerId: String, today: String, unit: MassUnit, dark: Boolean) {

        view.setContent {
            FitnessComposeTheme(dark) {
                AppDestination(host, screen, ownerId, today, unit)
            }
        }
    }
}

@Composable
private fun AppDestination(host: ScreenHost, screen: FitnessScreen, ownerId: String,
                           today: String, unit: MassUnit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)
    ) {
        when (screen) {
            FitnessScreen.HOME -> error("HOME is installed by ComposeHomeScreen")
            FitnessScreen.WORKOUT -> WorkoutOverview(host, ownerId, today, unit)
            FitnessScreen.STRENGTH -> StrengthScreen(host, ownerId)
            FitnessScreen.CARDIO -> CardioStartScreen(host)
            FitnessScreen.RECORDS -> RecordsScreen(host, ownerId, today, unit)
            FitnessScreen.DEVELOPMENT -> DevelopmentScreen(host, ownerId, today, unit)
            FitnessScreen.SETTINGS -> SettingsScreen(host)
            FitnessScreen.WORKOUT_SESSION -> WorkoutSessionScreen(host, ownerId, unit)
            FitnessScreen.WORKOUT_EXERCISE_DETAIL -> WorkoutDetailScreen(host, ownerId, unit)
            FitnessScreen.WORKOUT_SUMMARY -> WorkoutSummaryScreen(host, ownerId, unit)
            FitnessScreen.CARDIO_SESSION -> CardioSessionScreen(host, ownerId)
            FitnessScreen.CARDIO_SUMMARY -> CardioSummaryScreen(host, ownerId)
            FitnessScreen.MEALS -> MealScreen(host, ownerId, today, unit)
            FitnessScreen.SUPPLEMENTS -> SupplementScreen(host, ownerId, today)
            FitnessScreen.ROUTINE_DETAIL -> RoutineDetailScreen(host, ownerId)
            FitnessScreen.ROUTINE_ADD,
            FitnessScreen.WORKOUT_EXERCISE_ADD -> ExercisePickerScreen(host, ownerId, screen)
        }
    }
}

@Composable
private fun StateMessage(title: String, message: String) {
    AppHeader(title)
    AppCard(Modifier.fillMaxWidth()) { Text(message, Modifier.padding(AppSpacing.card)) }
}

@Composable
private fun WorkoutOverview(host: ScreenHost, ownerId: String, today: String, unit: MassUnit) {
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
private fun StrengthScreen(host: ScreenHost, ownerId: String) {
    val home by host.homeViewModel().uiState.observeAsState(HomeUiState.Idle)
    val routine by host.routineEntryViewModel().uiState.observeAsState(RoutineEntryUiState.Idle)
    val ready = home as? HomeUiState.Ready
    val routineReady = routine as? RoutineEntryUiState.Ready
    AppHeader("무산소", "루틴을 선택해 운동을 시작하세요.")
    if (ready == null || ready.snapshot.ownerId != ownerId || routineReady?.ownerId != ownerId) {
        Text("루틴을 불러오는 중입니다.")
        return
    }
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
private fun CardioStartScreen(host: ScreenHost) {
    AppHeader("유산소")
    com.yeonsik.fitnessapp.cardio.CardioActivityType.values().forEach { type ->
        AppOutlinedButton(onClick = { host.startCardioWorkout(type) }, Modifier.fillMaxWidth()) {
            Text(type.labelKo())
        }
    }
}


@Composable
private fun RecordsScreen(host: ScreenHost, ownerId: String, today: String, unit: MassUnit) {
    val state by host.homeViewModel().uiState.observeAsState(HomeUiState.Idle)
    LaunchedEffect(ownerId, today) { host.homeViewModel().enter(AccountScope(ownerId), today) }
    val ready = state as? HomeUiState.Ready
    AppHeader("기록", today)
    if (ready == null || ready.snapshot.ownerId != ownerId) {
        Text("기록을 불러오는 중입니다.")
        return
    }
    val snapshot = ready.snapshot
    val metrics = snapshot.dayMetrics[today]
    FitnessFactRow(
        first = { FitnessFactCard("운동", "${snapshot.todaySessions.size}회", "완료 기록") },
        second = { FitnessFactCard("볼륨", MassFormatter.withUnit(metrics?.totalVolumeKg ?: 0.0, unit), "완료 세트") }
    )
    snapshot.todayBodyMetrics.forEach { AppDataRow("체중", MassFormatter.withUnit(it.weightKg, unit)) }
    snapshot.todayMeals.forEach { AppDataRow(it.mealLabel, it.previewTitle) }
    AppOutlinedButton(onClick = { host.showBodyMetricDialog(today, null) }, Modifier.fillMaxWidth()) { Text("체중 기록") }
    AppOutlinedButton(onClick = { host.openMealManagement(today, FitnessScreen.RECORDS) }, Modifier.fillMaxWidth()) { Text("식사 기록") }
}

@Composable
private fun DevelopmentScreen(host: ScreenHost, ownerId: String, today: String, unit: MassUnit) {
    val state by host.developmentViewModel().uiState.observeAsState(DevelopmentUiState.Idle)
    LaunchedEffect(ownerId, today) { host.developmentViewModel().enter(AccountScope(ownerId), today) }
    val ready = state as? DevelopmentUiState.Ready
    AppHeader("발전", "최근 기록을 기반으로 계산합니다.")
    if (ready == null || ready.ownerId != ownerId) {
        Text("발전 리포트를 불러오는 중입니다.")
        return
    }
    val report = ready.report
    FitnessFactRow(
        first = { FitnessFactCard("이번 주 운동", "${report.currentWeekCompletedWorkoutSessions}회", "완료 세션") },
        second = { FitnessFactCard("최근 체중", report.latestWeightKg?.let { MassFormatter.withUnit(it, unit) } ?: "미기록", "기록 기준") }
    )
    Text("신체 정보", fontWeight = FontWeight.Bold)
    AppDataRow(
        "기준 신체 정보",
        "키 ${report.bodyProfile.heightLabelKo()} · 체중 " +
            (report.latestWeightKg?.let { MassFormatter.withUnit(it, unit) } ?: "미기록")
    )
    AppOutlinedButton(onClick = { host.showDevelopmentBodyProfileDialog() }, Modifier.fillMaxWidth()) {
        Text("신체 정보 수정")
    }
    Text("발전 목표", fontWeight = FontWeight.Bold)
    AppDataRow(
        report.goal.objectiveLabelKo(),
        "${report.goal.focusBodyPartLabelKo()} · 주 ${report.goal.weeklySessionsTarget ?: 0}일"
    )
    AppOutlinedButton(onClick = { host.showDevelopmentGoalDialog() }, Modifier.fillMaxWidth()) {
        Text("발전 목표 수정")
    }
    Text("우선 행동", fontWeight = FontWeight.Bold)
    if (report.insights.isEmpty()) Text("현재 기록에서 추가로 경고할 우선 행동이 없습니다.")
    report.insights.forEach { insight ->
        AppCard(Modifier.fillMaxWidth().clickable { host.openDevelopmentInsightAction(insight) }) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text(insight.title, fontWeight = FontWeight.Bold)
                Text(insight.evidence)
                Text(insight.nextAction)
            }
        }
    }
    Text("훈련 부위 근거", fontWeight = FontWeight.Bold)
    report.recentCompletedStrengthSetsByBodyPart.forEach { (part, sets) -> Text("$part · ${sets}세트") }
    Text("영양·회복 근거", fontWeight = FontWeight.Bold)
    Text("식사 ${report.mealRecordedDays}일 · 체크인 ${report.checkInRecordedDays}일")
    Text("논문 기반 점검", fontWeight = FontWeight.Bold)
    Text("적용 준비도")
    Text("판단 근거 범위", fontWeight = FontWeight.Bold)
    Text(
        "${report.dataCoverage.windowDays}일 중 기록 ${report.dataCoverage.daysWithAnyData}일 · " +
            "운동 ${report.dataCoverage.workoutRecordedDays}일 · 체중 ${report.dataCoverage.weightRecordedDays}일"
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SettingsScreen(host: ScreenHost) {
    var advancedConnectionsVisible by rememberSaveable { mutableStateOf(false) }
    AppHeader("설정")
    Text("표시 단위")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        MassUnit.values().forEach { unit ->
            AppOutlinedButton(onClick = { host.setPreferredMassUnit(unit) }, selected = host.preferredMassUnit() == unit) { Text(unit.labelKo()) }
        }
    }
    Text("테마")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        listOf("light", "dark", "system").forEach { mode ->
            AppOutlinedButton(onClick = { host.setThemeMode(mode) }, selected = host.themeMode() == mode) { Text(mode) }
        }
    }
    AppButton(onClick = { host.runManualSync() }, enabled = !host.isManualSyncing(),
        modifier = Modifier.fillMaxWidth()) { Text(host.syncLabel()) }
    Text(host.syncDetail())
    AppOutlinedButton(onClick = { host.openFleekDataImport() }, enabled = !host.isDataImporting(),
        modifier = Modifier.fillMaxWidth()) { Text("FLEEK 가져오기") }
    AppOutlinedButton(onClick = { host.openWorkoutTransferImport() }, enabled = !host.isDataTransferInProgress(),
        modifier = Modifier.fillMaxWidth()) { Text("운동 전송 가져오기") }
    AppOutlinedButton(onClick = { host.exportWorkoutTransfer() }, enabled = !host.isDataTransferInProgress(),
        modifier = Modifier.fillMaxWidth()) { Text("운동 전송 내보내기") }
    AppOutlinedButton(onClick = { host.createLocalBackup() }, Modifier.fillMaxWidth()) { Text("로컬 백업") }
    AppOutlinedButton(onClick = { host.restoreLocalBackup() }, Modifier.fillMaxWidth()) { Text("백업 복원") }
    AppOutlinedButton(onClick = { host.exportRecordsCsv() }, Modifier.fillMaxWidth()) { Text("CSV 내보내기") }
    if (host.isDeveloperSurfaceAllowed()) {
        AppOutlinedButton(
            onClick = { advancedConnectionsVisible = !advancedConnectionsVisible },
            Modifier.fillMaxWidth()
        ) { Text(if (advancedConnectionsVisible) "연결 설정 접기" else "연결 설정") }
        if (advancedConnectionsVisible) {
            ConnectionAccountSection(
                "Personal OS 공통 DB", host.supabaseConfig(), host.isSharedSupabaseConnectionManaged(),
                host::saveSupabaseConfig, host::signInToSupabase, host::signUpToSupabase,
                host::signOutFromSupabase
            )
            ConnectionAccountSection(
                "영양 전용 DB", host.nutritionSupabaseConfig(), host.isNutritionSupabaseConnectionManaged(),
                host::saveNutritionSupabaseConfig, host::signInToNutritionSupabase,
                host::signUpToNutritionSupabase, host::signOutFromNutritionSupabase
            )
            ConnectionAccountSection(
                "PriceTrace DB", host.priceTraceSupabaseConfig(), host.isPriceTraceSupabaseConnectionManaged(),
                host::savePriceTraceSupabaseConfig, host::signInToPriceTraceSupabase,
                host::signUpToPriceTraceSupabase, host::signOutFromPriceTraceSupabase
            )
        }
    } else {
        AccountControls(
            host.supabaseConfig(), host::signInToSupabase, host::signUpToSupabase,
            host::signOutFromSupabase
        )
    }
}

@Composable
private fun ConnectionAccountSection(
    title: String,
    config: com.yeonsik.fitnessapp.config.SupabaseConfig,
    managed: Boolean,
    saveConnection: (String, String) -> Unit,
    signIn: (String, String) -> Unit,
    signUp: (String, String) -> Unit,
    signOut: () -> Unit
) {
    var url by rememberSaveable(config.supabaseUrl) { mutableStateOf(config.supabaseUrl) }
    var key by rememberSaveable(config.supabaseAnonKey) { mutableStateOf(config.supabaseAnonKey) }
    Text(title, fontWeight = FontWeight.Bold)
    Text(if (config.isConfigured) "로그인됨 · ${config.email}" else if (config.isConnectionConfigured) "로그인 필요" else "연결 없음")
    if (!managed) {
        AppTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("DB URL") })
        AppTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("DB anon key") })
        AppOutlinedButton(onClick = { saveConnection(url, key) }, Modifier.fillMaxWidth()) { Text("연결 저장") }
    } else {
        Text("빌드 기본값으로 연결되었습니다.")
    }
    AccountControls(config, signIn, signUp, signOut)
}

@Composable
private fun AccountControls(
    config: com.yeonsik.fitnessapp.config.SupabaseConfig,
    signIn: (String, String) -> Unit,
    signUp: (String, String) -> Unit,
    signOut: () -> Unit
) {
    var email by rememberSaveable(config.email) { mutableStateOf(config.email) }
    var password by rememberSaveable(config.projectRef()) { mutableStateOf("") }
    if (config.isConfigured) {
        AppOutlinedButton(onClick = signOut, Modifier.fillMaxWidth()) { Text("로그아웃") }
    } else if (config.isConnectionConfigured) {
        AppTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("이메일") })
        AppTextField(
            password, { password = it }, Modifier.fillMaxWidth(), label = { Text("비밀번호") },
            visualTransformation = PasswordVisualTransformation()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
            AppButton(onClick = { signIn(email, password) }, Modifier.weight(1f)) { Text("로그인") }
            AppOutlinedButton(onClick = { signUp(email, password) }, Modifier.weight(1f)) { Text("계정 만들기") }
        }
    }
}

@Composable
private fun WorkoutSessionScreen(host: ScreenHost, ownerId: String, unit: MassUnit) {
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
private fun WorkoutDetailScreen(host: ScreenHost, ownerId: String, unit: MassUnit) {
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
private fun WorkoutSummaryScreen(host: ScreenHost, ownerId: String, unit: MassUnit) {
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
private fun CardioSessionScreen(host: ScreenHost, ownerId: String) {
    val state by host.cardioSessionViewModel().uiState.observeAsState(CardioSessionUiState.Idle)
    val ready = state as? CardioSessionUiState.Ready
    LaunchedEffect(ownerId, ready?.session?.recordId, ready?.session?.status) {
        while (ready?.session?.status != CardioSessionSnapshot.STATUS_COMPLETED) {
            delay(1_000)
            host.refreshCardioSession()
        }
    }
    AppHeader(ready?.session?.activityLabel ?: "유산소 진행", back = { host.back() })
    if (ready == null || ready.ownerId != ownerId) {
        Text("GPS 기록을 불러오는 중입니다.")
        return
    }
    val session = ready.session
    FitnessFactRow(
        first = { FitnessFactCard("시간", FitnessUi.formatElapsed(session.elapsedSeconds(System.currentTimeMillis())), "진행 시간") },
        second = { FitnessFactCard("거리", String.format("%.2f km", session.distanceMeters / 1_000.0), session.activityLabel) }
    )
    Text("GPS · ${session.gpsStatus} · ${session.acceptedPointCount} points")
    if (session.status == CardioSessionSnapshot.STATUS_TRACKING) {
        AppButton(onClick = { host.pauseCardioWorkout() }, Modifier.fillMaxWidth()) { Text("일시정지") }
    } else if (session.status == CardioSessionSnapshot.STATUS_PAUSED) {
        AppButton(onClick = { host.resumeCardioWorkout() }, Modifier.fillMaxWidth()) { Text("계속") }
    }
    AppOutlinedButton(onClick = { host.editCardioAverageHeartRate() }, Modifier.fillMaxWidth()) { Text("평균 심박수") }
    AppButton(onClick = { host.finishCardioWorkout() }, Modifier.fillMaxWidth()) { Text("완료") }
    AppOutlinedButton(onClick = { host.cancelCardioWorkout() }, Modifier.fillMaxWidth()) { Text("취소") }
}

@Composable
private fun CardioSummaryScreen(host: ScreenHost, ownerId: String) {
    val state by host.cardioSessionViewModel().uiState.observeAsState(CardioSessionUiState.Idle)
    val ready = state as? CardioSessionUiState.Ready
    AppHeader("유산소 요약", back = { host.back() })
    if (ready == null || ready.ownerId != ownerId) {
        Text("요약을 불러오는 중입니다.")
        return
    }
    val session = ready.session
    var route by remember(session.recordId) { mutableStateOf<CardioRouteProjection?>(null) }
    var routeError by remember(session.recordId) { mutableStateOf<String?>(null) }
    LaunchedEffect(session.recordId) {
        host.loadCardioRoute(session.recordId, object : ScreenHost.CardioRouteCallback {
            override fun onComplete(projection: CardioRouteProjection) {
                route = projection
                routeError = null
            }

            override fun onError(error: Exception) {
                route = null
                routeError = error.message ?: "경로를 불러오지 못했습니다."
            }
        })
    }
    Text(session.activityLabel, fontWeight = FontWeight.Bold)
    FitnessFactRow(
        first = { FitnessFactCard("시간", FitnessUi.formatElapsed(session.elapsedSeconds(System.currentTimeMillis())), "완료 기록") },
        second = { FitnessFactCard("거리", String.format("%.2f km", session.distanceMeters / 1_000.0), "완료 기록") }
    )
    Text(session.averageHeartRateBpm?.let { String.format("평균 심박 %.0f bpm", it) } ?: "평균 심박 미기록")
    when {
        routeError != null -> Text(routeError!!)
        route == null -> Text("경로를 불러오는 중입니다.")
        !route!!.hasRenderablePath() -> Text("표시할 GPS 경로가 없습니다.")
        !BuildConfig.MAPS_API_KEY_CONFIGURED -> Text("지도 키가 설정되지 않아 경로 지도를 표시할 수 없습니다.")
        else -> CardioRouteMap(route!!)
    }
}

@Composable
private fun MealScreen(host: ScreenHost, ownerId: String, today: String, unit: MassUnit) {
    val homeState by host.homeViewModel().uiState.observeAsState(HomeUiState.Idle)
    val editorState by host.mealViewModel().uiState.observeAsState(MealUiState.Idle)
    LaunchedEffect(ownerId, today) {
        host.homeViewModel().enter(AccountScope(ownerId), today)
        host.mealViewModel().enter(AccountScope(ownerId), today)
    }
    val ready = homeState as? HomeUiState.Ready
    val editor = editorState as? MealUiState.Ready
    LaunchedEffect(editor?.notice) {
        if (editor?.notice != null) {
            host.homeViewModel().enter(AccountScope(ownerId), today)
        }
    }

    AppHeader("식사", today, back = { host.back() })
    if (ready == null || ready.snapshot.ownerId != ownerId) {
        Text("식사 기록을 불러오는 중입니다.")
        return
    }
    val snapshot = ready.snapshot
    val totals = snapshot.mealNutritionTotals[today]
    FitnessFactRow(
        first = { FitnessFactCard("식사", "${snapshot.todayMeals.size}끼", today) },
        second = { FitnessFactCard("열량", totals?.total("calories_kcal")?.describedValue() ?: "?", "kcal") }
    )
    snapshot.todayMeals.forEach { meal ->
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text(meal.previewTitle, fontWeight = FontWeight.Bold)
                Text(meal.previewSubtitle())
                Text("${meal.calories} kcal · 단백질 ${meal.proteinGrams}g")
            }
        }
    }

    if (editor == null || editor.ownerId != ownerId || editor.date != today) {
        Text("식사 입력을 준비하는 중입니다.")
    } else if (!editor.editing) {
        editor.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        AppButton(onClick = { host.mealViewModel().startDraft() }, Modifier.fillMaxWidth()) {
            Text("새 끼니 기록")
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
            AppOutlinedButton(
                onClick = { host.mealViewModel().chooseFood() },
                modifier = Modifier.weight(1f),
                selected = !editor.diningOut
            ) { Text("식단") }
            AppOutlinedButton(
                onClick = { host.mealViewModel().chooseDiningOut() },
                modifier = Modifier.weight(1f),
                selected = editor.diningOut
            ) { Text("외식") }
        }
        if (editor.diningOut) {
            DiningOutEditor(host, ownerId, editor)
        } else {
            FoodMealEditor(host, ownerId, editor)
        }
    }

    AppOutlinedButton(onClick = { host.showBodyMetricDialog(today, null) }, Modifier.fillMaxWidth()) {
        Text("오늘 체중 · ${snapshot.todayWeight?.let { MassFormatter.withUnit(it.weightKg, unit) } ?: "미기록"}")
    }
}

@Composable
private fun FoodMealEditor(host: ScreenHost, ownerId: String, editor: MealUiState.Ready) {
    AppTextField(
        editor.query,
        { host.mealViewModel().search(it) },
        Modifier.fillMaxWidth(),
        label = { Text("식품 검색") }
    )
    editor.searchResults.forEach { food ->
        AppCard(Modifier.fillMaxWidth().clickable { host.mealViewModel().selectFood(food) }) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text(food.displayName(), fontWeight = FontWeight.Bold)
                Text("${food.basisLabel()} · ${food.extendedNutritionLabel()}")
            }
        }
    }
    editor.selectedFood?.let { food ->
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text("선택 · ${food.displayName()}", fontWeight = FontWeight.Bold)
                Text(food.basisLabel())
            }
        }
        AppTextField(
            editor.quantity,
            { host.mealViewModel().updateQuantity(it) },
            Modifier.fillMaxWidth(),
            label = { Text("섭취량 ${food.basisUnit}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
    AppTextField(
        editor.draft.time,
        { host.mealViewModel().updateTime(it) },
        Modifier.fillMaxWidth(),
        label = { Text("식사 시각 HH:mm") }
    )
    editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        AppOutlinedButton(
            onClick = { host.mealViewModel().closeDraft() },
            modifier = Modifier.weight(1f),
            enabled = !editor.saving
        ) { Text("취소") }
        AppButton(
            onClick = { host.mealViewModel().saveFood(AccountScope(ownerId)) { } },
            modifier = Modifier.weight(1f),
            enabled = !editor.saving && editor.selectedFood != null
        ) { Text(if (editor.saving) "저장 중" else "끼니 기록하기") }
    }
}

@Composable
private fun DiningOutEditor(host: ScreenHost, ownerId: String, editor: MealUiState.Ready) {
    val draft = editor.draft
    Text("외식 직접 등록", style = MaterialTheme.typography.titleMedium)
    AppTextField(draft.store, { host.mealViewModel().updateStore(it) }, Modifier.fillMaxWidth(), { Text("상호명") })
    AppTextField(draft.branch, { host.mealViewModel().updateBranch(it) }, Modifier.fillMaxWidth(), { Text("지점명 (선택)") })
    AppTextField(draft.menu, { host.mealViewModel().updateMenu(it) }, Modifier.fillMaxWidth(), { Text("메뉴명") })
    AppTextField(draft.time, { host.mealViewModel().updateTime(it) }, Modifier.fillMaxWidth(), { Text("식사 시각 HH:mm") })
    AppTextField(draft.calories, { host.mealViewModel().updateCalories(it) }, Modifier.fillMaxWidth(), { Text("칼로리 kcal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.carbs, { host.mealViewModel().updateCarbs(it) }, Modifier.fillMaxWidth(), { Text("탄수화물 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.protein, { host.mealViewModel().updateProtein(it) }, Modifier.fillMaxWidth(), { Text("단백질 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.fat, { host.mealViewModel().updateFat(it) }, Modifier.fillMaxWidth(), { Text("지방 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.sodium, { host.mealViewModel().updateSodium(it) }, Modifier.fillMaxWidth(), { Text("나트륨 mg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.sugars, { host.mealViewModel().updateSugars(it) }, Modifier.fillMaxWidth(), { Text("당류 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    AppTextField(draft.saturatedFat, { host.mealViewModel().updateSaturatedFat(it) }, Modifier.fillMaxWidth(), { Text("포화지방 g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        AppOutlinedButton(
            onClick = { host.mealViewModel().closeDraft() },
            modifier = Modifier.weight(1f),
            enabled = !editor.saving
        ) { Text("취소") }
        AppButton(
            onClick = { host.mealViewModel().save(AccountScope(ownerId)) { } },
            modifier = Modifier.weight(1f),
            enabled = !editor.saving
        ) { Text(if (editor.saving) "저장 중" else "외식만 기록") }
    }
}
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SupplementScreen(host: ScreenHost, ownerId: String, today: String) {
    val state by host.supplementViewModel().uiState.observeAsState(SupplementUiState.Idle)
    LaunchedEffect(ownerId, today) { host.supplementViewModel().enter(AccountScope(ownerId), today) }
    val ready = state as? SupplementUiState.Ready
    AppHeader("보충제", today, back = { host.back() })
    if (ready == null || ready.ownerId != ownerId) {
        Text("보충제 계획을 불러오는 중입니다.")
        return
    }
    Text("복용 ${ready.progress.taken}/${ready.progress.planned} · 14일 준수율 ${ready.adherence.adherencePercent()}%")
    ready.plans.forEach { plan ->
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AppSpacing.card), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text("${plan.typeName} · ${plan.brandName}", fontWeight = FontWeight.Bold)
                Text("${plan.doseAmount}${plan.doseUnit} · ${plan.recordedCount()}/${plan.timesPerDay}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    AppButton(onClick = { host.supplementViewModel().record(AccountScope(ownerId), today, plan.scheduleId, "taken") },
                        enabled = plan.unrecordedCount() > 0) { Text("복용") }
                    AppOutlinedButton(onClick = { host.supplementViewModel().record(AccountScope(ownerId), today, plan.scheduleId, "skipped") },
                        enabled = plan.unrecordedCount() > 0) { Text("건너뜀") }
                    AppOutlinedButton(onClick = { host.supplementViewModel().undo(AccountScope(ownerId), today, plan.scheduleId) },
                        enabled = plan.recordedCount() > 0) { Text("되돌리기") }
                }
            }
        }
    }
}

@Composable
private fun RoutineDetailScreen(host: ScreenHost, ownerId: String) {
    val home by host.homeViewModel().uiState.observeAsState(HomeUiState.Idle)
    val ready = home as? HomeUiState.Ready
    AppHeader("루틴", back = { host.back() })
    if (ready == null || ready.snapshot.ownerId != ownerId) {
        Text("루틴을 불러오는 중입니다.")
        return
    }
    val routineId = host.selectedRoutineId() ?: ready.snapshot.activeRoutineId
    val routine = ready.snapshot.routines.firstOrNull { it.id == routineId }
    Text(routine?.name ?: "기본 루틴", fontWeight = FontWeight.Bold)
    val exercises = ready.snapshot.routineExercises[routineId].orEmpty()
    exercises.forEach { AppDataRow("${it.order + 1}. ${it.nameKo}", it.equipment) }
    AppOutlinedButton(onClick = { host.navigate(FitnessScreen.ROUTINE_ADD) }, Modifier.fillMaxWidth()) { Text("종목 추가") }
    AppButton(onClick = { host.startRoutineWorkout(exercises) }, enabled = exercises.isNotEmpty(),
        modifier = Modifier.fillMaxWidth()) { Text("이 루틴으로 시작") }
}

@Composable
private fun ExercisePickerScreen(host: ScreenHost, ownerId: String, screen: FitnessScreen) {
    val state by host.exercisePickerViewModel().uiState.observeAsState(ExercisePickerUiState.Idle)
    val ready = state as? ExercisePickerUiState.Ready
    AppHeader(if (screen == FitnessScreen.ROUTINE_ADD) "루틴 종목 추가" else "운동 종목 선택",
        back = { host.back() })
    if (ready == null || ready.ownerId != ownerId || ready.mode != screen) {
        Text("운동 종목을 불러오는 중입니다.")
        return
    }
    AppTextField(ready.query, { host.exercisePickerViewModel().search(it) },
        label = { Text("종목 검색") }, modifier = Modifier.fillMaxWidth())
    ready.presets.take(100).forEach { preset ->
        AppCard(Modifier.fillMaxWidth().clickable { host.exercisePickerViewModel().choose(preset) }) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text(preset.displayName(), fontWeight = FontWeight.Bold)
                Text(listOfNotNull(preset.familyNameKo, preset.equipmentNameKo).joinToString(" · "))
            }
        }
    }
}

private object AppSpacing {
    val small = 8.dp
    val gap = 12.dp
    val card = 16.dp
    val section = 24.dp
    val touch = 48.dp
}

@Composable
private fun AppHeader(title: String, subtitle: String? = null, back: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = AppSpacing.small),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        if (back != null) androidx.compose.material3.TextButton(onClick = back) { Text("뒤로") }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), content = content)
}

@Composable
private fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                      content: @Composable RowScope.() -> Unit) {
    Button(onClick, modifier.heightIn(min = AppSpacing.touch), enabled = enabled,
        contentPadding = PaddingValues(horizontal = AppSpacing.card, vertical = AppSpacing.small), content = content)
}

@Composable
private fun AppOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                              destructive: Boolean = false, selected: Boolean = false,
                              content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier.heightIn(min = AppSpacing.touch), enabled = enabled,
        border = BorderStroke(1.dp, when {
            destructive -> MaterialTheme.colorScheme.error
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        }), content = content)
}

@Composable
private fun AppTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
                         label: @Composable (() -> Unit)? = null, singleLine: Boolean = true,
                         keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
                         keyboardActions: KeyboardActions? = null,
                         visualTransformation: VisualTransformation = VisualTransformation.None) {
    OutlinedTextField(value, onValueChange, modifier, label = label, singleLine = singleLine,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions ?: KeyboardActions.Default,
        visualTransformation = visualTransformation)
}

@Composable
private fun AppDataRow(title: String, detail: String, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.card)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
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