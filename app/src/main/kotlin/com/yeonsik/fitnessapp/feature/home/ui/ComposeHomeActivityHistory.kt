package com.yeonsik.fitnessapp.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import com.yeonsik.fitnessapp.core.ui.FitnessSection
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityCell
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityCellState
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityCoveragePolicyV1
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityDayDetails
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityKind
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityRecordSummary
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityWindow
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityWindowPolicy

private val HomeActivityCoverageColor = Color(FitnessUiTokens.COLOR_HOME_ACTIVITY_FULL_COVERAGE)

/** Plain state/callback rendering; loading, paging and cache ownership stay in HomeViewModel. */
@Composable
internal fun HomeActivityHistorySection(
    state: HomeActivityUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onRetry: () -> Unit,
    dayDetails: HomeActivityDayDetailsUiState = HomeActivityDayDetailsUiState.Idle,
    onSelectDate: (String) -> Unit = {},
    preferredMassUnit: MassUnit = MassUnit.KG,
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
                        var selectedDate by rememberSaveable(page.identity) { mutableStateOf<String?>(null) }
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
                            ActivityGrid(page.cells, selectedDate, dayDetails, preferredMassUnit) { date ->
                                selectedDate = if (selectedDate == date) null else date
                                if (selectedDate != null) onSelectDate(date)
                            }
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
private fun ActivityGrid(
    cells: List<HomeActivityCell>,
    selectedDate: String?,
    dayDetails: HomeActivityDayDetailsUiState,
    preferredMassUnit: MassUnit,
    onSelectCell: (String) -> Unit
) {
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
                    weeks.forEach { week ->
                        val cell = week[dayIndex]
                        ActivityCell(
                            cell = cell,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            selected = selectedDate == cell.date.toString(),
                            dayDetails = dayDetails,
                            preferredMassUnit = preferredMassUnit,
                            onClick = { onSelectCell(cell.date.toString()) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCell(
    cell: HomeActivityCell,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dayDetails: HomeActivityDayDetailsUiState = HomeActivityDayDetailsUiState.Idle,
    preferredMassUnit: MassUnit = MassUnit.KG,
    onClick: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(3.dp)
    val alpha = HomeActivityCoveragePolicyV1.alpha(cell)
    val date = cell.date.toString()
    val selectedDetails = (dayDetails as? HomeActivityDayDetailsUiState.Ready)
        ?.takeIf { it.details.date == date }?.details
    val background = when {
        alpha != null && alpha > 0f -> HomeActivityCoverageColor.copy(alpha = alpha)
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
            .clickable(enabled = cell.state == HomeActivityCellState.TRACKED, onClick = onClick)
            .testTag("home-activity-cell-${cell.date}")
            .semantics {
                contentDescription = cell.contentDescription
                if (cell.state != HomeActivityCellState.TRACKED) disabled()
            }
    ) {
        if (selected && cell.state == HomeActivityCellState.TRACKED) {
            ActivityDayBubble(date, dayDetails, selectedDetails, preferredMassUnit)
        }
    }
}

@Composable
private fun ActivityDayBubble(
    date: String,
    state: HomeActivityDayDetailsUiState,
    details: HomeActivityDayDetails?,
    preferredMassUnit: MassUnit
) {
    Popup(
        alignment = Alignment.BottomCenter,
        offset = androidx.compose.ui.unit.IntOffset.Zero,
        onDismissRequest = {},
        properties = PopupProperties(focusable = false)
    ) {
        Column(
            modifier = Modifier.testTag("home-activity-day-bubble-$date"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Color.White,
                contentColor = Color(0xFF202124),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 6.dp,
                tonalElevation = 0.dp
            ) {
                Column(
                    Modifier.widthIn(min = 126.dp, max = 220.dp)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(date, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF202124))
                    when {
                        state is HomeActivityDayDetailsUiState.Loading && state.date == date ->
                            BubbleLine("기록을 불러오는 중", color = Color(0xFF5F6368))
                        state is HomeActivityDayDetailsUiState.Error && state.date == date ->
                            BubbleLine("기록을 불러오지 못했어요", color = Color(0xFF5F6368))
                        details == null || details.records.isEmpty() ->
                            BubbleLine("기록 없음", color = Color(0xFF5F6368))
                        else -> HomeActivityKind.entries.forEach { kind ->
                            val records = details.recordsFor(kind)
                            if (records.isNotEmpty()) {
                                BubbleLine(
                                    activitySummary(kind, records, preferredMassUnit),
                                    color = Color(0xFF202124)
                                )
                            }
                        }
                    }
                }
            }
            Canvas(Modifier.size(width = 12.dp, height = 6.dp)) {
                val pointer = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width / 2f, size.height)
                    lineTo(size.width, 0f)
                    close()
                }
                drawPath(pointer, Color.White)
            }
        }
    }
}

@Composable
private fun BubbleLine(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun activitySummary(
    kind: HomeActivityKind,
    records: List<HomeActivityRecordSummary>,
    preferredMassUnit: MassUnit
): String {
    val labels = records.take(2).mapNotNull { record ->
        when (kind) {
            HomeActivityKind.EXERCISE -> record.name?.takeIf(String::isNotBlank)
            HomeActivityKind.WEIGHT -> record.weightKg?.let { MassFormatter.withUnit(it, preferredMassUnit) }
            HomeActivityKind.MEAL -> listOfNotNull(
                record.category?.takeIf(String::isNotBlank),
                record.name?.takeIf(String::isNotBlank)
            ).joinToString(" ").takeIf(String::isNotBlank)
        }
    }
    val more = records.size - labels.size
    val label = when (kind) {
        HomeActivityKind.EXERCISE -> "운동"
        HomeActivityKind.WEIGHT -> "체중"
        HomeActivityKind.MEAL -> "식단"
    }
    val summary = if (labels.isEmpty()) "기록 ${records.size}건" else labels.joinToString(", ")
    return "$label · $summary" + if (more > 0) " 외 ${more}건" else ""
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
                        else HomeActivityCoverageColor.copy(alpha = alpha), RoundedCornerShape(2.dp)
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
