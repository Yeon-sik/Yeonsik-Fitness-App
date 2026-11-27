package com.yeonsik.fitnessapp.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeonsik.fitnessapp.core.ui.AppCard
import com.yeonsik.fitnessapp.core.ui.AppOutlinedButton

@Composable
internal fun SettingsExpandableGroup(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(modifier) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (expanded) "접기" else "펼치기") {
                    onExpandedChange(!expanded)
                }
                .semantics { stateDescription = if (expanded) "펼쳐짐" else "접힘" }
                .heightIn(min = 64.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                SettingsSupportingText(summary)
            }
            Text(if (expanded) "접기" else "열기", color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp, lineHeight = 20.sp)
        }
        if (expanded) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@Composable
internal fun SettingsAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    AppOutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled) {
        Text(label, fontSize = 16.sp, lineHeight = 24.sp)
    }
}
