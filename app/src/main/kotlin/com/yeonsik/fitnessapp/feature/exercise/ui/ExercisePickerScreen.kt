package com.yeonsik.fitnessapp.feature.exercise.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import com.yeonsik.fitnessapp.core.ui.FitnessExerciseFamilyIllustration
import com.yeonsik.fitnessapp.core.ui.FitnessExerciseIllustration
import com.yeonsik.fitnessapp.core.ui.FitnessHeader
import com.yeonsik.fitnessapp.core.ui.FitnessOutlinedButton
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessStatusMessage
import com.yeonsik.fitnessapp.core.ui.FitnessTextField
import com.yeonsik.fitnessapp.core.ui.FitnessSemanticStatus
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.workout.model.ManualWorkoutExercise
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePicker
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset
import com.yeonsik.fitnessapp.exercise.UiEquipmentCategory
import com.yeonsik.fitnessapp.state.FitnessScreen

private const val PICKER_ROW_SCALE = 0.7f
private val PICKER_ROW_MIN_HEIGHT = 80.dp * PICKER_ROW_SCALE
private val PICKER_IMAGE_SIZE = 72.dp * PICKER_ROW_SCALE

interface ExercisePickerScreenActions {
    fun back()
    fun search(query: String)
    fun setBodyPart(bodyPart: BodyPart?)
    fun setPrimarySubPart(primarySubPart: String?)
    fun selectMuscleGroup(groupId: String)
    fun clearBodyPartSelection()
    fun setEquipmentCategory(category: UiEquipmentCategory?)
    fun setSortOrder(order: RuntimeExercisePicker.SortOrder)
    fun resetFilters()
    fun selectFamily(familyId: String)
    fun selectPreset(familyId: String, presetId: String)
    fun choose(preset: RuntimeExercisePreset)
    fun chooseManual(exercise: ManualWorkoutExercise)
}

@Composable
internal fun ExercisePickerScreen(
    state: ExercisePickerUiState,
    ownerId: String,
    screen: FitnessScreen,
    actions: ExercisePickerScreenActions
) {
    val title = pickerTitle(screen, (state as? ExercisePickerUiState.Ready)?.selectionMode)
    when (state) {
        is ExercisePickerUiState.Ready -> {
            if (state.ownerId != ownerId || state.mode != screen) {
                PickerStateChanged(title)
            } else {
                ExercisePickerReady(state, title, actions)
            }
        }
        is ExercisePickerUiState.Error -> {
            if (state.ownerId != ownerId || state.mode != screen) {
                PickerStateChanged(title)
            } else {
                FitnessHeader(title, back = actions::back)
                FitnessStatusMessage(
                    status = FitnessSemanticStatus.ERROR,
                    title = "운동 종목을 불러오지 못했습니다",
                    message = state.message
                )
            }
        }
        ExercisePickerUiState.Loading,
        ExercisePickerUiState.Idle,
        is ExercisePickerUiState.Saved -> {
            FitnessHeader(title, back = actions::back)
            FitnessStatusMessage(
                status = FitnessSemanticStatus.INFO,
                title = "운동 종목을 불러오는 중입니다",
                message = "운동 목록과 최근 사용 기록을 준비하고 있습니다."
            )
        }
    }
}

@Composable
private fun PickerStateChanged(title: String) {
    FitnessHeader(title)
    FitnessStatusMessage(
        status = FitnessSemanticStatus.UNKNOWN,
        title = "선택 상태가 변경되었습니다",
        message = "현재 계정과 대상에 맞는 운동 목록을 다시 불러오는 중입니다."
    )
}

