package com.yeonsik.fitnessapp.feature.workout.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.exercise.model.EquipmentType
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutCompletion
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetInput
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutSessionUiState
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutSummaryScreen
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog
import com.yeonsik.fitnessapp.exercise.ExerciseMasterAdapter
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Reopen a private synthetic Room file at each stop in the single-exercise workout. */
class OrdinaryWorkoutParityTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = "ordinary-parity-${UUID.randomUUID()}.db"
    private val owner = AccountScope("ordinary-set-fixture")

    private fun <T> reopen(block: (WorkoutRepositoryImplementation) -> T): T {
        val room = Room.databaseBuilder(context, FitnessRoomDatabase::class.java, database).build()
        return try { block(WorkoutRepositoryImplementation(room, context)) } finally { room.close() }
    }

    @Test fun firstSetKeepsItsIdentityAndInputThroughEditDeletionResumeAndHistory() {
        val preset = requireNotNull(ExerciseFamilyCatalog.load(context).runtimeCatalog().preset("chest_barbell_flat_bench_press"))
        val draft = ExerciseMasterAdapter.toRoutineExerciseDraft(preset)
        val bench = RoutineExerciseInstance("bench", draft.exerciseId, draft.nameKo,
            requireNotNull(BodyPart.fromId(draft.bodyPartId)).labelKo, draft.primarySubPart.orEmpty(),
            requireNotNull(EquipmentType.fromId(draft.equipmentVariantId)).labelKo,
            requireNotNull(draft.recordType), 1, draft.familyIdentity)
        val original = WorkoutSetInput(43.09127515, 8, null, null, null, null, 3, null, true,
            LoadState.EXTERNAL_LOAD, 95.0, MassUnit.LB)
        val edited = original.copy(weightKg = 45.359237, inputLoadValue = 100.0, reps = 10, rir = 2)
        try {
            val (record, exercise, firstSet) = reopen { repository ->
                val record = repository.createSessionFromRoutine(owner, "2026-11-27", "ParityBench", "routine", listOf(bench))
                val exercise = requireNotNull(repository.loadSession(owner, record)).exercises.single().id
                repository.ensureInitialSet(owner, record, exercise)
                val firstSet = requireNotNull(repository.loadExerciseDetail(owner, record, exercise)).sets.single().id
                assertTrue(repository.updateTypedSet(owner, record, firstSet, original))
                Triple(record, exercise, firstSet)
            }
            reopen { repository ->
                assertEquals(record, repository.latestInProgressSession(owner))
                val restored = requireNotNull(repository.loadExerciseDetail(owner, record, exercise)).sets.single()
                assertEquals(firstSet, restored.id)
                assertEquals(43.09127515, restored.weightKg, 1e-10)
                assertTrue(repository.updateTypedSet(owner, record, firstSet, edited))
                assertTrue(repository.updateTypedSet(owner, record, firstSet, edited))
                assertTrue(repository.addTypedSet(owner, record, exercise, 2,
                    original.copy(weightKg = 22.6796185, inputLoadValue = 50.0, reps = 6, rir = 1)))
                val rows = requireNotNull(repository.loadExerciseDetail(owner, record, exercise)).sets
                assertEquals(listOf(1, 2), rows.map { it.setIndex })
                assertFalse(repository.deleteSet(owner, record, firstSet))
                assertTrue(repository.deleteSet(owner, record, rows.last().id))
            }
            reopen { repository ->
                assertEquals(record, repository.latestInProgressSession(owner))
                assertEquals(WorkoutCompletion.COMPLETED, repository.completeIfEligible(owner, record))
            }
            reopen { repository ->
                assertNull(repository.latestInProgressSession(owner))
                val session = requireNotNull(repository.loadSession(owner, record))
                assertEquals("completed", session.status)
                assertEquals(1, session.completedSetCount)
                assertEquals(453.59237, session.totalVolumeKg, 1e-8)
                val history = requireNotNull(repository.loadRoutineExerciseHistory(owner, bench))
                assertEquals(record, history.recordId)
                assertEquals(exercise, history.activeExercise.id)
                val saved = history.sets.single()
                assertEquals(firstSet, saved.id)
                assertEquals(MassUnit.LB, saved.inputLoadUnit)
                assertEquals(100.0, requireNotNull(saved.inputLoadValue), 0.0)
                assertEquals(45.359237, saved.weightKg, 1e-10)
                assertEquals(10, saved.actualReps)
                assertEquals(2, saved.rir)
                var recordsOpened = false
                compose.setContent {
                    FitnessComposeTheme(false) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            WorkoutSummaryScreen(WorkoutSessionUiState.Ready(owner.ownerId, session), owner.ownerId,
                                MassUnit.LB, onBack = {}, onExercise = {}, onRecords = { recordsOpened = true },
                                recordWatch = {})
                        }
                    }
                }
                compose.onNodeWithText("운동 요약").assertExists()
                compose.onAllNodesWithText("1,000lb", useUnmergedTree = true).onFirst().assertExists()
                if (InstrumentationRegistry.getArguments().getString("captureSummary") == "true") {
                    compose.waitForIdle()
                    context.cacheDir.resolve("ordinary-summary.png").outputStream().use {
                        compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } else {
                    compose.onNodeWithTag("summary-sets").assertContentDescriptionEquals("완료 세트 1")
                    compose.onNodeWithTag("summary-volume").assertContentDescriptionEquals("총 볼륨 1,000lb")
                }
                compose.onNodeWithText("기록에서 보기").performScrollTo().performClick()
                compose.runOnIdle { assertTrue(recordsOpened) }
                // The CI comparator reads only this synthetic database's completed record.
                Log.i("WorkoutParity", "FITNESS_PARITY " + JSONObject().apply {
                    put("title", session.title)
                    put("exercise", history.activeExercise.name)
                    val exercise = session.exercises.single()
                    put("catalogId", exercise.exerciseId)
                    put("primaryPart", exercise.primarySubPart)
                    put("equipment", exercise.equipment)
                    put("status", session.status)
                    put("completedSets", session.completedSetCount)
                    put("setIndex", saved.setIndex)
                    put("inputWeight", saved.inputLoadValue)
                    put("unit", saved.inputLoadUnit?.id)
                    put("weightKg", saved.weightKg)
                    put("reps", saved.actualReps)
                    put("rir", saved.rir)
                    put("volumeKg", session.totalVolumeKg)
                })
            }
        } finally {
            context.deleteDatabase(database)
        }
    }
}
