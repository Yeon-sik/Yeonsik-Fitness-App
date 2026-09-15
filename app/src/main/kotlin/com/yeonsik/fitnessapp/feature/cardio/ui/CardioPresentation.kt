package com.yeonsik.fitnessapp.feature.cardio.ui

import com.yeonsik.fitnessapp.cardio.CardioMetrics
import com.yeonsik.fitnessapp.core.ui.FitnessSemanticStatus
import com.yeonsik.fitnessapp.core.ui.FitnessStatusPresentation
import com.yeonsik.fitnessapp.feature.cardio.data.CardioRepository
import java.util.Locale

/** Presentation mapping for the persisted GPS quality value. */
internal fun cardioGpsStatusPresentation(gpsStatus: String): FitnessStatusPresentation {
    val normalized = gpsStatus.trim().lowercase(Locale.ROOT)
    return when (normalized) {
        CardioRepository.GPS_READY -> FitnessStatusPresentation(
            FitnessSemanticStatus.SUCCESS,
            "GPS 연결됨"
        )
        CardioRepository.GPS_WEAK,
        CardioRepository.GPS_PERMISSION_MISSING -> FitnessStatusPresentation(
            FitnessSemanticStatus.WARNING,
            if (normalized == CardioRepository.GPS_WEAK) "GPS 신호 약함" else "정확한 위치 권한 필요"
        )
        CardioRepository.GPS_SEARCHING,
        CardioRepository.GPS_STOPPED -> FitnessStatusPresentation(
            FitnessSemanticStatus.INFO,
            if (normalized == CardioRepository.GPS_SEARCHING) "GPS 찾는 중" else "GPS 일시정지"
        )
        CardioRepository.GPS_UNAVAILABLE -> FitnessStatusPresentation(
            FitnessSemanticStatus.UNKNOWN,
            "위치 신호 없음"
        )
        else -> FitnessStatusPresentation(
            FitnessSemanticStatus.UNKNOWN,
            if (normalized.isBlank()) "GPS 상태 미상" else "GPS 상태 알 수 없음"
        )
    }
}

/** Presentation mapping for the lifecycle state; domain transitions stay in the owner. */
internal fun cardioSessionStatusPresentation(status: String): FitnessStatusPresentation {
    return when (status.trim().lowercase(Locale.ROOT)) {
        CardioRepository.STATUS_TRACKING -> FitnessStatusPresentation(
            FitnessSemanticStatus.INFO,
            "기록 중"
        )
        CardioRepository.STATUS_PAUSED -> FitnessStatusPresentation(
            FitnessSemanticStatus.WARNING,
            "일시정지"
        )
        CardioRepository.STATUS_COMPLETED -> FitnessStatusPresentation(
            FitnessSemanticStatus.SUCCESS,
            "완료"
        )
        else -> FitnessStatusPresentation(
            FitnessSemanticStatus.UNKNOWN,
            "상태 미상"
        )
    }
}

/** Explains measurement limits without inferring unavailable metrics. */
internal fun cardioMeasurementExplanation(
    elapsedSeconds: Int,
    distanceMeters: Double,
    acceptedPointCount: Int
): String {
    val safeElapsedSeconds = elapsedSeconds.coerceAtLeast(0)
    val safeDistanceMeters = distanceMeters.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    val safePointCount = acceptedPointCount.coerceAtLeast(0)
    if (safePointCount == 0) {
        return "수락된 GPS 지점이 없어 거리·페이스·속도를 계산할 수 없습니다."
    }
    if (safeElapsedSeconds == 0) {
        return "수락된 GPS 지점 ${safePointCount}개가 있지만 활성 기록 시간이 없어 평균 페이스와 속도를 계산할 수 없습니다."
    }
    if (safeDistanceMeters < 20.0) {
        return "수락된 GPS 지점 ${safePointCount}개를 기준으로 기록 중입니다. 20m 이상 이동하면 평균 페이스와 속도를 표시합니다."
    }
    return "평균 페이스와 속도는 수락된 GPS 지점 ${safePointCount}개와 활성 기록 시간만으로 계산합니다."
}

internal fun cardioPaceDisplay(elapsedSeconds: Int, distanceMeters: Double): String {
    val safeDistanceMeters = distanceMeters.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    val formatted = CardioMetrics.formatAveragePace(elapsedSeconds.coerceAtLeast(0), safeDistanceMeters)
    return if (formatted == "--:--") "미측정" else "$formatted /km"
}

internal fun cardioSpeedDisplay(elapsedSeconds: Int, distanceMeters: Double): String {
    val safeDistanceMeters = distanceMeters.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    val formatted = CardioMetrics.formatAverageSpeed(elapsedSeconds.coerceAtLeast(0), safeDistanceMeters)
    return if (formatted == "--") "미측정" else "$formatted km/h"
}
