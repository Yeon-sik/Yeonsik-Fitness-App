package com.yeonsik.fitnessapp.feature.workout.ui

import android.animation.ValueAnimator
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitnessapp.state.FitnessScreen

internal fun isWorkoutFlowScreen(screen: FitnessScreen): Boolean = when (screen) {
    FitnessScreen.ROUTINE_DETAIL, FitnessScreen.WORKOUT_SESSION, FitnessScreen.WORKOUT_EXERCISE_DETAIL,
    FitnessScreen.WORKOUT_EXERCISE_ADD, FitnessScreen.WORKOUT_SUMMARY -> true
    else -> false
}

internal val LocalWorkoutPageActive = compositionLocalOf { true }
internal val LocalWorkoutImagePlayback = compositionLocalOf { true }
internal val LocalWorkoutSessionPreview = compositionLocalOf<WorkoutSessionSnapshot?> { null }
private val LocalWorkoutRoutineBridge = compositionLocalOf<WorkoutRoutineBridge?> { null }
@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalWorkoutSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
private val LocalWorkoutMotionEnabled = compositionLocalOf { false }
private val LocalWorkoutMotionMillis = compositionLocalOf { 260 }
private data class WorkoutVisualAnchor(val role: String, val identity: Any)
private data class WorkoutFlowPage(val screen: FitnessScreen, val identity: String?)

internal fun workoutSessionImageAnchor(recordId: String, exerciseId: String): Any =
    WorkoutSessionExerciseAnchor(recordId, exerciseId)

@Composable
internal fun workoutSessionListAnchor(recordId: String, exerciseId: String): Any =
    LocalWorkoutRoutineBridge.current?.takeIf { it.recordId == recordId }?.sessionAnchors?.get(exerciseId)
        ?: workoutSessionImageAnchor(recordId, exerciseId)

@Composable
internal fun workoutSessionTitleAnchor(recordId: String): Any =
    LocalWorkoutRoutineBridge.current?.takeIf { it.recordId == recordId }?.let { WorkoutRoutineTitleAnchor(it.routineId) }
        ?: WorkoutSessionTitleAnchor(recordId)

/** Caller-managed visibility keeps the real editor and its drafts mounted during round trips. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.workoutSharedElement(identity: Any?, role: String = "image"): Modifier {
    val scope = LocalWorkoutSharedScope.current
    if (scope == null || identity == null || !LocalWorkoutMotionEnabled.current) return this
    val visible = LocalWorkoutPageActive.current
    val duration = LocalWorkoutMotionMillis.current
    return with(scope) {
        this@workoutSharedElement.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(WorkoutVisualAnchor(role, identity)),
            visible = visible,
            boundsTransform = { _, _ -> tween(duration, easing = FastOutSlowInEasing) }
        )
    }
}

/** Route identity drives motion; saves and timer updates never replace the editor. */
@OptIn(ExperimentalSharedTransitionApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun WorkoutFlowHost(
    ownerId: String,
    screen: FitnessScreen,
    recordId: String?,
    state: WorkoutSessionUiState,
    modifier: Modifier = Modifier,
    routineId: String? = null,
    routineExercises: List<RoutineExerciseInstance> = emptyList(),
    createdRoutineRecordId: String? = null,
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
    content: @Composable (FitnessScreen, Boolean) -> Unit
) {
    // Record identity is a page key, not a host key: a newly created record must
    // share a layout with the routine that was visible before it existed.
    key(ownerId) {
        var retained by remember(recordId) { mutableStateOf<WorkoutSessionSnapshot?>(null) }
        val ready = (state as? WorkoutSessionUiState.Ready)?.takeIf {
            it.ownerId == ownerId && it.session.recordId == recordId
        }?.session
        SideEffect { if (ready != null) retained = ready }
        val preview = when (state) {
            is WorkoutSessionUiState.Ready -> ready
            WorkoutSessionUiState.Loading -> retained
            is WorkoutSessionUiState.Completing -> retained?.takeIf { state.ownerId == ownerId && state.recordId == it.recordId }
            is WorkoutSessionUiState.Completed -> retained?.takeIf { state.ownerId == ownerId && state.recordId == it.recordId }
            else -> null
        }
        val page = WorkoutFlowPage(screen, if (screen == FitnessScreen.ROUTINE_DETAIL) routineId else recordId)
        val transition = updateTransition(page, label = "workout-flow")
        var retainedPages by remember(recordId, routineId) { mutableStateOf(emptySet<WorkoutFlowPage>()) }
        val keepPages = screen == FitnessScreen.ROUTINE_DETAIL || screen == FitnessScreen.WORKOUT_SESSION ||
            screen == FitnessScreen.WORKOUT_EXERCISE_DETAIL
        SideEffect { retainedPages = if (keepPages) retainedPages + page else emptySet() }
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(page) { focusManager.clearFocus(force = true); keyboard?.hide() }
        val destinations = (retainedPages + transition.currentState + page).filter {
            it.identity == if (it.screen == FitnessScreen.ROUTINE_DETAIL) routineId else recordId
        }
        val routineTransition = transition.currentState.screen == FitnessScreen.ROUTINE_DETAIL ||
            transition.targetState.screen == FitnessScreen.ROUTINE_DETAIL
        val bridge = if (routineTransition) workoutRoutineBridge(routineId, routineExercises, createdRoutineRecordId, preview) else null
        val duration = if (!motionEnabled) 0 else if (routineTransition) 240 else
            if (screen == FitnessScreen.WORKOUT_SESSION) 220 else 260
        SharedTransitionLayout(modifier.fillMaxSize()) {
            val sharedScope = this
            destinations.forEach { destination ->
                key(destination) {
                    val alpha = transition.animateFloat(transitionSpec = { tween(duration) }, label = "workout-page-alpha") {
                        if (it == destination) 1f else 0f
                    }
                    val active = destination == page
                    CompositionLocalProvider(
                        LocalWorkoutSharedScope provides sharedScope,
                        LocalWorkoutMotionEnabled provides motionEnabled,
                        LocalWorkoutMotionMillis provides duration,
                        LocalWorkoutRoutineBridge provides bridge,
                        LocalWorkoutSessionPreview provides preview,
                        LocalWorkoutPageActive provides active,
                        LocalWorkoutImagePlayback provides (motionEnabled && active && !transition.isRunning &&
                            !sharedScope.isTransitionActive && transition.currentState == transition.targetState)
                    ) {
                        Box(Modifier.fillMaxSize().zIndex(if (active) 1f else 0f)
                            .focusProperties { enter = { if (active) FocusRequester.Default else FocusRequester.Cancel } }
                            .focusGroup()
                            .graphicsLayer { this.alpha = alpha.value }
                            .then(if (active) Modifier else Modifier.clearAndSetSemantics { }.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            })) { content(destination.screen, active) }
                    }
                }
            }
        }
    }
}
