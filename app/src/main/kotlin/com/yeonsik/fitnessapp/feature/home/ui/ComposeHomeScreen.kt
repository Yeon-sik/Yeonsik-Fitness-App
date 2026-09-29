package com.yeonsik.fitnessapp.feature.home.ui

import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
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
    actions: HomeScreenActions
) {
    val ready = homeState as? HomeUiState.Ready
    if (ready == null || ready.snapshot.ownerId != ownerId || ready.snapshot.today != today) {
        LoadingHome()
        return
    }

    val snapshot = ready.snapshot
    val context = LocalContext.current
    val highContrast = Build.VERSION.SDK_INT >= 36 &&
        context.getSystemService(AccessibilityManager::class.java)
            ?.isHighContrastTextEnabled == true
    val useGlass = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !highContrast
    val status: String
    val message: String
    val cta: String
    val onCta: () -> Unit
    when {
        snapshot.inProgressSessionId != null -> {
            status = "진행 중"
            message = "진행 중인 운동을 이어서 기록하세요."
            cta = "운동 이어가기"
            onCta = actions::continueWorkout
        }
        snapshot.todaySessions.isEmpty() -> {
            status = "운동 전"
            message = "오늘 운동을 시작하세요."
            cta = "운동 시작"
            onCta = { actions.navigate(FitnessScreen.WORKOUT) }
        }
        else -> {
            status = "완료"
            message = "오늘 운동 기록을 확인할 수 있습니다."
            cta = "기록 보기"
            onCta = { actions.navigate(FitnessScreen.RECORDS) }
        }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
    ) {
        FitnessSection("오늘 상태") {
            HomeGlassHero(status, message, cta, onCta, useGlass)
        }
        FitnessSection("기록으로 이동") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
            ) {
                HomeGlassQuickRecordCard(
                    "체중",
                    "오늘 체중 기록",
                    actions::showBodyMetric,
                    useGlass,
                    Modifier.fillMaxWidth()
                )
                HomeGlassQuickRecordCard(
                    "식사",
                    "오늘 식사 기록",
                    { actions.openMealManagement(today, FitnessScreen.HOME) },
                    useGlass,
                    Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun HomeGlassHero(
    status: String,
    message: String,
    cta: String,
    onCta: () -> Unit,
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
                HomeHeroContent(status, message, cta, onCta, Modifier.fillMaxWidth())
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = FitnessShape.hero,
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer
        ) {
            HomeHeroContent(status, message, cta, onCta)
        }
    }
}

@Composable
private fun HomeHeroContent(
    status: String,
    message: String,
    cta: String,
    onCta: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.padding(FitnessSpacing.hero),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
    ) {
        Text(status, style = MaterialTheme.typography.displaySmall)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        FitnessButton(
            onClick = onCta,
            modifier = Modifier.fillMaxWidth().padding(top = FitnessSpacing.small)
        ) { Text(cta) }
    }
}

@Composable
private fun HomeGlassQuickRecordCard(
    title: String,
    detail: String,
    onClick: () -> Unit,
    useGlass: Boolean,
    modifier: Modifier = Modifier
) {
    val content: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(FitnessSpacing.card),
            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
