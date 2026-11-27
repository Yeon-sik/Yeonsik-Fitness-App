package com.yeonsik.fitness.shared.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit

@Composable
fun DisplaySettings(
    unit: MassUnit,
    themeId: String,
    onUnitSelected: (MassUnit) -> Unit,
    onThemeSelected: (String) -> Unit
) {
    Text("표시", Modifier.padding(top = 24.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
    SettingsChoiceGroup(
        "무게 단위", MassUnit.entries.map { it.id to it.labelKo }, unit.id,
        { key -> onUnitSelected(DisplayPreferences.massUnit(key)) }, Modifier.fillMaxWidth()
    )
    Text("선택한 단위로 표시하고 입력합니다. 기존 기록은 유지됩니다.",
        fontSize = 14.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SettingsChoiceGroup(
        "테마", DisplayTheme.entries.map { it.id to it.label }, themeId,
        onThemeSelected, Modifier.fillMaxWidth()
    )
}

/** Selection semantics and adaptive layout are scoped to Settings, not every app button. */
@Composable
private fun SettingsChoiceGroup(
    title: String,
    choices: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.semantics { heading() }, fontSize = 16.sp, lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold)
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
            val stacked = fontScale > 1.2f || maxWidth.value / choices.size < 112f * fontScale
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { (key, label) ->
                        SettingsChoice(key, label, selectedKey == key, { onSelect(key) }, Modifier.fillMaxWidth())
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { (key, label) ->
                        SettingsChoice(key, label, selectedKey == key, { onSelect(key) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsChoice(
    key: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(FitnessComposeTokens.BUTTON_RADIUS_DP.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer) else null
    ) {
        Row(
            Modifier.testTag("display-$key").selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.size(20.dp),
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Text(label, Modifier.weight(1f), fontSize = 16.sp, lineHeight = 24.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}
