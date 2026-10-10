package com.yeonsik.fitnessapp.exercise

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Tests actual bundled projection, decoding, and picker search rather than a second catalog fixture. */
class RequestedExerciseCatalogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun requestedNamesDisplaySearchableAliasesWithoutChangingCanonicalNames() {
        val catalog = ExerciseFamilyCatalog.load(context).runtimeCatalog()
        val picker = RuntimeExercisePicker(catalog)
        val cases = listOf(
            "machine_chest_fly" to "펙덱 플라이",
            "machine_rear_delt_fly" to "리버스 펙덱 플라이",
            "dumbbell_rear_delt_fly" to "벤트오버 덤벨 레터럴 레이즈",
            "shoulders_dumbbell_lateral_raise" to "사레레",
            "arms_machine_preacher_curl" to "머신 스콧 컬",
            "arms_dumbbell_preacher_curl" to "덤벨 스콧 컬",
            "arms_barbell_preacher_curl" to "이지바 스콧 컬",
            "arms_cable_preacher_curl" to "케이블 스콧 컬",
            "legs_barbell_romanian_deadlift" to "바벨 RDL",
            "legs_dumbbell_romanian_deadlift" to "덤벨 RDL",
            "legs_smith_romanian_deadlift" to "스미스머신 RDL",
            "arms_cable_rope_pushdown" to "케이블 로프 프레스다운",
            "arms_cable_triceps_pushdown_straight_bar" to "케이블 스트레이트바 프레스다운",
            "arms_cable_reverse_grip_pushdown" to "케이블 리버스 그립 프레스다운",
            "legs_machine_hip_abduction" to "아웃타이 머신",
            "legs_machine_hip_adduction" to "인너타이 머신",
            "legs_machine_leg_extension" to "니 익스텐션",
            "legs_machine_lying_leg_curl" to "라잉 햄스트링 컬",
            "legs_machine_seated_leg_curl" to "시티드 햄스트링 컬",
            "legs_machine_standing_leg_curl" to "스탠딩 햄스트링 컬",
            "shoulders_barbell_overhead_press" to "바벨 OHP"
        )
        for ((id, alias) in cases) {
            val preset = catalog.preset(id)!!
            val canonicalName = preset.displayName()
            assertTrue(alias, preset.pickerDisplayName().startsWith("$canonicalName ("))
            assertTrue(alias, preset.pickerDisplayName().contains(alias))
            assertEquals(alias, preset.identityId(), picker.search(alias).single().presets.single().identityId())
            assertEquals(canonicalName, preset.displayName())
        }
    }

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
