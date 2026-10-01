package com.yeonsik.fitnessapp.feature.home.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.yeonsik.fitnessapp.core.ui.FitnessButton
import com.yeonsik.fitnessapp.core.ui.FitnessRecordMarkerColors
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing

private const val HERO_STATUS_MOTION_MILLIS = 220

@Composable
internal fun HomeHeroContent(
    status: HomeTodayHeroStatus,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = LocalContentColor.current
    Column(
        modifier.padding(FitnessSpacing.hero),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
    ) {
        Text("오늘", style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("home-hero-title"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
            status.domains.forEach { domain ->
                HeroDomainStatus(domain, Modifier.weight(1f))
            }
        }
        Row(
            Modifier.fillMaxWidth().testTag("home-hero-progress").semantics {
                contentDescription = "오늘 ${status.completedDomainCount}/3 영역 기록"
                progressBarRangeInfo = ProgressBarRangeInfo(status.completedDomainCount / 3f, 0f..1f, 2)
            },
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            status.domains.forEach { domain ->
                val fill = animateColorAsState(
                    if (domain.recorded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    animationSpec = tween(HERO_STATUS_MOTION_MILLIS), label = "home-progress-${domain.key}"
                )
                Box(Modifier.weight(1f).height(4.dp).testTag("home-hero-segment-${domain.key}").drawBehind {
                    drawRoundRect(fill.value, cornerRadius = CornerRadius(2.dp.toPx()))
                })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
            status.domains.forEach { domain ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
                    val progress = animateFloatAsState(
                        if (domain.recorded) 1f else 0f,
                        animationSpec = tween(HERO_STATUS_MOTION_MILLIS), label = "home-marker-${domain.key}"
                    )
                    val marker = FitnessRecordMarkerColors.byKey.getValue(domain.key)
                    val outline = ink.copy(alpha = 0.55f)
                    Box(Modifier.size(10.dp).testTag("home-hero-marker-${domain.key}")
                        .semantics {
                            contentDescription = "${domain.label} 기록 표시"
                            stateDescription = if (domain.recorded) "기록 완료" else "미기록"
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
                    Text(domain.label, style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.8f))
                }
            }
        }
        if (status.showContinue) {
            FitnessButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(top = FitnessSpacing.small)) {
                Text("운동 이어가기")
            }
        }
    }
}

@Composable
private fun HeroDomainStatus(domain: HomeHeroDomainStatus, modifier: Modifier = Modifier) {
    val ink = LocalContentColor.current
    val typography = MaterialTheme.typography
    val valueHeight = with(LocalDensity.current) {
        typography.titleLarge.lineHeight.toDp() + typography.bodySmall.lineHeight.toDp()
    } + FitnessSpacing.micro
    Column(
        modifier.testTag("home-hero-domain-${domain.key}").semantics(mergeDescendants = true) {
            contentDescription = "${domain.label}, ${domain.accessibilityValue}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
    ) {
        Text(domain.label, style = typography.labelMedium, color = ink.copy(alpha = 0.8f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        AnimatedContent(
            targetState = domain.value to domain.detail,
            modifier = Modifier.fillMaxWidth().heightIn(min = valueHeight),
            transitionSpec = {
                (fadeIn(tween(HERO_STATUS_MOTION_MILLIS)) + slideInVertically { it / 12 }) togetherWith
                    (fadeOut(tween(180)) + slideOutVertically { -it / 12 })
            },
            label = "home-value-${domain.key}"
        ) { (value, detail) ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
                Text(value, style = typography.titleLarge, modifier = Modifier.testTag("home-hero-value-${domain.key}"), maxLines = 1, softWrap = false,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                if (detail != null) Text(detail, style = typography.bodySmall,
                    modifier = Modifier.testTag("home-hero-detail-${domain.key}"), maxLines = 1, softWrap = false,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}
