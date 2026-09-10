package com.yeonsik.fitnessapp.feature.records.ui

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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.BuildConfig
import com.yeonsik.fitnessapp.cardio.*
import com.yeonsik.fitnessapp.config.*
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
import kotlinx.coroutines.delay
import java.time.LocalDate

interface RecordsScreenActions {
    fun selectDate(date: String)
    fun openRecord(recordId: String)
    fun deleteRecord(recordId: String)
    fun showBodyMetric(date: String, recordId: String?)
    fun openMeals(date: String)
}

@Composable
internal fun RecordsScreen(
    state: HomeUiState,
    ownerId: String,
    today: String,
    unit: MassUnit,
    selectedDate: String,
    actions: RecordsScreenActions
) {
    val ready = state as? HomeUiState.Ready
    AppHeader("기록", selectedDate)
    if (ready == null || ready.snapshot.ownerId != ownerId) {
        Text("기록을 불러오는 중입니다.")
        return
    }
    val snapshot = ready.snapshot
    val date = snapshot.today
    val metrics = snapshot.dayMetrics[date]
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppOutlinedButton(
            onClick = {
                val previous = runCatching { LocalDate.parse(date).minusDays(1).toString() }
                    .getOrDefault(date)
                actions.selectDate(previous)
            },
            modifier = Modifier.weight(1f)
        ) { Text("이전 날짜") }
        Text(date, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        AppOutlinedButton(
            onClick = {
                val next = runCatching { LocalDate.parse(date).plusDays(1).toString() }
                    .getOrDefault(date)
                if (next <= today) actions.selectDate(next)
            },
            enabled = date < today,
            modifier = Modifier.weight(1f)
        ) { Text("다음 날짜") }
    }
    FitnessFactRow(
        first = { FitnessFactCard("운동", "${snapshot.todaySessions.size}회", "완료 기록") },
        second = { FitnessFactCard("볼륨", MassFormatter.withUnit(metrics?.totalVolumeKg ?: 0.0, unit), "완료 세트") }
    )
    if (snapshot.todaySessions.isNotEmpty()) {
        Text("운동 기록", fontWeight = FontWeight.Bold)
        snapshot.todaySessions.forEach { recordId ->
            AppCard(Modifier.fillMaxWidth().clickable { actions.openRecord(recordId) }) {
                Row(
                    Modifier.padding(AppSpacing.card),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("운동 기록", fontWeight = FontWeight.Bold)
                        Text(recordId.take(8), style = MaterialTheme.typography.bodySmall)
                    }
                    AppOutlinedButton(onClick = { actions.deleteRecord(recordId) }) { Text("삭제") }
                }
            }
        }
    }
    snapshot.todayBodyMetrics.forEach { metric ->
        AppCard(Modifier.fillMaxWidth().clickable {
            actions.showBodyMetric(metric.date, metric.id)
        }) {
            Row(
                Modifier.padding(AppSpacing.card),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("체중", fontWeight = FontWeight.Bold)
                    Text(MassFormatter.withUnit(metric.weightKg, unit))
                }
                Text("수정", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    snapshot.todayMeals.forEach { AppDataRow(it.mealLabel, it.previewTitle) }
    AppOutlinedButton(
        onClick = { actions.showBodyMetric(date, null) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("체중 기록") }
    AppOutlinedButton(onClick = { actions.openMeals(date) }, Modifier.fillMaxWidth()) { Text("식사 기록") }
}
