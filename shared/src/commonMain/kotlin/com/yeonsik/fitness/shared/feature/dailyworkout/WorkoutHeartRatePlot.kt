package com.yeonsik.fitness.shared.feature.dailyworkout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
fun WorkoutHeartRatePlot(samples: List<DailyHeartRateSample>, startMillis: Long?, endMillis: Long?,
    modifier: Modifier = Modifier, showInstructions: Boolean = true) {
    val slots = remember(samples, startMillis, endMillis) { heartRateBands(samples, startMillis, endMillis) }
    if (slots.isEmpty()) return
    val bands = remember(slots) { slots.filterNotNull() }
    val peakIndex = remember(slots) { slots.indices.maxBy { slots[it]?.high ?: 0.0 } }
    val low = bands.minOf { it.low }
    val high = checkNotNull(slots[peakIndex]).high
    val axisLow = maxOf(0.0, floor((low - 3) / 10) * 10)
    val axisHigh = ceil((high + 3) / 10) * 10
    val hasGap = slots.size > bands.size
    val description = "측정 순서별 심박 범위, ${bands.sumOf { it.count }}회 측정을 최대 ${readingsPerBar}회씩 ${bands.size}개 구간, " +
        "최저 ${low.roundToInt()}, 최고 ${high.roundToInt()} bpm, High는 최고 구간" + if (hasGap) ", 측정 공백 있음" else ""

    val scheme = MaterialTheme.colorScheme
    val pulse = if (scheme.surface.luminance() < 0.5f) Color(0xFFFF8097) else Color(0xFFB3244D)
    val grid = scheme.outlineVariant.copy(alpha = 0.55f)
    val caption = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val labelHeight = with(density) { caption.lineHeight.toDp() }
    val plotHeight = maxOf(80.dp, labelHeight * 3 + 8.dp)
    val markerSpace = labelHeight + 8.dp
    val measurer = rememberTextMeasurer()
    val peakLabel = remember(measurer, caption, pulse) {
        measurer.measure(AnnotatedString("High"), style = caption.copy(color = pulse), maxLines = 1)
    }
    val listState = remember(slots) { LazyListState() }
    var selectedIndex by remember(slots) { mutableIntStateOf(-1) }
    var dragX by remember(slots) { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val widthPx = constraints.maxWidth
            if (widthPx == 0) return@BoxWithConstraints
            val minPitch = with(density) { 4.dp.roundToPx() }.coerceAtLeast(2)
            val fits = widthPx / slots.size >= minPitch
            val pitch = if (fits) widthPx / slots.size else minPitch
            val remainder = if (fits) widthPx % slots.size else 0
            val edgePx = with(density) { 18.dp.toPx() }.coerceAtMost(widthPx / 4f)
            val scrollSpeed = with(density) { 220.dp.toPx() }

            fun selectAt(position: Float) {
                val physicalX = position.coerceIn(0f, widthPx - 1f)
                val x = if (rtl) widthPx - 1f - physicalX else physicalX
                listState.layoutInfo.visibleItemsInfo.firstOrNull {
                    x >= it.offset && x < it.offset + it.size
                }?.let { selectedIndex = it.index }
            }

            fun selectBand(index: Int): Boolean {
                if (index !in slots.indices) return false
                selectedIndex = index
                if (!fits && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                    scope.launch { listState.scrollToItem(index) }
                }
                return true
            }

            val edgeDirection by remember(slots, fits, widthPx, edgePx, rtl) {
                derivedStateOf {
                    val physicalX = dragX
                    if (fits || physicalX == null) return@derivedStateOf 0
                    val x = if (rtl) widthPx - physicalX else physicalX
                    when {
                        x < edgePx -> -1
                        x > widthPx - edgePx -> 1
                        else -> 0
                    }
                }
            }
            LaunchedEffect(listState, edgeDirection, scrollSpeed, widthPx) {
                val direction = edgeDirection
                if (direction == 0) return@LaunchedEffect
                var previousFrame = withFrameNanos { it }
                while (true) {
                    val frame = withFrameNanos { it }
                    val x = dragX ?: break
                    val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f)
                    previousFrame = frame
                    if (listState.scrollBy(direction * scrollSpeed * seconds) == 0f) break
                    selectAt(x)
                }
            }

            val selection = slots.getOrNull(selectedIndex)
            val selectionText = when {
                selection != null -> "최저 ${selection.low.roundToInt()} bpm · 최고 ${selection.high.roundToInt()} bpm"
                selectedIndex >= 0 -> "측정 공백"
                else -> null
            }
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(markerSpace + plotHeight)
                        .clipToBounds()
                        .semantics {
                            contentDescription = description
                            selectionText?.let { stateDescription = it }
                            customActions = buildList {
                                val previous = (selectedIndex - 1 downTo 0).firstOrNull { slots[it] != null }
                                val next = ((selectedIndex + 1)..slots.lastIndex).firstOrNull { slots[it] != null }
                                previous?.let { add(CustomAccessibilityAction("이전 심박 구간") { selectBand(it) }) }
                                next?.let { add(CustomAccessibilityAction("다음 심박 구간") { selectBand(it) }) }
                                add(CustomAccessibilityAction("최고 심박 구간") { selectBand(peakIndex) })
                            }
                            if (!fits) {
                                horizontalScrollAxisRange = ScrollAxisRange(
                                    value = { listState.firstVisibleItemIndex.toFloat() * pitch + listState.firstVisibleItemScrollOffset },
                                    maxValue = { slots.size.toFloat() * pitch - widthPx },
                                    reverseScrolling = rtl
                                )
                                scrollBy { x, _ ->
                                    scope.launch { listState.scrollBy(if (rtl) -x else x) }
                                    true
                                }
                            }
                        }
                        .pointerInput(slots, widthPx, rtl) {
                            detectTapGestures { selectAt(it.x) }
                        }
                        .pointerInput(slots, widthPx, rtl) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragX = it.x
                                    selectAt(it.x)
                                },
                                onHorizontalDrag = { change, _ ->
                                    dragX = change.position.x
                                    selectAt(change.position.x)
                                },
                                onDragEnd = { dragX = null },
                                onDragCancel = { dragX = null }
                            )
                        }
                ) {
                    LazyRow(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(plotHeight)
                            .padding(vertical = labelHeight / 2)
                            .drawBehind {
                                for (tick in 0..2) {
                                    val y = (size.height - 1f) * tick / 2 + 0.5f
                                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                                }
                            }
                            .clearAndSetSemantics {},
                        state = listState,
                        userScrollEnabled = false
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
                                val color = if (selectedIndex >= 0 && index != selectedIndex) pulse.copy(alpha = 0.65f) else pulse
                                drawRoundRect(
                                    color, Offset((size.width - stroke) / 2, (top + bottom - height) / 2),
                                    Size(stroke, height), CornerRadius(stroke / 2)
                                )
                            }
                        }
                    }
                    Canvas(Modifier.matchParentSize()) {
                        val chartTop = (markerSpace + labelHeight / 2).toPx()
                        val chartBottom = size.height - (labelHeight / 2).toPx()
                        val visible = listState.layoutInfo.visibleItemsInfo
                        fun centerOf(index: Int): Float? = visible.firstOrNull { it.index == index }?.let {
                            val center = it.offset + it.size / 2f
                            if (rtl) size.width - center else center
                        }?.takeIf { it in 0f..size.width }
                        if (slots.getOrNull(selectedIndex) != null) {
                            centerOf(selectedIndex)?.let { x ->
                                drawLine(pulse.copy(alpha = 0.35f), Offset(x, chartTop), Offset(x, chartBottom), 1.dp.toPx())
                            }
                        }
                        centerOf(peakIndex)?.let { x ->
                            val peakY = chartTop + ((axisHigh - high) / (axisHigh - axisLow) * (chartBottom - chartTop)).toFloat()
                            drawHeartRatePeak(x, peakY, peakLabel, pulse)
                        }
                    }
                }
                if (selectionText != null || showInstructions) Text(
                    selectionText ?: "막대를 누르거나 끌어 최저·최고 보기",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = if (selection != null) scheme.onSurface else scheme.onSurfaceVariant
                )
                if (showInstructions && (hasGap || !fits)) {
                    val note = listOfNotNull(
                        "빈칸은 측정 공백".takeIf { hasGap },
                        "양끝으로 끌어 좌우로 보기".takeIf { !fits }
                    )
                    Text(note.joinToString(" · "), style = caption, color = scheme.onSurfaceVariant)
                }
            }
        }
        Column(Modifier.padding(top = markerSpace).height(plotHeight).clearAndSetSemantics {}, verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End) {
            for (tick in listOf(axisHigh, (axisLow + axisHigh) / 2, axisLow)) {
                Text(tick.roundToInt().toString(), style = caption, color = scheme.onSurfaceVariant)
            }
        }
    }
}

private fun DrawScope.drawHeartRatePeak(x: Float, peakY: Float, label: TextLayoutResult, color: Color) {
    val labelX = (x - label.size.width / 2f).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f))
    drawText(label, topLeft = Offset(labelX, 0f))
    val tip = Offset(x, peakY - 3.dp.toPx())
    val wing = 3.dp.toPx()
    val stroke = 1.dp.toPx()
    drawLine(color, Offset(x, label.size.height + 1.dp.toPx()), tip, stroke)
    drawLine(color, tip, Offset(x - wing, tip.y - wing), stroke)
    drawLine(color, tip, Offset(x + wing, tip.y - wing), stroke)
}
