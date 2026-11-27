package com.yeonsik.fitness.shared.feature.workout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.feature.workout.ui.*

@Composable
fun CompletedWorkoutSummary(
    session: WorkoutSessionSnapshot,
    unit: MassUnit,
    onExercise: (String) -> Unit,
    onRecords: () -> Unit,
    onSaveAsRoutine: (String, List<WorkoutSessionExercise>) -> Unit,
    recordWatch: @Composable () -> Unit = {},
    conditionNote: @Composable () -> Unit = {},
    exerciseImage: @Composable (WorkoutSessionExercise) -> Unit = {
        FitnessStatusBadge(FitnessSemanticStatus.UNKNOWN, "운동 이미지 없음", Modifier.size(72.dp))
    }
) {
    if (session.status != "completed") {
        FitnessStatusMessage(
            status = FitnessSemanticStatus.WARNING,
            title = "완료된 운동만 요약할 수 있습니다",
            message = "진행 중이거나 상태를 확인할 수 없는 기록은 저장된 완료 요약으로 표시하지 않습니다."
        )
        return
    }

    val orderedExercises = stableWorkoutSessionExercises(session.exercises)
    val groupLabels = workoutSessionGroupLabels(orderedExercises)
    var routineName by rememberSaveable(session.recordId) {
        mutableStateOf("${session.title} 루틴")
    }

    Text(session.title, fontWeight = FontWeight.Bold)
    recordWatch()
    FitnessStatusBadge(
        status = FitnessSemanticStatus.SUCCESS,
        label = "완료된 운동 요약",
        modifier = Modifier.fillMaxWidth()
    )
    FitnessFactRow(
        first = {
            FitnessFactCard("완료 세트", session.completedSetCount.toString(), "저장된 세트",
                Modifier.testTag("summary-sets").semantics { contentDescription = "완료 세트 ${session.completedSetCount}" })
        },
        second = {
            val volume = WorkoutDisplayNumbers.withUnit(session.totalVolumeKg, unit)
            FitnessFactCard("총 볼륨", volume, "저장된 완료 기록",
                Modifier.testTag("summary-volume").semantics { contentDescription = "총 볼륨 $volume" })
        }
    )

    FitnessFactRow(
        first = { FitnessFactCard("운동 종목", orderedExercises.size.toString(), "저장된 snapshot") },
        second = { FitnessFactCard("운동 시간", formatWorkoutElapsedSeconds(session.durationSeconds), "기록된 운동 시간") }
    )

    conditionNote()
    FitnessSection("근육/부위 분포") {
        val distribution = workoutSummaryMuscleDistribution(orderedExercises)
        if (distribution.isEmpty()) {
            FitnessStatusMessage(
                status = FitnessSemanticStatus.UNKNOWN,
                title = "분포 데이터 없음",
                message = "완료된 세트에 연결된 운동 부위 정보가 없습니다."
            )
        } else {
            distribution.forEach { group ->
                FitnessProgressBar(
                    fitnessProgressPresentation(
                        ratio = group.fraction.toDouble(),
                        label = "${group.completedSetCount}세트"
                    ),
                    title = group.label
                )
            }
        }
    }

    FitnessSection("운동 종목") {
        if (orderedExercises.isEmpty()) {
            FitnessStatusMessage(
                status = FitnessSemanticStatus.UNKNOWN,
                title = "운동 종목 없음",
                message = "저장된 운동 snapshot에 종목이 없습니다."
            )
        } else {
            orderedExercises.forEach { exercise ->
                FitnessCard(Modifier.fillMaxWidth().clickable { onExercise(exercise.id) }) {
                    Column(
                        Modifier.fillMaxWidth().padding(FitnessSpacing.card * 0.6f),
                        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small * 0.6f)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap * 0.6f),
                            verticalAlignment = Alignment.Top
                        ) {
                            exerciseImage(exercise)
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small * 0.6f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        listOf(groupLabels[exercise.id].orEmpty(), exercise.name).filter { it.isNotBlank() }.joinToString(" · "),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "총 볼륨",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            WorkoutDisplayNumbers.withUnit(exercise.totalVolumeKg, unit),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Text(
                                    listOfNotNull(
                                        exercise.uiPart.takeIf { it.isNotBlank() },
                                        exercise.primarySubPart?.takeIf { it.isNotBlank() },
                                        exercise.equipment.takeIf { it.isNotBlank() },
                                        exercise.recordTypeLabel.takeIf { it.isNotBlank() }
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.micro),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.semantics {
                                        contentDescription =
                                            "완료 세트 ${exercise.completedSetCount}/${exercise.totalSetCount}, 완료 상태"
                                    }
                                ) {
                                    Text(
                                        FitnessSemanticStatus.SUCCESS.glyph(),
                                        color = LocalFitnessColors.current.success,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        "완료 세트 ${exercise.completedSetCount}/${exercise.totalSetCount}",
                                        color = LocalFitnessColors.current.success,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        WorkoutExerciseSetTable(
                            sets = exercise.completedSets,
                            recordType = exercise.recordType,
                            unit = unit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    WorkoutRoutineComparisonCard(session, unit)

    FitnessSection("볼륨 추세") {
        val trendPoints = session.recentVolumes.map {
            FitnessTrendPoint(
                label = it.label.ifBlank { it.date },
                value = MassUnit.fromKg(it.volumeKg, unit),
                detailLabel = it.date
            )
        } + FitnessTrendPoint(
            label = session.title.ifBlank { "현재" },
            value = MassUnit.fromKg(session.totalVolumeKg, unit)
        )
        FitnessTrendChart(
            model = fitnessTrendPresentation(trendPoints, minimumPoints = 2),
            unit = unit.symbol(),
            emptyLabel = "완료 운동 볼륨이 없습니다.",
            insufficientLabel = "비교할 완료 운동 볼륨 기록이 부족합니다."
        )
    }

    FitnessStatusMessage(
        status = FitnessSemanticStatus.INFO,
        title = "저장된 운동 snapshot",
        message = "이 요약은 완료 시 저장된 종목·세트·볼륨 snapshot으로 재구성됩니다. 이후 운동 마스터 변경은 과거 요약에 반영되지 않습니다."
    )

    FitnessSection("루틴으로 저장") {
        FitnessTextField(
            value = routineName,
            onValueChange = { routineName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("새 루틴 이름") }
        )
        FitnessButton(
            onClick = { onSaveAsRoutine(routineName, orderedExercises) },
            enabled = routineName.isNotBlank() && orderedExercises.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("이 운동을 루틴으로 저장") }
    }

    FitnessOutlinedButton(onClick = onRecords, modifier = Modifier.fillMaxWidth().testTag("summary-records")) {
        Text("기록에서 보기")
    }
}
