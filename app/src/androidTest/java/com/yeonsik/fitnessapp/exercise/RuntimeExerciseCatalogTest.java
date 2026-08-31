package com.yeonsik.fitnessapp.exercise;

import com.yeonsik.fitnessapp.data.FitnessRecordContract;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class RuntimeExerciseCatalogTest {
    @Test
    public void collapsesLegacyAliasesIntoOneCanonicalPreset() throws Exception {
        RuntimeExerciseCatalog catalog = fixture();

        assertEquals(3, catalog.presetCount());
        RuntimeExercisePreset merged = catalog.presetForLegacyId("push_up_old");
        assertNotNull(merged);
        assertSame(merged, catalog.presetForLegacyId("push_up_weighted"));
        assertEquals("push_up", merged.presetId);
        assertEquals("push_up_old", merged.storageExerciseId);
        assertEquals("푸쉬업", merged.nameKo);
        assertEquals(LoadState.ADDED_WEIGHT,
                merged.searchAliasLoadState("중량 푸쉬업"));
        assertSame(merged, catalog.presetForStorageExerciseId("push_up"));
    }

    @Test
    public void appliesCanonicalDisplayNamePriority() {
        ExerciseFamilyIdentity canonical = new ExerciseFamilyIdentity(
                "legacy", "family", "preset", "preset", "표준 이름", "Canonical Name",
                "기존 이름", "Legacy Name", "chest", "variant", "variant", null,
                "bodyweight", FitnessRecordContract.REPS_ONLY, null
        );
        assertEquals("표준 이름", canonical.displayName());

        ExerciseFamilyIdentity legacyOnly = new ExerciseFamilyIdentity(
                "legacy", "family", "preset", "preset", null, "Canonical Name",
                "기존 이름", "Legacy Name", "chest", "variant", "variant", null,
                "bodyweight", FitnessRecordContract.REPS_ONLY, null
        );
        assertEquals("기존 이름", legacyOnly.displayName());
    }

    @Test
    public void searchesFamilyOnceAndOpensOnlyMatchingPresetVariants() throws Exception {
        RuntimeExercisePicker picker = new RuntimeExercisePicker(fixture());

        List<RuntimeExercisePicker.FamilyResult> familyResults = picker.search("스쿼트");
        assertEquals(1, familyResults.size());
        assertEquals("squat", familyResults.get(0).family.familyId);
        assertEquals(2, familyResults.get(0).presets.size());
        assertEquals(2, picker.presetsForFamily("squat", "스쿼트").size());
        assertEquals("barbell_squat",
                picker.directPresetForFamily("squat", "바벨 스쿼트").presetId);
    }

    @Test
    public void appliesAndFiltersUiEquipmentWithoutChangingIdentity() throws Exception {
        RuntimeExercisePicker picker = new RuntimeExercisePicker(fixture());
        RuntimeExercisePicker.Filter filter = new RuntimeExercisePicker.Filter(
                "", BodyPart.LEGS, null, UiEquipmentCategory.FREE_WEIGHT,
                RuntimeExercisePicker.SortOrder.NAME,
                Collections.emptyMap()
        );

        List<RuntimeExercisePicker.FamilyResult> results = picker.search(filter);
        assertEquals(1, results.size());
        assertEquals(2, results.get(0).presets.size());
        assertEquals(UiEquipmentCategory.FREE_WEIGHT,
                results.get(0).presets.get(0).uiEquipmentCategory);
        assertEquals("squat", results.get(0).family.familyId);
    }

    @Test
    public void ranksRecentHistoryAtFamilyLevel() throws Exception {
        RuntimeExercisePicker picker = new RuntimeExercisePicker(fixture());
        Map<String, String> recent = new HashMap<>();
        recent.put("push_up", "2026-08-30");
        recent.put("barbell_squat", "2026-08-29");

        List<RuntimeExercisePicker.FamilyResult> recentResults = picker.search(
                new RuntimeExercisePicker.Filter(
                        "", null, null, null, RuntimeExercisePicker.SortOrder.RECENT,
                        recent
                )
        );
        assertEquals("push_up", recentResults.get(0).family.familyId);
        assertEquals("2026-08-30", recentResults.get(0).lastPerformedAt);
    }

    @Test
    public void loadsGeneratedProjectionWithoutDroppingLegacyOrApprovedPresets() {
        ExerciseFamilyCatalog catalog = ExerciseFamilyCatalog.load(
                ApplicationProvider.getApplicationContext()
        );
        RuntimeExerciseCatalog runtime = catalog.runtimeCatalog();

        assertEquals(103, runtime.familyCount());
        assertEquals(340, catalog.size());
        assertEquals(340, runtime.presetsByLegacyId.size());
        assertEquals(363, runtime.presetCount());

        Set<String> approvedIds = new HashSet<>(Arrays.asList(
                "knee_push_up",
                "trap_bar_deadlift_high_handles",
                "trap_bar_deadlift_low_handles",
                "lateral_lunge",
                "cossack_squat",
                "wall_tibialis_raise",
                "machine_tibialis_raise",
                "bird_dog",
                "copenhagen_plank",
                "barbell_seal_row",
                "ring_row",
                "ring_pull_up",
                "ring_dip",
                "dumbbell_suitcase_carry",
                "kettlebell_suitcase_carry",
                "reverse_nordic_curl",
                "box_jump",
                "bar_muscle_up",
                "ring_muscle_up",
                "dragon_flag",
                "parallel_bar_l_sit",
                "floor_l_sit",
                "hanging_windshield_wiper",
                "barbell_clean",
                "barbell_power_clean",
                "barbell_snatch",
                "barbell_power_snatch"
        ));
        int approvedCount = 0;
        for (RuntimeExercisePreset preset : runtime.presetsById.values()) {
            if (preset.approvedNewPreset) {
                approvedCount += 1;
                assertTrue(approvedIds.contains(preset.presetId));
                assertNotNull(preset.familyId);
                assertNotNull(preset.canonicalVariantKey);
                assertNotNull(preset.visualVariantKey);
                assertNotNull(preset.recordType);
                assertNotNull(preset.defaultLoadState);
            }
        }
        assertEquals(approvedIds.size(), approvedCount);
        for (String approvedId : approvedIds) {
            RuntimeExercisePreset approved = runtime.presetsById.get(approvedId);
            assertNotNull(approved);
            assertTrue(approved.approvedNewPreset);
        }
    }

    @Test
    public void keepsCanonicalAliasesAndWeightedSearchShortcutsInOneFamily() {
        ExerciseFamilyCatalog catalog = ExerciseFamilyCatalog.load(
                ApplicationProvider.getApplicationContext()
        );
        RuntimeExerciseCatalog runtime = catalog.runtimeCatalog();
        RuntimeExercisePreset pecDeck = runtime.presetForLegacyId(
                "chest_machine_pec_deck_fly"
        );
        assertNotNull(pecDeck);
        assertSame(pecDeck, runtime.presetForLegacyId("chest_machine_chest_fly"));
        assertEquals("machine_chest_fly", pecDeck.presetId);
        assertEquals("머신 체스트 플라이", pecDeck.displayName());

        RuntimeExercisePicker picker = new RuntimeExercisePicker(runtime);
        assertSinglePresetSearch(picker, "펙덱 플라이", "chest_fly", "machine_chest_fly");
        assertSinglePresetSearch(
                picker, "중량 푸쉬업", "push_up", "chest_bodyweight_weighted_push_up"
        );
        assertSinglePresetSearch(
                picker, "중량 풀업", "pull_up", "back_bodyweight_pull_up"
        );
        assertSinglePresetSearch(
                picker, "중량 친업", "pull_up", "back_bodyweight_chin_up"
        );
    }

    @Test
    public void resolvesExactVisualVariantThenFamilyDefaultForExistingImages() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        ExerciseFamilyCatalog catalog = ExerciseFamilyCatalog.load(context);
        ExerciseFamilyIdentity pullUp = catalog.identityForLegacyId("back_bodyweight_pull_up");
        ExerciseFamilyIdentity chinUp = catalog.identityForLegacyId("back_bodyweight_chin_up");
        assertNotNull(pullUp);
        assertNotNull(chinUp);
        assertNotNull(catalog.imageVariantFor(pullUp));
        assertNotNull(catalog.familyDefaultFor("pull_up"));
        assertEquals(
                "exact_visual_variant",
                ExerciseIllustrationLookup.resolve(context, pullUp).source
        );
        assertEquals(
                "family_default",
                ExerciseIllustrationLookup.resolve(context, chinUp).source
        );
    }

    @Test
    public void exactVariantLookupDoesNotUseFamilyDefault() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        ExerciseFamilyCatalog catalog = ExerciseFamilyCatalog.load(context);
        ExerciseFamilyIdentity pullUp = catalog.identityForLegacyId("back_bodyweight_pull_up");
        ExerciseFamilyIdentity chinUp = catalog.identityForLegacyId("back_bodyweight_chin_up");
        assertNotNull(pullUp);
        assertNotNull(chinUp);
        assertEquals(
                "exact_visual_variant",
                ExerciseIllustrationLookup.resolveExact(context, pullUp).source
        );
        assertTrue(ExerciseIllustrationLookup.resolveExact(context, chinUp).isPlaceholder());
    }

    @Test
    public void resolvesFamilyFallbackForApprovedPresetWithoutLegacyId() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        ExerciseFamilyCatalog catalog = ExerciseFamilyCatalog.load(context);
        RuntimeExercisePreset preset = catalog.runtimeCatalog().preset("ring_pull_up");
        assertNotNull(preset);
        assertTrue(preset.approvedNewPreset);
        assertTrue(preset.legacyIds.isEmpty());
        assertNull(catalog.identityForLegacyId(preset.storageExerciseId));

        ExerciseFamilyIdentity identity = catalog.identityForStorageExerciseId(
                preset.storageExerciseId
        );
        assertNotNull(identity);
        assertEquals("pull_up", identity.familyId);
        assertEquals(
                "family_default",
                ExerciseIllustrationLookup.resolve(context, preset.storageExerciseId).source
        );
        assertEquals(
                "family_default",
                ExerciseIllustrationLookup.resolve(context, identity).source
        );
    }

    private static void assertSinglePresetSearch(
            RuntimeExercisePicker picker,
            String query,
            String familyId,
            String presetId
    ) {
        List<RuntimeExercisePicker.FamilyResult> results = picker.search(query);
        assertEquals(1, results.size());
        assertEquals(familyId, results.get(0).family.familyId);
        assertEquals(1, results.get(0).presets.size());
        assertEquals(presetId, results.get(0).presets.get(0).presetId);
    }

    private static RuntimeExerciseCatalog fixture() throws Exception {
        JSONObject document = new JSONObject();
        document.put("schemaVersion", 1);
        document.put("allowedLoadStates", array(
                "bodyweight", "external_load", "added_weight", "assisted",
                "band_assisted", "band_resisted"
        ));

        JSONObject families = new JSONObject();
        families.put("squat", family(
                "스쿼트", "Squat", "legs", array("external_load")
        ));
        families.put("push_up", family(
                "푸쉬업", "Push-Up", "chest",
                array("bodyweight", "added_weight", "band_resisted")
        ));
        document.put("families", families);

        JSONArray legacyExercises = new JSONArray();
        legacyExercises.put(legacy(
                "legacy_barbell_squat", "squat", "barbell_squat",
                "바벨 스쿼트", "Barbell Squat", "barbell", "legs"
        ));
        legacyExercises.put(legacy(
                "legacy_dumbbell_squat", "squat", "dumbbell_squat",
                "덤벨 스쿼트", "Dumbbell Squat", "dumbbell", "legs"
        ));
        legacyExercises.put(legacy(
                "push_up_old", "push_up", "push_up",
                "푸쉬업", "Push-Up", "bodyweight", "chest"
        ));
        legacyExercises.put(legacy(
                "push_up_weighted", "push_up", "push_up",
                "중량 푸쉬업", "Weighted Push-Up", "bodyweight", "chest"
        ));
        document.put("legacyExercises", legacyExercises);

        JSONObject aliasMerge = new JSONObject();
        aliasMerge.put("canonicalPresetId", "push_up");
        aliasMerge.put("familyId", "push_up");
        aliasMerge.put("nameKo", "푸쉬업");
        aliasMerge.put("legacyIds", array("push_up_old", "push_up_weighted"));
        aliasMerge.put("aliases", array("푸쉬업 변형"));
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
        for (Object value : values) {
            result.put(value);
        }
        return result;
    }

    private static JSONObject family(
            String nameKo,
            String nameEn,
            String defaultUiPart,
            JSONArray allowedLoadStates
    ) throws Exception {
        JSONObject result = new JSONObject();
        result.put("nameKo", nameKo);
        result.put("nameEn", nameEn);
        result.put("defaultUiPart", defaultUiPart);
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
            String uiPart
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
        result.put("primarySubPart", "overall");
        result.put("primarySubPartNameKo", "전체");
        JSONObject variant = new JSONObject();
        variant.put("equipment", equipment);
        result.put("variant", variant);
        result.put("defaultUiPart", uiPart);
        return result;
    }
}
