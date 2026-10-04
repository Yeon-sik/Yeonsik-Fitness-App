package com.yeonsik.fitnessapp.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun fitnessStatusColor(status: FitnessSemanticStatus): Color = when (status) {
    FitnessSemanticStatus.SUCCESS -> LocalFitnessColors.current.success
    FitnessSemanticStatus.WARNING -> LocalFitnessColors.current.warning
    FitnessSemanticStatus.ERROR -> MaterialTheme.colorScheme.error
    FitnessSemanticStatus.INFO -> MaterialTheme.colorScheme.primary
    FitnessSemanticStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun fitnessStatusContainerColor(status: FitnessSemanticStatus): Color {
    val statusColor = fitnessStatusColor(status)
    return when (status) {
        FitnessSemanticStatus.SUCCESS,
        FitnessSemanticStatus.WARNING -> statusColor.copy(alpha = 0.12f)
        FitnessSemanticStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        FitnessSemanticStatus.INFO -> MaterialTheme.colorScheme.primaryContainer
        FitnessSemanticStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
    }
}

/**
 * Status is expressed by a glyph, text, and accessibility semantics together. Consumers do not
 * need to rely on color alone to communicate the state.
 */
@Composable
fun FitnessStatusBadge(
    status: FitnessSemanticStatus,
    label: String = status.label(),
    modifier: Modifier = Modifier
) {
    val statusColor = fitnessStatusColor(status)
    Row(
        modifier = modifier
            .heightIn(min = FitnessSpacing.touch)
            .clip(FitnessShape.input)
            .background(fitnessStatusContainerColor(status))
            .padding(horizontal = FitnessSpacing.gap, vertical = FitnessSpacing.small)
            .semantics {
                contentDescription = "$label, ${status.label()} 상태"
            },
        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = status.glyph(),
            color = statusColor,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = label,
            color = statusColor,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
            softWrap = true
        )
    }
}

