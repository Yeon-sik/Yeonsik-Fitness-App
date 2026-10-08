package com.yeonsik.fitnessapp.feature.nutrition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.data.NutritionFood

/** Each result remains an add/load action, including foods already in the draft. */
@Composable
internal fun FoodSearchResults(
    foods: List<NutritionFood>,
    onAdd: (NutritionFood) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    includedFoodIds: Set<String> = emptySet(),
    actionLabel: String = "추가",
    includedLabel: String = "구성에 있음"
) {
    if (foods.isEmpty()) return
    val shape = MaterialTheme.shapes.medium
    val largeText = LocalDensity.current.fontScale >= 1.35f
    LazyColumn(modifier.fillMaxWidth().heightIn(max = 288.dp).clip(shape)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
        itemsIndexed(foods, key = { index, food -> food.id + "#" + index }) { index, food ->
            val included = food.id in includedFoodIds
            Row(Modifier.fillMaxWidth().testTag("food-result-${food.id}")
                .background(if (included) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .semantics { if (included) stateDescription = includedLabel }
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = "${food.displayName()} $actionLabel") { onAdd(food) }
                .heightIn(min = 64.dp).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (food.isPackagedFood()) food.packagedProductLabel() else food.displayName(),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (food.isPackagedFood()) food.packagedVariantLabel() else food.basisLabel(),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(food.nutritionLabel(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (included) Text(includedLabel, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (largeText) Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (!largeText) Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (index < foods.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Bounded radio choices shared by meal entry and nutrition publication. */
@Composable
internal fun <T> NutritionChoiceList(
    items: List<T>,
    itemKey: (T) -> String,
    selectedKey: String?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemContent: @Composable ColumnScope.(T) -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    LazyColumn(modifier.heightIn(max = 288.dp).clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
        // An index suffix also tolerates duplicate IDs in a remote response.
        itemsIndexed(items, key = { index, item -> itemKey(item) + "#" + index }) { index, item ->
            val selected = itemKey(item) == selectedKey
            Row(Modifier.fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(item) })
                .heightIn(min = 64.dp).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { itemContent(item) }
                Text(if (selected) "선택됨" else "선택", style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (index < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
