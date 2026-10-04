package com.yeonsik.fitnessapp.feature.cardio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.cardio.model.CardioActivityType
import com.yeonsik.fitness.shared.feature.cardio.model.CardioEnvironment
import com.yeonsik.fitnessapp.R
import com.yeonsik.fitnessapp.core.ui.*

/** Rendering-only boundary: no repository, navigation owner, or ViewModel is required. */
@Composable
internal fun CardioStartContent(
    state: CardioStartUiState,
    onSearch: (String) -> Unit,
    onSelectActivity: (CardioActivityType) -> Unit,
    onSelectEnvironment: (CardioEnvironment) -> Unit,
    onStart: (CardioActivityType, CardioEnvironment) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    starting: Boolean = false
) {
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxSize().imePadding(), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
        FitnessHeader("유산소", back = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
            FitnessTextField(state.query, onSearch, Modifier.fillMaxWidth().testTag("cardio_search"),
                label = { Text("운동 검색") })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("운동 종류", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("최근 사용순 · 이름순", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(Modifier.fillMaxWidth(), shape = FitnessShape.card,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                LazyColumn(Modifier.fillMaxWidth().height(220.dp).selectableGroup().testTag("cardio_activities"),
                    contentPadding = PaddingValues(4.dp)) {
                    items(state.activities, key = { it.id }) { type ->
                        CardioActivityRow(type, type == state.selectedActivity,
                            state.lastStartedAt.containsKey(type.id), onClick = {
                                focus.clearFocus(); onSelectActivity(type)
                            })
                    }
                    if (state.activities.isEmpty()) item {
                        Text("검색 결과가 없습니다.", Modifier.padding(FitnessSpacing.card))
                    }
                }
            }
            state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            CardioActivityDescription(state)
            Text("운동 환경", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
                CardioEnvironment.entries.forEach { environment ->
                    FitnessOutlinedButton(
                        onClick = { onSelectEnvironment(environment) }, modifier = Modifier.weight(1f),
                        enabled = state.selectedActivity?.supportsOutdoorGps == true && !starting,
                        selected = state.environment == environment
                    ) { Text(environment.labelKo) }
                }
            }
        }
        FitnessButton(onClick = {
            val type = state.selectedActivity
            val environment = state.environment
            if (state.canStart && !starting && type != null && environment != null) {
                focus.clearFocus(); onStart(type, environment)
            }
        }, modifier = Modifier.fillMaxWidth().testTag("cardio_start"), enabled = state.canStart && !starting) {
            Text(if (starting) "운동 준비 중…" else "운동 시작")
        }
    }
}

@Composable
private fun CardioActivityRow(type: CardioActivityType, selected: Boolean, recent: Boolean,
                             onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = FitnessShape.button,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = FitnessSpacing.touch)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .testTag("cardio_activity_${type.id}")
            .padding(horizontal = FitnessSpacing.card, vertical = FitnessSpacing.gap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
            Icon(painterResource(cardioIconResources[type.id] ?: R.drawable.ic_cardio_running), null,
                Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(type.labelKo, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (recent) Text("최근", style = MaterialTheme.typography.labelSmall)
            if (selected) Text("선택", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CardioActivityDescription(state: CardioStartUiState) {
    val type = state.selectedActivity
    FitnessCard(Modifier.fillMaxWidth().testTag("cardio_description")) {
        Column(Modifier.padding(FitnessSpacing.card), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(type?.description ?: "운동 종류를 선택하세요.", style = MaterialTheme.typography.bodyMedium)
            if (type != null) {
                Text(if (type.supportsOutdoorGps) "실내·실외 선택 가능" else "실내 기구 운동 · 환경 변경 불가",
                    style = MaterialTheme.typography.labelLarge)
                val values = when {
                    state.environment == CardioEnvironment.OUTDOOR -> "시간 · GPS 거리·경로 · 평균 심박수(선택)"
                    type.supportsManualDistance -> "시간 · 기구 거리(선택) · 평균 심박수(선택)"
                    else -> "시간 · 평균 심박수(선택)"
                }
                Text(values, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Text("선택한 운동의 기록 항목과 환경을 여기서 확인할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val cardioIconResources = mapOf(
    "walking" to R.drawable.ic_cardio_walking, "running" to R.drawable.ic_cardio_running,
    "cycling" to R.drawable.ic_cardio_cycling, "stair_stepper" to R.drawable.ic_cardio_stair_stepper,
    "rowing" to R.drawable.ic_cardio_rowing, "elliptical" to R.drawable.ic_cardio_elliptical
)