@Composable
fun FitnessStatusMessage(
    status: FitnessSemanticStatus,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    FitnessCard(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FitnessSpacing.card),
            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
        ) {
            FitnessStatusBadge(status = status, label = title, modifier = Modifier.fillMaxWidth())
            if (message.isNotBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FitnessProgressBar(
    presentation: FitnessProgressPresentation,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    val fraction = presentation.fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FitnessSpacing.touch)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            },
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
    ) {
        if (!title.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    softWrap = true
                )
                if (presentation.label.isNotBlank()) {
                    Text(
                        text = presentation.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        softWrap = true
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FitnessSpacing.micro),
            color = LocalFitnessColors.current.action,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        if (title.isNullOrBlank() && presentation.label.isNotBlank()) {
            Text(
                text = presentation.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FitnessTrendChart(
    model: FitnessTrendPresentation,
    modifier: Modifier = Modifier,
    unit: String = "",
    emptyLabel: String = "추세를 표시할 기록이 없습니다.",
    insufficientLabel: String = "추세를 표시하려면 기록이 더 필요합니다."
) {
    val accessibilityDescription = fitnessTrendAccessibilityDescription(model, unit)
    when (model.state) {
        FitnessTrendState.EMPTY -> FitnessStatusMessage(
            status = FitnessSemanticStatus.UNKNOWN,
            title = model.state.label(),
            message = emptyLabel,
            modifier = modifier
                .fillMaxWidth()
                .semantics { contentDescription = accessibilityDescription }
        )

        FitnessTrendState.INSUFFICIENT -> FitnessStatusMessage(
            status = FitnessSemanticStatus.WARNING,
            title = model.state.label(),
            message = insufficientLabel,
            modifier = modifier
                .fillMaxWidth()
                .semantics { contentDescription = accessibilityDescription }
        )

        FitnessTrendState.READY -> FitnessTrendPlot(
            model = model,
            unit = unit,
            modifier = modifier
                .fillMaxWidth()
                .semantics { contentDescription = accessibilityDescription }
        )
    }
}

@Composable
private fun FitnessTrendPlot(
    model: FitnessTrendPresentation,
    unit: String,
    modifier: Modifier = Modifier
) {
    val points = model.finitePoints
    val lineColor = LocalFitnessColors.current.action
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val currentRingColor = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current
    var selectedPointIndex by remember(model, unit) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = formatFitnessTrendValue(model.range.max, unit),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatFitnessTrendValue(model.range.min, unit),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                softWrap = true
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(FitnessUiTokens.TREND_CHART_HEIGHT_DP.dp * density.fontScale.coerceAtLeast(1f))
                .semantics {
                    customActions = points.mapIndexed { index, point ->
                        CustomAccessibilityAction(
                            label = "${point.detailLabel}, ${formatFitnessTrendValue(point.value ?: Double.NaN, unit)} 자세히 보기",
                            action = { selectedPointIndex = index; true }
                        )
                    }
                    selectedPointIndex?.let { index ->
                        stateDescription = "${points[index].detailLabel}, ${formatFitnessTrendValue(points[index].value ?: Double.NaN, unit)}"
                    }
                }
        ) {
            val padding = with(density) { (FitnessSpacing.touch / 2).toPx() }
            val plotSize = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
            val offsets = remember(model, plotSize, padding) {
                fitnessTrendPointOffsets(model, plotSize, padding)
            }
            Canvas(
                Modifier.fillMaxSize().pointerInput(model, unit, offsets, padding) {
                    detectTapGestures { tap ->
                        val index = fitnessTrendHitTest(offsets, tap, padding)
                        selectedPointIndex = if (index == selectedPointIndex) null else index
                    }
                }
            ) {
                val bottom = size.height - padding
                drawLine(
                    color = axisColor,
                    start = Offset(padding, bottom),
                    end = Offset(size.width - padding, bottom),
                    strokeWidth = 1.dp.toPx()
                )

                val path = Path()
                offsets.forEachIndexed { index, offset ->
                    if (index == 0) path.moveTo(offset.x, offset.y)
                    else path.lineTo(offset.x, offset.y)
                }

                if (points.size > 1) {
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                offsets.forEachIndexed { index, offset ->
                    drawCircle(color = lineColor, radius = 4.dp.toPx(), center = offset)
                    if (model.currentPointIndex == model.finitePointIndices.getOrNull(index) ||
                        selectedPointIndex == index) {
                        drawCircle(
                            color = currentRingColor,
                            radius = 7.dp.toPx(),
                            center = offset,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
            selectedPointIndex?.let { index ->
                FitnessTrendTooltip(
                    point = points[index],
                    unit = unit,
                    anchor = offsets[index],
                    modifier = Modifier.matchParentSize()
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = points.firstOrNull()?.label.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = true
            )
            Text(
                text = points.lastOrNull()?.label.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                softWrap = true
            )
        }
    }
}

/** Measure the bubble before placing it so edge points and larger text stay inside the plot. */
@Composable
private fun FitnessTrendTooltip(
    point: FitnessTrendPoint,
    unit: String,
    anchor: Offset,
    modifier: Modifier = Modifier
) {
    val bubbleColor = MaterialTheme.colorScheme.inverseSurface
    Layout(
        modifier = modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
        content = {
            Surface(
                shape = FitnessShape.input,
                color = bubbleColor,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 4.dp
            ) {
                Column(
                    Modifier.padding(horizontal = FitnessSpacing.gap, vertical = FitnessSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
                ) {
                    Text(point.detailLabel, style = MaterialTheme.typography.labelMedium)
                    Text(
                        formatFitnessTrendValue(point.value ?: Double.NaN, unit),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Canvas(Modifier.size(width = 12.dp, height = 6.dp)) {
                drawPath(Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }, bubbleColor)
            }
        }
    ) { measurables, constraints ->
        val margin = FitnessSpacing.small.roundToPx()
        val gap = FitnessSpacing.micro.roundToPx()
        val body = measurables[0].measure(constraints.copy(
            minWidth = 0,
            minHeight = 0,
            maxWidth = (constraints.maxWidth - 2 * margin).coerceAtLeast(0)
        ))
        val caret = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val above = anchor.y >= body.height + caret.height + gap
        val bodyX = (anchor.x - body.width / 2f).roundToInt().coerceIn(
            margin, (width - body.width - margin).coerceAtLeast(margin)
        )
        val desiredY = if (above) anchor.y - gap - caret.height - body.height
        else anchor.y + gap + caret.height
        val bodyY = desiredY.roundToInt().coerceIn(0, (height - body.height).coerceAtLeast(0))
        val caretX = (anchor.x - caret.width / 2f).roundToInt().coerceIn(
            bodyX + gap, (bodyX + body.width - caret.width - gap).coerceAtLeast(bodyX + gap)
        )
        val caretY = if (above) bodyY + body.height else bodyY - caret.height
        layout(width, height) {
            body.place(bodyX, bodyY)
            caret.placeWithLayer(caretX, caretY) { rotationZ = if (above) 0f else 180f }
        }
    }
}

@Composable
fun FitnessMonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault()
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FitnessSpacing.touch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onPrevious,
            modifier = Modifier
                .heightIn(min = FitnessSpacing.touch)
                .semantics { contentDescription = "이전 달" }
        ) {
            Text("‹", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = formatFitnessMonth(month, locale),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        TextButton(
            onClick = onNext,
            modifier = Modifier
                .heightIn(min = FitnessSpacing.touch)
                .semantics { contentDescription = "다음 달" }
        ) {
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun FitnessCalendarDayCell(
    day: FitnessCalendarDayPresentation,
    markerColors: Map<String, Color> = emptyMap(),
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val selected = day.isSelected
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface.copy(
            alpha = if (day.isOutsideDisplayedMonth) 0.48f else 1f
        )
    }
    val todayOutline = LocalFitnessColors.current.action
    val cellDescription = buildString {
        append(day.date)
        if (day.isSelected) append(", 선택됨")
        if (day.isToday) append(", 오늘")
        day.markers.map { it.label.trim() }
            .filter { it.isNotEmpty() }
            .forEach { append(", ").append(it) }
    }

    Column(
        modifier = modifier
            .widthIn(min = FitnessSpacing.touch)
            .heightIn(min = FitnessSpacing.touch)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .semantics {
                this.selected = selected
                contentDescription = cellDescription
            }
            .padding(horizontal = FitnessSpacing.micro),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
                .then(if (day.isToday) Modifier.border(1.dp, todayOutline, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                color = foreground,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.micro),
            verticalAlignment = Alignment.CenterVertically
        ) {
            day.markers.forEach { marker ->
                Box(
                    modifier = Modifier
                        .size(FitnessSpacing.micro)
                        .clip(FitnessShape.input)
                        .background(markerColors[marker.key] ?: MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun FitnessEvidenceCard(
    presentation: FitnessEvidencePresentation,
    modifier: Modifier = Modifier
) {
    FitnessCard(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FitnessSpacing.card),
            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
        ) {
            if (presentation.title.isNotBlank()) {
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            FitnessEvidenceText(
                label = "근거",
                value = presentation.evidence,
                emptyLabel = "근거 정보 없음"
            )
            FitnessEvidenceText(
                label = "제한",
                value = presentation.limitation,
                emptyLabel = "제한 사항 미기재"
            )
            if (!presentation.source.isNullOrBlank()) {
                FitnessEvidenceText(label = "출처", value = presentation.source, emptyLabel = "출처 정보 없음")
            }
            FitnessStatusBadge(
                status = presentation.confidence.status(),
                label = "검토 수준: ${presentation.confidence.label()}",
                modifier = Modifier.fillMaxWidth()
            )
            FitnessStatusBadge(
                status = presentation.sufficiency.status(),
                label = "데이터 상태: ${presentation.sufficiency.label()}",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FitnessEvidenceText(label: String, value: String?, emptyLabel: String) {
    Column(verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: emptyLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = if (value.isNullOrBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