@Composable
private fun ExercisePickerReady(
    state: ExercisePickerUiState.Ready,
    title: String,
    actions: ExercisePickerScreenActions
) {
    val listState = rememberLazyListState()
    var showManualEntry by rememberSaveable(state.ownerId, state.recordId, state.selectionMode) {
        mutableStateOf(false)
    }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Unsubmitted text is form state. Only an explicit search changes the catalog query/results.
    var queryDraft by rememberSaveable(
        state.ownerId, state.recordId, state.routineId, state.selectionMode, state.query
    ) { mutableStateOf(state.query) }
    val submitSearch = {
        focusManager.clearFocus()
        keyboard?.hide()
        actions.search(queryDraft)
    }
    LaunchedEffect(state.ownerId, state.routineId, state.selectionMode) {
        if (state.selectionMode == ExercisePickerSelectionMode.ROUTINE_ADD) {
            listState.scrollToItem(0)
            withFrameNanos { }
            searchFocusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val selectedFamily = state.selectedFamilyId?.let { familyId ->
        state.families.firstOrNull { result ->
            result.family.familyId == familyId && result.presets.size > 1
        }
    }
    Box(Modifier.fillMaxWidth().fillMaxHeight()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            contentPadding = PaddingValues(bottom = FitnessSpacing.card),
            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
        ) {
            item {
                FitnessHeader(title, back = actions::back)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FitnessTextField(
                        value = queryDraft,
                        onValueChange = { queryDraft = it },
                        label = { Text("종목 검색") },
                        modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch() })
                    )
                    FitnessOutlinedButton(onClick = submitSearch) { Text("검색") }
                }
            }
            item {
                ExercisePickerFilters(state, actions)
            }
            if (state.selectionMode == ExercisePickerSelectionMode.WORKOUT_ADD) {
                item {
                    FitnessOutlinedButton(onClick = { showManualEntry = true }, Modifier.fillMaxWidth()) {
                        Text("목록에 없는 운동 직접 추가")
                    }
                }
            }
            if (state.selectionMode == ExercisePickerSelectionMode.WORKOUT_LINK_MANUAL) {
                item {
                    Text("연결할 정식 운동을 선택하세요. 당시 운동명과 세트 기록은 보존됩니다.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.families.size}개 운동 그룹",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    FitnessOutlinedButton(onClick = {
                        queryDraft = ""
                        actions.resetFilters()
                    }) {
                        Text("필터 초기화")
                    }
                }
            }
            if (state.families.isEmpty()) {
                item {
                    FitnessStatusMessage(
                        status = FitnessSemanticStatus.UNKNOWN,
                        title = "검색 결과 없음",
                        message = "조건에 맞는 운동 종목이 없습니다. 검색어나 필터를 바꿔 보세요."
                    )
                }
            } else {
                items(
                    items = state.families,
                    key = { result -> result.family.familyId }
                ) { result ->
                    ExerciseFamilyPickerCard(result, state, actions, onSelectFamily = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        actions.selectFamily(result.family.familyId)
                    })
                }
            }
        }

        selectedFamily?.let { result ->
            ExercisePickerVariantSheet(result, state, actions)
        }
        if (showManualEntry) {
            ManualWorkoutExerciseDialog(queryDraft, onDismiss = { showManualEntry = false }) { exercise ->
                showManualEntry = false
                actions.chooseManual(exercise)
            }
        }
    }
}

