package com.yeonsik.fitnessapp.feature.cardio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.yeonsik.fitnessapp.BuildConfig
import com.yeonsik.fitnessapp.cardio.CardioActivityType
import com.yeonsik.fitnessapp.cardio.CardioMetrics
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitness.shared.feature.cardio.model.CardioSessionSnapshot
import kotlinx.coroutines.delay

@Composable
internal fun CardioStartScreen(actions: CardioScreenActions) {
    AppHeader("유산소")
    CardioActivityType.values().forEach { type ->
        AppOutlinedButton(onClick = { actions.start(type) }, Modifier.fillMaxWidth()) {
            Text(type.labelKo())
        }
    }
}

@Composable
internal fun CardioSessionScreen(
    state: CardioSessionUiState,
    ownerId: String,
    actions: CardioScreenActions
) {
    val ready = state as? CardioSessionUiState.Ready
    LaunchedEffect(ownerId, ready?.session?.recordId, ready?.session?.status) {
        while (ready?.session?.status == CardioSessionSnapshot.STATUS_TRACKING
            || ready?.session?.status == CardioSessionSnapshot.STATUS_PAUSED) {
            delay(1_000)
            actions.refresh()
        }
    }
    AppHeader(ready?.session?.activityLabel ?: "유산소 진행", back = actions::back)
    if (ready == null || ready.ownerId != ownerId) {
        Text("GPS 기록을 불러오는 중입니다.")
        return
    }
    val session = ready.session
    val elapsedSeconds = session.elapsedSeconds(System.currentTimeMillis())
    val sessionStatus = cardioSessionStatusPresentation(session.status)
    val gpsStatus = cardioGpsStatusPresentation(session.gpsStatus)
    FitnessStatusBadge(
        status = sessionStatus.status,
        label = sessionStatus.label,
        modifier = Modifier.fillMaxWidth()
    )
    FitnessFactRow(
        first = { FitnessFactCard("시간", CardioMetrics.formatElapsed(elapsedSeconds), "진행 시간") },
        second = {
            FitnessFactCard(
                "거리",
                "${CardioMetrics.formatDistanceKilometers(session.distanceMeters)} km",
                session.activityLabel
            )
        }
    )
    FitnessFactRow(
        first = { FitnessFactCard("평균 페이스", cardioPaceDisplay(elapsedSeconds, session.distanceMeters), "수락된 GPS 기준") },
        second = { FitnessFactCard("평균 속도", cardioSpeedDisplay(elapsedSeconds, session.distanceMeters), "수락된 GPS 기준") }
    )
    FitnessStatusBadge(
        status = gpsStatus.status,
        label = "${gpsStatus.label} · 수락된 지점 ${session.acceptedPointCount}개",
        modifier = Modifier.fillMaxWidth()
    )
    FitnessStatusMessage(
        status = if (session.acceptedPointCount > 0) FitnessSemanticStatus.INFO else FitnessSemanticStatus.UNKNOWN,
        title = "측정 안내",
        message = cardioMeasurementExplanation(elapsedSeconds, session.distanceMeters, session.acceptedPointCount)
    )
    if (session.status == CardioSessionSnapshot.STATUS_TRACKING) {
        FitnessButton(onClick = actions::pause, Modifier.fillMaxWidth()) { Text("일시정지") }
    } else if (session.status == CardioSessionSnapshot.STATUS_PAUSED) {
        FitnessButton(onClick = actions::resume, Modifier.fillMaxWidth()) { Text("계속") }
    }
    if (session.status != CardioSessionSnapshot.STATUS_COMPLETED) {
        FitnessOutlinedButton(onClick = actions::editAverageHeartRate, Modifier.fillMaxWidth()) {
            Text("평균 심박수")
        }
        FitnessButton(onClick = actions::finish, Modifier.fillMaxWidth()) { Text("완료") }
        FitnessOutlinedButton(
            onClick = actions::cancel,
            modifier = Modifier.fillMaxWidth(),
            destructive = true
        ) { Text("취소") }
    }
}

