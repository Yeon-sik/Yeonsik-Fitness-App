package com.yeonsik.fitnessapp.feature.exercise.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import com.yeonsik.fitnessapp.core.ui.FitnessExerciseIllustration
import com.yeonsik.fitnessapp.core.ui.FitnessHeader
import com.yeonsik.fitnessapp.core.ui.FitnessOutlinedButton
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessStatusBadge
import com.yeonsik.fitnessapp.core.ui.FitnessStatusMessage
import com.yeonsik.fitnessapp.core.ui.FitnessTextField
import com.yeonsik.fitnessapp.core.ui.FitnessSemanticStatus
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePicker
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset
import com.yeonsik.fitnessapp.exercise.UiEquipmentCategory
import com.yeonsik.fitnessapp.state.FitnessScreen

interface ExercisePickerScreenActions {
    fun back()
    fun search(query: String)
    fun setBodyPart(bodyPart: BodyPart?)
    fun setPrimarySubPart(primarySubPart: String?)
    fun selectMuscleGroup(groupId: String)
    fun setEquipmentCategory(category: UiEquipmentCategory?)
    fun setSortOrder(order: RuntimeExercisePicker.SortOrder)
    fun resetFilters()
    fun selectFamily(familyId: String)
    fun selectPreset(familyId: String, presetId: String)
    fun choose(preset: RuntimeExercisePreset)
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
    LazyColumn(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(bottom = FitnessSpacing.card),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
    ) {
        item {
            FitnessHeader(title, back = actions::back)
        }
        item {
            FitnessTextField(
                value = state.query,
                onValueChange = actions::search,
                label = { Text("종목 검색") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            ExercisePickerFilters(state, actions)
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
                FitnessOutlinedButton(onClick = actions::resetFilters) {
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
                ExerciseFamilyPickerCard(result, state, actions)
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
        ExerciseMuscleMap(state, actions::selectMuscleGroup)
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
    actions: ExercisePickerScreenActions
) {
    val family = result.family
    val expanded = !result.hasSinglePreset() && state.selectedFamilyId == family.familyId
    val bodyPart = BodyPart.fromId(family.defaultUiPart)?.labelKo() ?: family.defaultUiPart.orEmpty()

    FitnessCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FitnessSpacing.card),
            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
                    Text(
                        text = family.displayName().orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = listOfNotNull(
                            bodyPart.takeIf { it.isNotBlank() },
                            "${result.presets.size}개 변형"
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                result.lastPerformedAt?.takeIf { it.isNotBlank() }?.let { date ->
                    FitnessStatusBadge(
                        status = FitnessSemanticStatus.INFO,
                        label = "최근 사용 $date",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (result.hasSinglePreset()) {
                val preset = result.presets.single()
                ExercisePickerPresetRow(
                    preset = preset,
                    selected = state.selectedPresetId == preset.presetId,
                    actionLabel = "선택",
                    onClick = { actions.choose(preset) }
                )
            } else {
                FitnessOutlinedButton(
                    onClick = { actions.selectFamily(family.familyId) },
                    modifier = Modifier.fillMaxWidth(),
                    selected = expanded
                ) {
                    Text(if (expanded) "변형 닫기" else "변형 선택")
                }
                if (expanded) {
                    result.presets.forEach { preset ->
                        ExercisePickerPresetRow(
                            preset = preset,
                            selected = state.selectedPresetId == preset.presetId,
                            actionLabel = "변형 선택",
                            onClick = {
                                actions.selectPreset(family.familyId, preset.presetId)
                            }
                        )
                    }
                    val selectedPreset = result.presets.firstOrNull {
                        it.presetId == state.selectedPresetId
                    }
                    if (selectedPreset != null) {
                        FitnessOutlinedButton(
                            onClick = { actions.choose(selectedPreset) },
                            modifier = Modifier.fillMaxWidth(),
                            selected = true
                        ) {
                            Text("이 변형으로 선택")
                        }
                    }
                }
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
    val stack = LocalDensity.current.fontScale >= 1.3f
    val metadata = listOfNotNull(
        BodyPart.fromId(preset.defaultUiPart)?.labelKo(),
        preset.primarySubPartNameKo?.takeIf { it.isNotBlank() },
        preset.equipmentNameKo?.takeIf { it.isNotBlank() },
        FitnessRecordContract.displayRecordTypeKo(preset.recordType)
    ).joinToString(" · ")

    if (stack) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExercisePickerImage(preset)
                ExercisePickerPresetText(preset, metadata, Modifier.weight(1f))
            }
            FitnessOutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                selected = selected
            ) { Text(actionLabel) }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExercisePickerImage(preset)
            ExercisePickerPresetText(preset, metadata, Modifier.weight(1f))
            FitnessOutlinedButton(onClick = onClick, selected = selected) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun ExercisePickerPresetText(
    preset: RuntimeExercisePreset,
    metadata: String,
    modifier: Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
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
        preset.canonicalVariantKey?.takeIf { it.isNotBlank() }?.let { variant ->
            Text(
                text = "변형 ID: $variant",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExercisePickerImage(preset: RuntimeExercisePreset) {
    val context = LocalContext.current
    val identity = remember(preset) {
        ExerciseFamilyCatalog.empty().identityForPreset(preset)
    }
    val modifier = Modifier.size(72.dp)
    if (context is Activity && identity != null) {
        FitnessExerciseIllustration(
            activity = context,
            identity = identity,
            exactVariant = true,
            modifier = modifier,
            contentDescription = "${preset.displayName()} 운동 이미지",
            fallback = {
                FitnessExerciseIllustration(
                    activity = context,
                    identity = identity,
                    exactVariant = false,
                    modifier = modifier,
                    contentDescription = "${preset.displayName()} 운동 이미지"
                ) { ExercisePickerImageFallback() }
            }
        )
    } else {
        ExercisePickerImageFallback(modifier)
    }
}

@Composable
private fun ExercisePickerImageFallback(modifier: Modifier = Modifier.size(72.dp)) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "이미지 없음",
            style = MaterialTheme.typography.labelSmall,
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
    ExercisePickerSelectionMode.WORKOUT_ADD -> "운동 종목 추가"
    null -> if (screen == FitnessScreen.ROUTINE_ADD) "루틴 종목 추가" else "운동 종목 선택"
}
