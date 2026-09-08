package com.yeonsik.fitnessapp.feature.exercise.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.exercise.ExerciseMasterAdapter
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset
import com.yeonsik.fitnessapp.feature.exercise.api.ExerciseMasterRepositoryApi
import com.yeonsik.fitnessapp.feature.routine.api.RoutineRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.state.FitnessScreen
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface ExercisePickerUiState {
    data object Idle : ExercisePickerUiState
    data object Loading : ExercisePickerUiState
    data class Ready(
        val ownerId: String,
        val mode: FitnessScreen,
        val query: String,
        val presets: List<RuntimeExercisePreset>
    ) : ExercisePickerUiState
    data class Saved(val ownerId: String, val mode: FitnessScreen) : ExercisePickerUiState
    data class Error(val ownerId: String, val message: String) : ExercisePickerUiState
}

class ExercisePickerViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val masterRepository: ExerciseMasterRepositoryApi,
    private val routineRepository: RoutineRepositoryApi,
    private val workoutRepository: WorkoutRepositoryApi,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<ExercisePickerUiState>(ExercisePickerUiState.Idle)
    val uiState: LiveData<ExercisePickerUiState> = mutableState
    private var scope: AccountScope? = null
    private var mode: FitnessScreen = FitnessScreen.ROUTINE_ADD
    private var recordId: String? = null
    private var replacementId: String? = null
    private var routineId: String? = null
    private var allPresets: List<RuntimeExercisePreset> = emptyList()

    fun enter(
        scope: AccountScope,
        mode: FitnessScreen,
        recordId: String?,
        replacementId: String?,
        routineId: String?
    ) {
        this.scope = scope
        this.mode = mode
        this.recordId = recordId
        this.replacementId = replacementId
        this.routineId = routineId
        savedStateHandle[KEY_MODE] = mode.name
        savedStateHandle[KEY_RECORD_ID] = recordId
        savedStateHandle[KEY_REPLACEMENT_ID] = replacementId
        savedStateHandle[KEY_ROUTINE_ID] = routineId
        mutableState.value = ExercisePickerUiState.Loading
        executor.execute {
            allPresets = masterRepository.runtimeCatalog().presetsById.values
                .distinctBy { it.identityId() }
                .sortedBy { it.displayName() }
            publish(savedStateHandle[KEY_QUERY] ?: "")
        }
    }

    fun search(query: String) {
        savedStateHandle[KEY_QUERY] = query
        publish(query)
    }

    fun choose(preset: RuntimeExercisePreset) {
        val capturedScope = scope ?: return
        val capturedMode = mode
        val capturedRecord = recordId
        val capturedReplacement = replacementId
        val capturedRoutine = routineId
        executor.execute {
            try {
                val saved = when (capturedMode) {
                        FitnessScreen.ROUTINE_ADD ->
                        routineRepository.addExercise(
                            capturedScope,
                            capturedRoutine ?: routineRepository.activeRoutineId(capturedScope),
                            ExerciseMasterAdapter.toRoutineExerciseDraft(preset)
                        )
                    FitnessScreen.WORKOUT_EXERCISE_ADD -> {
                        if (capturedRecord == null) false
                        else if (capturedReplacement == null) workoutRepository.addExercise(
                            capturedScope,
                            capturedRecord,
                            ExerciseMasterAdapter.toWorkoutExerciseReplacement(preset)
                        ) else workoutRepository.replaceExercise(
                            capturedScope,
                            capturedRecord,
                            capturedReplacement,
                            ExerciseMasterAdapter.toWorkoutExerciseReplacement(preset)
                        )
                    }
                    else -> false
                }
                mutableState.postValue(if (saved) {
                    ExercisePickerUiState.Saved(capturedScope.ownerId, capturedMode)
                } else {
                    ExercisePickerUiState.Error(capturedScope.ownerId, "운동 종목을 저장하지 못했습니다.")
                })
            } catch (error: Exception) {
                mutableState.postValue(ExercisePickerUiState.Error(
                    capturedScope.ownerId,
                    error.message ?: "운동 종목을 저장하지 못했습니다."
                ))
            }
        }
    }

    private fun publish(query: String) {
        val capturedScope = scope ?: return
        val normalized = query.trim().lowercase(Locale.ROOT)
        val filtered = if (normalized.isEmpty()) allPresets else allPresets.filter { preset ->
            listOf(preset.displayName(), preset.nameEn, preset.familyNameKo, preset.equipmentNameKo)
                .any { it?.lowercase(Locale.ROOT)?.contains(normalized) == true }
        }
        mutableState.postValue(ExercisePickerUiState.Ready(
            capturedScope.ownerId, mode, query, filtered
        ))
    }

    override fun onCleared() { executor.shutdownNow() }

    private companion object {
        const val KEY_MODE = "exercise_picker.mode"
        const val KEY_RECORD_ID = "exercise_picker.record_id"
        const val KEY_REPLACEMENT_ID = "exercise_picker.replacement_id"
        const val KEY_ROUTINE_ID = "exercise_picker.routine_id"
        const val KEY_QUERY = "exercise_picker.query"
    }
}
