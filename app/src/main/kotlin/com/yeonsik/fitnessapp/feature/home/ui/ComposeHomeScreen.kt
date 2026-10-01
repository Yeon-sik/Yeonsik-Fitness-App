package com.yeonsik.fitnessapp.feature.home.ui

import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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
import com.yeonsik.fitnessapp.core.ui.FitnessButton
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import com.yeonsik.fitnessapp.core.ui.FitnessSection
import com.yeonsik.fitnessapp.core.ui.FitnessShape
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.state.FitnessScreen
import java.util.Locale

interface HomeScreenActions {
    fun continueWorkout()
    fun navigate(screen: FitnessScreen)
    fun showBodyMetric()
    fun openMealManagement(date: String, returnScreen: FitnessScreen)
}

private val HomeStrengthIcon = ImageVector.Builder(
    name = "HomeStrength",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3f, 9f)
        verticalLineTo(15f)
        moveTo(6f, 6f)
        verticalLineTo(18f)
        moveTo(6f, 12f)
        horizontalLineTo(18f)
        moveTo(18f, 6f)
        verticalLineTo(18f)
        moveTo(21f, 9f)
        verticalLineTo(15f)
    }
}.build()

private val HomeCardioIcon = ImageVector.Builder(
    name = "HomeCardio",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(12f, 20.5f)
        curveTo(8f, 17f, 3f, 13.1f, 3f, 8.6f)
        curveTo(3f, 5.6f, 5f, 3.6f, 7.7f, 3.6f)
        curveTo(9.5f, 3.6f, 11f, 4.5f, 12f, 6f)
        curveTo(13f, 4.5f, 14.5f, 3.6f, 16.3f, 3.6f)
        curveTo(19f, 3.6f, 21f, 5.6f, 21f, 8.6f)
        curveTo(21f, 13.1f, 16f, 17f, 12f, 20.5f)
        close()
    }
}.build()

private val HomeBodyMetricIcon = ImageVector.Builder(
    name = "HomeBodyMetric",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(12f, 2.8f)
        curveTo(10.5f, 2.8f, 9.3f, 4f, 9.3f, 5.5f)
        curveTo(9.3f, 7f, 10.5f, 8.2f, 12f, 8.2f)
        curveTo(13.5f, 8.2f, 14.7f, 7f, 14.7f, 5.5f)
        curveTo(14.7f, 4f, 13.5f, 2.8f, 12f, 2.8f)
        close()
        moveTo(3.5f, 9.6f)
        horizontalLineTo(20.5f)
        moveTo(12f, 9.6f)
        verticalLineTo(15.4f)
        moveTo(12f, 15.4f)
        lineTo(7.7f, 20.7f)
        moveTo(12f, 15.4f)
        lineTo(16.3f, 20.7f)
    }
}.build()

