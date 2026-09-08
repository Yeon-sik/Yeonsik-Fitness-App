package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import com.yeonsik.fitnessapp.core.ui.FitnessFactCard
import com.yeonsik.fitnessapp.core.ui.FitnessFactRow
import com.yeonsik.fitnessapp.core.ui.FitnessHeader
import com.yeonsik.fitnessapp.core.ui.FitnessSection
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.LocalFitnessColors
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.data.MassUnit
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionSnapshot

/** Golden screen: render the existing snapshot and emit only the existing exercise action. */
@Composable
fun WorkoutSessionContent(session: WorkoutSessionSnapshot, unit: MassUnit, onExercise: (String) -> Unit) {
    FitnessHeader("운동 진행", session.title)
    FitnessFactRow(
        first = { FitnessFactCard("완료 세트", session.completedSetCount.toString(), "현재 운동") },
        second = { FitnessFactCard("볼륨", MassFormatter.withUnit(session.totalVolumeKg, unit), "현재 운동") }
    )
    FitnessSection("운동 종목") {
        if (session.exercises.isEmpty()) {
            FitnessCard(Modifier.fillMaxWidth()) {
                Text("종목을 추가해 운동을 기록하세요.", Modifier.padding(FitnessSpacing.card),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        session.exercises.forEach { exercise ->
            val complete = exercise.totalSetCount > 0 && exercise.completedSetCount == exercise.totalSetCount
            FitnessCard(onClick = { onExercise(exercise.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(FitnessSpacing.card),
                    horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text(exercise.recordTypeLabel, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${exercise.completedSetCount}/${exercise.totalSetCount}",
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.SemiBold,
                            color = if (complete) LocalFitnessColors.current.success else MaterialTheme.colorScheme.onSurface)
                        Text(if (complete) "세트 완료" else "세트", style = MaterialTheme.typography.labelMedium,
                            color = if (complete) LocalFitnessColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
