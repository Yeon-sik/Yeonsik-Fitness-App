package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.core.ui.FitnessAlertDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.meal.model.MealSnapshotRead
import com.yeonsik.fitnessapp.core.ui.AppOutlinedButton
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.data.MealEntryPolicy
import com.yeonsik.fitnessapp.data.NutritionCalculator
import com.yeonsik.fitnessapp.data.NutritionUnit
import com.yeonsik.fitnessapp.feature.meal.model.PhotoEstimateMeal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal fun mealRecordDateError(date: String): String? = runCatching {
    require(date.isNotBlank()) { "날짜를 선택하세요." }
    MealEntryPolicy.requireRecordDate(date, LocalDate.now())
}.exceptionOrNull()?.message

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MealRecordEditDialog(
    editor: MealRecordEditor,
    saving: Boolean,
    error: String?,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var date by rememberSaveable(editor.recordId) { mutableStateOf(editor.date) }
    var time by rememberSaveable(editor.recordId) { mutableStateOf(editor.time) }
    var showDatePicker by rememberSaveable(editor.recordId) { mutableStateOf(false) }
    val dateError = mealRecordDateError(date)
    if (!showDatePicker) {
        FitnessAlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            title = { Text("식단 수정") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(editor.title, fontWeight = FontWeight.Bold)
                    AppOutlinedButton({ showDatePicker = true },
                        Modifier.fillMaxWidth().testTag("meal-record-edit-date"), enabled = !saving) {
                        Text("날짜 선택 · $date")
                    }
                    MealTimeField(time, { time = it }, Modifier.fillMaxWidth(), enabled = !saving)
                    (dateError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton({ onSave(editor.recordId, date, time) },
                    enabled = !saving && dateError == null && mealTimeError(time) == null,
                    modifier = Modifier.testTag("meal-record-edit-save")) {
                    Text(if (saving) "저장 중" else "저장")
                }
            },
            dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("취소") } }
        )
    } else {
        val today = LocalDate.now()
        val selectableDates = remember(today) { object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        } }
        // Match the existing body editor's Material date picker and UTC date conversion.
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull(),
            selectableDates = selectableDates
        )
        val selected = picker.selectedDateMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
        }
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton({
                    if (selected != null) date = selected
                    showDatePicker = false
                }, enabled = selected != null && mealRecordDateError(selected) == null) { Text("선택") }
            },
            dismissButton = { TextButton({ showDatePicker = false }) { Text("취소") } }
        ) { DatePicker(picker, showModeToggle = false) }
    }
}

@Composable
internal fun MealSnapshotDetailDialog(state: MealDetailState, onDismiss: () -> Unit) {
    MealSnapshotDetailDialog(state, onDismiss, onEditDateTime = null)
}

@Composable
internal fun MealSnapshotDetailDialog(
    state: MealDetailState,
    onDismiss: () -> Unit,
    onEditDateTime: (() -> Unit)?
) {
    if (state == MealDetailState.Idle) return
    FitnessAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("메뉴 자세히 보기") },
        text = {
            when (state) {
                is MealDetailState.Loading -> Text("식단 상세를 불러오는 중",
                    Modifier.testTag("meal-detail-loading").semantics { liveRegion = LiveRegionMode.Polite })
                is MealDetailState.Error -> Text(state.message,
                    Modifier.testTag("meal-detail-error").semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error)
                is MealDetailState.Ready -> MealSnapshotDetailContent(state.snapshot,
                    Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()))
                MealDetailState.Idle -> Unit
            }
        },
        confirmButton = {
            Row {
                if (state is MealDetailState.Ready && onEditDateTime != null) {
                    TextButton(onEditDateTime) { Text("날짜·시간 수정") }
                }
                TextButton(onDismiss) { Text("닫기") }
            }
        }
    )
}

internal fun mealSnapshotAmount(quantity: Double, unit: String, fraction: Double = 1.0): String {
    val displayUnit = if (unit == NutritionUnit.SERVING) "인분" else NutritionUnit.display(unit)
    return "${NutritionCalculator.trim(quantity * fraction)} $displayUnit"
}

@Composable
internal fun MealSnapshotDetailContent(snapshot: MealSnapshotRead, modifier: Modifier = Modifier) {
    Column(modifier.testTag("meal-detail-${snapshot.id}"),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        Text(snapshot.date, style = MaterialTheme.typography.bodyMedium)
        val photoEstimate = PhotoEstimateMeal.isPhotoEstimate(snapshot.metadata)
        if (photoEstimate) {
            Text(PhotoEstimateMeal.title(snapshot.date), style = MaterialTheme.typography.titleMedium)
            Text(PhotoEstimateMeal.LABEL, Modifier.testTag("meal-detail-photo-estimate"),
                style = MaterialTheme.typography.labelLarge)
            Text("식사 전체 · 약 ${NutritionCalculator.trim(snapshot.nutrition.calories ?: 0.0)} kcal")
            listOf("탄수화물" to snapshot.nutrition.carbsGrams, "단백질" to snapshot.nutrition.proteinGrams,
                "지방" to snapshot.nutrition.fatGrams).forEach { (label, value) ->
                Text("$label · ${value?.let { "${NutritionCalculator.trim(it)} g" } ?: "미입력"}")
            }
        } else if (snapshot.items.isEmpty()) {
            Text("상세 구성 정보가 저장되어 있지 않습니다.", Modifier.testTag("meal-detail-missing"))
        }
        snapshot.items.forEach { item -> key(item.id) {
            val fraction = item.consumption?.consumedFraction ?: 1.0
            Column(Modifier.fillMaxWidth().testTag("meal-detail-item-${item.id}"),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text(item.foodName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val identity = listOfNotNull(item.brand, item.brandName, item.subBrandName, item.productName)
                    .filter { it.isNotBlank() && it != item.foodName }.distinct().joinToString(" · ")
                if (identity.isNotEmpty()) Text(identity, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("실제 먹은 양 ${mealSnapshotAmount(item.quantity, item.unit, fraction)}",
                    style = MaterialTheme.typography.bodyMedium)
                if (fraction != 1.0) Text(
                    "기록한 양 ${mealSnapshotAmount(item.quantity, item.unit)} · ${NutritionCalculator.trim(fraction * 100)}% 섭취",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.components.isNotEmpty()) {
                    Column(Modifier.padding(start = AppSpacing.gap),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        Text("구성 음식", style = MaterialTheme.typography.labelLarge)
                        item.components.forEach { component ->
                            Text("${component.foodName} · ${mealSnapshotAmount(component.quantity, component.unit,
                                component.consumedFraction ?: fraction)}",
                                Modifier.testTag("meal-detail-component-${component.id}"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } }
    }
}
