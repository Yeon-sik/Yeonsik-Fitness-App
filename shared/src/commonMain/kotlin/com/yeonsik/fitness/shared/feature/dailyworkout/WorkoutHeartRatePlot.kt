package com.yeonsik.fitness.shared.feature.dailyworkout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val readingsPerBar = 5

internal data class HeartRateBand(val low: Double, val high: Double, val count: Int)

/** One slot per band in observation order. An empty slot marks an interruption, irrespective of its duration. */
internal fun heartRateBands(samples: List<DailyHeartRateSample>, startMillis: Long?, endMillis: Long?): List<HeartRateBand?> {
    val ordered = samples.filter { it.bpm.isFinite() && it.bpm > 0 }.sortedBy { it.timeMillis }
    if (ordered.isEmpty()) return emptyList()
    val intervals = ordered.zipWithNext { a, b -> b.timeMillis - a.timeMillis }.filter { it > 0 }.sorted()
    val cadence = if (intervals.size >= 3) intervals[(intervals.size - 1) / 2] else 0L
    val pause = maxOf(60_000.0, cadence * 4.0)
    fun interrupted(earlier: Long, later: Long) = later.toDouble() - earlier > pause

    return buildList {
        if (startMillis != null && interrupted(startMillis, ordered.first().timeMillis)) add(null)
        var index = 0
        while (index < ordered.size) {
            val first = index
            var low = ordered[index].bpm
            var high = low
            index++
            while (index < ordered.size && index - first < readingsPerBar &&
                !interrupted(ordered[index - 1].timeMillis, ordered[index].timeMillis)) {
                low = minOf(low, ordered[index].bpm)
                high = maxOf(high, ordered[index].bpm)
                index++
            }
            add(HeartRateBand(low, high, index - first))
            if (index < ordered.size && interrupted(ordered[index - 1].timeMillis, ordered[index].timeMillis)) add(null)
        }
        if (endMillis != null && interrupted(ordered.last().timeMillis, endMillis)) add(null)
    }
}

@Composable
fun WorkoutHeartRatePlot(samples: List<DailyHeartRateSample>, startMillis: Long?, endMillis: Long?, modifier: Modifier = Modifier) {
    val slots = remember(samples, startMillis, endMillis) { heartRateBands(samples, startMillis, endMillis) }
    if (slots.isEmpty()) return
    val bands = remember(slots) { slots.filterNotNull() }
    val low = bands.minOf { it.low }
    val high = bands.maxOf { it.high }
    val axisLow = maxOf(0.0, floor((low - 3) / 10) * 10)
    val axisHigh = ceil((high + 3) / 10) * 10
    val hasGap = slots.size > bands.size
    val description = "측정 순서별 심박 범위, ${bands.sumOf { it.count }}회 측정을 최대 ${readingsPerBar}회씩 ${bands.size}개 구간, " +
        "최저 ${low.roundToInt()}, 최고 ${high.roundToInt()} bpm" + if (hasGap) ", 측정 공백 있음" else ""

    val scheme = MaterialTheme.colorScheme
    val pulse = if (scheme.surface.luminance() < 0.5f) Color(0xFFFF8097) else Color(0xFFB3244D)
    val grid = scheme.outlineVariant.copy(alpha = 0.55f)
    val caption = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val labelHeight = with(density) { caption.lineHeight.toDp() }
    val plotHeight = maxOf(80.dp, labelHeight * 3 + 8.dp)

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val minPitch = with(density) { 4.dp.roundToPx() }.coerceAtLeast(2)
            val fits = constraints.maxWidth / slots.size >= minPitch
            val pitch = if (fits) constraints.maxWidth / slots.size else minPitch
            val remainder = if (fits) constraints.maxWidth % slots.size else 0
            Column {
                LazyRow(
                    Modifier.fillMaxWidth().height(plotHeight).padding(vertical = labelHeight / 2).drawBehind {
                        for (fraction in listOf(0f, 0.5f, 1f)) {
                            val y = (size.height - 1f) * fraction + 0.5f
                            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        }
                    }.semantics { contentDescription = description },
                    userScrollEnabled = !fits
                ) {
                    items(slots.size) { index ->
                        val width = with(density) { (pitch + if (index < remainder) 1 else 0).toDp() }
                        val band = slots[index]
                        Canvas(Modifier.width(width).fillParentMaxHeight()) {
                            if (band == null) return@Canvas
                            val stroke = minOf(7.dp.toPx(), size.width * 0.58f)
                            fun y(bpm: Double) = ((axisHigh - bpm) / (axisHigh - axisLow) * size.height).toFloat()
                            val top = y(band.high)
                            val bottom = y(band.low)
                            val height = maxOf(stroke, bottom - top)
                            drawRoundRect(pulse, Offset((size.width - stroke) / 2, (top + bottom - height) / 2),
                                Size(stroke, height), CornerRadius(stroke / 2))
                        }
                    }
                }
                val note = buildList {
                    add("측정 순서 · ${readingsPerBar}회씩 최저–최고")
                    if (hasGap) add("빈칸은 측정 공백")
                    if (!fits) add("좌우로 보기")
                }
                Text(note.joinToString(" · "), style = caption, color = scheme.onSurfaceVariant)
            }
        }
        Column(Modifier.height(plotHeight).clearAndSetSemantics {}, verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End) {
            for (tick in listOf(axisHigh, (axisLow + axisHigh) / 2, axisLow)) {
                Text(tick.roundToInt().toString(), style = caption, color = scheme.onSurfaceVariant)
            }
        }
    }
}
