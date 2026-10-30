package com.yeonsik.fitnessapp.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FitnessHeader(title: String, subtitle: String? = null, back: (() -> Unit)? = null,
                  titleStyle: TextStyle = MaterialTheme.typography.headlineLarge,
                  titleModifier: Modifier = Modifier) {
    Column(Modifier.fillMaxWidth().padding(bottom = FitnessSpacing.small),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
        if (back != null) {
            // Its own row above the title; the offset aligns the glyph with the page margin.
            FitnessBackButton(back, Modifier.offset(x = (-12).dp))
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
    floating: Boolean = false,
    backdrop: FitnessBackdrop? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val container = if (floating) {
        // A glass capsule over the page; [backdrop] must record the page content beneath it.
        val shape = RoundedCornerShape(FitnessUiTokens.SESSION_BAR_RADIUS_DP.dp)
        modifier
            .statusBarsPadding()
            .padding(start = FitnessSpacing.gap, end = FitnessSpacing.gap, top = FitnessSpacing.small)
            .fillMaxWidth()
            .fitnessGlassSurface(shape, FitnessGlassLevel.BAR, backdrop)
            // Taps on the glass rim never reach the content underneath.
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .padding(horizontal = FitnessSpacing.micro)
    } else {
        // Edge-to-edge: the opaque bar extends under the status bar, its content below it.
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = FitnessSpacing.gap, vertical = FitnessSpacing.small)
    }
    Row(container, verticalAlignment = Alignment.CenterVertically) {
        FitnessBackButton(onBack)
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

@Composable
fun FitnessSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = FitnessSpacing.section),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
fun FitnessCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
                outlined: Boolean = false,
                content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    // A flat card separates from the page with a hairline only; glass is for floating controls.
    val border = if (outlined) {
        val alpha = when {
            rememberHighContrastTextEnabled() -> 1f
            LocalFitnessDarkTheme.current -> FitnessUiTokens.CARD_HAIRLINE_ALPHA_DARK
            else -> FitnessUiTokens.CARD_HAIRLINE_ALPHA_LIGHT
        }
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha))
    } else null
    if (onClick == null) {
        Card(modifier, shape = FitnessShape.card, colors = colors,
            elevation = elevation, border = border, content = content)
    } else {
        Card(onClick, modifier, shape = FitnessShape.card, colors = colors,
            elevation = elevation, border = border, content = content)
    }
}

@Composable
fun FitnessButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                  content: @Composable RowScope.() -> Unit) {
    Button(onClick, modifier.heightIn(min = FitnessSpacing.touch), enabled = enabled,
        shape = FitnessShape.button,
        contentPadding = PaddingValues(horizontal = FitnessSpacing.card, vertical = FitnessSpacing.gap),
        colors = ButtonDefaults.buttonColors(containerColor = LocalFitnessColors.current.action,
            contentColor = LocalFitnessColors.current.onAction), content = content)
}

@Composable
fun FitnessOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                          destructive: Boolean = false, selected: Boolean = false,
                          content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier.heightIn(min = FitnessSpacing.touch)
        .semantics { this.selected = selected }, enabled = enabled,
        shape = FitnessShape.button,
        contentPadding = PaddingValues(horizontal = FitnessSpacing.card, vertical = FitnessSpacing.gap),
        border = if (selected) BorderStroke(1.dp, LocalFitnessColors.current.action) else null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = when {
                destructive -> MaterialTheme.colorScheme.surface
                selected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = when {
                destructive -> MaterialTheme.colorScheme.error
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
        ), content = content)
}

@Composable
fun FitnessTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
                     label: @Composable (() -> Unit)? = null, singleLine: Boolean = true,
                     keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                     keyboardActions: KeyboardActions? = null,
                     visualTransformation: VisualTransformation = VisualTransformation.None) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value, onValueChange, modifier, label = label, singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge, shape = FitnessShape.input,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions ?: KeyboardActions(onDone = { focus.clearFocus() }),
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ))
}

@Composable
fun FitnessFactCard(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    FitnessCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(FitnessSpacing.card), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"))
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Metrics reflow with accessibility font sizes instead of clipping their values. */
@Composable
fun FitnessFactRow(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    if (LocalDensity.current.fontScale >= 1.3f) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
            first(); second()
        }
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap),
            verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f).fillMaxHeight(), propagateMinConstraints = true) { first() }
            Box(Modifier.weight(1f).fillMaxHeight(), propagateMinConstraints = true) { second() }
        }
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

/** The one back control: an icon with a 48dp target and no label, read as "뒤로, 버튼". */
@Composable
fun FitnessBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "뒤로"
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Icon(FitnessBackIcon, contentDescription)
    }
}

/**
 * Previous/next stepper for months, dates and periods. Callers that already label the button
 * through [modifier] semantics pass no [contentDescription]. Disabled buttons dim to 0.38.
 */
@Composable
fun FitnessStepButton(
    forward: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Icon(if (forward) FitnessActionNextIcon else FitnessBackIcon, contentDescription)
    }
}

/**
 * Edge-to-edge status bar scrim: the page background fades in under the clock and icons so
 * scrolled content never collides with them. It draws only and never consumes touches.
 */
@Composable
fun FitnessStatusBarScrim(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val height = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        FitnessUiTokens.STATUS_BAR_SCRIM_EXTENT_DP.dp
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    listOf(background.copy(alpha = FitnessUiTokens.STATUS_BAR_SCRIM_ALPHA), Color.Transparent)
                )
            )
    )
}
