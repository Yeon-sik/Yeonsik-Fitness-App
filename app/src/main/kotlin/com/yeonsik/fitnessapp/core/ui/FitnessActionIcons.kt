package com.yeonsik.fitnessapp.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val FitnessStrengthIcon = ImageVector.Builder(
    name = "FitnessStrength",
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
        moveTo(3f, 9f)
        verticalLineTo(15f)
        moveTo(6f, 6f)
        verticalLineTo(18f)
        moveTo(6f, 12f)
        horizontalLineTo(18f)
        moveTo(18f, 6f)
        verticalLineTo(18f)
        moveTo(21f, 9f)
        verticalLineTo(15f)
    }
}.build()

internal val FitnessCardioIcon = ImageVector.Builder(
    name = "FitnessCardio",
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
        moveTo(12f, 20.5f)
        curveTo(8f, 17f, 3f, 13.1f, 3f, 8.6f)
        curveTo(3f, 5.6f, 5f, 3.6f, 7.7f, 3.6f)
        curveTo(9.5f, 3.6f, 11f, 4.5f, 12f, 6f)
        curveTo(13f, 4.5f, 14.5f, 3.6f, 16.3f, 3.6f)
        curveTo(19f, 3.6f, 21f, 5.6f, 21f, 8.6f)
        curveTo(21f, 13.1f, 16f, 17f, 12f, 20.5f)
        close()
    }
}.build()

internal val FitnessBodyMetricIcon = ImageVector.Builder(
    name = "FitnessBodyMetric",
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
        moveTo(12f, 1.8f)
        curveTo(9.7f, 1.8f, 8.2f, 3.5f, 8.2f, 5.7f)
        curveTo(8.2f, 7.9f, 9.7f, 9.2f, 12f, 9.2f)
        curveTo(14.3f, 9.2f, 15.8f, 7.8f, 15.8f, 5.5f)
        curveTo(15.8f, 3.3f, 14.3f, 1.8f, 12f, 1.8f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(10.5f, 7.5f)
        curveTo(9.5f, 7.3f, 8.8f, 8.1f, 7.9f, 8.4f)
        lineTo(3.5f, 7.2f)
        curveTo(2.4f, 6.9f, 1.7f, 7.5f, 1.8f, 8.4f)
        curveTo(1.9f, 9.2f, 2.5f, 9.6f, 3.4f, 9.8f)
        lineTo(8.7f, 11f)
        curveTo(8.3f, 12.6f, 8.4f, 14.7f, 8.7f, 15.9f)
        curveTo(8.9f, 16.7f, 8.6f, 17.4f, 8.1f, 18.1f)
        lineTo(6.6f, 20.3f)
        curveTo(6.1f, 21.1f, 6.4f, 21.9f, 7.2f, 22f)
        curveTo(7.9f, 22.1f, 8.6f, 21.8f, 9.2f, 21.1f)
        lineTo(10.9f, 19.2f)
        curveTo(11.5f, 18.6f, 11.8f, 17.8f, 11.8f, 17f)
        lineTo(12f, 13.8f)
        lineTo(13.2f, 17.5f)
        curveTo(13.6f, 18.5f, 14.3f, 19.8f, 15f, 20.9f)
        curveTo(15.5f, 21.7f, 16.3f, 22.1f, 17f, 21.8f)
        curveTo(17.8f, 21.5f, 18f, 20.7f, 17.6f, 19.9f)
        lineTo(16f, 16.1f)
        curveTo(15.5f, 14.7f, 15.4f, 12.8f, 14.9f, 11f)
        lineTo(20.6f, 9.8f)
        curveTo(21.5f, 9.6f, 22.1f, 9.1f, 22.1f, 8.4f)
        curveTo(22.1f, 7.6f, 21.4f, 7f, 20.5f, 7.2f)
        lineTo(15.5f, 8.3f)
        curveTo(14.4f, 8.6f, 13.5f, 7.7f, 12.7f, 7.5f)
    }
}.build()

internal val FitnessMealIcon = ImageVector.Builder(
    name = "FitnessMeal",
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
        moveTo(8f, 7f)
        curveTo(6.6f, 5.8f, 8.8f, 4.8f, 7.7f, 3.5f)
        moveTo(12f, 7f)
        curveTo(10.6f, 5.8f, 12.8f, 4.8f, 11.7f, 3.5f)
        moveTo(16f, 7f)
        curveTo(14.6f, 5.8f, 16.8f, 4.8f, 15.7f, 3.5f)
        moveTo(3.5f, 9.5f)
        horizontalLineTo(20.5f)
        curveTo(19.9f, 14.1f, 16.6f, 17.5f, 12f, 17.5f)
        curveTo(7.4f, 17.5f, 4.1f, 14.1f, 3.5f, 9.5f)
        moveTo(9f, 20.5f)
        horizontalLineTo(15f)
    }
}.build()

internal val FitnessActionNextIcon = ImageVector.Builder(
    name = "FitnessActionNext",
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
        moveTo(9f, 6f)
        lineTo(15f, 12f)
        lineTo(9f, 18f)
    }
}.build()
