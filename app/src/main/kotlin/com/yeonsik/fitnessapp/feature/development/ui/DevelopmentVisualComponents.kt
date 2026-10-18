package com.yeonsik.fitnessapp.feature.development.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*

/** Icons accompany visible names, so decorative glyphs do not repeat TalkBack announcements. */
@Composable
internal fun DevelopmentIconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier.size(44.dp), shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp)) }
    }
}

@Composable
internal fun DevelopmentGroupCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    FitnessCard(modifier, onClick = onClick) {
        Column(Modifier.padding(FitnessSpacing.card), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap),
                verticalAlignment = Alignment.CenterVertically) {
                DevelopmentIconBadge(icon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
                    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                trailingContent?.invoke()
            }
            content()
        }
    }
}

/** Affordance inside a card that already owns the click; it adds no nested tap target. */
@Composable
internal fun DevelopmentDisclosureHint(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Icon(FitnessActionNextIcon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One clearly labelled tap target, used for disclosure and settings destinations. */
@Composable
internal fun DevelopmentActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingIcon: ImageVector = FitnessActionNextIcon
) {
    Surface(onClick, modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(FitnessSpacing.gap),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(trailingIcon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DevelopmentFactRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
