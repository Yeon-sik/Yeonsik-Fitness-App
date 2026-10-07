package com.yeonsik.fitnessapp.feature.workout.model

import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.exercise.model.EquipmentType
import com.yeonsik.fitness.shared.feature.workout.model.ManualWorkoutExercise
import org.junit.Assert.*
import org.junit.Test

class ManualWorkoutExerciseTest {
    @Test fun draftCarriesOnlyExplicitSnapshotFieldsAndNeverInventsCanonicalIdentity() {
        ManualWorkoutExercise.RECORD_TYPES.forEach { recordType ->
            val snapshot = ManualWorkoutExercise("  나만의 운동  ", BodyPart.LEGS,
                EquipmentType.KETTLEBELL, recordType).toReplacement()
            assertEquals("manual", snapshot.masterExerciseId)
            assertEquals("나만의 운동", snapshot.nameKo)
            assertEquals(BodyPart.LEGS, snapshot.bodyPart)
            assertEquals(EquipmentType.KETTLEBELL, snapshot.equipmentType)
            assertEquals(recordType, snapshot.recordType)
            assertNull(snapshot.familyIdentity)
        }
    }

    @Test fun incompleteOrUnknownInputFailsBeforeStorage() {
        for ((name, type) in listOf(" " to "weight_reps", "운동" to "unknown")) {
            assertThrows(IllegalArgumentException::class.java) {
                ManualWorkoutExercise(name, BodyPart.ARMS, EquipmentType.OTHER, type)
            }
        }
    }
}
