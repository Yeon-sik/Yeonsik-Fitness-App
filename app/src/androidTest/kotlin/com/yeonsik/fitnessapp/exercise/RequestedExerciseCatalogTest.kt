package com.yeonsik.fitnessapp.exercise

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Tests actual bundled projection, decoding, and picker search rather than a second catalog fixture. */
class RequestedExerciseCatalogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun everyDeclaredAliasSelectsExactlyOneCanonicalPresetWithEquipmentAndPosturePreserved() {
        val document = JSONObject(context.assets.open("exercise_family_mapping_v1.json")
            .bufferedReader().use { it.readText() })
        val catalog = RuntimeExerciseCatalog.fromJson(document)
        val picker = RuntimeExercisePicker(catalog)
        val aliases = document.getJSONArray("searchPresetAliases")
        for (index in 0 until aliases.length()) {
            val alias = aliases.getJSONObject(index)
            val query = alias.getString("alias")
            val expected = catalog.presetForStorageExerciseId(alias.getString("targetPreset"))!!
            val matches = picker.search(query).flatMap { it.presets }
            assertEquals(query, 1, matches.size)
            assertEquals(query, expected.identityId(), matches.single().identityId())
        }
        val merges = document.getJSONArray("canonicalAliasMerges")
        for (index in 0 until merges.length()) {
            val merge = merges.getJSONObject(index)
            val expected = merge.getString("canonicalPresetId")
            val names = merge.optJSONArray("aliases") ?: continue
            for (i in 0 until names.length()) {
                val matches = picker.search(names.getString(i)).flatMap { it.presets }
                assertEquals(names.getString(i), 1, matches.size)
                assertEquals(expected, matches.single().canonicalPresetId)
            }
        }
        assertEquals("shoulders_dumbbell_lateral_raise", picker.search("사레레").single().presets.single().canonicalPresetId)
        assertEquals("machine_rear_delt_fly", picker.search("리버스 펙덱 플라이").single().presets.single().canonicalPresetId)
        assertTrue(picker.search("스쿼트").flatMap { it.presets }.size > 1)
    }

    @Test fun kettlebellSumoHasCorrectSearchAnatomyLoadSemanticsAndDistinctSquatVariant() {
        val catalog = ExerciseFamilyCatalog.load(context).runtimeCatalog()
        val picker = RuntimeExercisePicker(catalog)
        val a = catalog.preset("legs_dumbbell_sumo_squat")!!
        val b = catalog.preset("legs_kettlebell_sumo_squat")!!
        for (query in listOf("케틀벨 스모 스쿼트", "Kettlebell Sumo Squat")) {
            assertEquals(b.identityId(), picker.search(query).single().presets.single().identityId())
        }
        assertEquals("squat", b.familyId)
        assertEquals("kettlebell", b.equipmentVariantId)
        assertEquals(a.primarySubPart, b.primarySubPart)
        assertEquals(a.secondarySubParts, b.secondarySubParts)
        assertEquals(a.recordType, b.recordType)
        assertEquals(a.defaultLoadState, b.defaultLoadState)
        assertEquals(a.implementMultiplier, b.implementMultiplier)
        assertNotEquals(a.identityId(), b.identityId())
        assertNotEquals(a.canonicalVariantKey, b.canonicalVariantKey)
    }
}
