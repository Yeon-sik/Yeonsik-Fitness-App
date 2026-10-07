package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.yeonsik.fitnessapp.core.ui.AppButton
import com.yeonsik.fitnessapp.core.ui.AppOutlinedButton
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.core.ui.AppTextField
import com.yeonsik.fitnessapp.data.NutritionUnit

@Composable
internal fun ManualFoodEditor(
    draft: ManualFoodDraft,
    onChange: (ManualFoodDraft) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    saving: Boolean,
    error: String?
) {
    var unitMenuOpen by remember { mutableStateOf(false) }
    var showOptional by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
    val textOptions = KeyboardOptions(imeAction = ImeAction.Next)
    val numberOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        Text("새 식품 영양정보", style = MaterialTheme.typography.titleMedium)
        Text("영양표에 적힌 기준량과 영양정보를 입력하세요. 등록 후 실제 섭취량을 따로 입력합니다.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppTextField(draft.name, { onChange(draft.copy(name = it)) }, Modifier.fillMaxWidth(),
            label = { Text("식품명") }, enabled = !saving, keyboardOptions = textOptions, keyboardActions = next)
        AppTextField(draft.brand, { onChange(draft.copy(brand = it)) }, Modifier.fillMaxWidth(),
            label = { Text("브랜드 (선택)") }, enabled = !saving, keyboardOptions = textOptions, keyboardActions = next)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            AppTextField(draft.basisAmount, { onChange(draft.copy(basisAmount = it)) }, Modifier.weight(1f),
                label = { Text("기준량") }, enabled = !saving, keyboardOptions = numberOptions, keyboardActions = next)
            Box {
                AppOutlinedButton(onClick = { unitMenuOpen = true }, enabled = !saving) {
                    Text("단위 · ${mealQuantityUnitLabel(draft.basisUnit)}")
                }
                DropdownMenu(expanded = unitMenuOpen, onDismissRequest = { unitMenuOpen = false }) {
                    NutritionUnit.options().forEach { unit ->
                        DropdownMenuItem(text = { Text(mealQuantityUnitLabel(unit)) }, onClick = {
                            unitMenuOpen = false
                            onChange(draft.copy(basisUnit = unit))
                        })
                    }
                }
            }
        }
        Text("${draft.basisAmount.ifBlank { "?" }} ${mealQuantityUnitLabel(draft.basisUnit)} 기준 영양정보",
            style = MaterialTheme.typography.bodyMedium)
        AppTextField(draft.calories, { onChange(draft.copy(calories = it)) }, Modifier.fillMaxWidth(),
            label = { Text("칼로리 kcal") }, enabled = !saving, keyboardOptions = numberOptions, keyboardActions = next)
        AppTextField(draft.carbs, { onChange(draft.copy(carbs = it)) }, Modifier.fillMaxWidth(),
            label = { Text("탄수화물 g") }, enabled = !saving, keyboardOptions = numberOptions, keyboardActions = next)
        AppTextField(draft.protein, { onChange(draft.copy(protein = it)) }, Modifier.fillMaxWidth(),
            label = { Text("단백질 g") }, enabled = !saving, keyboardOptions = numberOptions, keyboardActions = next)
        AppTextField(draft.fat, { onChange(draft.copy(fat = it)) }, Modifier.fillMaxWidth(),
            label = { Text("지방 g") }, enabled = !saving, keyboardOptions = numberOptions, keyboardActions = next)
        AppOutlinedButton(onClick = { showOptional = !showOptional }, modifier = Modifier.fillMaxWidth(), enabled = !saving) {
            Text(if (showOptional) "추가 영양정보 접기" else "나트륨·당류·포화지방 입력 (선택)")
        }
        if (showOptional) {
            Text("모르는 값은 비워 두세요. 0인 경우에는 0을 입력하세요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppTextField(draft.sodium, { onChange(draft.copy(sodium = it)) }, Modifier.fillMaxWidth(),
                label = { Text("나트륨 mg (선택)") }, enabled = !saving, keyboardOptions = numberOptions, keyboardActions = next)
            AppTextField(draft.sugars, { onChange(draft.copy(sugars = it)) }, Modifier.fillMaxWidth(),
                label = { Text("당류 g (선택)") }, enabled = !saving, keyboardOptions = numberOptions, keyboardActions = next)
            AppTextField(draft.saturatedFat, { onChange(draft.copy(saturatedFat = it)) }, Modifier.fillMaxWidth(),
                label = { Text("포화지방 g (선택)") }, enabled = !saving, keyboardOptions = numberOptions, keyboardActions = next)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            AppOutlinedButton(onClick = onClose, modifier = Modifier.weight(1f), enabled = !saving) { Text("닫기") }
            AppButton(onClick = onSave, modifier = Modifier.weight(1f), enabled = !saving) {
                Text(if (saving) "등록 중" else "내 식품으로 등록")
            }
        }
    }
}

internal fun mealQuantityUnitLabel(unit: String): String = when (unit) {
    NutritionUnit.SERVING -> "회분"
    "portion" -> "인분"
    "pack" -> "팩"
    else -> unit
}
