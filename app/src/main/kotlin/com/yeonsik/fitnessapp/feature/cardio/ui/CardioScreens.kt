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
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot
import com.yeonsik.fitnessapp.ui.FitnessUi
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
        while (ready?.session?.status != CardioSessionSnapshot.STATUS_COMPLETED) {
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
    FitnessFactRow(
        first = { FitnessFactCard("시간", FitnessUi.formatElapsed(session.elapsedSeconds(System.currentTimeMillis())), "진행 시간") },
        second = { FitnessFactCard("거리", String.format("%.2f km", session.distanceMeters / 1_000.0), session.activityLabel) }
    )
    Text("GPS · ${session.gpsStatus} · ${session.acceptedPointCount} points")
    if (session.status == CardioSessionSnapshot.STATUS_TRACKING) {
        AppButton(onClick = actions::pause, Modifier.fillMaxWidth()) { Text("일시정지") }
    } else if (session.status == CardioSessionSnapshot.STATUS_PAUSED) {
        AppButton(onClick = actions::resume, Modifier.fillMaxWidth()) { Text("계속") }
    }
    AppOutlinedButton(onClick = actions::editAverageHeartRate, Modifier.fillMaxWidth()) { Text("평균 심박수") }
    AppButton(onClick = actions::finish, Modifier.fillMaxWidth()) { Text("완료") }
    AppOutlinedButton(onClick = actions::cancel, Modifier.fillMaxWidth()) { Text("취소") }
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
    Text(session.activityLabel, fontWeight = FontWeight.Bold)
    FitnessFactRow(
        first = { FitnessFactCard("시간", FitnessUi.formatElapsed(session.elapsedSeconds(System.currentTimeMillis())), "완료 기록") },
        second = { FitnessFactCard("거리", String.format("%.2f km", session.distanceMeters / 1_000.0), "완료 기록") }
    )
    Text(session.averageHeartRateBpm?.let { String.format("평균 심박 %.0f bpm", it) } ?: "평균 심박 미기록")
    val route = (routeState as? CardioRouteUiState.Ready)
        ?.takeIf { it.ownerId == ownerId && it.recordId == session.recordId }
        ?.projection
    val routeError = (routeState as? CardioRouteUiState.Error)
        ?.takeIf { it.ownerId == ownerId && it.recordId == session.recordId }
        ?.message
    when {
        routeError != null -> Text(routeError)
        route == null -> Text("경로를 불러오는 중입니다.")
        !route.hasRenderablePath() -> Text("표시할 GPS 경로가 없습니다.")
        !BuildConfig.MAPS_API_KEY_CONFIGURED -> Text("지도 키가 설정되지 않아 경로 지도를 표시할 수 없습니다.")
        else -> CardioRouteMap(route)
    }
}

