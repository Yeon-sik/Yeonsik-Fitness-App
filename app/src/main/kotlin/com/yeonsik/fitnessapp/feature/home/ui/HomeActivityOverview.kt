package com.yeonsik.fitnessapp.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityCell
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityCellState
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityKind

/** The summary and accessible date list use exactly the dates displayed by the existing heatmap. */
@Composable
internal fun HomeActivityOverview(
    cells: List<HomeActivityCell>,
    enabled: Boolean,
    onSelectDate: (String) -> Unit
) {
    val tracked = remember(cells) {
        cells.filter { it.state == HomeActivityCellState.TRACKED }.sortedByDescending { it.date }
    }
    val recordedDays = tracked.count { it.kinds.isNotEmpty() }
    var datesOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) datesOpen = false
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("기록한 날 ${recordedDays}일", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("home-activity-recorded-days"))
            Text("이 기간 집계 ${tracked.size}일 중", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(
            onClick = { datesOpen = true },
            enabled = enabled && tracked.isNotEmpty(),
            modifier = Modifier.heightIn(min = 48.dp).testTag("home-activity-date-list")
        ) {
            Text("날짜 목록", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
    if (datesOpen && enabled) {
        AlertDialog(
            onDismissRequest = { datesOpen = false },
            title = { Text("날짜 선택") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("이 기간의 날짜를 선택하면 기록 요약을 볼 수 있어요.",
                        style = MaterialTheme.typography.bodyMedium)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)
                        .testTag("home-activity-dates")) {
                        items(tracked, key = { it.date.toString() }) { cell ->
                            Column(
                                Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                    .clickable(role = Role.Button) {
                                        datesOpen = false
                                        onSelectDate(cell.date.toString())
                                    }
                                    .padding(vertical = 12.dp)
                                    .testTag("home-activity-date-option-${cell.date}")
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = cell.contentDescription
                                    },
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(cell.date.toString(), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    HomeActivityKind.entries.filter { it in cell.kinds }
                                        .joinToString(" · ") { it.label }.ifEmpty { "기록 없음" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { datesOpen = false },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("home-activity-dates-close")) {
                    Text("닫기", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}
