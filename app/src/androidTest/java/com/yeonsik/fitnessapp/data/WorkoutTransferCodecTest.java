package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