@Composable
internal fun CardioSummaryScreen(
    state: CardioSessionUiState,
    routeState: CardioRouteUiState,
    ownerId: String,
    actions: CardioScreenActions
) {
    val ready = state as? CardioSessionUiState.Ready
    AppHeader("유산소 요약", back = actions::back)
    if (ready == null || ready.ownerId != ownerId) {
        Text("요약을 불러오는 중입니다.")
        return
    }
    val session = ready.session
    LaunchedEffect(session.recordId) {
        actions.loadRoute(session.recordId)
    }
    val elapsedSeconds = session.elapsedSeconds(System.currentTimeMillis())
    val sessionStatus = cardioSessionStatusPresentation(session.status)
    val gpsStatus = cardioGpsStatusPresentation(session.gpsStatus)
    Text(session.activityLabel, fontWeight = FontWeight.Bold)
    FitnessStatusBadge(
        status = sessionStatus.status,
        label = "${sessionStatus.label} · ${gpsStatus.label}",
        modifier = Modifier.fillMaxWidth()
    )
    FitnessFactRow(
        first = { FitnessFactCard("시간", CardioMetrics.formatElapsed(elapsedSeconds), "완료 기록") },
        second = {
            FitnessFactCard(
                "거리",
                "${CardioMetrics.formatDistanceKilometers(session.distanceMeters)} km",
                "완료 기록"
            )
        }
    )
    FitnessFactRow(
        first = { FitnessFactCard("평균 페이스", cardioPaceDisplay(elapsedSeconds, session.distanceMeters), "수락된 GPS 기준") },
        second = { FitnessFactCard("평균 속도", cardioSpeedDisplay(elapsedSeconds, session.distanceMeters), "수락된 GPS 기준") }
    )
    if (CardioMetrics.hasAverageHeartRate(session.averageHeartRateBpm)) {
        FitnessStatusMessage(
            status = FitnessSemanticStatus.SUCCESS,
            title = "평균 심박수 ${CardioMetrics.formatAverageHeartRate(session.averageHeartRateBpm)} bpm",
            message = "기록에 저장된 평균 심박수입니다."
        )
    } else {
        FitnessStatusMessage(
            status = FitnessSemanticStatus.UNKNOWN,
            title = "평균 심박수 미기록",
            message = "심박수 원본이 없어 평균값을 표시하지 않습니다."
        )
    }
    FitnessStatusMessage(
        status = if (session.acceptedPointCount > 0) FitnessSemanticStatus.INFO else FitnessSemanticStatus.UNKNOWN,
        title = "측정 안내",
        message = cardioMeasurementExplanation(elapsedSeconds, session.distanceMeters, session.acceptedPointCount)
    )
    val route = (routeState as? CardioRouteUiState.Ready)
        ?.takeIf { it.ownerId == ownerId && it.recordId == session.recordId }
        ?.projection
    val routeError = (routeState as? CardioRouteUiState.Error)
        ?.takeIf { it.ownerId == ownerId && it.recordId == session.recordId }
        ?.message
    FitnessSection("GPS 경로") {
        when {
            routeError != null -> FitnessStatusMessage(
                status = FitnessSemanticStatus.ERROR,
                title = "경로를 불러오지 못했습니다",
                message = routeError
            )
            route == null -> FitnessStatusMessage(
                status = FitnessSemanticStatus.INFO,
                title = "경로 불러오는 중",
                message = "완료된 기록의 수락된 GPS 지점을 읽고 있습니다."
            )
            !route.hasRenderablePath() -> FitnessStatusMessage(
                status = FitnessSemanticStatus.UNKNOWN,
                title = "표시할 GPS 경로가 없습니다",
                message = "저장된 수락 GPS 지점이 부족합니다."
            )
            !BuildConfig.MAPS_API_KEY_CONFIGURED -> FitnessStatusMessage(
                status = FitnessSemanticStatus.INFO,
                title = "지도 키 미설정",
                message = "수락된 GPS 지점 ${route.displayPointCount()}개는 저장되어 있지만 지도 키가 없어 지도를 표시할 수 없습니다."
            )
            else -> {
                Text("수락된 GPS 지점 ${route.displayPointCount()}개")
                CardioRouteMap(route)
            }
        }
    }
}

