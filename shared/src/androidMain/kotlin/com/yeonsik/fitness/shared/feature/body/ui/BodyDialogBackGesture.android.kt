package com.yeonsik.fitness.shared.feature.body.ui

import androidx.compose.runtime.Composable

// Android Dialog sends system Back to onDismissRequest itself.
@Composable
internal actual fun BodyDialogBackGesture(onBack: () -> Unit) = Unit
