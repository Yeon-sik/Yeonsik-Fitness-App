package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.home.ui.HomeUiState
import com.yeonsik.fitnessapp.state.FitnessScreen

/** Entry actions remain available while today's read model loads or retries. */
@Composable
internal fun WorkoutOverview(
    state: HomeUiState,
    ownerId: String,
    today: String,
    unit: MassUnit,
    actions: WorkoutOverviewActions,
    entranceState: TopLevelEntranceState = rememberTopLevelEntranceState("WORKOUT"),
    entranceToken: Long? = null,
    isActualActive: Boolean = true,
    inProgressIsCardio: Boolean? = null
) {
    val snapshot = (state as? HomeUiState.Ready)?.snapshot
        ?.takeIf { it.ownerId == ownerId && it.today == today }
    val error = (state as? HomeUiState.Error)?.takeIf { it.ownerId == ownerId && it.date == today }
    val inProgress = snapshot?.inProgressSessionId != null
    val strengthInProgress = inProgress && inProgressIsCardio == false
    val cardioInProgress = inProgress && inProgressIsCardio == true
    val entrance = rememberTopLevelEntranceMotion(
        entranceState, entranceToken, isActualActive, contentReady = true
    )
    var order = 0
    TopLevelEntranceContent(entrance, order++) {
        AppHeader("피트니스", today)
    }
    // A known session can be resumed even while its type is being resolved.
    if (inProgress && inProgressIsCardio == null) {
        TopLevelEntranceContent(entrance, order++) {
            WorkoutOverviewAction("진행 중 운동", "이어서 기록하기", FitnessStrengthIcon,
                actions::continueWorkout, "resume", inProgress = true)
        }
    }
    TopLevelEntranceContent(entrance, order++) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
            WorkoutOverviewHeading("운동 기록")
            WorkoutOverviewAction(
                "무산소 운동",
                if (strengthInProgress) "진행 중 · 이어하기" else "루틴 선택 · 자유 운동",
                FitnessStrengthIcon,
                { if (strengthInProgress) actions.continueWorkout() else actions.navigate(FitnessScreen.STRENGTH) },
                "strength", inProgress = strengthInProgress
            )
            WorkoutOverviewAction(
                "유산소 운동",
                if (cardioInProgress) "진행 중 · 이어하기" else "종목 선택 · 실내/실외",
                FitnessCardioIcon,
                { if (cardioInProgress) actions.continueWorkout() else actions.navigate(FitnessScreen.CARDIO) },
                "cardio", inProgress = cardioInProgress
            )
        }
    }
    TopLevelEntranceContent(entrance, order++) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
            WorkoutOverviewHeading("생활 기록")
            WorkoutOverviewAction("체중 기록", "오늘 체중 입력", FitnessBodyMetricIcon,
                actions::showBodyMetric, "weight")
            WorkoutOverviewAction("식단 기록", "오늘 식단 조회 · 기록", FitnessMealIcon,
                actions::openMeals, "meal")
        }
    }
    TopLevelEntranceContent(entrance, order) {
        WorkoutOverviewSummary(snapshot, today, unit, error != null, actions::retryOverview)
    }
}

@Composable
private fun WorkoutOverviewHeading(text: String) {
    Text(text, Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
}

@Composable
private fun WorkoutOverviewAction(
    label: String,
    supportingText: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    inProgress: Boolean = false
) {
    val colors = LocalFitnessColors.current
    val scheme = MaterialTheme.colorScheme
    val description = if (LocalDensity.current.fontScale > 1.3f) {
        supportingText.replace(" · ", "\n")
    } else supportingText
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = if (inProgress) 96.dp else 80.dp)
            .testTag("fitness-action-$tag")
            .semantics { if (inProgress) stateDescription = "진행 중" },
        shape = FitnessShape.card,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (inProgress) colors.action else scheme.surface,
            contentColor = if (inProgress) colors.onAction else scheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, Modifier.size(24.dp),
                tint = if (inProgress) colors.onAction else colors.action)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, Modifier.testTag("fitness-title-$tag"),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp))
                Text(description, Modifier.testTag("fitness-description-$tag"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (inProgress) colors.onAction else scheme.onSurfaceVariant)
            }
            Icon(FitnessActionNextIcon, contentDescription = null, Modifier.size(20.dp))
        }
    }
}

@Composable
private fun WorkoutOverviewSummary(
    snapshot: HomeSnapshot?,
    today: String,
    unit: MassUnit,
    hasError: Boolean,
    retry: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        WorkoutOverviewHeading("오늘 기록")
        AppCard(Modifier.fillMaxWidth().testTag("fitness-summary")) {
            if (snapshot != null) {
                val volume = MassFormatter.withUnit(snapshot.dayMetrics[today]?.totalVolumeKg ?: 0.0, unit)
                val meals = "${snapshot.mealCounts[today] ?: 0}끼"
                BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
                    if (LocalDensity.current.fontScale > 1.3f || maxWidth < 280.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            WorkoutOverviewMetric("완료 세트 볼륨", volume, "volume")
                            WorkoutOverviewMetric("식단", meals, "meals")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            WorkoutOverviewMetric("완료 세트 볼륨", volume, "volume", Modifier.weight(1f))
                            WorkoutOverviewMetric("식단", meals, "meals", Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (hasError) "오늘 기록을 불러오지 못했습니다." else "오늘 기록을 불러오는 중입니다.",
                        Modifier.testTag("fitness-summary-status").semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium)
                    if (hasError) {
                        TextButton(onClick = retry,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("fitness-summary-retry"),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                            Text("다시 불러오기")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutOverviewMetric(label: String, value: String, tag: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.testTag("fitness-value-$tag"), style = MaterialTheme.typography.headlineSmall)
    }
}
