package com.yeonsik.fitnessapp.feature.dailyworkout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutDecision
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutRecordReview
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutRecordWatchCard
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutReport
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutRepository
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitnessapp.integration.wearable.WearableImportAction
import com.yeonsik.fitnessapp.integration.wearable.WearableImportEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

@Composable
internal fun WorkoutRecordWatchSection(repository: DailyWorkoutRepository?, ownerId: String, session: WorkoutSessionSnapshot) {
    if (repository == null || session.status != "completed") {
        WearableImportEntry(ownerId, session.recordId, session.startedAt.take(10))
        return
    }
    key(repository, ownerId, session.recordId) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val scope = rememberCoroutineScope()
        var review by remember { mutableStateOf<DailyWorkoutRecordReview?>(null) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    review = repository.loadForRecord(session.recordId, TimeZone.currentSystemDefault())
                    error = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = "이 운동의 워치 기록을 읽지 못했습니다."
                }
            }
        }
        fun change(current: DailyWorkoutRecordReview, operation: suspend () -> DailyWorkoutReport) {
            if (busy) return
            busy = true
            error = null
            scope.launch {
                try {
                    review = current.copy(report = operation())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = "연결 결정을 저장하지 못했습니다. 운동 기록을 다시 열어 주세요."
                } finally {
                    busy = false
                }
            }
        }
        val current = review
        val entry = current?.entry
        val date = current?.report?.day?.date?.toString() ?: session.startedAt.take(10)
        val linked = entry?.sessions?.firstOrNull()?.hasLinkedWatch == true
        if (current != null) {
            val day = current.report.day
            if (entry != null) {
                DailyWorkoutRecordWatchCard(entry, day.zone, busy, { match, same ->
                    change(current) { repository.decide(day, match, same) }
                }, Modifier.fillMaxWidth()) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }, enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = "워치 기록 옵션" }) {
                            val tint = LocalContentColor.current
                            Canvas(Modifier.size(20.dp)) {
                                for (position in 1..3) drawCircle(tint, 1.6.dp.toPx(), Offset(center.x, size.height * position / 4))
                            }
                        }
                        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                            for (decision in current.decisions) {
                                DropdownMenuItem(text = { Text(current.undoLabel(decision)) }, onClick = {
                                    expanded = false
                                    change(current) { repository.undo(day, decision) }
                                })
                            }
                            WearableImportAction(ownerId, session.recordId, date) { open ->
                                DropdownMenuItem(text = { Text("다른 워치 기록 가져오기") }, onClick = { expanded = false; open() })
                            }
                        }
                    }
                }
            }
            if (!linked) for (decision in current.decisions) {
                TextButton({ change(current) { repository.undo(day, decision) } }, enabled = !busy) {
                    Text(current.undoLabel(decision))
                }
            }
        }
        error?.let { Text(it) }
        if (entry == null) WearableImportEntry(ownerId, session.recordId, date) else if (!linked) {
            WearableImportAction(ownerId, session.recordId, date) { open ->
                TextButton(open, enabled = !busy) { Text("다른 워치 기록 가져오기") }
            }
        }
    }
}

private fun DailyWorkoutRecordReview.undoLabel(decision: DailyWorkoutDecision): String {
    val otherKey = if (decision.first == sourceKey) decision.second else decision.first
    val action = if (decision.sameWorkout) "연결 취소" else "별개 운동 결정 취소"
    return "$action · ${report.sources.getValue(otherKey).title}"
}
