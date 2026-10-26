package com.yeonsik.fitnessapp.feature.home.ui

import com.yeonsik.fitnessapp.core.ui.FitnessLoadingState
import com.yeonsik.fitnessapp.core.ui.FitnessLoadingVariant
import com.yeonsik.fitnessapp.core.ui.rememberDelayedLoadingVisible
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import com.yeonsik.fitnessapp.core.ui.FitnessStrengthIcon
import com.yeonsik.fitnessapp.core.ui.FitnessCardioIcon
import com.yeonsik.fitnessapp.core.ui.FitnessBodyMetricIcon
import com.yeonsik.fitnessapp.core.ui.FitnessMealIcon
import com.yeonsik.fitnessapp.core.ui.FitnessSection
import com.yeonsik.fitnessapp.core.ui.FitnessShape
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.LocalFitnessHeroColors
import com.yeonsik.fitnessapp.core.ui.TopLevelEntranceContent
import com.yeonsik.fitnessapp.core.ui.TopLevelEntranceState
import com.yeonsik.fitnessapp.core.ui.rememberTopLevelEntranceMotion
import com.yeonsik.fitnessapp.core.ui.rememberTopLevelEntranceState
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.state.FitnessScreen

interface HomeScreenActions {
    fun continueWorkout()
    fun navigate(screen: FitnessScreen)
    fun showBodyMetric()
    fun openMealManagement(date: String, returnScreen: FitnessScreen)
}

private val HomeQuickActionMinHeight = 64.dp

@Composable
internal fun HomeDestination(
    homeState: HomeUiState,
    ownerId: String,
    today: String,
    actions: HomeScreenActions,
    activityState: HomeActivityUiState = HomeActivityUiState.Idle,
    onActivityPrevious: () -> Unit = {},
    onActivityNext: () -> Unit = {},
    onActivitySelectPage: (Int) -> Unit = {},
    onActivityRetry: () -> Unit = {},
    activityDayDetails: HomeActivityDayDetailsUiState = HomeActivityDayDetailsUiState.Idle,
    onActivityDateSelected: (String) -> Unit = {},
    onActivityOpenRecords: (String) -> Unit = {},
    entranceState: TopLevelEntranceState = rememberTopLevelEntranceState("HOME"),
    preferredMassUnit: MassUnit = MassUnit.KG,
    entranceToken: Long? = null,
    isActualActive: Boolean = true,
    onRetry: () -> Unit = {}
) {
    val ready = (homeState as? HomeUiState.Ready)?.takeIf {
        it.snapshot.ownerId == ownerId && it.snapshot.today == today
    }
    val failed = homeState is HomeUiState.Error && homeState.ownerId == ownerId &&
        (homeState.date == null || homeState.date == today)
    val loading = ready == null && !failed
    val showLoading = rememberDelayedLoadingVisible(loading, ownerId to today)
    val entrance = rememberTopLevelEntranceMotion(
        entranceState, entranceToken, isActualActive, contentReady = (ready != null || failed) && !showLoading
    )
    if (loading || showLoading) {
        LoadingHome(showLoading)
        return
    }

    val heroStatus = ready?.snapshot?.let { homeTodayHeroStatus(it, preferredMassUnit) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
    ) {
        TopLevelEntranceContent(entrance, order = 0) {
            FitnessSection("오늘 상태") {
                if (heroStatus != null) HomeTodayHero(heroStatus, actions::continueWorkout)
                else HomeStatusError(onRetry)
            }
        }
        TopLevelEntranceContent(entrance, order = 1) {
            HomeWorkoutQuickActions(
                onStrength = { actions.navigate(FitnessScreen.STRENGTH) },
                onCardio = { actions.navigate(FitnessScreen.CARDIO) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        TopLevelEntranceContent(entrance, order = 2) {
            HomeQuickActionCard(
                title = "식단",
                icon = FitnessMealIcon,
                onClick = { actions.openMealManagement(today, FitnessScreen.HOME) },
                modifier = Modifier.fillMaxWidth().testTag("home-quick-meal")
            )
        }
        TopLevelEntranceContent(entrance, order = 3) {
            HomeQuickActionCard(
                title = "체중",
                icon = FitnessBodyMetricIcon,
                onClick = actions::showBodyMetric,
                modifier = Modifier.fillMaxWidth().testTag("home-quick-weight")
            )
        }
        TopLevelEntranceContent(entrance, order = 4) {
            HomeActivityHistorySection(
                state = activityState.takeIf { it.identity?.ownerId == ownerId && it.identity?.today == today }
                    ?: HomeActivityUiState.Idle,
                onPrevious = onActivityPrevious,
                onNext = onActivityNext,
                onSelectPage = onActivitySelectPage,
                onRetry = onActivityRetry,
                dayDetails = activityDayDetails,
                onSelectDate = onActivityDateSelected,
                onOpenRecords = onActivityOpenRecords,
                preferredMassUnit = preferredMassUnit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun HomeTodayHero(
    status: HomeTodayHeroStatus,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalFitnessHeroColors.current
    Box(
        modifier = modifier.fillMaxWidth().testTag("home-today-hero")
            .clip(FitnessShape.hero)
            .background(colors.surface, FitnessShape.hero)
            .border(1.dp, colors.border, FitnessShape.hero)
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.title) {
            HomeHeroContent(status)
        }
    }
}

@Composable
private fun HomeWorkoutQuickActions(
    onStrength: () -> Unit,
    onCardio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    FitnessCard(
        modifier = modifier.heightIn(min = FitnessSpacing.homeActionMinHeight)
            .testTag("home-quick-workout"),
        outlined = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = HomeQuickActionMinHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeQuickActionItem(
                title = "무산소",
                icon = FitnessStrengthIcon,
                onClick = onStrength,
                modifier = Modifier.weight(1f).testTag("home-quick-strength")
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(colors.outlineVariant.copy(alpha = 0.6f))
            )
            HomeQuickActionItem(
                title = "유산소",
                icon = FitnessCardioIcon,
                onClick = onCardio,
                modifier = Modifier.weight(1f).testTag("home-quick-cardio")
            )
        }
    }
}

@Composable
private fun HomeQuickActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FitnessCard(
        modifier = modifier.heightIn(min = FitnessSpacing.homeActionMinHeight),
        onClick = onClick,
        outlined = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = HomeQuickActionMinHeight)
                .padding(horizontal = FitnessSpacing.card, vertical = FitnessSpacing.gap),
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun HomeQuickActionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = HomeQuickActionMinHeight)
            .clip(FitnessShape.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = FitnessSpacing.card, vertical = FitnessSpacing.gap),
        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HomeStatusError(onRetry: () -> Unit) {
    FitnessCard {
        Column(Modifier.fillMaxWidth().padding(FitnessSpacing.card),
            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
            Text("오늘 상태를 불러오지 못했어요.",
                modifier = Modifier.testTag("home-status-error")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyLarge)
            Text("다시 시도하거나 아래에서 기록을 이어갈 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp).testTag("home-status-retry")) {
                Text("다시 시도", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun LoadingHome(visible: Boolean) {
    FitnessLoadingState(listOf("오늘 상태를 불러오는 중"), FitnessLoadingVariant.SECTION,
        Modifier.fillMaxWidth().padding(vertical = FitnessSpacing.section), visible)
}
