package com.yeonsik.fitnessapp.feature.home.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessRecordMarkerColors
import com.yeonsik.fitnessapp.core.ui.FitnessShape
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing

private const val HERO_STATUS_MOTION_MILLIS = 220
// Hero keeps a blue surface in both themes; these tones stay legible on that surface.
internal val HomeHeroWeightIncreaseColor = Color(0xFF0B4020)
internal val HomeHeroWeightDecreaseColor = Color(0xFF7F1D1D)

@Composable
internal fun HomeHeroContent(
    status: HomeTodayHeroStatus,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.padding(FitnessSpacing.hero),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.card)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.card),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("오늘", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag("home-hero-title"))
            HorizontalDivider(Modifier.weight(1f), color = colors.onPrimary.copy(alpha = 0.18f))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
        ) {
            status.domains.forEach { domain ->
                HeroDomainStatus(domain, Modifier.weight(1f))
            }
        }
        HeroRecordSummary(status)
    }
}

@Composable
private fun HeroRecordSummary(status: HomeTodayHeroStatus) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
        Row(
            Modifier.fillMaxWidth().testTag("home-hero-progress").semantics {
                contentDescription = "오늘 ${status.completedDomainCount}/3 영역 완료"
                progressBarRangeInfo = ProgressBarRangeInfo(status.progress, 0f..1f)
            },
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
        ) {
            status.domains.forEach { domain ->
                val fill = animateFloatAsState(
                    domain.progress,
                    animationSpec = tween(HERO_STATUS_MOTION_MILLIS), label = "home-progress-${domain.key}"
                )
                val marker = FitnessRecordMarkerColors.byKey.getValue(domain.key)
                val track = colors.onPrimary.copy(alpha = 0.18f)
                Box(Modifier.weight(1f).height(4.dp).testTag("home-hero-segment-${domain.key}")
                    .semantics {
                        contentDescription = "${domain.label} 진행률"
                        progressBarRangeInfo = ProgressBarRangeInfo(domain.progress, 0f..1f)
                    }.drawBehind {
                        drawRoundRect(track, cornerRadius = CornerRadius(2.dp.toPx()))
                        if (fill.value > 0f) drawRoundRect(marker,
                            size = Size(size.width * fill.value, size.height),
                            cornerRadius = CornerRadius(2.dp.toPx()))
                    })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
            status.domains.forEach { domain ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.micro, Alignment.CenterHorizontally)) {
                    val progress = animateFloatAsState(
                        if (domain.recorded) 1f else 0f,
                        animationSpec = tween(HERO_STATUS_MOTION_MILLIS), label = "home-marker-${domain.key}"
                    )
                    val marker = FitnessRecordMarkerColors.byKey.getValue(domain.key)
                    val outline = colors.onPrimary.copy(alpha = 0.55f)
                    Box(Modifier.size(10.dp).testTag("home-hero-marker-${domain.key}")
                        .semantics {
                            contentDescription = "${domain.label} 기록 표시"
                            stateDescription = domain.completionDescription
                        }
                        .graphicsLayer {
                            scaleX = 0.9f + progress.value * 0.1f
                            scaleY = scaleX
                        }
                        .drawBehind {
                            drawCircle(marker.copy(alpha = progress.value))
                            drawCircle(if (domain.recorded) marker else outline,
                                radius = size.minDimension / 2 - 0.5.dp.toPx(),
                                style = Stroke(1.dp.toPx()))
                        })
                }
            }
        }
    }
}

@Composable
private fun HeroDomainStatus(domain: HomeHeroDomainStatus, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onPrimary
    val typography = MaterialTheme.typography
    val valueHeight = with(LocalDensity.current) {
        typography.titleLarge.lineHeight.toDp() + typography.bodySmall.lineHeight.toDp()
    } + FitnessSpacing.micro
    Column(
        modifier.clipToBounds().testTag("home-hero-domain-${domain.key}").semantics(mergeDescendants = true) {
            contentDescription = "${domain.label}, ${domain.accessibilityValue}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
    ) {
        Text(domain.label, style = typography.labelMedium, color = muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        AnimatedContent(
            targetState = domain,
            modifier = Modifier.fillMaxWidth().heightIn(min = valueHeight),
            transitionSpec = {
                (fadeIn(tween(HERO_STATUS_MOTION_MILLIS)) + slideInVertically { it / 12 }) togetherWith
                    (fadeOut(tween(180)) + slideOutVertically { -it / 12 })
            },
            label = "home-value-${domain.key}"
        ) { displayed ->
            val value = displayed.mealCount?.let { "${it}끼" } ?: displayed.value
            val detail = displayed.detail.takeIf { displayed.mealCount == null }
            val detailColor = when (displayed.detailTone) {
                HomeHeroDetailTone.INCREASE -> HomeHeroWeightIncreaseColor
                HomeHeroDetailTone.DECREASE -> HomeHeroWeightDecreaseColor
                HomeHeroDetailTone.DEFAULT -> muted
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
                Text(value, style = typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.fillMaxWidth().testTag("home-hero-value-${domain.key}"),
                    maxLines = 1, softWrap = false,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                if (displayed.mealCount != null) {
                    if (displayed.detail != null) Text(displayed.detail,
                        style = typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = muted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().testTag("home-hero-protein-target"))
                    Text(displayed.value.replace(" (", "\u00A0("),
                        style = typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = muted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().testTag("home-hero-protein-current"))
                }
                if (detail != null) Text(detail, style = typography.bodySmall, color = detailColor,
                    modifier = Modifier.fillMaxWidth().testTag("home-hero-detail-${domain.key}"),
                    textAlign = TextAlign.Center)
                if (displayed.additionalDetail != null) Text(displayed.additionalDetail,
                    style = typography.bodySmall, color = muted,
                    modifier = Modifier.fillMaxWidth().testTag("home-hero-additional-detail-${domain.key}"),
                    textAlign = TextAlign.Center)
            }
        }
    }
}
