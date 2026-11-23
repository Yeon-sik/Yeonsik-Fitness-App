package com.yeonsik.fitness.shared.feature.dailyworkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Instant

interface DailyWorkoutDeviceAccess {
    suspend fun readWearables(day: WorkoutDay): DailyWearableRecords?
    suspend fun pickFleekCsv(): String?
    suspend fun exportJson(name: String, json: String): Boolean
}

@Composable
fun DailyWorkoutScreen(
    initialDate: String,
    repository: DailyWorkoutRepository,
    device: DailyWorkoutDeviceAccess,
    onClose: () -> Unit
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    var date by rememberSaveable(initialDate) { mutableStateOf(initialDate) }
    val day = remember(date, zone) { WorkoutDay(LocalDate.parse(date), zone) }
    val scope = rememberCoroutineScope()
    var report by remember(day, repository) { mutableStateOf<DailyWorkoutReport?>(null) }
    var busy by remember(day, repository) { mutableStateOf(true) }
    var message by remember(day, repository) { mutableStateOf<String?>(null) }

    LaunchedEffect(day, repository) {
        try {
            report = repository.load(day)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            message = "하루 운동 기록을 읽지 못했습니다."
        } finally {
            busy = false
        }
    }

    fun runOperation(failure: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalArgumentException) {
                message = error.message ?: failure
            } catch (error: Exception) {
                message = failure
            } finally {
                busy = false
            }
        }
    }

    DailyWorkoutContent(
        state = DailyWorkoutScreenState(day, report, busy, message),
        onDate = { date = it.toString() },
        onClose = onClose,
        onSync = {
            runOperation("워치 기록을 읽지 못했습니다. 건강 앱 권한과 기록을 확인해 주세요.") {
                val records = device.readWearables(day)
                if (records != null) {
                    report = repository.saveWearables(day, records)
                    message = "워치 기록을 불러왔습니다."
                } else {
                    message = "읽을 수 있는 워치 기록이 없습니다. 날짜와 건강 앱 권한을 확인해 주세요."
                }
            }
        },
        onImport = {
            runOperation("FLEEK 파일을 가져오지 못했습니다.") {
                device.pickFleekCsv()?.let {
                    report = repository.importFleek(day, it)
                    message = "FLEEK 기록을 가져왔습니다."
                }
            }
        },
        onExport = {
            runOperation("파일을 저장하지 못했습니다.") {
                val snapshot = report ?: return@runOperation
                if (device.exportJson("fitness-${day.date}.json", snapshot.toJson())) {
                    message = "JSON 파일을 저장했습니다."
                }
            }
        },
        onDecision = { candidate, same ->
            runOperation("운동 연결 결정을 저장하지 못했습니다.") { report = repository.decide(day, candidate, same) }
        },
        onUndo = { decision ->
            runOperation("운동 연결 결정을 취소하지 못했습니다.") { report = repository.undo(day, decision) }
        }
    )
}

private data class DailyWorkoutScreenState(
    val day: WorkoutDay,
    val report: DailyWorkoutReport?,
    val busy: Boolean,
    val message: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyWorkoutContent(
    state: DailyWorkoutScreenState,
    onDate: (LocalDate) -> Unit,
    onClose: () -> Unit,
    onSync: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDecision: (DailyWorkoutCandidate, Boolean) -> Unit,
    onUndo: (DailyWorkoutDecision) -> Unit
) {
    var selectDate by rememberSaveable { mutableStateOf(false) }
    val report = state.report
    Scaffold { insets ->
        LazyColumn(
            Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("하루 운동 기록", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onClose) { Text("닫기") }
                }
                OutlinedButton({ selectDate = true }, enabled = !state.busy, modifier = Modifier.testTag("daily-workout-date")) {
                    Text(state.day.date.toString())
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onSync, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("워치 기록 불러오기") }
                    OutlinedButton(onImport, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("FLEEK CSV 가져오기") }
                    Button(onExport, Modifier.fillMaxWidth(), enabled = !state.busy && report != null) { Text("AI용 JSON 저장") }
                    if (state.busy) Text("처리 중…") else state.message?.let { Text(it) }
                    if (report != null) {
                        Text("운동 ${report.sessions.size}회", style = MaterialTheme.typography.titleMedium)
                        if (report.pendingCount > 0) Text("확인 대기 ${report.pendingCount}건은 추가 집계가 보류됩니다.")
                    }
                }
            }
            if (report != null) {
                items(report.candidates) { candidate ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("같은 운동인지 확인", style = MaterialTheme.typography.titleMedium)
                            Text(candidate.first.summary(state.day.zone))
                            Text(candidate.second.summary(state.day.zone))
                            TextButton({ onDecision(candidate, true) }, enabled = !state.busy) { Text("같은 운동으로 연결") }
                            TextButton({ onDecision(candidate, false) }, enabled = !state.busy) { Text("별개 운동으로 집계") }
                        }
                    }
                }
                items(report.sessions, key = { it.primary.key }) { session ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(session.primary.title, style = MaterialTheme.typography.titleMedium)
                            Text(session.timing.summary(state.day.zone))
                            if (session.sources.size > 1) Text(session.sources.joinToString(" · ") { it.writerLabel() })
                            session.details?.let { details -> Text("${details.exercises.size}종목 · ${details.exercises.sumOf { it.sets.size }}세트") }
                            session.heartRate?.let { Text("평균 ${it.averageBpm.roundToInt()} bpm · ${it.sampleCount}개 측정") }
                        }
                    }
                }
                if (report.unlinkedHeartRate.isNotEmpty()) item {
                    Text("운동에 연결되지 않은 심박 · ${report.unlinkedHeartRate.sumOf { it.sampleCount }}개 측정")
                }
                items(report.decisions) { decision ->
                    val first = report.sources.getValue(decision.first)
                    val second = report.sources.getValue(decision.second)
                    TextButton({ onUndo(decision) }, enabled = !state.busy) {
                        val action = if (decision.sameWorkout) "연결 취소" else "별개 운동 결정 취소"
                        Text("$action · ${first.title} / ${second.title}")
                    }
                }
            }
        }
    }
    if (selectDate) {
        val picker = rememberDatePickerState(state.day.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { selectDate = false },
            confirmButton = {
                TextButton({
                    picker.selectedDateMillis?.let { onDate(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) }
                    selectDate = false
                }, enabled = picker.selectedDateMillis != null) { Text("선택") }
            },
            dismissButton = { TextButton({ selectDate = false }) { Text("취소") } }
        ) { DatePicker(picker) }
    }
}

private fun DailyWorkoutSource.summary(zone: TimeZone): String {
    val time = startMillis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).time.toString().take(5) }
    return "$title · ${writerLabel()} · ${time ?: "시각 미상"}"
}

private fun DailyWorkoutSource.writerLabel(): String = when {
    writer == "com.sec.android.app.shealth" -> "Samsung Health"
    writer.contains("fleek", ignoreCase = true) -> "FLEEK"
    origin == WorkoutOrigin.HEALTH_KIT && writer.startsWith("com.apple.") -> "Apple 건강"
    else -> writer
}
