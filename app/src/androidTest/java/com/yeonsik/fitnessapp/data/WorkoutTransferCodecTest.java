package com.yeonsik.fitnessapp.data;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class WorkoutTransferCodecTest {
    @Test
    public void v2RoundTripsMixedUnitsWithinOneExercise() {
        WorkoutTransferCodec.Document decoded = WorkoutTransferCodec.decode(mixedV2Json());
        WorkoutTransferCodec.SetData first = decoded.sessions.get(0).exercises.get(0).sets.get(0);
        WorkoutTransferCodec.SetData second = decoded.sessions.get(0).exercises.get(0).sets.get(1);
        assertEquals(65d, first.weightKg, 0d);
        assertEquals(65d, first.inputLoadValue, 0d);
        assertEquals(MassUnit.KG.id(), first.inputLoadUnit);
        assertEquals(225d, second.inputLoadValue, 0d);
        assertEquals(MassUnit.LB.id(), second.inputLoadUnit);
        assertEquals(MassUnit.toKg(225d, MassUnit.LB), second.weightKg, 0d);
    }

    @Test
    public void v1ImportDoesNotInventInputProvenance() {
        WorkoutTransferCodec.Document decoded = WorkoutTransferCodec.decode(v1Json());
        WorkoutTransferCodec.SetData decodedSet = decoded.sessions.get(0).exercises.get(0).sets.get(0);
        assertNull(decodedSet.inputLoadValue);
        assertNull(decodedSet.inputLoadUnit);
    }

    @Test
    public void exportIncludesV2ProvenanceFields() {
        WorkoutTransferCodec.SetData set = new WorkoutTransferCodec.SetData(
                1,
                1,
                1,
                80d,
                80d,
                null,
                null,
                null,
                null,
                null,
                "external_load",
                true,
                null,
                null,
                null,
                80d,
                "kg"
        );
        WorkoutTransferCodec.Document document = new WorkoutTransferCodec.Document(
                WorkoutTransferCodec.V2,
                "test.app",
                "2026-09-05T00:00:00Z",
                java.util.Collections.singletonList(new WorkoutTransferCodec.Session(
                        "fitness",
                        "source-record-1",
                        "2026-09-05",
                        "Export",
                        "strength",
                        "가슴",
                        null,
                        null,
                        null,
                        java.util.Collections.singletonList(new WorkoutTransferCodec.Exercise(
                                "chest_dumbbell_decline_bench_press",
                                null,
                                null,
                                "덤벨 디클라인 벤치프레스",
                                1,
                                "weight_reps",
                                "가슴",
                                "덤벨",
                                java.util.Collections.singletonList(set)
                        ))
                ))
        );
        String json = WorkoutTransferCodec.encode(document);
        assertTrue(json.contains("\"formatVersion\":2"));
        assertTrue(json.contains("\"inputLoadValue\":80"));
        assertTrue(json.contains("\"inputLoadUnit\":\"kg\""));
    }

    @Test
    public void decodesActualLegacyWorkoutsFixtureWithoutDate() {
        WorkoutTransferCodec.Document decoded =
                WorkoutTransferCodec.decode(actualLegacyV1Json());
        WorkoutTransferCodec.Session session = decoded.sessions.get(0);
        WorkoutTransferCodec.Exercise exercise = session.exercises.get(0);
        WorkoutTransferCodec.SetData set = exercise.sets.get(0);

        assertEquals(WorkoutTransferCodec.V1, decoded.formatVersion);
        assertEquals("legacy.friend.fitness", session.sourceApp);
        assertEquals("2026-09-05", session.date);
        assertEquals("2026-09-05T10:15:55.057Z", session.startedAt);
        assertEquals("2026-09-05T11:00:55.057Z", session.endedAt);
        assertEquals("legacy memo", session.memo);
        assertEquals("chest_dumbbell_decline_bench_press", exercise.exerciseId);
        assertEquals("chest_dumbbell_decline_bench_press", exercise.presetId);
        assertEquals("덤벨 디클라인 벤치프레스", exercise.exerciseName);
        assertEquals("chest", exercise.uiPart);
        assertEquals("legacy-set-1", set.sourceSetId);
        assertEquals(8, set.actualReps.intValue());
        assertEquals(80d, set.weightKg, 0d);
        assertTrue(set.isCompleted);
        assertNull(set.inputLoadValue);
        assertNull(set.inputLoadUnit);
    }

    @Test
    public void canonicalV2ExportUsesLegacyContractNames() throws Exception {
        String json = WorkoutTransferCodec.encode(
                WorkoutTransferCodec.decode(legacyShapedMixedV2Json())
        );
        JSONObject root = new JSONObject(json);
        JSONObject workout = root.getJSONArray("workouts").getJSONObject(0);
        JSONObject exercise = workout.getJSONArray("exercises").getJSONObject(0);
        JSONObject set = exercise.getJSONArray("sets").getJSONObject(1);

        assertTrue(root.has("workouts"));
        assertFalse(root.has("sessions"));
        assertTrue(workout.has("sourceRecordId"));
        assertTrue(workout.has("status"));
        assertTrue(workout.has("title"));
        assertTrue(workout.has("startedAt"));
        assertTrue(workout.has("endedAt"));
        assertTrue(workout.has("memo"));
        assertFalse(workout.has("date"));
        assertTrue(exercise.has("storageExerciseId"));
        assertTrue(exercise.has("presetId"));
        assertTrue(exercise.has("canonicalPresetId"));
        assertTrue(exercise.has("nameSnapshot"));
        assertTrue(exercise.has("defaultUiPart"));
        assertTrue(exercise.has("equipmentSnapshot"));
        assertFalse(exercise.has("exerciseId"));
        assertTrue(set.has("reps"));
        assertTrue(set.has("completed"));
        assertTrue(set.has("inputLoadValue"));
        assertTrue(set.has("inputLoadUnit"));
        assertFalse(set.has("actualReps"));
        assertFalse(set.has("isCompleted"));
    }

    @Test
    public void rejectsConflictingAliasValuesAndTimezoneLessFallback() {
        expectIllegalArgument(() -> WorkoutTransferCodec.decode(
                mixedV2Json().replace(
                        "\"actualReps\":1",
                        "\"actualReps\":1,\"reps\":2"
                )
        ));
        expectIllegalArgument(() -> WorkoutTransferCodec.decode(
                actualLegacyV1Json().replace(
                        "\"startedAt\":\"2026-09-05T10:15:55.057Z\"",
                        "\"startedAt\":\"2026-09-05T10:15:55.057\""
                )
        ));
    }

    @Test
    public void rejectsPartialOrMismatchedV2Provenance() {
        expectIllegalArgument(() -> WorkoutTransferCodec.decode(v2JsonWithProvenance(
                "\"inputLoadValue\":80"
        )));
        expectIllegalArgument(() -> WorkoutTransferCodec.decode(v2JsonWithProvenance(
                "\"inputLoadValue\":80,\"inputLoadUnit\":\"lb\""
        )));
    }

    private static String mixedV2Json() {
        return "{\"format\":\"yeonsik.workout-transfer\",\"formatVersion\":2,"
                + "\"sourceApp\":\"test.app\",\"sessions\":[{"
                + "\"sourceApp\":\"fitness\",\"sourceRecordId\":\"source-record-1\","
                + "\"date\":\"2026-09-05\",\"exercises\":[{"
                + "\"exerciseId\":\"chest_dumbbell_decline_bench_press\","
                + "\"orderIndex\":1,\"recordType\":\"weight_reps\",\"sets\":["
                + "{\"setIndex\":1,\"actualReps\":1,\"weightKg\":65,\"volumeKg\":65,"
                + "\"loadState\":\"external_load\",\"isCompleted\":true,"
                + "\"inputLoadValue\":65,\"inputLoadUnit\":\"kg\"},"
                + "{\"setIndex\":2,\"actualReps\":1,\"weightKg\":102.05828325,"
                + "\"volumeKg\":102.05828325,\"loadState\":\"external_load\","
                + "\"isCompleted\":true,\"inputLoadValue\":225,"
                + "\"inputLoadUnit\":\"lb\"}]}]}]}";
    }

    private static String v1Json() {
        return "{\"format\":\"yeonsik.workout-transfer\",\"formatVersion\":1,"
                + "\"sourceApp\":\"test.app\",\"sessions\":[{"
                + "\"sourceApp\":\"fitness\",\"sourceRecordId\":\"source-record-1\","
                + "\"date\":\"2026-09-05\",\"exercises\":[{"
                + "\"exerciseId\":\"chest_dumbbell_decline_bench_press\","
                + "\"orderIndex\":1,\"recordType\":\"weight_reps\",\"sets\":["
                + "{\"setIndex\":1,\"actualReps\":1,\"weightKg\":80,\"volumeKg\":80,"
                + "\"loadState\":\"external_load\",\"isCompleted\":true}]}]}]}";
    }

    private static String actualLegacyV1Json() {
        return "{\"format\":\"yeonsik.workout-transfer\",\"formatVersion\":1,"
                + "\"sourceApp\":\"legacy.friend.fitness\","
                + "\"exportedAt\":\"2026-09-05T12:00:00Z\",\"workouts\":[{"
                + "\"sourceRecordId\":\"legacy-workout-1\",\"status\":\"completed\","
                + "\"title\":\"Legacy workout\","
                + "\"startedAt\":\"2026-09-05T10:15:55.057Z\","
                + "\"endedAt\":\"2026-09-05T11:00:55.057Z\","
                + "\"memo\":\"legacy memo\",\"exercises\":[{"
                + "\"storageExerciseId\":\"chest_dumbbell_decline_bench_press\","
                + "\"presetId\":\"chest_dumbbell_decline_bench_press\","
                + "\"canonicalPresetId\":\"chest_dumbbell_decline_bench_press\","
                + "\"nameSnapshot\":\"덤벨 디클라인 벤치프레스\","
                + "\"defaultUiPart\":\"chest\",\"equipmentSnapshot\":\"덤벨\","
                + "\"recordType\":\"weight_reps\",\"orderIndex\":1,\"sets\":[{"
                + "\"sourceSetId\":\"legacy-set-1\",\"setIndex\":1,\"weightKg\":80,"
                + "\"reps\":8,\"restSeconds\":90,\"loadState\":\"external_load\","
                + "\"rir\":2,\"completed\":true}]}]}]}";
    }

    private static String legacyShapedMixedV2Json() {
        return "{\"format\":\"yeonsik.workout-transfer\",\"formatVersion\":2,"
                + "\"sourceApp\":\"legacy.friend.fitness\","
                + "\"exportedAt\":\"2026-09-05T12:00:00Z\",\"workouts\":[{"
                + "\"sourceRecordId\":\"mixed-workout-1\",\"status\":\"completed\","
                + "\"title\":\"Mixed units\","
                + "\"startedAt\":\"2026-09-05T10:15:55.057Z\","
                + "\"endedAt\":\"2026-09-05T11:00:55.057Z\",\"memo\":null,"
                + "\"exercises\":[{"
                + "\"storageExerciseId\":\"chest_dumbbell_decline_bench_press\","
                + "\"presetId\":\"chest_dumbbell_decline_bench_press\","
                + "\"canonicalPresetId\":\"chest_dumbbell_decline_bench_press\","
                + "\"nameSnapshot\":\"덤벨 디클라인 벤치프레스\","
                + "\"defaultUiPart\":\"chest\",\"equipmentSnapshot\":\"덤벨\","
                + "\"recordType\":\"weight_reps\",\"orderIndex\":1,\"sets\":["
                + "{\"sourceSetId\":\"mixed-set-1\",\"setIndex\":1,\"weightKg\":60,"
                + "\"reps\":8,\"loadState\":\"external_load\",\"completed\":true,"
                + "\"inputLoadValue\":60,\"inputLoadUnit\":\"kg\"},"
                + "{\"sourceSetId\":\"mixed-set-2\",\"setIndex\":2,"
                + "\"weightKg\":63.5029318,\"reps\":8,\"loadState\":\"external_load\","
                + "\"completed\":true,\"inputLoadValue\":140,\"inputLoadUnit\":\"lb\"},"
                + "{\"sourceSetId\":\"mixed-set-3\",\"setIndex\":3,\"weightKg\":65,"
                + "\"reps\":8,\"loadState\":\"external_load\",\"completed\":true,"
                + "\"inputLoadValue\":65,\"inputLoadUnit\":\"kg\"}"
                + "]}]}]}";
    }

    private static String v2JsonWithProvenance(String provenance) {
        return "{\"format\":\"yeonsik.workout-transfer\",\"formatVersion\":2,"
                + "\"sourceApp\":\"test.app\",\"sessions\":[{"
                + "\"sourceApp\":\"fitness\",\"sourceRecordId\":\"source-record-1\","
                + "\"date\":\"2026-09-05\",\"exercises\":[{"
                + "\"exerciseId\":\"chest_dumbbell_decline_bench_press\","
                + "\"orderIndex\":1,\"recordType\":\"weight_reps\",\"sets\":[{"
                + "\"setIndex\":1,\"actualReps\":1,\"weightKg\":80,\"volumeKg\":80,"
                + "\"loadState\":\"external_load\",\"isCompleted\":true," + provenance
                + "}]}]}]}";
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }
}
