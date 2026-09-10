package com.yeonsik.fitnessapp.feature.development.ui

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
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.development.BodyProfile
import com.yeonsik.fitnessapp.development.DevelopmentGoal
import com.yeonsik.fitnessapp.BuildConfig
import com.yeonsik.fitnessapp.app.navigation.*
import com.yeonsik.fitnessapp.cardio.*
import com.yeonsik.fitnessapp.config.*
import com.yeonsik.fitnessapp.core.account.*
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.development.DevelopmentInsight
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

interface DevelopmentScreenActions {
    fun showBodyProfile()
    fun showGoal()
    fun openInsightAction(insight: DevelopmentInsight)
}

@Composable
internal fun DevelopmentScreen(
    state: DevelopmentUiState,
    ownerId: String,
    unit: MassUnit,
    actions: DevelopmentScreenActions
) {
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
    AppOutlinedButton(onClick = actions::showBodyProfile, Modifier.fillMaxWidth()) {
        Text("신체 정보 수정")
    }
    Text("발전 목표", fontWeight = FontWeight.Bold)
    AppDataRow(
        report.goal.objectiveLabelKo(),
        "${report.goal.focusBodyPartLabelKo()} · 주 ${report.goal.weeklySessionsTarget ?: 0}일"
    )
    AppOutlinedButton(onClick = actions::showGoal, Modifier.fillMaxWidth()) {
        Text("발전 목표 수정")
    }
    Text("우선 행동", fontWeight = FontWeight.Bold)
    if (report.insights.isEmpty()) Text("현재 기록에서 추가로 경고할 우선 행동이 없습니다.")
    report.insights.forEach { insight ->
        AppCard(Modifier.fillMaxWidth().clickable { actions.openInsightAction(insight) }) {
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

interface DevelopmentEditorActions {
    fun saveProfile(
        profile: BodyProfile?,
        weightRecordId: String?,
        date: String,
        weightKg: Double?,
        memo: String
    )

    fun saveGoal(goal: DevelopmentGoal)
    fun dismiss()
    fun notify(message: String)
}

@Composable
fun DevelopmentProfileEditorDialog(
    state: DevelopmentProfileEditorUiState,
    ownerId: String,
    unit: MassUnit,
    actions: DevelopmentEditorActions
) {
    val ready = state as? DevelopmentProfileEditorUiState.Ready
    if (ready == null || ready.ownerId != ownerId) return

    val editor = ready.editor
    var height by rememberSaveable(ready.requestId) {
        mutableStateOf(editor.profile?.heightCm?.toString() ?: "")
    }
    var weight by rememberSaveable(ready.requestId) {
        mutableStateOf(editor.weightKg?.let { MassFormatter.formatInput(it, unit) } ?: "")
    }

    AlertDialog(
        onDismissRequest = actions::dismiss,
        title = { Text("바디 정보 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text("키 cm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("오늘 체중 ${unit.symbol()}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (height.trim().isEmpty() && weight.trim().isEmpty()) {
                    actions.notify("키 또는 오늘 체중을 하나 이상 입력해 주세요.")
                    return@TextButton
                }
                try {
                    val nextProfile = if (height.trim().isEmpty()) {
                        null
                    } else {
                        BodyProfile(height.trim().toInt(), "", "")
                    }
                    val nextWeightKg = if (weight.trim().isEmpty()) {
                        null
                    } else {
                        unit.toKg(weight.trim().toDouble()).also {
                            if (it < 20.0 || it > 400.0) {
                                throw IllegalArgumentException("체중은 20~400kg 범위로 입력해 주세요.")
                            }
                        }
                    }
                    actions.saveProfile(
                        nextProfile,
                        editor.weightRecordId,
                        ready.date,
                        nextWeightKg,
                        editor.weightMemo
                    )
                } catch (_: NumberFormatException) {
                    actions.notify("숫자 형식이 올바르지 않습니다.")
                } catch (error: IllegalArgumentException) {
                    actions.notify(error.message ?: "바디 정보를 저장하지 못했습니다.")
                }
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = actions::dismiss) { Text("취소") } }
    )
}

@Composable
fun DevelopmentGoalEditorDialog(
    state: DevelopmentGoalEditorUiState,
    ownerId: String,
    today: String,
    actions: DevelopmentEditorActions
) {
    val ready = state as? DevelopmentGoalEditorUiState.Ready
    if (ready == null || ready.ownerId != ownerId) return

    val currentGoal = ready.goal
    var objective by rememberSaveable(ready.requestId) {
        mutableStateOf(
            if (currentGoal.isConfigured()) currentGoal.objective
            else DevelopmentGoal.OBJECTIVE_MUSCLE_GAIN
        )
    }
    var focus by rememberSaveable(ready.requestId) {
        mutableStateOf(
            if (currentGoal.isConfigured()) currentGoal.focusBodyPart
            else DevelopmentGoal.BODY_PART_CHEST
        )
    }
    var weekly by rememberSaveable(ready.requestId) {
        mutableStateOf(currentGoal.weeklySessionsTarget?.toString() ?: "3")
    }
    var objectiveExpanded by remember { mutableStateOf(false) }
    var focusExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = actions::dismiss,
        title = { Text("발전 목표 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Box {
                    OutlinedButton(onClick = { objectiveExpanded = true }) {
                        Text("목표 · ${DevelopmentGoal.objectiveLabelKo(objective)}")
                    }
                    DropdownMenu(
                        expanded = objectiveExpanded,
                        onDismissRequest = { objectiveExpanded = false }
                    ) {
                        DevelopmentGoal.OBJECTIVES.forEach { code ->
                            DropdownMenuItem(
                                text = { Text(DevelopmentGoal.objectiveLabelKo(code)) },
                                onClick = {
                                    objective = code
                                    objectiveExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = weekly,
                    onValueChange = { weekly = it },
                    label = { Text("주간 운동 목표 1~7회") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { focusExpanded = true }) {
                        Text("집중 부위 · ${DevelopmentGoal.bodyPartLabelKo(focus)}")
                    }
                    DropdownMenu(
                        expanded = focusExpanded,
                        onDismissRequest = { focusExpanded = false }
                    ) {
                        DevelopmentGoal.FOCUS_BODY_PARTS.forEach { code ->
                            DropdownMenuItem(
                                text = { Text(DevelopmentGoal.bodyPartLabelKo(code)) },
                                onClick = {
                                    focus = code
                                    focusExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val weeklyTarget = weekly.trim().toInt()
                    val unchanged = currentGoal.isConfigured()
                        && currentGoal.objective == objective
                        && currentGoal.weeklySessionsTarget == weeklyTarget
                        && currentGoal.focusBodyPart == focus
                    actions.saveGoal(
                        DevelopmentGoal(
                            objective,
                            weeklyTarget,
                            focus,
                            if (unchanged) currentGoal.effectiveFrom else today,
                            "",
                            ""
                        )
                    )
                } catch (_: NumberFormatException) {
                    actions.notify("주간 세션은 숫자로 입력해 주세요.")
                } catch (error: IllegalArgumentException) {
                    actions.notify(error.message ?: "발전 목표를 저장하지 못했습니다.")
                }
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = actions::dismiss) { Text("취소") } }
    )
}

