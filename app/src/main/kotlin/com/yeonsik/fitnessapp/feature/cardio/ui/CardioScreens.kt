package com.yeonsik.fitnessapp.feature.cardio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.BuildConfig
import com.yeonsik.fitnessapp.app.navigation.*
import com.yeonsik.fitnessapp.cardio.*
import com.yeonsik.fitnessapp.config.*
import com.yeonsik.fitnessapp.core.account.*
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.cardio.model.*
import com.yeonsik.fitnessapp.feature.cardio.ui.*
import com.yeonsik.fitnessapp.feature.development.ui.*
import com.yeonsik.fitnessapp.feature.exercise.ui.*
import com.yeonsik.fitnessapp.feature.home.ui.*
import com.yeonsik.fitnessapp.feature.meal.ui.*
import com.yeonsik.fitnessapp.feature.routine.ui.*
import com.yeonsik.fitnessapp.feature.supplement.ui.*
import com.yeonsik.fitnessapp.feature.workout.model.*
import com.yeonsik.fitnessapp.feature.workout.ui.*
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.*
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
internal fun CardioStartScreen(host: ScreenHost) {
    AppHeader("유산소")
    com.yeonsik.fitnessapp.cardio.CardioActivityType.values().forEach { type ->
        AppOutlinedButton(onClick = { host.startCardioWorkout(type) }, Modifier.fillMaxWidth()) {
            Text(type.labelKo())
        }
    }
}

@Composable
internal fun CardioSessionScreen(host: ScreenHost, ownerId: String) {
    val state by host.cardioSessionViewModel().uiState.observeAsState(CardioSessionUiState.Idle)
    val ready = state as? CardioSessionUiState.Ready
    LaunchedEffect(ownerId, ready?.session?.recordId, ready?.session?.status) {
        while (ready?.session?.status != CardioSessionSnapshot.STATUS_COMPLETED) {
            delay(1_000)
            host.refreshCardioSession()
        }
    }
    AppHeader(ready?.session?.activityLabel ?: "유산소 진행", back = { host.back() })
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
        AppButton(onClick = { host.pauseCardioWorkout() }, Modifier.fillMaxWidth()) { Text("일시정지") }
    } else if (session.status == CardioSessionSnapshot.STATUS_PAUSED) {
        AppButton(onClick = { host.resumeCardioWorkout() }, Modifier.fillMaxWidth()) { Text("계속") }
    }
    AppOutlinedButton(onClick = { host.editCardioAverageHeartRate() }, Modifier.fillMaxWidth()) { Text("평균 심박수") }
    AppButton(onClick = { host.finishCardioWorkout() }, Modifier.fillMaxWidth()) { Text("완료") }
    AppOutlinedButton(onClick = { host.cancelCardioWorkout() }, Modifier.fillMaxWidth()) { Text("취소") }
}

@Composable
internal fun CardioSummaryScreen(host: ScreenHost, ownerId: String) {
    val state by host.cardioSessionViewModel().uiState.observeAsState(CardioSessionUiState.Idle)
    val ready = state as? CardioSessionUiState.Ready
    AppHeader("유산소 요약", back = { host.back() })
    if (ready == null || ready.ownerId != ownerId) {
        Text("요약을 불러오는 중입니다.")
        return
    }
    val session = ready.session
    var route by remember(session.recordId) { mutableStateOf<CardioRouteProjection?>(null) }
    var routeError by remember(session.recordId) { mutableStateOf<String?>(null) }
    LaunchedEffect(session.recordId) {
        host.loadCardioRoute(session.recordId, object : ScreenHost.CardioRouteCallback {
            override fun onComplete(projection: CardioRouteProjection) {
                route = projection
                routeError = null
            }

            override fun onError(error: Exception) {
                route = null
                routeError = error.message ?: "경로를 불러오지 못했습니다."
            }
        })
    }
    Text(session.activityLabel, fontWeight = FontWeight.Bold)
    FitnessFactRow(
        first = { FitnessFactCard("시간", FitnessUi.formatElapsed(session.elapsedSeconds(System.currentTimeMillis())), "완료 기록") },
        second = { FitnessFactCard("거리", String.format("%.2f km", session.distanceMeters / 1_000.0), "완료 기록") }
    )
    Text(session.averageHeartRateBpm?.let { String.format("평균 심박 %.0f bpm", it) } ?: "평균 심박 미기록")
    when {
        routeError != null -> Text(routeError!!)
        route == null -> Text("경로를 불러오는 중입니다.")
        !route!!.hasRenderablePath() -> Text("표시할 GPS 경로가 없습니다.")
        !BuildConfig.MAPS_API_KEY_CONFIGURED -> Text("지도 키가 설정되지 않아 경로 지도를 표시할 수 없습니다.")
        else -> CardioRouteMap(route!!)
    }
}

