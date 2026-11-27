package com.yeonsik.fitnessapp.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.util.Locale

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
