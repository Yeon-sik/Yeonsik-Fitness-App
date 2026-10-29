package com.yeonsik.fitnessapp.feature.records.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeonsik.fitness.shared.feature.records.model.RecordsDayDetail
import com.yeonsik.fitnessapp.core.ui.AppCard
import com.yeonsik.fitnessapp.core.ui.AppOutlinedButton
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The calendar grid, marker mapping and selection appearance remain in their existing components. */
@Composable
internal fun RecordsMonthControls(
    month: YearMonth,
    isToday: Boolean,
    actions: RecordsScreenActions,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = actions::previousMonth,
                modifier = Modifier.width(48.dp).heightIn(min = 48.dp).semantics { contentDescription = "이전 달" }) {
                Text("‹", fontSize = 24.sp)
            }
            Text(
                "${month.year}년 ${month.monthValue}월",
                Modifier.weight(1f).semantics { heading() },
                fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = actions::nextMonth,
                modifier = Modifier.width(48.dp).heightIn(min = 48.dp).semantics { contentDescription = "다음 달" }) {
                Text("›", fontSize = 24.sp)
            }
        }
        RecordsTodayButton(isToday, actions, Modifier.align(Alignment.End))
    }
}

@Composable
private fun RecordsTodayButton(
    isToday: Boolean,
    actions: RecordsScreenActions,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = actions::today,
        enabled = !isToday,
        modifier = modifier.heightIn(min = 48.dp).testTag("records-today"),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) { Text("오늘로 이동", fontSize = 14.sp, lineHeight = 22.sp) }
}

internal fun recordsSelectedDateLabel(date: String): String =
    runCatching {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))
    }.getOrDefault(date)

internal fun recordsDayCounts(detail: RecordsDayDetail): String =
    if (!detail.hasAnyRecord) "저장된 기록이 없습니다"
    else "운동 ${detail.workouts.size}건 · 식단 ${detail.meals.size}건 · 체중 ${detail.bodyMetrics.size}건"

@Composable
internal fun RecordsSelectedDaySummary(
    detail: RecordsDayDetail,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(modifier.fillMaxWidth().testTag("records-selected-summary")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (LocalDensity.current.fontScale > 1.3f) {
                RecordsSummaryText(detail, Modifier.fillMaxWidth())
                AppOutlinedButton(onClick = onOpenDetails,
                    modifier = Modifier.align(Alignment.End).testTag("records-open-day")) {
                    Text("기록 보기", fontSize = 14.sp, lineHeight = 22.sp)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RecordsSummaryText(detail, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    AppOutlinedButton(
                        onClick = onOpenDetails,
                        modifier = Modifier.testTag("records-open-day")
                    ) { Text("기록 보기", fontSize = 14.sp, lineHeight = 22.sp) }
                }
            }
            val muscles = detail.workouts.flatMap { it.muscleLabels }.distinct()
            if (muscles.isNotEmpty()) {
                Text("운동 부위 · ${muscles.joinToString(" · ")}",
                    Modifier.padding(top = 4.dp), fontSize = 14.sp, lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecordsSummaryText(detail: RecordsDayDetail, modifier: Modifier) {
    Column(modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(recordsSelectedDateLabel(detail.date), fontSize = 16.sp, lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold)
        Text(recordsDayCounts(detail), fontSize = 14.sp, lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordsDetailSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("records-day-sheet").semantics { paneTitle = "선택일 기록" }
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("선택일 기록", Modifier.weight(1f).semantics { heading() },
                fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)
                .testTag("records-day-sheet-close"),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text("닫기", fontSize = 16.sp)
            }
        }
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
