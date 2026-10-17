package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.zIndex
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot

internal fun isWorkoutFlowScreen(screen: FitnessScreen): Boolean = when (screen) {
    FitnessScreen.WORKOUT_SESSION, FitnessScreen.WORKOUT_EXERCISE_DETAIL,
    FitnessScreen.WORKOUT_EXERCISE_ADD, FitnessScreen.WORKOUT_SUMMARY -> true
    else -> false
}

internal val LocalWorkoutPageActive = androidx.compose.runtime.compositionLocalOf { true }
internal val LocalWorkoutImagePlayback = androidx.compose.runtime.compositionLocalOf { true }
// Session refreshes change elapsed time and totals. Invalidate preview readers only,
// not every retained editor beneath the provider on each return to the session.
internal val LocalWorkoutSessionPreview = compositionLocalOf<WorkoutSessionSnapshot?> { null }

/** Animate route identity only: saving a set must never replace or animate its editor. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun WorkoutFlowHost(
    ownerId: String,
    screen: FitnessScreen,
    recordId: String?,
    state: WorkoutSessionUiState,
    modifier: Modifier = Modifier,
    content: @Composable (FitnessScreen, Boolean) -> Unit
) {
    key(ownerId, recordId) {
        var retained by remember(recordId) { mutableStateOf<WorkoutSessionSnapshot?>(null) }
        val ready = (state as? WorkoutSessionUiState.Ready)?.takeIf {
            it.ownerId == ownerId && it.session.recordId == recordId
        }?.session
        SideEffect { if (ready != null) retained = ready }
        val preview = when (state) {
            is WorkoutSessionUiState.Ready -> ready
            WorkoutSessionUiState.Loading -> retained
            is WorkoutSessionUiState.Completing -> retained?.takeIf {
                state.ownerId == ownerId && state.recordId == it.recordId
            }
            is WorkoutSessionUiState.Completed -> retained?.takeIf {
                state.ownerId == ownerId && state.recordId == it.recordId
            }
            else -> null
        }
        val transition = updateTransition(screen, label = "workout-flow")
        var retainedScreens by remember { mutableStateOf(emptySet<FitnessScreen>()) }
        val keepPair = screen == FitnessScreen.WORKOUT_SESSION || screen == FitnessScreen.WORKOUT_EXERCISE_DETAIL
        SideEffect {
            retainedScreens = if (keepPair) retainedScreens + screen else emptySet()
        }
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(screen) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
        // Keep at most the session and current set editor mounted during repeated
        // round trips. A different record/owner or leaving this pair releases them.
        val destinations = retainedScreens + transition.currentState + screen
        Box(modifier.fillMaxSize()) {
            destinations.forEach { destination ->
                key(destination) {
                    val alpha = transition.animateFloat(transitionSpec = { tween(160) }, label = "workout-page-alpha") {
                        if (it == destination) 1f else 0f
                    }
                    val active = destination == screen
                    CompositionLocalProvider(
                        LocalWorkoutSessionPreview provides preview,
                        LocalWorkoutPageActive provides active,
                        LocalWorkoutImagePlayback provides (active && !transition.isRunning &&
                            transition.currentState == transition.targetState)
                    ) {
                        Box(Modifier.fillMaxSize().zIndex(if (active) 1f else 0f)
                            .focusProperties { enter = { if (active) FocusRequester.Default else FocusRequester.Cancel } }
                            .focusGroup()
                            .graphicsLayer { this.alpha = alpha.value }
                            .then(if (active) Modifier else Modifier
                                .clearAndSetSemantics { }
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) awaitPointerEvent(PointerEventPass.Initial)
                                            .changes.forEach { it.consume() }
                                    }
                                })) {
                            content(destination, active)
                        }
                    }
                }
            }
        }
    }
}
