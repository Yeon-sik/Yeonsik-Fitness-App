package com.yeonsik.fitnessapp.feature.exercise.ui

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
internal fun ExercisePickerScreen(host: ScreenHost, ownerId: String, screen: FitnessScreen) {
    val state by host.exercisePickerViewModel().uiState.observeAsState(ExercisePickerUiState.Idle)
    val ready = state as? ExercisePickerUiState.Ready
    AppHeader(if (screen == FitnessScreen.ROUTINE_ADD) "루틴 종목 추가" else "운동 종목 선택",
        back = { host.back() })
    if (ready == null || ready.ownerId != ownerId || ready.mode != screen) {
        Text("운동 종목을 불러오는 중입니다.")
        return
    }
    AppTextField(ready.query, { host.exercisePickerViewModel().search(it) },
        label = { Text("종목 검색") }, modifier = Modifier.fillMaxWidth())
    ready.presets.take(100).forEach { preset ->
        AppCard(Modifier.fillMaxWidth().clickable { host.exercisePickerViewModel().choose(preset) }) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text(preset.displayName(), fontWeight = FontWeight.Bold)
                Text(listOfNotNull(preset.familyNameKo, preset.equipmentNameKo).joinToString(" · "))
            }
        }
    }
}

