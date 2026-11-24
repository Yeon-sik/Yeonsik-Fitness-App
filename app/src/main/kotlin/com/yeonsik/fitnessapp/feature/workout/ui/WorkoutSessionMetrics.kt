package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.data.MassFormatter

@Composable
internal fun WorkoutSessionMetrics(session: WorkoutSessionSnapshot, modifier: Modifier = Modifier) {
    val gap = FitnessSpacing.gap
    BoxWithConstraints(modifier.testTag("workout-session-metrics")) {
        val cellSize = ((maxWidth - gap) / 2).coerceAtLeast(FitnessSpacing.micro)
        // Keep square cells at the default size and let each row grow together for enlarged text.
        val cell = Modifier.fillMaxHeight().heightIn(min = cellSize)
        val row = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(row, horizontalArrangement = Arrangement.spacedBy(gap)) {
                WorkoutSessionMetric(
                    "운동 시각", formatWorkoutStartDateTime(session.recordedStartAt), Modifier.weight(1f).then(cell),
                    valueStyle = MaterialTheme.typography.titleMedium
                )
                WorkoutSessionMetric("운동 종목", "${session.exercises.size}개", Modifier.weight(1f).then(cell))
            }
            Row(row, horizontalArrangement = Arrangement.spacedBy(gap)) {
                WorkoutSessionMetric("진행 세트", "${session.completedSetCount}세트", Modifier.weight(1f).then(cell))
                WorkoutSessionMetric(
                    "총 볼륨", "${MassFormatter.format(session.totalVolumeKg, MassUnit.KG)} kg", Modifier.weight(1f).then(cell)
                )
            }
        }
    }
}

@Composable
private fun WorkoutSessionMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.headlineSmall
) {
    val padding = FitnessSpacing.gap
    FitnessCard(modifier.semantics(mergeDescendants = true) {}) {
        Text(
            label, Modifier.fillMaxWidth().padding(start = padding, end = padding, top = padding),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            value, Modifier.fillMaxWidth()
                .padding(start = padding, end = padding, top = FitnessSpacing.micro, bottom = padding),
            style = valueStyle.copy(fontFeatureSettings = "tnum")
        )
    }
}
