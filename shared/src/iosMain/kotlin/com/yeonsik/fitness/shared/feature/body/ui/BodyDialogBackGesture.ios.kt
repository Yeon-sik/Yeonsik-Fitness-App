package com.yeonsik.fitness.shared.feature.body.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun BodyDialogBackGesture(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
}
