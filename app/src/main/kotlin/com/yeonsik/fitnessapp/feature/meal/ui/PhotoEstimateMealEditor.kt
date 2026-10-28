package com.yeonsik.fitnessapp.feature.meal.ui

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessAlertDialog
import com.yeonsik.fitnessapp.feature.meal.model.PhotoEstimateMeal
import com.yeonsik.fitnessapp.feature.meal.model.PhotoEstimateMealInput

data class PhotoEstimateDraft(
    val calories: String = "", val time: String = "", val protein: String = "",
    val carbs: String = "", val fat: String = "", val recordId: String? = null, val updatedAt: String? = null
) {
    fun error(): String? = mealNutritionInputError(calories, "칼로리") ?: mealTimeError(time)
        ?: listOf("단백질" to protein, "탄수화물" to carbs, "지방" to fat)
            .firstNotNullOfOrNull { (label, value) -> mealNutritionInputError(value, label, required = false) }

    fun input(): PhotoEstimateMealInput {
        require(error() == null) { error().orEmpty() }
        return PhotoEstimateMealInput(calories.trim().toDouble(), protein.trim().toDoubleOrNull(),
            carbs.trim().toDoubleOrNull(), fat.trim().toDoubleOrNull())
    }

    fun toBundle() = Bundle().apply {
        putString("calories", calories); putString("time", time); putString("protein", protein)
        putString("carbs", carbs); putString("fat", fat); putString("recordId", recordId); putString("updatedAt", updatedAt)
    }

    companion object {
        fun fromBundle(bundle: Bundle?): PhotoEstimateDraft? = bundle?.let {
            PhotoEstimateDraft(it.getString("calories").orEmpty(), it.getString("time").orEmpty(),
                it.getString("protein").orEmpty(), it.getString("carbs").orEmpty(), it.getString("fat").orEmpty(),
                it.getString("recordId"), it.getString("updatedAt"))
        }
    }
}

/** Stateless editor; entered values and save requests belong to MealViewModel. */
@Composable
internal fun PhotoEstimateMealEditor(date: String, draft: PhotoEstimateDraft, saving: Boolean, error: String?,
    onChange: (PhotoEstimateDraft) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    var showMacros by rememberSaveable(date, draft.recordId) {
        mutableStateOf(listOf(draft.protein, draft.carbs, draft.fat).any { it.isNotBlank() })
    }
    FitnessAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.testTag("photo-estimate-editor"),
        title = { Text(PhotoEstimateMeal.LABEL) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text(PhotoEstimateMeal.title(date), style = MaterialTheme.typography.titleMedium)
                Text("사진으로 추정한 식사 전체의 칼로리를 입력하세요. 제목은 기록 날짜로 저장돼요.",
                    style = MaterialTheme.typography.bodyMedium)
                MealNutritionField(draft.calories, { onChange(draft.copy(calories = it)) }, "총 칼로리", "kcal",
                    Modifier.fillMaxWidth().testTag("photo-estimate-calories"), !saving)
                MealTimeField(draft.time, { onChange(draft.copy(time = it)) }, Modifier.fillMaxWidth(), enabled = !saving)
                TextButton({ showMacros = !showMacros }, enabled = !saving) {
                    Text(if (showMacros) "탄단지 접기" else "탄단지 추가 (선택)")
                }
                if (showMacros) {
                    Text("모르는 값은 비워 두세요. 미입력으로 표시합니다.", style = MaterialTheme.typography.bodySmall)
                    MealNutritionField(draft.carbs, { onChange(draft.copy(carbs = it)) }, "탄수화물", "g", Modifier.fillMaxWidth(), !saving, required = false)
                    MealNutritionField(draft.protein, { onChange(draft.copy(protein = it)) }, "단백질", "g", Modifier.fillMaxWidth(), !saving, required = false)
                    MealNutritionField(draft.fat, { onChange(draft.copy(fat = it)) }, "지방", "g", Modifier.fillMaxWidth(), !saving, required = false)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (draft.calories.isNotBlank()) draft.error()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onSave, enabled = !saving && draft.error() == null,
            modifier = Modifier.testTag("photo-estimate-save")) { Text(if (saving) "저장 중" else "저장") } },
        dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("취소") } }
    )
}
