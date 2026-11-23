package com.yeonsik.fitness.shared.feature.dailyworkout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Instant

@Composable
internal fun DailyWorkoutEntryCard(
    entry: DailyWorkoutEntry, zone: TimeZone, busy: Boolean, onDecision: (DailyWorkoutCandidate, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = entry.sessions.first().primary
    OutlinedCard(modifier.testTag("daily-workout-entry")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val multipleOptions = entry.candidates.size > 1
            Text(if (multipleOptions) "연결할 운동 선택" else primary.title,
                Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            for (session in entry.sessions) {
                val local = session.sources.filterNot { it.origin.isHealth }
                if (local.isNotEmpty()) {
                    if (multipleOptions || session.primary != primary) Text(session.primary.title, style = MaterialTheme.typography.titleSmall)
                    local.forEach { Text("${it.writerLabel()} · ${it.timeLabel(zone)}", style = MaterialTheme.typography.bodySmall) }
                    session.details?.let { details ->
                        Text("${details.exercises.size}종목 · ${details.exercises.sumOf { it.sets.size }}세트")
                    }
                }
                SessionWatchMeasurements(session, zone, entry.candidates.isNotEmpty())
            }
            MatchActions(entry.candidates, zone, busy, onDecision)
        }
    }
}

/** Watch section inside a native workout whose exercises and sets are already on screen. */
@Composable
fun DailyWorkoutRecordWatchCard(
    entry: DailyWorkoutEntry, zone: TimeZone, busy: Boolean, onDecision: (DailyWorkoutCandidate, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier.testTag("workout-record-watch")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entry.sessions.forEach { SessionWatchMeasurements(it, zone, entry.candidates.isNotEmpty()) }
            MatchActions(entry.candidates, zone, busy, onDecision)
        }
    }
}

@Composable
private fun SessionWatchMeasurements(session: DailyWorkoutSession, zone: TimeZone, hasSuggestions: Boolean) {
    val watches = session.sources.filter { it.origin.isHealth }
    val heart = session.heartRate
    if (watches.isEmpty() && heart == null) return
    val heading = when {
        watches.isEmpty() -> "운동 중 심박"
        session.sources.any { !it.origin.isHealth } -> "연결된 워치 기록"
        hasSuggestions -> "워치 기록 후보"
        else -> "워치 기록"
    }
    Column(Modifier.testTag("workout-watch-measurements"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(heading, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        for (watch in watches) Text("${watch.writerLabel()} · ${watch.timeLabel(zone)}", style = MaterialTheme.typography.bodyMedium)
        if (heart != null) {
            Text("평균 ${heart.averageBpm.roundToInt()} bpm · 최고 ${heart.maxBpm.roundToInt()} bpm")
            Text("심박 ${heart.sampleCount}개 측정", style = MaterialTheme.typography.bodySmall)
            val source = watches.firstOrNull { it.writer == heart.writer } ?: session.timing
            WorkoutHeartRatePlot(session.measuredSamples, source.startMillis, source.endMillis, Modifier.fillMaxWidth().height(72.dp))
        }
    }
}

@Composable
fun WorkoutHeartRatePlot(samples: List<DailyHeartRateSample>, startMillis: Long?, endMillis: Long?, modifier: Modifier = Modifier) {
    if (samples.isEmpty()) return
    val points = remember(samples, startMillis, endMillis) {
        val first = samples.minOf { it.timeMillis }
        val last = samples.maxOf { it.timeMillis }
        val start = minOf(startMillis ?: first, first)
        val span = (maxOf(endMillis ?: last, last) - start).coerceAtLeast(1).toDouble()
        val low = samples.minOf { it.bpm } - 5
        val high = samples.maxOf { it.bpm } + 5
        samples.map { Offset(((it.timeMillis - start) / span).toFloat(), (1 - (it.bpm - low) / (high - low)).toFloat()) }
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.semantics { contentDescription = "시간대별 심박 측정점" }) {
        val radius = 2.dp.toPx()
        val width = (size.width - 2 * radius).coerceAtLeast(0f)
        val height = (size.height - 2 * radius).coerceAtLeast(0f)
        for (point in points) drawCircle(color, radius, Offset(radius + point.x * width, radius + point.y * height))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchActions(
    candidates: List<DailyWorkoutCandidate>, zone: TimeZone, busy: Boolean, onDecision: (DailyWorkoutCandidate, Boolean) -> Unit
) {
    if (candidates.isEmpty()) return
    HorizontalDivider()
    Text(if (candidates.size == 1) "연결 제안" else "연결 후보 ${candidates.size}개",
        Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
    for (candidate in candidates) {
        Column(Modifier.testTag("workout-match-option"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (candidates.size > 1) {
                Text("${candidate.first.title} (${candidate.first.timeLabel(zone)}) ↔ ${candidate.second.title} (${candidate.second.timeLabel(zone)})",
                    style = MaterialTheme.typography.bodyMedium)
            }
            Text(candidate.reason(), style = MaterialTheme.typography.bodyMedium)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ onDecision(candidate, true) }, enabled = !busy) { Text("같은 운동으로 연결") }
                TextButton({ onDecision(candidate, false) }, enabled = !busy) { Text("별개 운동으로 집계") }
            }
        }
    }
}

private fun DailyWorkoutCandidate.reason(): String = when (basis) {
    WorkoutMatchBasis.OVERLAP -> if (overlapMillis < 60_000) "운동 시간이 1분 미만 겹칩니다."
        else "운동 시간이 ${overlapMillis / 60_000}분 겹칩니다."
    WorkoutMatchBasis.START_IN_INTERVAL -> "한 기록의 시작 시각이 다른 기록의 운동 구간에 들어갑니다."
    WorkoutMatchBasis.NEAR_START -> when (val gap = requireNotNull(startDifferenceMillis)) {
        0L -> "시작 시각이 같습니다."
        in 1 until 60_000L -> "시작 시각 차이가 1분 미만입니다."
        else -> "시작 시각 차이가 ${gap / 60_000}분입니다."
    }
    WorkoutMatchBasis.DATE_ONLY -> "시작 시각이 없는 기록입니다. 같은 날짜의 후보로 제안합니다."
}

private fun DailyWorkoutSource.timeLabel(zone: TimeZone): String {
    val startTime = startMillis ?: return "시각 미기록"
    val start = Instant.fromEpochMilliseconds(startTime).toLocalDateTime(zone)
    val from = start.time.toString().take(5)
    val end = endMillis?.takeIf { it > startTime }?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone) }
        ?: return "시작 $from"
    val until = end.time.toString().take(5)
    return if (start.date == end.date) "$from–$until" else "$from–${end.date} $until"
}

private fun DailyWorkoutSource.writerLabel(): String = when {
    writer == "com.sec.android.app.shealth" -> "Samsung Health"
    writer == "com.yeonsik.fitnessapp.samsungexport" || writer == "com.yeonsik.fitnessapp.samsungexport.debug" -> "삼성 헬스 가져오기"
    writer.contains("fleek", ignoreCase = true) -> "FLEEK"
    origin == WorkoutOrigin.HEALTH_KIT && writer.startsWith("com.apple.") -> "Apple 건강"
    else -> writer
}
