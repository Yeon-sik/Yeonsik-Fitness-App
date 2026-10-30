package com.yeonsik.fitnessapp.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Companion glyphs to the Home actions: 24-unit canvas, rounded 1.9-unit strokes. */
private fun progressIcon(name: String, drawing: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { drawing() }
    }.build()

internal val FitnessRecoveryIcon = progressIcon("FitnessRecovery") {
    moveTo(20.5f, 14.5f)
    curveTo(19.4f, 18.9f, 15.4f, 21.5f, 11f, 20.6f)
    curveTo(6.6f, 19.7f, 3.7f, 15.8f, 4.2f, 11.3f)
    curveTo(4.6f, 7.8f, 7.1f, 4.9f, 10.5f, 4f)
    curveTo(9f, 6.8f, 9.4f, 10.1f, 11.6f, 12.4f)
    curveTo(13.9f, 14.7f, 17.6f, 15.5f, 20.5f, 14.5f)
    close()
}

internal val FitnessWorkingSetsIcon = progressIcon("FitnessWorkingSets") {
    moveTo(4f, 6f); lineTo(5.5f, 7.5f); lineTo(8f, 4.5f)
    moveTo(11f, 6f); horizontalLineTo(20f)
    moveTo(4f, 12f); lineTo(5.5f, 13.5f); lineTo(8f, 10.5f)
    moveTo(11f, 12f); horizontalLineTo(20f)
    moveTo(4f, 18f); lineTo(5.5f, 19.5f); lineTo(8f, 16.5f)
    moveTo(11f, 18f); horizontalLineTo(20f)
}

internal val FitnessTrendIcon = progressIcon("FitnessTrend") {
    moveTo(4f, 4f); verticalLineTo(20f); horizontalLineTo(21f)
    moveTo(7f, 15f); lineTo(11f, 11f); lineTo(15f, 14f); lineTo(21f, 7f)
    moveTo(16.5f, 7f); horizontalLineTo(21f); verticalLineTo(11.5f)
}

internal val FitnessStatisticsIcon = progressIcon("FitnessStatistics") {
    moveTo(4f, 4f); verticalLineTo(20f); horizontalLineTo(21f)
    moveTo(8.5f, 16.5f); verticalLineTo(12.5f)
    moveTo(13f, 16.5f); verticalLineTo(8f)
    moveTo(17.5f, 16.5f); verticalLineTo(10.5f)
}

internal val FitnessCalendarIcon = progressIcon("FitnessCalendar") {
    moveTo(6f, 4.5f); horizontalLineTo(18f)
    curveTo(19.4f, 4.5f, 20f, 5.1f, 20f, 6.5f)
    verticalLineTo(19f); curveTo(20f, 20.4f, 19.4f, 21f, 18f, 21f)
    horizontalLineTo(6f); curveTo(4.6f, 21f, 4f, 20.4f, 4f, 19f)
    verticalLineTo(6.5f); curveTo(4f, 5.1f, 4.6f, 4.5f, 6f, 4.5f); close()
    moveTo(8f, 3f); verticalLineTo(7f)
    moveTo(16f, 3f); verticalLineTo(7f)
    moveTo(4f, 9.5f); horizontalLineTo(20f)
    moveTo(8f, 15f); lineTo(10.5f, 17.5f); lineTo(16f, 12.5f)
}

internal val FitnessEvidenceIcon = progressIcon("FitnessEvidence") {
    moveTo(14f, 3f); horizontalLineTo(6f)
    curveTo(4.6f, 3f, 4f, 3.6f, 4f, 5f); verticalLineTo(19f)
    curveTo(4f, 20.4f, 4.6f, 21f, 6f, 21f); horizontalLineTo(18f)
    curveTo(19.4f, 21f, 20f, 20.4f, 20f, 19f); verticalLineTo(9f)
    lineTo(14f, 3f); verticalLineTo(9f); horizontalLineTo(20f)
    moveTo(8f, 13f); horizontalLineTo(16f)
    moveTo(8f, 17f); horizontalLineTo(13f)
}

internal val FitnessSettingsIcon = progressIcon("FitnessSettings") {
    moveTo(4f, 7f); horizontalLineTo(7f)
    moveTo(11f, 7f); horizontalLineTo(20f)
    moveTo(4f, 17f); horizontalLineTo(13f)
    moveTo(17f, 17f); horizontalLineTo(20f)
    moveTo(7f, 4f); horizontalLineTo(11f); verticalLineTo(10f); horizontalLineTo(7f); close()
    moveTo(13f, 14f); horizontalLineTo(17f); verticalLineTo(20f); horizontalLineTo(13f); close()
}

internal val FitnessPlayIcon = progressIcon("FitnessPlay") {
    moveTo(8f, 4.5f); lineTo(19f, 12f); lineTo(8f, 19.5f); close()
}

internal val FitnessExpandIcon = progressIcon("FitnessExpand") {
    moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
}

internal val FitnessCollapseIcon = progressIcon("FitnessCollapse") {
    moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f)
}

internal val FitnessRefreshIcon = progressIcon("FitnessRefresh") {
    moveTo(20f, 10f)
    curveTo(19f, 5.8f, 15f, 3.2f, 10.8f, 4f)
    curveTo(6.6f, 4.8f, 3.8f, 8.3f, 4f, 12.5f)
    curveTo(4.2f, 16.7f, 7.7f, 20f, 12f, 20f)
    curveTo(15f, 20f, 17.8f, 18.3f, 19.2f, 15.8f)
    moveTo(20f, 4.5f); verticalLineTo(10f); horizontalLineTo(14.5f)
}
