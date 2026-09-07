package com.yeonsik.fitnessapp.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.livedata.observeAsState
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.core.ui.FitnessFactCard
import com.yeonsik.fitnessapp.core.ui.FitnessFactRow
import com.yeonsik.fitnessapp.core.ui.FitnessSection
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.data.MassUnit
import com.yeonsik.fitnessapp.feature.routine.ui.RoutineEntryUiState
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.ScreenHost

/**
 * The Compose entry point deliberately consumes only already-loaded UiState.
 * Navigation and writes remain explicit ScreenHost actions; it never reads a
 * repository or initializes a record while composing.
 */
object ComposeHomeScreen {
    @JvmStatic
    fun install(
        view: ComposeView,
        host: ScreenHost,
        ownerId: String,
        today: String,
        displayUnit: MassUnit,
        dark: Boolean
    ) {
        view.setContent {
            FitnessComposeTheme(dark) {
                HomeRoute(host, ownerId, today, displayUnit)
            }
        }
    }
}

@Composable
private fun HomeRoute(host: ScreenHost, ownerId: String, today: String, displayUnit: MassUnit) {
    val homeState by host.homeViewModel().uiState.observeAsState(HomeUiState.Idle)
    val routineState by host.routineEntryViewModel().uiState.observeAsState(RoutineEntryUiState.Idle)
    val ready = homeState as? HomeUiState.Ready
    val routinesReady = routineState as? RoutineEntryUiState.Ready
    if (ready == null || ready.snapshot.ownerId != ownerId || ready.snapshot.today != today
        || routinesReady == null || routinesReady.ownerId != ownerId) {
        LoadingHome()
        return
    }

    val snapshot = ready.snapshot
    val metrics = snapshot.dayMetrics[today]
    val inProgress = snapshot.inProgressSessionId != null
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("오늘의 훈련", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(today, style = MaterialTheme.typography.bodyMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (inProgress) "운동이 진행 중입니다"
                    else if (snapshot.todaySessions.isEmpty()) "오늘 운동 전입니다"
                    else "오늘 ${snapshot.todaySessions.size}회 운동했습니다",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        if (inProgress) host.continueWorkoutIfAvailable()
                        else host.navigate(FitnessScreen.WORKOUT)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (inProgress) "운동 이어가기" else "피트니스에서 시작") }
            }
        }

        FitnessFactRow(
            first = {
                FitnessFactCard(
                    "오늘 볼륨",
                    MassFormatter.format(metrics?.totalVolumeKg ?: 0.0, displayUnit),
                    displayUnit.symbol()
                )
            },
            second = {
                FitnessFactCard(
                    "완료 세트",
                    "${metrics?.totalSetCount ?: 0}개",
                    "오늘 기록"
                )
            }
        )

        FitnessSection("루틴 빠른 시작") {
            if (snapshot.routines.isEmpty()) {
                Text("만들어진 루틴이 없습니다.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { host.navigate(FitnessScreen.WORKOUT) }) { Text("운동 선택") }
            } else {
                snapshot.routines.take(2).forEach { routine ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            host.selectRoutine(routine.id)
                            host.startRoutineWorkout(snapshot.routineExercises[routine.id].orEmpty())
                        }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${routine.exerciseCount}개 종목 · 탭하여 시작", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                OutlinedButton(onClick = { host.navigate(FitnessScreen.WORKOUT) }) { Text("전체 루틴 보기") }
            }
        }

        FitnessSection("빠른 기록") {
            FitnessFactRow(
                first = {
                    FitnessFactCard(
                        "체중",
                        snapshot.todayWeight?.let { MassFormatter.format(it.weightKg, displayUnit) } ?: "미입력",
                        "오늘"
                    )
                },
                second = { FitnessFactCard("식사", "${snapshot.mealCounts[today] ?: 0}끼", "오늘") }
            )
            OutlinedButton(onClick = { host.showBodyMetricDialog() }, modifier = Modifier.fillMaxWidth()) {
                Text("체중 기록")
            }
            OutlinedButton(onClick = { host.openMealManagement() }, modifier = Modifier.fillMaxWidth()) {
                Text("식사 기록")
            }
        }

        FitnessSection("오늘 기록") {
            if (snapshot.todaySessions.isEmpty() && snapshot.todayMeals.isEmpty()) {
                Text("오늘 완료한 운동과 식사 기록이 없습니다.", style = MaterialTheme.typography.bodyMedium)
            }
            snapshot.todaySessions.take(3).forEach { sessionId ->
                Card(modifier = Modifier.fillMaxWidth().clickable { host.navigate(FitnessScreen.RECORDS) }) {
                    Text("완료 운동 기록", modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
                }
            }
            snapshot.todayMeals.take(3).forEach { meal ->
                Card(modifier = Modifier.fillMaxWidth().clickable { host.openMealManagement(today) }) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(meal.previewTitle, fontWeight = FontWeight.Bold)
                        Text(meal.previewSubtitle(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedButton(onClick = { host.navigate(FitnessScreen.RECORDS) }, modifier = Modifier.fillMaxWidth()) {
                Text("전체 기록 보기")
            }
        }
    }
}

@Composable
private fun LoadingHome() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
        Text("오늘의 훈련", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("오늘의 기록과 루틴을 불러오는 중입니다.", modifier = Modifier.padding(top = 8.dp))
    }
}
