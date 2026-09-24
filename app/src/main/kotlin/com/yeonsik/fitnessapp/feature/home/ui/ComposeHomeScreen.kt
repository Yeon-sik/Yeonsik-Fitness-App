package com.yeonsik.fitnessapp.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessButton
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import com.yeonsik.fitnessapp.core.ui.FitnessHeader
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
        FitnessHeader("메인", today)
        FitnessSection("오늘 상태") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = FitnessShape.hero,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Column(
                    Modifier.padding(FitnessSpacing.hero),
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
        }
        FitnessSection("빠른 기록") {
            val fontScale = LocalDensity.current.fontScale
            val weightAction: @Composable (Modifier) -> Unit = { modifier ->
                QuickRecordCard("체중", "오늘 체중 기록", actions::showBodyMetric, modifier)
            }
            val mealAction: @Composable (Modifier) -> Unit = { modifier ->
                QuickRecordCard("식사", "오늘 식사 기록", {
                    actions.openMealManagement(today, FitnessScreen.HOME)
                }, modifier)
            }
            if (fontScale >= 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
                    weightAction(Modifier.fillMaxWidth())
                    mealAction(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap),
                    verticalAlignment = Alignment.Top
                ) {
                    weightAction(Modifier.weight(1f))
                    mealAction(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickRecordCard(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FitnessCard(modifier.heightIn(min = FitnessSpacing.homeActionMinHeight), onClick = onClick) {
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
}

@Composable
private fun LoadingHome() {
    Column(Modifier.fillMaxWidth().padding(vertical = FitnessSpacing.section)) {
        FitnessHeader("메인")
        Text(
            "오늘 상태를 불러오는 중입니다.",
            modifier = Modifier.padding(top = FitnessSpacing.small),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}