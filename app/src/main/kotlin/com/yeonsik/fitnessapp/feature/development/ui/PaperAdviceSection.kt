package com.yeonsik.fitnessapp.feature.development.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.yeonsik.fitnessapp.core.ui.AppCard
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.development.PaperAdvice
import com.yeonsik.fitnessapp.development.PaperAdviceAssessment
import java.util.Locale

@Composable
internal fun PaperAdviceSection(
    state: PaperAdviceUiState,
    ownerId: String
) {
    when (state) {
        PaperAdviceUiState.Idle -> Text("논문 기반 점검을 준비하는 중입니다.")
        is PaperAdviceUiState.Loading -> {
            if (state.ownerId == ownerId) {
                Text("논문 기반 점검을 불러오는 중입니다.")
            }
        }
        is PaperAdviceUiState.Error -> {
            if (state.ownerId == ownerId) {
                AppCard(Modifier.fillMaxWidth()) {
                    Text(state.message, Modifier.padding(AppSpacing.card))
                }
            }
        }
        is PaperAdviceUiState.Ready -> {
            if (state.ownerId == ownerId) {
                PaperAdviceAssessmentContent(state.assessment)
            }
        }
    }
}

@Composable
private fun PaperAdviceAssessmentContent(assessment: PaperAdviceAssessment) {
    val input = assessment.input
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AppSpacing.card),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Text("Readiness · 검토 후보", fontWeight = FontWeight.Bold)
            Text("기준일 " + input.referenceDate + " · 최근 관찰 " + input.recentDataDays + "일")
            Text("기록된 source가 없는 입력은 추정하지 않고 미기록으로 남깁니다.")
        }
    }

    if (assessment.advice.isEmpty()) {
        Text("현재 입력에서 표시할 검토 후보가 없습니다.")
    }
    assessment.advice.forEach { advice ->
        PaperAdviceCard(advice)
    }
}

@Composable
private fun PaperAdviceCard(advice: PaperAdvice) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AppSpacing.card),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Text(advice.titleKo, fontWeight = FontWeight.Bold)
            Text(
                "상태 · " + statusLabel(advice.status) +
                    " · 분류 " + advice.category +
                    " · ID " + advice.adviceId
            )
            Text("관찰된 기록", fontWeight = FontWeight.Bold)
            Text(advice.observationKo)
            Text("근거 참조", fontWeight = FontWeight.Bold)
            if (advice.evidenceRefs.isEmpty()) {
                Text("연결된 source ref가 없습니다.")
            } else {
                Text(advice.evidenceRefs.joinToString(", "))
            }
            Text("제한사항", fontWeight = FontWeight.Bold)
            Text(advice.limitationKo)
            Text("질적 신뢰도 · " + confidenceLabel(advice.confidence))
            Text("검토 후보 · 다음 행동", fontWeight = FontWeight.Bold)
            Text(advice.recommendationKo)
        }
    }
}

private fun statusLabel(status: PaperAdvice.Status): String = when (status) {
    PaperAdvice.Status.ACTIONABLE -> "행동 검토 가능"
    PaperAdvice.Status.INFORMATIONAL -> "정보 제공"
    PaperAdvice.Status.INSUFFICIENT_DATA -> "데이터 부족"
    PaperAdvice.Status.SAFETY_REVIEW -> "안전 검토 우선"
}

private fun confidenceLabel(confidence: String): String = when (
    confidence.trim().lowercase(Locale.ROOT)
) {
    "high" -> "높음"
    "moderate" -> "중간"
    "low" -> "낮음"
    else -> "알 수 없음"
}
