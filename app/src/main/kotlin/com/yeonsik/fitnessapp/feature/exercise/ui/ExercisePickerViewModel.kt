package com.yeonsik.fitnessapp.feature.exercise.ui

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.exercise.BodyPart
import com.yeonsik.fitnessapp.exercise.ExerciseMasterAdapter
import com.yeonsik.fitnessapp.exercise.RuntimeExerciseCatalog
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePicker
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset
import com.yeonsik.fitnessapp.exercise.UiEquipmentCategory
import com.yeonsik.fitnessapp.feature.exercise.api.ExerciseMasterRepositoryApi
import com.yeonsik.fitnessapp.feature.routine.api.RoutineRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.state.FitnessScreen
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface ExercisePickerUiState {
    data object Idle : ExercisePickerUiState
    data object Loading : ExercisePickerUiState

    data class Ready(
        val ownerId: String,
        val mode: FitnessScreen,
        val selectionMode: ExercisePickerSelectionMode,
        val recordId: String?,
        val replacementId: String?,
        val routineId: String?,
        val query: String,
        val bodyPart: BodyPart?,
        val primarySubPart: String?,
        val equipmentCategory: UiEquipmentCategory?,
        val sortOrder: RuntimeExercisePicker.SortOrder,
        val families: List<RuntimeExercisePicker.FamilyResult>,
        val availablePrimarySubParts: List<ExercisePickerSubPartOption>,
        val selectedFamilyId: String?,
        val selectedPresetId: String?
    ) : ExercisePickerUiState {
        /** Compatibility projection for existing callers; family grouping remains canonical. */
        val presets: List<RuntimeExercisePreset>
            get() = families.flatMap { it.presets }
    }

    data class Saved(
        val ownerId: String,
        val mode: FitnessScreen,
        val selectionMode: ExercisePickerSelectionMode,
        val recordId: String?,
        val replacementId: String?,
        val routineId: String?
    ) : ExercisePickerUiState

    data class Error(
        val ownerId: String,
        val mode: FitnessScreen,
        val selectionMode: ExercisePickerSelectionMode,
        val message: String
    ) : ExercisePickerUiState
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

    private val lock = Any()
    private val requestGate = ExercisePickerRequestGate()
    private var currentKey: ExercisePickerRequestKey? = null
    private var currentToken = 0L
    private var currentScope: AccountScope? = null
    private var currentMode = FitnessScreen.ROUTINE_ADD
    private var currentSelectionMode = ExercisePickerSelectionMode.ROUTINE_ADD
    private var currentRecordId: String? = null
    private var currentReplacementId: String? = null
    private var currentRoutineId: String? = null

    private var query = ""
    private var bodyPart: BodyPart? = null
    private var primarySubPart: String? = null
    private var equipmentCategory: UiEquipmentCategory? = null
    private var sortOrder = RuntimeExercisePicker.SortOrder.RECENT
    private var selectedFamilyId: String? = null
    private var selectedPresetId: String? = null

    private var loaded = false
    private var picker: RuntimeExercisePicker? = null
    private var recentByPreset: Map<String, String> = emptyMap()
    private var availableSubParts: List<ExercisePickerSubPartOption> = emptyList()

    /**
     * Enters the existing route with an explicit selection contract. The default keeps callers
     * source-compatible while all production navigation passes the mode explicitly.
     */
    fun enter(
        scope: AccountScope,
        mode: FitnessScreen,
        recordId: String?,
        replacementId: String?,
        routineId: String?,
        selectionMode: ExercisePickerSelectionMode =
            ExercisePickerSelectionMode.forTarget(mode, replacementId)
    ) {
        val normalizedReplacementId = replacementId?.takeIf { it.isNotBlank() }
        val key = ExercisePickerRequestKey(
            ownerId = scope.ownerId,
            screen = mode,
            selectionMode = selectionMode,
            recordId = recordId,
            replacementId = normalizedReplacementId,
            routineId = routineId
        )
        val token = requestGate.begin(key)
        synchronized(lock) {
            currentKey = key
            currentToken = token
            currentScope = scope
            currentMode = mode
            currentSelectionMode = selectionMode
            currentRecordId = recordId
            currentReplacementId = normalizedReplacementId
            currentRoutineId = routineId
            query = savedStateHandle.get<String>(KEY_QUERY).orEmpty()
            bodyPart = savedStateHandle.get<String>(KEY_BODY_PART)?.let(BodyPart::fromId)
            primarySubPart = savedStateHandle.get<String>(KEY_PRIMARY_SUB_PART)
                ?.takeIf { it.isNotBlank() }
            equipmentCategory = savedStateHandle.get<String>(KEY_EQUIPMENT_CATEGORY)
                ?.let(::equipmentCategoryOrNull)
            sortOrder = savedStateHandle.get<String>(KEY_SORT_ORDER)
                ?.let(::sortOrderOrNull)
                ?: RuntimeExercisePicker.SortOrder.RECENT
            val restoreSelection = savedStateHandle.get<String>(KEY_SELECTION_TARGET) == key.toString()
            selectedFamilyId = if (restoreSelection) {
                savedStateHandle.get<String>(KEY_SELECTED_FAMILY_ID)
            } else {
                null
            }
            selectedPresetId = if (restoreSelection) {
                savedStateHandle.get<String>(KEY_SELECTED_PRESET_ID)
            } else {
                null
            }
            loaded = false
            picker = null
            recentByPreset = emptyMap()
            availableSubParts = emptyList()
        }
        savedStateHandle[KEY_MODE] = mode.name
        savedStateHandle[KEY_SELECTION_MODE] = selectionMode.name
        savedStateHandle[KEY_RECORD_ID] = recordId
        savedStateHandle[KEY_REPLACEMENT_ID] = normalizedReplacementId
        savedStateHandle[KEY_ROUTINE_ID] = routineId
        savedStateHandle[KEY_SELECTION_TARGET] = key.toString()
        mutableState.value = ExercisePickerUiState.Loading

        executor.execute {
            try {
                val catalog = masterRepository.runtimeCatalog()
                val recent = workoutRepository.lastPerformedAtByCanonicalPreset(scope).toMap()
                synchronized(lock) {
                    if (requestGate.accepts(token, key)) {
                        loaded = true
                        picker = RuntimeExercisePicker(catalog)
                        recentByPreset = recent
                        availableSubParts = primarySubPartOptions(catalog)
                    }
                }
                postReadyIfCurrent(token, key)
            } catch (error: Exception) {
                postErrorIfCurrent(
                    token,
                    key,
                    error.message ?: "운동 종목을 불러오지 못했습니다."
                )
            }
        }
    }

    fun search(query: String) {
        synchronized(lock) {
            this.query = query
            savedStateHandle[KEY_QUERY] = query
            clearSelectionLocked()
        }
        publishCurrentIfLoaded()
    }

    fun setBodyPart(bodyPart: BodyPart?) {
        synchronized(lock) {
            this.bodyPart = bodyPart
            savedStateHandle[KEY_BODY_PART] = bodyPart?.id()
            clearSelectionLocked()
        }
        publishCurrentIfLoaded()
    }

    fun setPrimarySubPart(primarySubPart: String?) {
        val normalized = primarySubPart?.trim()?.takeIf { it.isNotEmpty() }
        synchronized(lock) {
            this.primarySubPart = normalized
            savedStateHandle[KEY_PRIMARY_SUB_PART] = normalized
            clearSelectionLocked()
        }
        publishCurrentIfLoaded()
    }

    fun setEquipmentCategory(category: UiEquipmentCategory?) {
        synchronized(lock) {
            equipmentCategory = category
            savedStateHandle[KEY_EQUIPMENT_CATEGORY] = category?.id()
            clearSelectionLocked()
        }
        publishCurrentIfLoaded()
    }

    fun setSortOrder(order: RuntimeExercisePicker.SortOrder) {
        synchronized(lock) {
            sortOrder = order
            savedStateHandle[KEY_SORT_ORDER] = order.name
            clearSelectionLocked()
        }
        publishCurrentIfLoaded()
    }

    fun resetFilters() {
        synchronized(lock) {
            query = ""
            bodyPart = null
            primarySubPart = null
            equipmentCategory = null
            sortOrder = RuntimeExercisePicker.SortOrder.RECENT
            savedStateHandle[KEY_QUERY] = ""
            savedStateHandle[KEY_BODY_PART] = null
            savedStateHandle[KEY_PRIMARY_SUB_PART] = null
            savedStateHandle[KEY_EQUIPMENT_CATEGORY] = null
            savedStateHandle[KEY_SORT_ORDER] = sortOrder.name
            clearSelectionLocked()
        }
        publishCurrentIfLoaded()
    }

    fun selectFamily(familyId: String) {
        synchronized(lock) {
            val result = currentResultsLocked().firstOrNull {
                it.family.familyId == familyId
            } ?: return
            selectedFamilyId = if (selectedFamilyId == result.family.familyId) null else result.family.familyId
            selectedPresetId = null
            saveSelectionLocked()
        }
        publishCurrentIfLoaded()
    }

    fun selectPreset(familyId: String, presetId: String) {
        synchronized(lock) {
            val result = currentResultsLocked().firstOrNull {
                it.family.familyId == familyId
            } ?: return
            val preset = result.presets.firstOrNull { it.presetId == presetId } ?: return
            selectedFamilyId = result.family.familyId
            selectedPresetId = preset.presetId
            saveSelectionLocked()
        }
        publishCurrentIfLoaded()
    }

    /** Restored target used by the navigation root after a successful save. */
    fun activeRecordId(): String? = synchronized(lock) {
        currentRecordId ?: savedStateHandle.get<String>(KEY_RECORD_ID)
    }

    fun clearActiveRecordIfMatches(recordId: String?) {
        synchronized(lock) {
            if (recordId != null && activeRecordId() == recordId) {
                currentRecordId = null
                savedStateHandle[KEY_RECORD_ID] = null
            }
        }
    }

    /** Restored exercise being replaced, if this picker was opened in replacement mode. */
    fun activeReplacementId(): String? = synchronized(lock) {
        currentReplacementId ?: savedStateHandle.get<String>(KEY_REPLACEMENT_ID)
    }

    fun rememberReplacementExercise(exerciseId: String?) {
        val normalized = exerciseId?.takeIf { it.isNotBlank() }
        synchronized(lock) {
            currentReplacementId = normalized
            savedStateHandle[KEY_REPLACEMENT_ID] = normalized
        }
    }

    fun clearReplacementExercise() {
        rememberReplacementExercise(null)
    }

    fun choose(preset: RuntimeExercisePreset) {
        val choice = synchronized(lock) {
            if (!loaded || picker == null || currentKey == null || currentScope == null) {
                return
            }
            val result = currentResultsLocked().firstOrNull { result ->
                result.presets.any { candidate ->
                    candidate.presetId == preset.presetId ||
                        candidate.identityId() == preset.identityId()
                }
            } ?: return
            val canonicalPreset = result.presets.firstOrNull { it.presetId == preset.presetId }
                ?: result.presets.firstOrNull { it.identityId() == preset.identityId() }
                ?: return
            selectedFamilyId = result.family.familyId
            selectedPresetId = canonicalPreset.presetId
            saveSelectionLocked()
            PickerChoice(
                token = currentToken,
                key = currentKey!!,
                scope = currentScope!!,
                selectionMode = currentSelectionMode,
                recordId = currentRecordId,
                replacementId = currentReplacementId,
                routineId = currentRoutineId,
                preset = canonicalPreset
            )
        }
        publishCurrentIfLoaded()
        executor.execute {
            if (!requestGate.accepts(choice.token, choice.key)) return@execute
            try {
                val saved = when (choice.selectionMode) {
                    ExercisePickerSelectionMode.ROUTINE_ADD ->
                        routineRepository.addExercise(
                            choice.scope,
                            choice.routineId ?: routineRepository.activeRoutineId(choice.scope),
                            ExerciseMasterAdapter.toRoutineExerciseDraft(choice.preset)
                        )
                    ExercisePickerSelectionMode.WORKOUT_ADD ->
                        choice.recordId?.let {
                            workoutRepository.addExercise(
                                choice.scope,
                                it,
                                ExerciseMasterAdapter.toWorkoutExerciseReplacement(choice.preset)
                            )
                        } ?: false
                    ExercisePickerSelectionMode.WORKOUT_REPLACE ->
                        if (choice.recordId == null || choice.replacementId == null) {
                            false
                        } else {
                            workoutRepository.replaceExercise(
                                choice.scope,
                                choice.recordId,
                                choice.replacementId,
                                ExerciseMasterAdapter.toWorkoutExerciseReplacement(choice.preset)
                            )
                        }
                }
                if (saved) {
                    postSavedIfCurrent(choice)
                } else {
                    postErrorIfCurrent(choice, "운동 종목을 저장하지 못했습니다.")
                }
            } catch (error: Exception) {
                postErrorIfCurrent(
                    choice,
                    error.message ?: "운동 종목을 저장하지 못했습니다."
                )
            }
        }
    }

    private fun publishCurrentIfLoaded() {
        val next = synchronized(lock) {
            if (!loaded || picker == null || currentScope == null) null else readyStateLocked()
        }
        if (next != null) mutableState.value = next
    }

    private fun readyStateLocked(): ExercisePickerUiState.Ready {
        val results = currentResultsLocked()
        val validFamily = results.firstOrNull { it.family.familyId == selectedFamilyId }
        val validFamilyId = validFamily?.family?.familyId
        val validPresetId = selectedPresetId?.takeIf { presetId ->
            validFamily?.presets?.any { it.presetId == presetId } == true
        }
        if (validFamilyId == null) {
            selectedFamilyId = null
            selectedPresetId = null
        } else if (validPresetId == null) {
            selectedPresetId = null
        }
        saveSelectionLocked()
        return ExercisePickerUiState.Ready(
            ownerId = currentScope!!.ownerId,
            mode = currentMode,
            selectionMode = currentSelectionMode,
            recordId = currentRecordId,
            replacementId = currentReplacementId,
            routineId = currentRoutineId,
            query = query,
            bodyPart = bodyPart,
            primarySubPart = primarySubPart,
            equipmentCategory = equipmentCategory,
            sortOrder = sortOrder,
            families = results,
            availablePrimarySubParts = availableSubParts,
            selectedFamilyId = selectedFamilyId,
            selectedPresetId = selectedPresetId
        )
    }

    private fun currentResultsLocked(): List<RuntimeExercisePicker.FamilyResult> =
        picker?.search(
            RuntimeExercisePicker.Filter(
                query,
                bodyPart,
                primarySubPart,
                equipmentCategory,
                sortOrder,
                recentByPreset
            )
        ).orEmpty()

    private fun clearSelectionLocked() {
        selectedFamilyId = null
        selectedPresetId = null
        savedStateHandle[KEY_SELECTED_FAMILY_ID] = null
        savedStateHandle[KEY_SELECTED_PRESET_ID] = null
    }

    private fun saveSelectionLocked() {
        savedStateHandle[KEY_SELECTED_FAMILY_ID] = selectedFamilyId
        savedStateHandle[KEY_SELECTED_PRESET_ID] = selectedPresetId
    }

    private fun postReadyIfCurrent(token: Long, key: ExercisePickerRequestKey) {
        ArchTaskExecutor.getInstance().postToMainThread {
            if (!requestGate.accepts(token, key)) return@postToMainThread
            val next = synchronized(lock) {
                if (!loaded || currentKey != key || currentToken != token) null else readyStateLocked()
            }
            if (next != null) mutableState.value = next
        }
    }

    private fun postErrorIfCurrent(
        token: Long,
        key: ExercisePickerRequestKey,
        message: String
    ) {
        ArchTaskExecutor.getInstance().postToMainThread {
            if (!requestGate.accepts(token, key)) return@postToMainThread
            val next = synchronized(lock) {
                if (currentKey != key || currentToken != token || currentScope == null) {
                    null
                } else {
                    ExercisePickerUiState.Error(
                        currentScope!!.ownerId,
                        currentMode,
                        currentSelectionMode,
                        message
                    )
                }
            }
            if (next != null) mutableState.value = next
        }
    }

    private fun postSavedIfCurrent(choice: PickerChoice) {
        ArchTaskExecutor.getInstance().postToMainThread {
            if (!requestGate.accepts(choice.token, choice.key)) return@postToMainThread
            mutableState.value = ExercisePickerUiState.Saved(
                choice.scope.ownerId,
                choice.key.screen,
                choice.selectionMode,
                choice.recordId,
                choice.replacementId,
                choice.routineId
            )
        }
    }

    private fun postErrorIfCurrent(choice: PickerChoice, message: String) {
        ArchTaskExecutor.getInstance().postToMainThread {
            if (!requestGate.accepts(choice.token, choice.key)) return@postToMainThread
            mutableState.value = ExercisePickerUiState.Error(
                choice.scope.ownerId,
                choice.key.screen,
                choice.selectionMode,
                message
            )
        }
    }

    override fun onCleared() {
        executor.shutdownNow()
    }

    private data class PickerChoice(
        val token: Long,
        val key: ExercisePickerRequestKey,
        val scope: AccountScope,
        val selectionMode: ExercisePickerSelectionMode,
        val recordId: String?,
        val replacementId: String?,
        val routineId: String?,
        val preset: RuntimeExercisePreset
    )

    private companion object {
        const val KEY_MODE = "exercise_picker.mode"
        const val KEY_SELECTION_MODE = "exercise_picker.selection_mode"
        const val KEY_RECORD_ID = "exercise_picker.record_id"
        const val KEY_REPLACEMENT_ID = "exercise_picker.replacement_id"
        const val KEY_ROUTINE_ID = "exercise_picker.routine_id"
        const val KEY_QUERY = "exercise_picker.query"
        const val KEY_BODY_PART = "exercise_picker.body_part"
        const val KEY_PRIMARY_SUB_PART = "exercise_picker.primary_sub_part"
        const val KEY_EQUIPMENT_CATEGORY = "exercise_picker.equipment_category"
        const val KEY_SORT_ORDER = "exercise_picker.sort_order"
        const val KEY_SELECTION_TARGET = "exercise_picker.selection_target"
        const val KEY_SELECTED_FAMILY_ID = "exercise_picker.selected_family_id"
        const val KEY_SELECTED_PRESET_ID = "exercise_picker.selected_preset_id"

        fun equipmentCategoryOrNull(value: String): UiEquipmentCategory? =
            UiEquipmentCategory.values().firstOrNull { it.name == value }
                ?: UiEquipmentCategory.values().firstOrNull { it.id() == value }

        fun sortOrderOrNull(value: String): RuntimeExercisePicker.SortOrder? =
            RuntimeExercisePicker.SortOrder.values().firstOrNull { it.name == value }

        fun primarySubPartOptions(
            catalog: RuntimeExerciseCatalog
        ): List<ExercisePickerSubPartOption> = catalog.families
            .asSequence()
            .flatMap { it.presets.asSequence() }
            .mapNotNull { preset ->
                val id = preset.primarySubPart?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                ExercisePickerSubPartOption(
                    id = id,
                    label = preset.primarySubPartNameKo?.trim()?.takeIf { it.isNotEmpty() } ?: id
                )
            }
            .distinctBy { it.id }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }
}
