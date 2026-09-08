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
internal fun DevelopmentScreen(host: ScreenHost, ownerId: String, today: String, unit: MassUnit) {
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

