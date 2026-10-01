package com.yeonsik.fitnessapp.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import com.yeonsik.fitnessapp.core.ui.FitnessSection
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityCell
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityCellState
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityCoveragePolicyV1
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityWindow
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityWindowPolicy

/** Plain state/callback rendering; loading, paging and cache ownership stay in HomeViewModel. */
@Composable
internal fun HomeActivityHistorySection(
    state: HomeActivityUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val page = when (state) {
        is HomeActivityUiState.Ready -> state
        is HomeActivityUiState.Loading -> state.previous
        is HomeActivityUiState.Error -> state.previous
        else -> null
    }
    Column(modifier.testTag("home-activity-history")) {
        FitnessSection("활동 내역") {
            FitnessCard {
                Box(Modifier.padding(FitnessSpacing.card)) {
                    if (page != null) {
                        val interactive = state is HomeActivityUiState.Ready
                        // Keep the measured page while a read is pending so verticalScroll cannot clamp upward.
                        Column(
                            Modifier.fillMaxWidth().then(
                                if (interactive) Modifier else Modifier
                                    .graphicsLayer { alpha = 0f }
                                    .clearAndSetSemantics { }
                            ),
                            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
                        ) {
                            ActivityPeriodSelector(page.window, onSelectPage, interactive)
                            ActivityGrid(page.cells)
                            ActivityLegend()
                            Row(Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = onPrevious,
                                    enabled = interactive && page.window.canGoPrevious,
                                    modifier = Modifier.weight(1f).testTag("home-activity-previous")
                                ) { Text("‹ 이전 13주") }
                                TextButton(
                                    onClick = onNext,
                                    enabled = interactive && page.window.canGoNext,
                                    modifier = Modifier.weight(1f).testTag("home-activity-next")
                                ) { Text("다음 13주 ›") }
                            }
                        }
                    }
                    if (state !is HomeActivityUiState.Ready) {
                        Column(
                            if (page != null) Modifier.matchParentSize() else Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when (state) {
                                is HomeActivityUiState.Empty ->
                                    Text("아직 활동 기록이 없어요.", style = MaterialTheme.typography.bodyMedium)
                                is HomeActivityUiState.Error -> {
                                    Text("활동 내역을 불러오지 못했습니다.", style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = onRetry, modifier = Modifier.testTag("home-activity-retry")) {
                                        Text("다시 시도")
                                    }
                                }
                                else -> Text("활동 내역을 불러오는 중입니다.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityPeriodSelector(
    window: HomeActivityWindow, onSelectPage: (Int) -> Unit, enabled: Boolean = true
) {
    var expanded by rememberSaveable(window.today, window.firstRecordedDate) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("home-activity-period")
        ) { Text("${window.periodLabel} ▾", style = MaterialTheme.typography.bodySmall) }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            HomeActivityWindowPolicy.windows(window.today, window.firstRecordedDate).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.periodLabel, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        expanded = false
                        onSelectPage(option.pageOffset)
                    },
                    modifier = Modifier.testTag("home-activity-period-${option.pageOffset}")
                )
            }
        }
    }
}

@Composable
private fun ActivityGrid(cells: List<HomeActivityCell>) {
    val weeks = cells.chunked(7)
    val monthGroups = weeks.map { it.first().date.monthValue }.fold(mutableListOf<Pair<Int, Int>>()) { groups, month ->
        if (groups.lastOrNull()?.first == month) {
            val last = groups.removeAt(groups.lastIndex)
            groups.add(month to last.second + 1)
        } else groups.add(month to 1)
        groups
    }
    Column(
        modifier = Modifier.fillMaxWidth().testTag("home-activity-grid"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(24.dp))
            Row(Modifier.weight(1f)) {
                monthGroups.forEach { (month, count) ->
                    Text(
                        "${month}월", modifier = Modifier.weight(count.toFloat()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        listOf("월", "화", "수", "목", "금", "토", "일").forEachIndexed { dayIndex, day ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(day, modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelSmall)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    weeks.forEach { week -> ActivityCell(week[dayIndex], Modifier.weight(1f).aspectRatio(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ActivityCell(cell: HomeActivityCell, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(3.dp)
    val alpha = HomeActivityCoveragePolicyV1.alpha(cell)
    val background = when {
        alpha != null && alpha > 0f -> colors.primary.copy(alpha = alpha)
        cell.state == HomeActivityCellState.TRACKED -> colors.surfaceContainerHighest
        else -> colors.surface
    }
    val outline = colors.outlineVariant
    Box(
        modifier
            .background(background, shape)
            .then(if (cell.state == HomeActivityCellState.BEFORE_TRACKING) {
                Modifier.border(0.5.dp, outline, shape).drawBehind {
                    drawLine(outline, Offset(size.width * 0.3f, size.height * 0.7f),
                        Offset(size.width * 0.7f, size.height * 0.3f), strokeWidth = 1.dp.toPx())
                }
            } else Modifier)
            .testTag("home-activity-cell-${cell.date}")
            .semantics {
                contentDescription = cell.contentDescription
                if (cell.state != HomeActivityCellState.TRACKED) disabled()
            }
    )
}

@Composable
private fun ActivityLegend() {
    Column(Modifier.fillMaxWidth().padding(top = FitnessSpacing.small)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..3).forEach { count ->
                val kinds = HomeActivityCoveragePolicyV1.baselineKinds.take(count).toSet()
                val alpha = HomeActivityCoveragePolicyV1.alpha(kinds)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(10.dp).background(
                        if (count == 0) MaterialTheme.colorScheme.surfaceContainerHighest
                        else MaterialTheme.colorScheme.primary.copy(alpha = alpha), RoundedCornerShape(2.dp)
                    ))
                    Text("${count}종", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(
            "사선: 첫 기록 이전 · 빈 칸: 미래 날짜", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = FitnessSpacing.small)
        )
    }
}