private val HomeMealIcon = ImageVector.Builder(
    name = "HomeMeal",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(8f, 7f)
        curveTo(6.6f, 5.8f, 8.8f, 4.8f, 7.7f, 3.5f)
        moveTo(12f, 7f)
        curveTo(10.6f, 5.8f, 12.8f, 4.8f, 11.7f, 3.5f)
        moveTo(16f, 7f)
        curveTo(14.6f, 5.8f, 16.8f, 4.8f, 15.7f, 3.5f)
        moveTo(3.5f, 9.5f)
        horizontalLineTo(20.5f)
        curveTo(19.9f, 14.1f, 16.6f, 17.5f, 12f, 17.5f)
        curveTo(7.4f, 17.5f, 4.1f, 14.1f, 3.5f, 9.5f)
        moveTo(9f, 20.5f)
        horizontalLineTo(15f)
    }
}.build()

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
    entranceState: HomeEntranceState = rememberHomeEntranceState(ownerId)
) {
    val ready = (homeState as? HomeUiState.Ready)?.takeIf {
        it.snapshot.ownerId == ownerId && it.snapshot.today == today
    }
    val contentReady = ready != null
    LaunchedEffect(contentReady, entranceState) {
        if (contentReady) entranceState.play()
    }
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
    val workoutSummary = homeWorkoutSummary(snapshot)

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
    ) {
        HomeEntranceContent(entranceState.played, order = 0) {
            FitnessSection("오늘 상태") {
                HomeGlassHero(snapshot, workoutSummary, actions::continueWorkout, useGlass)
            }
        }
        HomeEntranceContent(entranceState.played, order = 1) {
            FitnessSection("빠른 이동") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
                ) {
                    HomeEntranceContent(entranceState.played, order = 2) {
                        HomeGlassWorkoutQuickActions(
                            useGlass = useGlass,
                            onStrength = { actions.navigate(FitnessScreen.STRENGTH) },
                            onCardio = { actions.navigate(FitnessScreen.CARDIO) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    HomeEntranceContent(entranceState.played, order = 3) {
                        HomeGlassQuickActionCard(
                            title = "체중",
                            icon = HomeBodyMetricIcon,
                            onClick = actions::showBodyMetric,
                            useGlass = useGlass,
                            modifier = Modifier.fillMaxWidth().testTag("home-quick-weight")
                        )
                    }
                    HomeEntranceContent(entranceState.played, order = 4) {
                        HomeGlassQuickActionCard(
                            title = "식단",
                            icon = HomeMealIcon,
                            onClick = { actions.openMealManagement(today, FitnessScreen.HOME) },
                            useGlass = useGlass,
                            modifier = Modifier.fillMaxWidth().testTag("home-quick-meal")
                        )
                    }
                }
            }
        }
        HomeActivityHistorySection(
            state = activityState.takeIf { it.identity?.ownerId == ownerId && it.identity?.today == today }
                ?: HomeActivityUiState.Idle,
            onPrevious = onActivityPrevious,
            onNext = onActivityNext,
            onSelectPage = onActivitySelectPage,
            onRetry = onActivityRetry,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private const val HOME_ENTRANCE_DURATION_MILLIS = 390
private const val HOME_ENTRANCE_STAGGER_MILLIS = 70

@Composable
private fun HomeEntranceContent(
    visible: Boolean,
    order: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = HOME_ENTRANCE_DURATION_MILLIS,
            delayMillis = order * HOME_ENTRANCE_STAGGER_MILLIS,
            easing = FastOutSlowInEasing
        ),
        label = "home-entrance-$order"
    )
    Box(
        modifier.graphicsLayer {
                alpha = progress
                translationY = 14.dp.toPx() * (1f - progress)
                scaleX = 0.985f + 0.015f * progress
                scaleY = 0.985f + 0.015f * progress
            }
    ) {
        content()
    }
}

@Composable
private fun HomeGlassHero(
    snapshot: HomeSnapshot,
    workoutSummary: String?,
    onContinue: () -> Unit,
    useGlass: Boolean
) {
    val colors = MaterialTheme.colorScheme
    if (useGlass) {
        HomeGlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = FitnessShape.hero,
            source = Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerHigh)),
            tint = colors.primaryContainer.copy(alpha = 0.32f),
            hero = true
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.onPrimaryContainer) {
                HomeHeroContent(snapshot, workoutSummary, onContinue, Modifier.fillMaxWidth())
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = FitnessShape.hero,
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer
        ) {
            HomeHeroContent(snapshot, workoutSummary, onContinue)
        }
    }
}

@Composable
private fun HomeHeroContent(
    snapshot: HomeSnapshot,
    workoutSummary: String?,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inProgress = snapshot.inProgressSessionId != null
    Column(
        modifier.padding(FitnessSpacing.hero),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
    ) {
        Text(if (inProgress) "운동 진행 중" else "오늘", style = MaterialTheme.typography.displaySmall)
        Text(
            when {
                inProgress -> "진행 중인 운동을 이어서 기록하세요."
                workoutSummary != null -> "오늘 운동 완료"
                else -> "아직 완료한 운동이 없어요."
            },
            style = MaterialTheme.typography.bodyLarge
        )
        if (inProgress) {
            FitnessButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().padding(top = FitnessSpacing.small)
            ) { Text("운동 이어가기") }
        } else {
            if (workoutSummary != null) {
                Text(workoutSummary, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                homeBodyMealSummary(snapshot),
                style = MaterialTheme.typography.bodyMedium
            )
        }
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
                icon = HomeStrengthIcon,
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
                icon = HomeCardioIcon,
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

internal fun homeWorkoutSummary(snapshot: HomeSnapshot): String? {
    val metrics = snapshot.dayMetrics[snapshot.today] ?: return null
    if (metrics.sessionCount <= 0) return null
    val parts = buildList {
        add("${metrics.sessionCount}회")
        if (metrics.totalSetCount > 0) add("${metrics.totalSetCount}세트")
        if (metrics.totalVolumeKg > 0.0) {
            add(
                if (metrics.totalVolumeKg >= 1000.0) {
                    String.format(Locale.KOREAN, "%.1ft", metrics.totalVolumeKg / 1000.0)
                } else {
                    "${FitnessUiTokens.formatVolume(metrics.totalVolumeKg)}kg"
                }
            )
        }
        if (metrics.totalDurationSeconds > 0) {
            add(FitnessUiTokens.formatDuration(metrics.totalDurationSeconds))
        }
    }
    return parts.joinToString(" · ")
}

internal fun homeBodyMealSummary(snapshot: HomeSnapshot): String {
    val weight = snapshot.todayWeight?.let { "체중 ${FitnessUiTokens.trimDouble(it.weightKg)}kg" }
        ?: "체중 미기록"
    val meals = snapshot.mealCounts[snapshot.today] ?: 0
    val mealStatus = if (meals > 0) "식사 ${meals}회" else "식사 미기록"
    return "$weight · $mealStatus"
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
