package com.yeonsik.fitnessapp.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.util.Locale

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
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(FitnessUiTokens.TREND_CHART_HEIGHT_DP.dp)
        ) {
            val left = FitnessSpacing.small.toPx()
            val right = size.width - FitnessSpacing.small.toPx()
            val top = FitnessSpacing.small.toPx()
            val bottom = size.height - FitnessSpacing.small.toPx()
            val ySpan = (bottom - top).coerceAtLeast(1f)
            val xSpan = (right - left).coerceAtLeast(1f)

            drawLine(
                color = axisColor,
                start = androidx.compose.ui.geometry.Offset(left, bottom),
                end = androidx.compose.ui.geometry.Offset(right, bottom),
                strokeWidth = 1.dp.toPx()
            )

            val path = Path()
            points.forEachIndexed { index, point ->
                val x = if (points.size == 1) {
                    left + xSpan / 2f
                } else {
                    left + xSpan * index / (points.size - 1).toFloat()
                }
                val normalized = model.range.normalize(point.value ?: Double.NaN)
                val y = bottom - ySpan * normalized
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            if (points.size > 1) {
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            points.forEachIndexed { index, point ->
                val x = if (points.size == 1) {
                    left + xSpan / 2f
                } else {
                    left + xSpan * index / (points.size - 1).toFloat()
                }
                val normalized = model.range.normalize(point.value ?: Double.NaN)
                val y = bottom - ySpan * normalized
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                if (model.currentPointIndex == model.finitePointIndices.getOrNull(index)) {
                    drawCircle(
                        color = currentRingColor,
                        radius = 7.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(x, y),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
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
    val borderColor = when {
        selected -> LocalFitnessColors.current.action
        day.isToday -> LocalFitnessColors.current.action
        else -> MaterialTheme.colorScheme.outlineVariant
    }
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
            .clip(FitnessShape.input)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(1.dp, borderColor, FitnessShape.input)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .semantics {
                this.selected = selected
                contentDescription = cellDescription
            }
            .padding(vertical = FitnessSpacing.small, horizontal = FitnessSpacing.micro),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            color = foreground,
            style = MaterialTheme.typography.labelLarge
        )
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
