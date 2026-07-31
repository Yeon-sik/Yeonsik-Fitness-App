package com.yeonsik.fitnessapp.data;

import com.yeonsik.fitnessapp.exercise.BodyPart;
import com.yeonsik.fitnessapp.exercise.EquipmentType;
import com.yeonsik.fitnessapp.exercise.WeightExercise;

import org.junit.Test;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class FleekCsvImporterTest {
    @Test
    public void parsesSessionsAndMapsConditionalExerciseTypes() throws Exception {
        String csv = "\uFEFFDate,Exercise,Weight(kg),Reps,Duration(s),Distance(m),Rpe,Set type,Grip type\n"
                + "2026-07-31T09:10:00.976Z,바벨 플랫 벤치 프레스,45,8,-,-,8,웜업,노멀\n"
                + "2026-07-31T09:10:00.976Z,풀 업,-,10,-,-,-,일반,노멀\n"
                + "2026-07-31T09:10:00.976Z,어시스티드 머신 풀 업,30,8,-,-,-,보조,노멀\n"
                + "2026-07-30T09:10:00.976Z,푸시 업,-,12,-,-,11,암렙,노멀\n";

        FleekCsvImporter.ImportPlan plan = FleekCsvImporter.parse(
                new StringReader(csv),
                Arrays.asList(
                        master("bench", "바벨 플랫 벤치 프레스", BodyPart.CHEST,
                                EquipmentType.BARBELL, FitnessRecordContract.WEIGHT_REPS),
                        master("pull_up", "풀업", BodyPart.BACK,
                                EquipmentType.BODYWEIGHT, FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS)
                )
        );

        assertEquals(2, plan.sessions.size());
        assertEquals(4, plan.importableSets());
        assertEquals(2, plan.matchedSets);
        assertEquals("2026-07-31", plan.sessions.get(0).date);

        FleekCsvImporter.ExerciseData bench = plan.sessions.get(0).exercises.get(0);
        assertEquals("bench", bench.exerciseId);
        assertEquals(FitnessRecordContract.WEIGHT_REPS, bench.recordType);
        assertEquals(Double.valueOf(45), bench.sets.get(0).weightKg);
        assertEquals(Integer.valueOf(8), bench.sets.get(0).rpe);
        assertEquals("웜업", bench.sets.get(0).setType);

        FleekCsvImporter.ExerciseData pullUp = plan.sessions.get(0).exercises.get(1);
        assertEquals("pull_up", pullUp.exerciseId);
        assertEquals(FitnessRecordContract.REPS_ONLY, pullUp.recordType);
        assertNull(pullUp.sets.get(0).addedWeightKg);

        FleekCsvImporter.ExerciseData assisted = plan.sessions.get(0).exercises.get(2);
        assertEquals("manual", assisted.exerciseId);
        assertEquals(FitnessRecordContract.ASSISTED_WEIGHT_REPS, assisted.recordType);
        assertEquals(Double.valueOf(30), assisted.sets.get(0).assistedWeightKg);

        FleekCsvImporter.ExerciseData pushUp = plan.sessions.get(1).exercises.get(0);
        assertEquals(FitnessRecordContract.REPS_ONLY, pushUp.recordType);
        assertNull(pushUp.sets.get(0).rpe);
    }

    @Test
    public void handlesQuotedExerciseNamesAndEmbeddedCommas() throws Exception {
        String csv = "Date,Exercise,Weight(kg),Reps\r\n"
                + "2026-07-31T09:10:00Z,\"케이블, 시티드 로우\",25,10\r\n";

        FleekCsvImporter.ImportPlan plan = FleekCsvImporter.parse(
                new StringReader(csv),
                Collections.emptyList()
        );

        assertEquals("케이블, 시티드 로우", plan.sessions.get(0).exercises.get(0).name);
        assertEquals(1, plan.importableSets());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFilesWithoutRequiredHeaders() throws Exception {
        FleekCsvImporter.parse(
                new StringReader("Date,Exercise,Weight(kg)\n2026-07-31,스쿼트,50\n"),
                Collections.emptyList()
        );
    }

    @Test
    public void normalizesWhitespaceAndPunctuationForMasterMatching() {
        assertEquals(
                FleekCsvImporter.normalizeExerciseName("풀업"),
                FleekCsvImporter.normalizeExerciseName(" 풀-업 ")
        );
        assertTrue(FleekCsvImporter.normalizeExerciseName("스미스 머신 스쿼트").contains("스미스머신"));
    }

    private static WeightExercise master(
            String id,
            String name,
            BodyPart bodyPart,
            EquipmentType equipment,
            String recordType
    ) {
        return new WeightExercise(
                id,
                name,
                "",
                bodyPart,
                bodyPart.id(),
                bodyPart.labelKo(),
                Collections.emptyList(),
                Collections.emptyList(),
                equipment,
                equipment.labelKo(),
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                recordType,
                "",
                "",
                "",
                ""
        );
    }
}
