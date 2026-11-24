package com.yeonsik.fitness.shared.feature.dailyworkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CompletedWorkoutCloudControls(day: WorkoutDay, sync: CompletedWorkoutSync?,
    export: suspend (String, String) -> Boolean, refresh: suspend () -> Unit) {
    var open by remember(day, sync) { mutableStateOf(false) }
    OutlinedButton({ open = true }, Modifier.fillMaxWidth()) { Text("완료 운동 기기 간 동기화") }
    if (!open) return
    var entries by remember(sync, day) { mutableStateOf(emptyMap<String, SyncedWorkout>()) }
    var drafts by remember(sync, day) { mutableStateOf(emptyMap<String, String>()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun reload() {
        if (sync != null) entries = withContext(Dispatchers.Default) { sync.cached(day) }
    }
    LaunchedEffect(sync, day) {
        try { reload() } catch (error: IllegalStateException) { status = error.message.orEmpty() }
    }
    fun run(success: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
                reload()
                refresh()
                status = if (entries.values.any { it.conflict }) "다른 기기의 변경과 겹치는 기록을 확인해 주세요." else success
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                status = if (error is IllegalStateException || error is IllegalArgumentException) error.message.orEmpty()
                    else "동기화를 완료하지 못했습니다. 연결을 확인하고 다시 시도하세요."
            } finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) open = false }, title = { Text("완료 운동 동기화") }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sync == null) {
                Text("Android는 설정의 영양 DB 계정, iPhone은 기록 탭의 동기화 계정에 로그인하세요. 두 기기에서 같은 Fitness 계정을 사용하세요.")
            } else {
                Text("${sync.account.localProfile} ↔ ${sync.account.label} · ${sync.account.host}", style = MaterialTheme.typography.labelMedium)
                Text("${day.date}에 앱에서 완료한 근력 운동의 종목·세트·볼륨과 동기화 메모를 이 계정에 주고받습니다.")
                TextButton({
                    scope.launch {
                        if (busy) return@launch
                        busy = true
                        try {
                            val summary = sync.weeklySummary(day)
                            status = if (export("fitness-week-${day.date}.json", summary)) "주간 운동 요약을 저장했습니다." else ""
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { status = "동기화된 주간 요약을 가져오지 못했습니다." }
                        finally { busy = false }
                    }
                }, enabled = !busy) { Text("최근 7일 AI 요약 저장") }
                for ((id, entry) in entries) {
                    val row = entry.server ?: continue
                    val workout = row.workout
                    if (workout == null && !entry.conflict) continue
                    HorizontalDivider()
                    Text(workout?.title ?: "다른 기기에서 삭제한 운동", style = MaterialTheme.typography.titleSmall)
                    if (workout != null) {
                        Text("${workout.exercises.sumOf { it.sets.size }}세트 · ${workout.totalVolumeKg} kg")
                        for (exercise in workout.exercises) {
                            Text("${exercise.name} · ${exercise.sets.size}세트", style = MaterialTheme.typography.labelLarge)
                            for ((index, set) in exercise.sets.withIndex()) {
                                val values = listOfNotNull(
                                    set.inputLoad?.takeIf { set.inputUnit != null }?.let { "$it ${set.inputUnit}" } ?: set.weightKg?.let { "$it kg" },
                                    set.reps?.let { "${it}회" }, set.rir?.let { "RIR $it" },
                                    set.durationSeconds?.let { "${it}초" }, set.distanceMeters?.let { "$it m" })
                                Text("${index + 1}세트  ${values.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (entry.conflict) {
                        Text("서버 메모  ${row.memo}")
                        entry.memoDraft?.let { Text("기기에 남은 메모  $it") }
                        TextButton({ run("서버 기록을 적용했습니다.") { sync.acceptServer(day, id) } }, enabled = !busy) { Text("서버 기록 사용") }
                    } else {
                        val saved = entry.memoDraft ?: row.memo
                        val memo = drafts[id] ?: saved
                        OutlinedTextField(memo, {
                            if (it.length <= 1000) drafts = if (it == saved) drafts - id else drafts + (id to it)
                        }, Modifier.fillMaxWidth(),
                            enabled = !busy, label = { Text("동기화 메모") })
                        if (id in drafts) {
                            TextButton({ run("메모를 기기에 저장했습니다. 동기화하면 다른 기기에 반영됩니다.") {
                                sync.edit(day, id, memo)
                                drafts = drafts - id
                            } }, enabled = !busy) { Text("메모 기기에 저장") }
                        }
                        if (entry.memoDraft != null) Text("전송 대기 중", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (status.isNotBlank()) Text(status)
        }
    }, confirmButton = {
        if (sync != null) TextButton({ run("동기화했습니다.") {
            for ((id, memo) in drafts.toMap()) {
                sync.edit(day, id, memo)
                drafts = drafts - id
            }
            sync.synchronize(day)
        } }, enabled = !busy) {
            Text(if (busy) "처리 중…" else "이 날짜 동기화")
        }
    }, dismissButton = { TextButton({ open = false }, enabled = !busy) { Text("닫기") } })
}
