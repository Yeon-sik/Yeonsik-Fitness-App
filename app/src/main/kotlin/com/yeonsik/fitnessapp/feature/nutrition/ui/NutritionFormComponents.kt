package com.yeonsik.fitnessapp.feature.nutrition.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.model.FoodPortionDraft
import kotlinx.coroutines.launch

@Composable
internal fun NutritionEntryFrame(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    closeEnabled: Boolean = true,
    showScrollToTop: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val canScrollToTop by remember { derivedStateOf { scrollState.value > 0 } }
    Dialog(
        onDismissRequest = { if (closeEnabled) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier.fillMaxSize().semantics { paneTitle = title }, color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxHeight().fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onClose, enabled = closeEnabled, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(scrollState)
                                .testTag("nutrition-entry-scroll")
                                .padding(20.dp)
                                .padding(bottom = if (showScrollToTop) 64.dp else 0.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp), content = content
                        )
                        if (showScrollToTop && canScrollToTop) {
                            FloatingActionButton(
                                onClick = { scope.launch { scrollState.animateScrollTo(0) } },
                                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                                    .size(56.dp).testTag("meal-entry-top")
                                    .semantics { contentDescription = "식단 등록 최상단으로 이동" },
                                shape = CircleShape
                            ) { Text("TOP", style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                    if (footer != null) {
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            Column {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) { footer() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NutritionFormSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
internal fun FoodPortionList(
    portions: List<FoodPortionDraft>,
    onAmountChange: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    amountLabel: String = "섭취량",
    itemContent: @Composable (FoodPortionDraft) -> Unit = {}
) {
    if (portions.isEmpty()) return
    LazyColumn(modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(portions, key = { it.food.id }) { portion ->
            val food = portion.food
            val amount = portion.amount
            val step = food.basisAmount / 4.0
            val calories = portion.profile?.value(NutritionProfile.CALORIES_KCAL)?.let { NutritionCalculator.trim(it) } ?: "미확인"
            NutritionFormSection(food.displayName(), Modifier.fillMaxWidth(), "${food.basisLabel()} 기준 · $amountLabel $calories kcal") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    OutlinedButton(
                        onClick = { onAmountChange(food.id, NutritionCalculator.trim(amount!! - step)) },
                        enabled = enabled && amount != null && amount > step,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "${food.displayName()} $amountLabel 줄이기" },
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    NutritionNumberField(portion.quantity, { onAmountChange(food.id, it) }, amountLabel, NutritionUnit.display(food.basisUnit),
                        Modifier.weight(1f), enabled = enabled, showError = portion.quantity.isNotBlank() && portion.profile == null, positive = true)
                    OutlinedButton(
                        onClick = { onAmountChange(food.id, NutritionCalculator.trim(amount!! + step)) },
                        enabled = enabled && amount != null && (amount + step).isFinite(),
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "${food.displayName()} $amountLabel 늘리기" },
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
                itemContent(portion)
                TextButton(onClick = { onRemove(food.id) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = "${food.displayName()} 구성에서 삭제" }) { Text("구성에서 삭제", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

internal fun nutritionTotalText(total: NutritionTotals.Total): String = when {
    total.knownCount() == 0 && total.missingCount() == 0 -> "0"
    total.knownCount() == 0 -> "미확인"
    total.missingCount() > 0 -> "${NutritionCalculator.trim(total.knownSum())} + 미확인"
    else -> NutritionCalculator.trim(total.knownSum())
}

@Composable
internal fun NutritionTotalPreview(totals: NutritionTotals, modifier: Modifier = Modifier, label: String = "합계") {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${nutritionTotalText(totals.total(NutritionProfile.CALORIES_KCAL))} kcal",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        val values = listOf("단백질" to NutritionProfile.PROTEIN_GRAMS, "탄수화물" to NutritionProfile.CARBS_GRAMS, "지방" to NutritionProfile.FAT_GRAMS)
        BoxWithConstraints {
            if (maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.35f) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    values.forEach { (name, key) -> Text("$name ${nutritionTotalText(totals.total(key))} g", style = MaterialTheme.typography.bodyMedium) }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    values.forEach { (name, key) ->
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${nutritionTotalText(totals.total(key))} g", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NutritionNumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    required: Boolean = true,
    showError: Boolean = false,
    positive: Boolean = false
) {
    val number = value.trim().toDoubleOrNull()
    val invalid = (required && value.isBlank()) || (value.isNotBlank() && (number == null || !number.isFinite() || number < 0 || (positive && number == 0.0)))
    OutlinedTextField(value, onChange, modifier, enabled = enabled, singleLine = true,
        label = { Text(label) }, suffix = { Text(unit) }, shape = FitnessShape.input,
        isError = showError && invalid,
        supportingText = if (showError && invalid) ({ Text(if (positive) "0보다 큰 숫자를 입력하세요" else "0 이상의 숫자를 입력하세요") }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
}

@Composable
internal fun NutritionFieldGrid(
    fields: List<FoodEntryField>,
    value: (FoodEntryField) -> String,
    onChange: (FoodEntryField, String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    required: Boolean = true,
    submitted: Boolean = false
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 280.dp && LocalDensity.current.fontScale < 1.35f) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            fields.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { field ->
                        val raw = value(field)
                        NutritionNumberField(raw, { onChange(field, it) }, field.label, field.unit, Modifier.weight(1f),
                            enabled = enabled, required = required, showError = submitted || raw.isNotBlank())
                    }
                    if (columns == 2 && row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
