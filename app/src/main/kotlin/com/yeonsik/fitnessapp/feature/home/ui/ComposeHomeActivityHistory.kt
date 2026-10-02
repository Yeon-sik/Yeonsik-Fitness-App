package com.yeonsik.fitnessapp.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import com.yeonsik.fitnessapp.core.ui.FitnessSection
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityCell
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityCellState
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityCoveragePolicyV1
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityKind
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityWindow
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityWindowPolicy
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil

private val HomeActivityCoverageColor = Color(FitnessUiTokens.COLOR_HOME_ACTIVITY_FULL_COVERAGE)
private val HomeActivitySelectionColor = Color(0xFFFFD54F)

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
    onOpenRecords: (String) -> Unit = {},
    preferredMassUnit: MassUnit = MassUnit.KG,
    modifier: Modifier = Modifier
) {
    val page = when (state) {
        is HomeActivityUiState.Ready -> state
        is HomeActivityUiState.Loading -> state.previous
        is HomeActivityUiState.Error -> state.previous
        else -> null
    }
    var selectedDate by rememberSaveable(page?.identity) { mutableStateOf<String?>(null) }
    Column(
        modifier.then(
            if (selectedDate != null) Modifier.clickable { selectedDate = null } else Modifier
        ).testTag("home-activity-history")
    ) {
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
                            ActivityWindowNavigation(
                                page.window,
                                onPrevious,
                                onNext,
                                onSelectPage,
                                interactive
                            )
                            ActivityGrid(
                                cells = page.cells,
                                selectedDate = selectedDate.takeIf { interactive },
                                ownerId = page.identity.ownerId,
                                dayDetails = dayDetails,
                                preferredMassUnit = preferredMassUnit,
                                onSelectCell = { date ->
                                    selectedDate = if (selectedDate == date) null else date
                                    if (selectedDate != null) onSelectDate(date)
                                },
                                onDismissSelection = { selectedDate = null },
                                onOpenRecords = { date ->
                                    selectedDate = null
                                    onOpenRecords(date)
                                }
                            )
                            ActivityLegend()
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
private fun ActivityWindowNavigation(
    window: HomeActivityWindow,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectPage: (Int) -> Unit,
    enabled: Boolean
) {
    var expanded by rememberSaveable(window.today, window.firstRecordedDate) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = onPrevious,
            enabled = enabled && window.canGoPrevious,
            modifier = Modifier.width(48.dp).testTag("home-activity-previous")
                .semantics { contentDescription = "이전 기간" }
        ) { Text("‹", style = MaterialTheme.typography.titleMedium) }
        Box(Modifier.weight(1f)) {
            TextButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("home-activity-period")
            ) { Text("${window.periodLabel} ▾", style = MaterialTheme.typography.bodySmall, maxLines = 1) }
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
        TextButton(
            onClick = onNext,
            enabled = enabled && window.canGoNext,
            modifier = Modifier.width(48.dp).testTag("home-activity-next")
                .semantics { contentDescription = "다음 기간" }
        ) { Text("›", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun ActivityGrid(
    cells: List<HomeActivityCell>,
    selectedDate: String?,
    ownerId: String,
    dayDetails: HomeActivityDayDetailsUiState,
    preferredMassUnit: MassUnit,
    onSelectCell: (String) -> Unit,
    onDismissSelection: () -> Unit,
    onOpenRecords: (String) -> Unit
) {
    val weeks = cells.chunked(7)
    Column(
        modifier = Modifier.fillMaxWidth().testTag("home-activity-grid"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(24.dp))
            ActivityMonthLabels(cells.map { it.date }, Modifier.weight(1f))
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
                            onClick = { onSelectCell(cell.date.toString()) },
                            preview = if (selectedDate == cell.date.toString()) {
                                {
                                    ActivityDayPreview(
                                        date = cell.date.toString(),
                                        expectedOwnerId = ownerId,
                                        state = dayDetails,
                                        preferredMassUnit = preferredMassUnit,
                                        onDismissRequest = onDismissSelection,
                                        onOpenRecords = { onOpenRecords(cell.date.toString()) }
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityMonthLabels(dates: List<LocalDate>, modifier: Modifier = Modifier) {
    val months = remember(dates) { HomeActivityWindowPolicy.visibleMonths(dates) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelSmall
    val gap = 3.dp
    BoxWithConstraints(modifier.heightIn(min = 18.dp)) {
        val columnStep = (maxWidth + gap) / HomeActivityWindowPolicy.WEEKS
        val columnStepPx = with(density) { columnStep.toPx() }.coerceAtLeast(1f)
        val widthsInColumns = months.associate { month ->
            val label = "${month.yearMonth.monthValue}월"
            val widthPx = textMeasurer.measure(label, style = labelStyle).size.width
            month.yearMonth to ceil(widthPx / columnStepPx).toInt().coerceAtLeast(1)
        }
        val placements = HomeActivityWindowPolicy.placeMonthLabels(months, widthsInColumns)
        placements.forEach { placement ->
            val label = "${placement.yearMonth.monthValue}월"
            val width = (columnStep * placement.columnSpan - gap).coerceAtLeast(1.dp)
            Text(
                text = label,
                modifier = Modifier
                    .offset(x = columnStep * placement.firstWeekColumn)
                    .widthIn(max = width)
                    .testTag("home-activity-month-${placement.yearMonth}")
                    .semantics {
                        contentDescription = "${placement.yearMonth.year}년 ${placement.yearMonth.monthValue}월"
                    },
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActivityCell(
    cell: HomeActivityCell,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    preview: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(3.dp)
    val alpha = HomeActivityCoveragePolicyV1.alpha(cell)
    val date = cell.date.toString()
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
            .then(if (selected) Modifier.border(2.5.dp, HomeActivitySelectionColor, shape) else Modifier)
            .clickable(enabled = cell.state == HomeActivityCellState.TRACKED, onClick = onClick)
            .testTag("home-activity-cell-${cell.date}")
            .semantics {
                contentDescription = cell.contentDescription + if (selected) ", 선택됨" else ""
                if (cell.state != HomeActivityCellState.TRACKED) disabled()
            }
    ) {
        preview?.invoke()
    }
}

@Composable
private fun ActivityDayPreview(
    date: String,
    expectedOwnerId: String,
    state: HomeActivityDayDetailsUiState,
    preferredMassUnit: MassUnit,
    onDismissRequest: () -> Unit,
    onOpenRecords: () -> Unit
) {
    val details = (state as? HomeActivityDayDetailsUiState.Ready)
        ?.takeIf { it.ownerId == expectedOwnerId && it.details.date == date }
        ?.details
    val isLoading = (state as? HomeActivityDayDetailsUiState.Loading)
        ?.let { it.ownerId == expectedOwnerId && it.date == date } ?: false
    val error = (state as? HomeActivityDayDetailsUiState.Error)
        ?.let { it.ownerId == expectedOwnerId && it.date == date } ?: false
    val rows = remember(details, preferredMassUnit) {
        details?.let { homeActivityPreviewRows(it, preferredMassUnit) }.orEmpty()
    }
    val density = LocalDensity.current
    val positionProvider = remember(density) { ActivityCalloutPositionProvider(density) }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = false)
    ) {
        Column(
            modifier = Modifier.widthIn(max = 240.dp)
                .drawBehind {
                    val tailHeight = 6.dp.toPx()
                    val halfWidth = 7.dp.toPx()
                    val centerX = positionProvider.arrowCenterPx.toFloat()
                        .coerceIn(halfWidth, size.width - halfWidth)
                    val path = Path().apply {
                        moveTo(centerX - halfWidth, size.height - tailHeight)
                        lineTo(centerX, size.height)
                        lineTo(centerX + halfWidth, size.height - tailHeight)
                        close()
                    }
                    drawPath(path, Color.White)
                }
                .padding(bottom = 6.dp)
                .testTag("home-activity-preview-$date"),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Column(
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(8.dp))
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        date,
                        modifier = Modifier.testTag("home-activity-preview-date")
                            .semantics { contentDescription = date.replace('-', ' ') },
                        color = Color(0xFF202124),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        "기록 보기 ›",
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onOpenRecords)
                            .padding(horizontal = 3.dp, vertical = 2.dp)
                            .testTag("home-activity-open-records"),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
                when {
                    isLoading || details == null && !error ->
                        Text("기록을 불러오는 중", color = Color(0xFF202124), style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    error -> Text("기록을 불러오지 못했어요", color = Color(0xFF202124),
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    details?.records?.isEmpty() != false || rows.isEmpty() ->
                        Text("기록 없음", modifier = Modifier.testTag("home-activity-preview-empty"),
                            color = Color(0xFF202124), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    else -> rows.forEach { row ->
                        Row(
                            Modifier.testTag("home-activity-preview-${row.kind.name.lowercase(Locale.ROOT)}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(row.kind.label,
                                color = Color(0xFF202124), style = MaterialTheme.typography.labelSmall)
                            Text("·", color = Color(0xFF202124), style = MaterialTheme.typography.labelSmall)
                            Text(
                                row.summary,
                                color = Color(0xFF202124),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private class ActivityCalloutPositionProvider(
    private val density: androidx.compose.ui.unit.Density
) : PopupPositionProvider {
    var arrowCenterPx: Int = 0
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val margin = with(density) { 8.dp.roundToPx() }
        val minX = margin
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(minX)
        val centeredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val x = centeredX.coerceIn(minX, maxX)
        arrowCenterPx = (anchorBounds.center.x - x)
            .coerceIn(with(density) { 14.dp.roundToPx() }, popupContentSize.width - with(density) { 14.dp.roundToPx() })

        val y = (anchorBounds.top - popupContentSize.height).coerceAtLeast(margin)
        return IntOffset(x, y)
    }
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
