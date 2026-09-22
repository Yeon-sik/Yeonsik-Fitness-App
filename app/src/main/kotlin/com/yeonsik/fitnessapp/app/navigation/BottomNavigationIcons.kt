package com.yeonsik.fitnessapp.app.navigation

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.state.FitnessScreen

/** Decorative tab symbols; the adjacent label supplies each tab's accessible name. */
@Composable
internal fun BottomNavigationIcon(
    screen: FitnessScreen,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val icon = when (screen) {
        FitnessScreen.HOME -> HomeIcon
        FitnessScreen.WORKOUT -> WorkoutIcon
        FitnessScreen.RECORDS -> RecordsIcon
        FitnessScreen.SETTINGS -> SettingsIcon
        else -> return
    }
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier)
}

private val HomeIcon = ImageVector.Builder(
    name = "BottomNavigationHome",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3f, 10.5f)
        lineTo(12f, 3.2f)
        lineTo(21f, 10.5f)
        moveTo(5.2f, 9f)
        verticalLineTo(19f)
        curveTo(5.2f, 20f, 5.8f, 20.6f, 6.8f, 20.6f)
        horizontalLineTo(9.4f)
        verticalLineTo(14f)
        horizontalLineTo(14.6f)
        verticalLineTo(20.6f)
        horizontalLineTo(17.2f)
        curveTo(18.2f, 20.6f, 18.8f, 20f, 18.8f, 19f)
        verticalLineTo(9f)
    }
}.build()

private val WorkoutIcon = ImageVector.Builder(
    name = "BottomNavigationWorkout",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.5f, 10.5f)
        horizontalLineTo(16.5f)
        moveTo(7.5f, 13.5f)
        horizontalLineTo(16.5f)
        moveTo(4.8f, 6.5f)
        horizontalLineTo(6.5f)
        curveTo(7.1f, 6.5f, 7.5f, 6.9f, 7.5f, 7.5f)
        verticalLineTo(16.5f)
        curveTo(7.5f, 17.1f, 7.1f, 17.5f, 6.5f, 17.5f)
        horizontalLineTo(4.8f)
        curveTo(4.2f, 17.5f, 3.8f, 17.1f, 3.8f, 16.5f)
        verticalLineTo(7.5f)
        curveTo(3.8f, 6.9f, 4.2f, 6.5f, 4.8f, 6.5f)
        close()
        moveTo(17.5f, 6.5f)
        horizontalLineTo(19.2f)
        curveTo(19.8f, 6.5f, 20.2f, 6.9f, 20.2f, 7.5f)
        verticalLineTo(16.5f)
        curveTo(20.2f, 17.1f, 19.8f, 17.5f, 19.2f, 17.5f)
        horizontalLineTo(17.5f)
        curveTo(16.9f, 17.5f, 16.5f, 17.1f, 16.5f, 16.5f)
        verticalLineTo(7.5f)
        curveTo(16.5f, 6.9f, 16.9f, 6.5f, 17.5f, 6.5f)
        close()
        moveTo(1.5f, 10f)
        verticalLineTo(14f)
        moveTo(22.5f, 10f)
        verticalLineTo(14f)
    }
}.build()

private val RecordsIcon = ImageVector.Builder(
    name = "BottomNavigationRecords",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(6f, 5f)
        horizontalLineTo(18f)
        curveTo(19.1f, 5f, 20f, 5.9f, 20f, 7f)
        verticalLineTo(18.5f)
        curveTo(20f, 19.6f, 19.1f, 20.5f, 18f, 20.5f)
        horizontalLineTo(6f)
        curveTo(4.9f, 20.5f, 4f, 19.6f, 4f, 18.5f)
        verticalLineTo(7f)
        curveTo(4f, 5.9f, 4.9f, 5f, 6f, 5f)
        close()
        moveTo(8f, 3f)
        verticalLineTo(7f)
        moveTo(16f, 3f)
        verticalLineTo(7f)
        moveTo(4f, 10f)
        horizontalLineTo(20f)
        moveTo(8.5f, 15.2f)
        lineTo(11f, 17.5f)
        lineTo(15.5f, 13f)
    }
}.build()

private val SettingsIcon = ImageVector.Builder(
    name = "BottomNavigationSettings",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(10.1f, 3f)
        horizontalLineTo(13.9f)
        lineTo(14.4f, 5.5f)
        lineTo(16.2f, 6.5f)
        lineTo(18.6f, 5.7f)
        lineTo(20.5f, 9f)
        lineTo(18.6f, 10.7f)
        verticalLineTo(13.3f)
        lineTo(20.5f, 15f)
        lineTo(18.6f, 18.3f)
        lineTo(16.2f, 17.5f)
        lineTo(14.4f, 18.5f)
        lineTo(13.9f, 21f)
        horizontalLineTo(10.1f)
        lineTo(9.6f, 18.5f)
        lineTo(7.8f, 17.5f)
        lineTo(5.4f, 18.3f)
        lineTo(3.5f, 15f)
        lineTo(5.4f, 13.3f)
        verticalLineTo(10.7f)
        lineTo(3.5f, 9f)
        lineTo(5.4f, 5.7f)
        lineTo(7.8f, 6.5f)
        lineTo(9.6f, 5.5f)
        close()
        moveTo(15.2f, 12f)
        curveTo(15.2f, 13.8f, 13.8f, 15.2f, 12f, 15.2f)
        curveTo(10.2f, 15.2f, 8.8f, 13.8f, 8.8f, 12f)
        curveTo(8.8f, 10.2f, 10.2f, 8.8f, 12f, 8.8f)
        curveTo(13.8f, 8.8f, 15.2f, 10.2f, 15.2f, 12f)
        close()
    }
}.build()