@Composable
private fun ExercisePickerFilters(
    state: ExercisePickerUiState.Ready,
    actions: ExercisePickerScreenActions
) {
    Column(verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
        ExerciseMuscleMap(state, actions::selectMuscleGroup, actions::clearBodyPartSelection)
        FilterPopupRow(
            title = "부위",
            options = BodyPart.values().map { it.id() to it.labelKo() },
            selectedId = state.bodyPart?.id(),
            onSelect = { id -> actions.setBodyPart(id?.let(BodyPart::fromId)) }
        )
        FilterPopupRow(
            title = "주요 세부 부위",
            options = state.availablePrimarySubParts.map { it.id to it.label },
            selectedId = state.primarySubPart,
            onSelect = actions::setPrimarySubPart
        )
        FilterPopupRow(
            title = "기구",
            options = UiEquipmentCategory.values().map { it.id() to it.labelKo() },
            selectedId = state.equipmentCategory?.id(),
            onSelect = { id ->
                actions.setEquipmentCategory(
                    id?.let { value -> UiEquipmentCategory.values().firstOrNull { it.id() == value } }
                )
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("정렬", style = MaterialTheme.typography.labelLarge)
            FitnessOutlinedButton(
                onClick = { actions.setSortOrder(RuntimeExercisePicker.SortOrder.RECENT) },
                selected = state.sortOrder == RuntimeExercisePicker.SortOrder.RECENT
            ) { Text("최근 사용") }
            FitnessOutlinedButton(
                onClick = { actions.setSortOrder(RuntimeExercisePicker.SortOrder.NAME) },
                selected = state.sortOrder == RuntimeExercisePicker.SortOrder.NAME
            ) { Text("이름") }
        }
    }
}

@Composable
private fun FilterPopupRow(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    var isOpen by rememberSaveable { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second
        ?: if (selectedId == null) "전체" else "알 수 없음"

    FitnessCard(Modifier.fillMaxWidth(), onClick = { isOpen = true }) {
        Row(
            Modifier.fillMaxWidth().padding(FitnessSpacing.card),
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(
                selectedLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (isOpen) {
        AlertDialog(
            onDismissRequest = { isOpen = false },
            title = { Text(title) },
            text = {
                val maxListHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxListHeight)) {
                    item {
                        FilterPopupOption(
                            label = "전체",
                            selected = selectedId == null,
                            onClick = {
                                isOpen = false
                                onSelect(null)
                            }
                        )
                    }
                    items(options, key = { it.first }) { (id, label) ->
                        FilterPopupOption(
                            label = label,
                            selected = selectedId == id,
                            onClick = {
                                isOpen = false
                                onSelect(id)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isOpen = false }) { Text("닫기") }
            }
        )
    }
}

@Composable
private fun FilterPopupOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = FitnessSpacing.micro, vertical = FitnessSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun ExerciseFamilyPickerCard(
    result: RuntimeExercisePicker.FamilyResult,
    state: ExercisePickerUiState.Ready,
    actions: ExercisePickerScreenActions,
    onSelectFamily: () -> Unit
) {
    val family = result.family
    val selectedFamily = result.presets.size > 1 && state.selectedFamilyId == family.familyId
    val bodyPart = BodyPart.fromId(family.defaultUiPart)?.labelKo() ?: family.defaultUiPart.orEmpty()
    val singlePreset = result.presets.singleOrNull()
    val representative = family.presets.firstOrNull() ?: result.presets.first()
    val title = singlePreset?.displayName().orEmpty().ifBlank { family.displayName().orEmpty() }
    val metadata = if (singlePreset != null) {
        exercisePickerPresetMetadata(singlePreset)
    } else {
        listOfNotNull(bodyPart.takeIf { it.isNotBlank() }, "${result.presets.size}개 변형")
            .joinToString(" · ")
    }
    val actionLabel = if (singlePreset != null) "선택" else "변형 선택"

    FitnessCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(
                horizontal = FitnessSpacing.card,
                vertical = FitnessSpacing.card * PICKER_ROW_SCALE
            ),
            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small * PICKER_ROW_SCALE)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = PICKER_ROW_MIN_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (singlePreset != null) {
                    ExercisePickerImage(singlePreset)
                } else {
                    ExercisePickerFamilyImage(family.familyId, family.displayName().orEmpty())
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.micro),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = metadata,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FitnessOutlinedButton(
                            onClick = {
                                if (singlePreset != null) actions.choose(singlePreset)
                                else onSelectFamily()
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "$title $actionLabel"
                            },
                            selected = if (singlePreset != null) {
                                state.selectedPresetId == singlePreset.presetId
                            } else selectedFamily
                        ) { Text(actionLabel) }
                    }
                }
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerVariantSheet(
    result: RuntimeExercisePicker.FamilyResult,
    state: ExercisePickerUiState.Ready,
    actions: ExercisePickerScreenActions
) {
    val familyId = result.family.familyId
    val familyTitle = result.family.displayName().orEmpty().ifBlank { "운동 변형" }
    val selectedPreset = result.presets.firstOrNull { it.presetId == state.selectedPresetId }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { actions.selectFamily(familyId) },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = screenHeight * 0.84f)
                .padding(horizontal = FitnessSpacing.card)
        ) {
            Text(
                text = "$familyTitle 변형 선택",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${result.presets.size}개 변형",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.fillMaxWidth().height(FitnessSpacing.small))
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = screenHeight * 0.56f)
                    .semantics { contentDescription = "$familyTitle 변형 목록" },
                contentPadding = PaddingValues(bottom = FitnessSpacing.small),
                verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
            ) {
                items(result.presets, key = { preset -> preset.presetId }) { preset ->
                    val selected = state.selectedPresetId == preset.presetId
                    ExercisePickerPresetRow(
                        preset = preset,
                        selected = selected,
                        actionLabel = if (selected) "선택됨" else "선택",
                        onClick = { actions.selectPreset(familyId, preset.presetId) }
                    )
                }
            }
            FitnessOutlinedButton(
                onClick = { selectedPreset?.let(actions::choose) },
                modifier = Modifier.fillMaxWidth()
                    .padding(top = FitnessSpacing.small, bottom = FitnessSpacing.card),
                enabled = selectedPreset != null,
                selected = selectedPreset != null
            ) {
                Text(if (state.selectionMode == ExercisePickerSelectionMode.WORKOUT_LINK_MANUAL)
                    "이 운동에 연결" else "이 변형으로 선택")
            }
        }
    }
}

