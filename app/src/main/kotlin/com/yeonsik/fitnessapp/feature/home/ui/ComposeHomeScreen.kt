package com.yeonsik.fitnessapp.feature.home.ui

import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import com.yeonsik.fitnessapp.core.ui.FitnessStrengthIcon
import com.yeonsik.fitnessapp.core.ui.FitnessCardioIcon
import com.yeonsik.fitnessapp.core.ui.FitnessBodyMetricIcon
import com.yeonsik.fitnessapp.core.ui.FitnessMealIcon
import com.yeonsik.fitnessapp.core.ui.FitnessSection
import com.yeonsik.fitnessapp.core.ui.FitnessShape
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
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
    isActualActive: Boolean = true
) {
    val ready = (homeState as? HomeUiState.Ready)?.takeIf {
        it.snapshot.ownerId == ownerId && it.snapshot.today == today
    }
    val entrance = rememberTopLevelEntranceMotion(
        entranceState, entranceToken, isActualActive, contentReady = ready != null
    )
    if (ready == null) {
        LoadingHome()
        return
    }

    val snapshot = ready.snapshot
    val context = LocalContext.current
    val highContrast = Build.VERSION.SDK_INT >= 36 &&
        context.getSystemService(AccessibilityManager::class.java)
            ?.isHighContrastTextEnabled == true
    val useGlass = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !highContrast
    val heroStatus = homeTodayHeroStatus(snapshot, preferredMassUnit)

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
    ) {
        TopLevelEntranceContent(entrance, order = 0) {
            FitnessSection("오늘 상태") {
                HomeTodayHero(heroStatus, actions::continueWorkout)
            }
        }
        TopLevelEntranceContent(entrance, order = 1) {
            FitnessSection("빠른 이동") {}
        }
        TopLevelEntranceContent(entrance, order = 2) {
            HomeGlassWorkoutQuickActions(
                useGlass = useGlass,
                onStrength = { actions.navigate(FitnessScreen.STRENGTH) },
                onCardio = { actions.navigate(FitnessScreen.CARDIO) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        TopLevelEntranceContent(entrance, order = 3) {
            HomeGlassQuickActionCard(
                title = "식단",
                icon = FitnessMealIcon,
                onClick = { actions.openMealManagement(today, FitnessScreen.HOME) },
                useGlass = useGlass,
                modifier = Modifier.fillMaxWidth().testTag("home-quick-meal")
            )
        }
        TopLevelEntranceContent(entrance, order = 4) {
            HomeGlassQuickActionCard(
                title = "체중",
                icon = FitnessBodyMetricIcon,
                onClick = actions::showBodyMetric,
                useGlass = useGlass,
                modifier = Modifier.fillMaxWidth().testTag("home-quick-weight")
            )
        }
        TopLevelEntranceContent(entrance, order = 5) {
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
    Surface(
        modifier = modifier.fillMaxWidth().testTag("home-today-hero"),
        shape = FitnessShape.hero,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        HomeHeroContent(status, onContinue)
    }
}

@Composable
private fun HomeGlassWorkoutQuickActions(
    useGlass: Boolean,
    onStrength: () -> Unit,
    onCardio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = FitnessSpacing.homeActionMinHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeQuickActionItem(
                title = "근력 운동",
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
    if (useGlass) {
        HomeGlassPanel(
            modifier = modifier.heightIn(min = FitnessSpacing.homeActionMinHeight)
                .testTag("home-quick-workout"),
            shape = FitnessShape.card,
            source = Brush.linearGradient(listOf(colors.surface, colors.surfaceContainerHigh)),
            tint = colors.surface.copy(alpha = 0.28f),
            hero = false,
            content = { content() }
        )
    } else {
        FitnessCard(
            modifier = modifier.heightIn(min = FitnessSpacing.homeActionMinHeight)
                .testTag("home-quick-workout"),
            content = { content() }
        )
    }
}

@Composable
private fun HomeGlassQuickActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    useGlass: Boolean,
    modifier: Modifier = Modifier
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = FitnessSpacing.homeActionMinHeight)
                .padding(horizontal = FitnessSpacing.card),
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
    if (useGlass) {
        val colors = MaterialTheme.colorScheme
        HomeGlassPanel(
            modifier = modifier.heightIn(min = FitnessSpacing.homeActionMinHeight),
            shape = FitnessShape.card,
            source = Brush.linearGradient(listOf(colors.surface, colors.surfaceContainerHigh)),
            tint = colors.surface.copy(alpha = 0.28f),
            hero = false,
            onClick = onClick
        ) { content() }
    } else {
        FitnessCard(modifier.heightIn(min = FitnessSpacing.homeActionMinHeight), onClick = onClick) {
            content()
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
            .heightIn(min = FitnessSpacing.homeActionMinHeight)
            .clip(FitnessShape.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = FitnessSpacing.card),
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

@Composable
private fun HomeGlassPanel(
    modifier: Modifier,
    shape: Shape,
    source: Brush,
    tint: Color,
    hero: Boolean,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    // The navigation backdrop captures the whole page for the bottom bar. This local source
    // is drawn first so a Home panel never samples itself from that parent layer.
    val backdrop = rememberLayerBackdrop()
    val fullEffects = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    Box(
        modifier.clip(shape).then(
            if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
            else Modifier
        )
    ) {
        Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(source))
        Box(
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (hero && fullEffects) vibrancy()
                    blur((if (hero) 8.dp else 5.dp).toPx())
                    if (fullEffects) {
                        lens(
                            (if (hero) 6.dp else 3.dp).toPx(),
                            (if (hero) 10.dp else 5.dp).toPx()
                        )
                    }
                },
                highlight = {
                    if (hero) Highlight.Ambient.copy(alpha = 0.55f)
                    else Highlight.Plain.copy(alpha = 0.35f)
                },
                onDrawSurface = { drawRect(tint) }
            ).matchParentSize()
        )
        content()
    }
}

@Composable
private fun LoadingHome() {
    Column(Modifier.fillMaxWidth().padding(vertical = FitnessSpacing.section)) {
        Text(
            "오늘 상태를 불러오는 중입니다.",
            modifier = Modifier.padding(top = FitnessSpacing.small),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
