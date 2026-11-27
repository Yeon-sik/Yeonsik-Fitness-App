package com.yeonsik.fitnessapp.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FitnessHeader(title: String, subtitle: String? = null, back: (() -> Unit)? = null,
                  titleStyle: TextStyle = MaterialTheme.typography.headlineLarge,
                  titleModifier: Modifier = Modifier) {
    Column(Modifier.fillMaxWidth().padding(bottom = FitnessSpacing.small),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
        if (back != null) {
            TextButton(onClick = back, contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier.heightIn(min = FitnessSpacing.touch)) { Text("‹ 뒤로") }
        }
        Text(title, titleModifier.semantics { heading() }, style = titleStyle)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun FitnessSessionTopBar(
    title: String,
    canCancel: Boolean,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = FitnessSpacing.gap, vertical = FitnessSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text("←", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (canCancel) {
            TextButton(onClick = onCancel) {
                Text("운동 취소", color = MaterialTheme.colorScheme.error)
            }
        }
        actions()
    }
}

/** Repeated record/summary content uses the same title, detail and surface rhythm. */
@Composable
fun FitnessDataRow(title: String, detail: String, modifier: Modifier = Modifier) {
    FitnessCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(FitnessSpacing.card), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
