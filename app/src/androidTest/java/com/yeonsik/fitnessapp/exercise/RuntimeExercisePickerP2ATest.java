package com.yeonsik.fitnessapp.exercise;

import com.yeonsik.fitnessapp.data.FitnessRecordContract;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** Instrumented coverage for the canonical family-first projection consumed by P2A. */
public final class RuntimeExercisePickerP2ATest {
    @Test
    public void queryAliasAndVariantProjectionRemainFamilyFirst() throws Exception {
        RuntimeExercisePicker picker = new RuntimeExercisePicker(fixture());

        List<RuntimeExercisePicker.FamilyResult> squat = picker.search("스쿼트");
        assertEquals(1, squat.size());
        assertEquals("squat", squat.get(0).family.familyId);
        assertEquals(2, squat.get(0).presets.size());
        assertNull(picker.directPresetForFamily("squat", "스쿼트"));
        assertEquals("barbell_squat", picker.directPresetForFamily("squat", "바벨 스쿼트").presetId);

        RuntimeExercisePreset alias = picker.search("중량 푸쉬업").get(0).presets.get(0);
        assertEquals("push_up", alias.identityId());
        assertEquals("push_up", alias.canonicalPresetId);
    }

    @Test
    public void bodySubPartAndEquipmentFiltersUseCatalogMetadata() throws Exception {
        RuntimeExercisePicker picker = new RuntimeExercisePicker(fixture());
        RuntimeExercisePicker.Filter filter = new RuntimeExercisePicker.Filter(
                "",
                BodyPart.LEGS,
                "quadriceps",
                UiEquipmentCategory.FREE_WEIGHT,
                RuntimeExercisePicker.SortOrder.NAME,
                Collections.emptyMap()
        );

        List<RuntimeExercisePicker.FamilyResult> results = picker.search(filter);
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).presets.size());
        assertEquals("barbell_squat", results.get(0).presets.get(0).presetId);
        assertEquals(UiEquipmentCategory.FREE_WEIGHT,
                results.get(0).presets.get(0).uiEquipmentCategory);
    }

    @Test
    public void recentAndNameSortsAreStableAndEmptyDataIsSafe() throws Exception {
        RuntimeExercisePicker picker = new RuntimeExercisePicker(fixture());
        Map<String, String> recent = new HashMap<>();
        recent.put("push_up", "2026-09-01");
        recent.put("barbell_squat", "2026-09-02");

        List<RuntimeExercisePicker.FamilyResult> recentResults = picker.search(
                new RuntimeExercisePicker.Filter(
                        "", null, null, null, RuntimeExercisePicker.SortOrder.RECENT, recent
                )
        );
        assertEquals("squat", recentResults.get(0).family.familyId);
        assertEquals("2026-09-02", recentResults.get(0).lastPerformedAt);

        List<RuntimeExercisePicker.FamilyResult> noResults = picker.search(
                new RuntimeExercisePicker.Filter(
                        "does-not-exist", null, null, null,
                        RuntimeExercisePicker.SortOrder.NAME, recent
                )
        );
        assertEquals(0, noResults.size());
        assertSame(
                picker.search("바벨 스쿼트").get(0).presets.get(0),
                picker.directPresetForFamily("squat", "바벨 스쿼트")
        );
    }

    private static RuntimeExerciseCatalog fixture() throws Exception {
        JSONObject document = new JSONObject();
        document.put("schemaVersion", 1);
        document.put("allowedLoadStates", array("bodyweight", "external_load", "added_weight"));

        JSONObject families = new JSONObject();
        families.put("squat", family("스쿼트", "Squat", "legs", array("external_load")));
        families.put("push_up", family("푸쉬업", "Push-Up", "chest", array("bodyweight", "added_weight")));
        document.put("families", families);

        JSONArray legacyExercises = new JSONArray();
        legacyExercises.put(legacy(
                "legacy_barbell_squat", "squat", "barbell_squat",
                "바벨 스쿼트", "Barbell Squat", "barbell", "quadriceps"
        ));
        legacyExercises.put(legacy(
                "legacy_dumbbell_squat", "squat", "dumbbell_squat",
                "덤벨 스쿼트", "Dumbbell Squat", "dumbbell", "glutes"
        ));
        legacyExercises.put(legacy(
                "push_up_old", "push_up", "push_up",
                "푸쉬업", "Push-Up", "bodyweight", "overall"
        ));
        document.put("legacyExercises", legacyExercises);

        JSONObject aliasMerge = new JSONObject();
        aliasMerge.put("canonicalPresetId", "push_up");
        aliasMerge.put("familyId", "push_up");
        aliasMerge.put("nameKo", "푸쉬업");
        aliasMerge.put("legacyIds", array("push_up_old"));
        aliasMerge.put("aliases", array("중량 푸쉬업"));
        document.put("canonicalAliasMerges", array(aliasMerge));

        JSONObject searchAlias = new JSONObject();
        searchAlias.put("alias", "중량 푸쉬업");
        searchAlias.put("familyId", "push_up");
        searchAlias.put("targetPreset", "push_up");
        searchAlias.put("defaultLoadState", "added_weight");
        document.put("searchPresetAliases", array(searchAlias));
        return RuntimeExerciseCatalog.fromJson(document);
    }

    private static JSONArray array(Object... values) throws Exception {
        JSONArray result = new JSONArray();
        for (Object value : values) result.put(value);
        return result;
    }

    private static JSONObject family(
            String nameKo,
            String nameEn,
            String part,
            JSONArray allowedLoadStates
    ) throws Exception {
        JSONObject result = new JSONObject();
        result.put("nameKo", nameKo);
        result.put("nameEn", nameEn);
        result.put("defaultUiPart", part);
        result.put("allowedLoadStates", allowedLoadStates);
        return result;
    }

    private static JSONObject legacy(
            String legacyId,
            String familyId,
            String presetId,
            String nameKo,
            String nameEn,
            String equipment,
            String primarySubPart
    ) throws Exception {
        JSONObject result = new JSONObject();
        result.put("status", "mapped");
        result.put("legacyExerciseId", legacyId);
        result.put("familyId", familyId);
        result.put("canonicalPresetId", presetId);
        result.put("presetNameKo", nameKo);
        result.put("presetNameEn", nameEn);
        result.put("nameKo", nameKo);
        result.put("nameEn", nameEn);
        result.put("legacyEquipment", equipment);
        result.put("legacyRecordType", FitnessRecordContract.WEIGHT_REPS);
        result.put("defaultLoadState", "external_load");
        result.put("primarySubPart", primarySubPart);
        result.put("primarySubPartNameKo", primarySubPart);
        result.put("defaultUiPart", familyId.equals("squat") ? "legs" : "chest");
        JSONObject variant = new JSONObject();
        variant.put("equipment", equipment);
        result.put("variant", variant);
        return result;
    }
}
