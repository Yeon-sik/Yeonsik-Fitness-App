package com.yeonsik.fitnessapp.feature.supplement.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.BuildConfig
import com.yeonsik.fitnessapp.app.navigation.*
import com.yeonsik.fitnessapp.cardio.*
import com.yeonsik.fitnessapp.config.*
import com.yeonsik.fitnessapp.core.account.*
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.cardio.model.*
import com.yeonsik.fitnessapp.feature.cardio.ui.*
import com.yeonsik.fitnessapp.feature.development.ui.*
import com.yeonsik.fitnessapp.feature.exercise.ui.*
import com.yeonsik.fitnessapp.feature.home.ui.*
import com.yeonsik.fitnessapp.feature.meal.ui.*
import com.yeonsik.fitnessapp.feature.routine.ui.*
import com.yeonsik.fitnessapp.feature.supplement.ui.*
import com.yeonsik.fitnessapp.feature.workout.model.*
import com.yeonsik.fitnessapp.feature.workout.ui.*
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.*
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SupplementScreen(host: ScreenHost, ownerId: String, today: String) {
    val state by host.supplementViewModel().uiState.observeAsState(SupplementUiState.Idle)
    LaunchedEffect(ownerId, today) { host.supplementViewModel().enter(AccountScope(ownerId), today) }
    val ready = state as? SupplementUiState.Ready
    AppHeader("보충제", today, back = { host.back() })
    if (ready == null || ready.ownerId != ownerId) {
        Text("보충제 계획을 불러오는 중입니다.")
        return
    }
    Text("복용 ${ready.progress.taken}/${ready.progress.planned} · 14일 준수율 ${ready.adherence.adherencePercent()}%")
    ready.plans.forEach { plan ->
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AppSpacing.card), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text("${plan.typeName} · ${plan.brandName}", fontWeight = FontWeight.Bold)
                Text("${plan.doseAmount}${plan.doseUnit} · ${plan.recordedCount()}/${plan.timesPerDay}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    AppButton(onClick = { host.supplementViewModel().record(AccountScope(ownerId), today, plan.scheduleId, "taken") },
                        enabled = plan.unrecordedCount() > 0) { Text("복용") }
                    AppOutlinedButton(onClick = { host.supplementViewModel().record(AccountScope(ownerId), today, plan.scheduleId, "skipped") },
                        enabled = plan.unrecordedCount() > 0) { Text("건너뜀") }
                    AppOutlinedButton(onClick = { host.supplementViewModel().undo(AccountScope(ownerId), today, plan.scheduleId) },
                        enabled = plan.recordedCount() > 0) { Text("되돌리기") }
                }
            }
        }
    }
}