@Composable
private fun ExercisePickerPresetRow(
    preset: RuntimeExercisePreset,
    selected: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = PICKER_ROW_MIN_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExercisePickerImage(preset)
        ExercisePickerPresetText(preset, exercisePickerPresetMetadata(preset), Modifier.weight(1f))
        FitnessOutlinedButton(
            onClick = onClick,
            modifier = Modifier.semantics {
                contentDescription = "${preset.displayName()} $actionLabel"
            },
            selected = selected
        ) {
            Text(actionLabel)
        }
    }
}

private fun exercisePickerPresetMetadata(preset: RuntimeExercisePreset): String = listOfNotNull(
    BodyPart.fromId(preset.defaultUiPart)?.labelKo(),
    preset.primarySubPartNameKo?.takeIf { it.isNotBlank() },
    preset.equipmentNameKo?.takeIf { it.isNotBlank() },
    FitnessRecordContract.displayRecordTypeKo(preset.recordType)
).joinToString(" · ")

@Composable
private fun ExercisePickerPresetText(
    preset: RuntimeExercisePreset,
    metadata: String,
    modifier: Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro * PICKER_ROW_SCALE)) {
        Text(
            text = preset.displayName().orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        if (metadata.isNotBlank()) {
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExercisePickerFamilyImage(familyId: String, name: String) {
    val activity = LocalActivity.current
    val modifier = Modifier.size(PICKER_IMAGE_SIZE)
    if (activity != null) {
        FitnessExerciseFamilyIllustration(
            activity = activity,
            familyId = familyId,
            modifier = modifier,
            contentDescription = "$name 대표 운동 이미지"
        ) { ExercisePickerImageFallback(modifier) }
    } else {
        ExercisePickerImageFallback(modifier)
    }
}

@Composable
private fun ExercisePickerImage(preset: RuntimeExercisePreset, exactVariant: Boolean = true) {
    val activity = LocalActivity.current
    val identity = remember(preset) {
        ExerciseFamilyCatalog.empty().identityForPreset(preset)
    }
    val modifier = Modifier.size(PICKER_IMAGE_SIZE)
    if (activity != null && identity != null) {
        FitnessExerciseIllustration(
            activity = activity,
            identity = identity,
            exactVariant = exactVariant,
            modifier = modifier,
            contentDescription = "${preset.displayName()} 운동 이미지"
        ) { ExercisePickerImageFallback(modifier) }
    } else {
        ExercisePickerImageFallback(modifier)
    }
}

@Composable
private fun ExercisePickerImageFallback(modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "이미지\n없음",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun pickerTitle(
    screen: FitnessScreen,
    selectionMode: ExercisePickerSelectionMode?
): String = when (selectionMode) {
    ExercisePickerSelectionMode.ROUTINE_ADD -> "루틴 종목 추가"
    ExercisePickerSelectionMode.WORKOUT_REPLACE -> "운동 종목 교체"
    ExercisePickerSelectionMode.WORKOUT_LINK_MANUAL -> "정식 운동에 연결"
    ExercisePickerSelectionMode.WORKOUT_ADD -> "운동 종목 추가"
    null -> if (screen == FitnessScreen.ROUTINE_ADD) "루틴 종목 추가" else "운동 종목 선택"
}
