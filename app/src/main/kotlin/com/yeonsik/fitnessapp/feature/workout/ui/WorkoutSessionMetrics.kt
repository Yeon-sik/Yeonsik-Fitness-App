package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    val gap = FitnessSpacing.small
    val row = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
    Column(modifier.testTag("workout-session-metrics"), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(row, horizontalArrangement = Arrangement.spacedBy(gap)) {
            val cell = Modifier.weight(1f).fillMaxHeight()
            WorkoutSessionMetric(
                "운동 시각", formatWorkoutStartDateTime(session.recordedStartAt), cell,
                valueStyle = MaterialTheme.typography.titleSmall
            )
            WorkoutSessionMetric("운동 종목", "${session.exercises.size}개", cell)
        }
        Row(row, horizontalArrangement = Arrangement.spacedBy(gap)) {
            val cell = Modifier.weight(1f).fillMaxHeight()
            WorkoutSessionMetric("진행 세트", "${session.completedSetCount}세트", cell)
            WorkoutSessionMetric("총 볼륨", "${MassFormatter.format(session.totalVolumeKg, MassUnit.KG)} kg", cell)
        }
    }
}

@Composable
private fun WorkoutSessionMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.titleMedium
) {
    FitnessCard(modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(FitnessSpacing.small), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
            Text(
                label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, Modifier.fillMaxWidth(), style = valueStyle.copy(fontFeatureSettings = "tnum"))
        }
    }
}
