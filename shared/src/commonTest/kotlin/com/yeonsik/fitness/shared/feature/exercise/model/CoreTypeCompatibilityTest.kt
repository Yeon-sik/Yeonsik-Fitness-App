package com.yeonsik.fitness.shared.feature.exercise.model

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CoreTypeCompatibilityTest {
    @Test
    fun preservesEveryPersistedEnumIdAndKoreanLabel() {
        assertEquals(
            listOf("bodyweight", "external_load", "added_weight", "assisted", "band_assisted", "band_resisted"),
            LoadState.values().map { it.id() }
        )
        assertEquals(
            listOf("가슴", "등", "하체", "어깨", "팔", "복근"),
            BodyPart.values().map { it.labelKo() }
        )
        assertEquals(
            listOf("chest", "back", "legs", "shoulders", "arms", "abs"),
            BodyPart.values().map { it.id() }
        )
        assertEquals(
            listOf(
                "bodyweight", "machine", "dumbbell", "barbell", "smith_machine",
                "cable", "band", "kettlebell", "plate", "other"
            ),
            EquipmentType.values().map { it.id() }
        )
        assertEquals(
            listOf("맨몸", "머신", "덤벨", "바벨", "스미스 머신", "케이블", "밴드", "케틀벨", "플레이트", "기타"),
            EquipmentType.values().map { it.labelKo() }
        )
        assertEquals(listOf("kg", "lb"), MassUnit.values().map { it.id() })
        assertEquals(listOf("킬로그램", "파운드"), MassUnit.values().map { it.labelKo() })
    }

    @Test
    fun preservesNullBlankUppercaseAndUnknownParsing() {
        assertNull(LoadState.fromId(null))
        assertNull(LoadState.fromId("   "))
        assertSame(LoadState.BAND_RESISTED, LoadState.fromId(" BAND_RESISTED "))
        assertNull(LoadState.fromId("future_load"))

        assertNull(BodyPart.fromId(null))
        assertNull(BodyPart.fromId("   "))
        assertSame(BodyPart.SHOULDERS, BodyPart.fromId(" SHOULDERS "))
        assertNull(BodyPart.fromId("future_body_part"))

        assertNull(EquipmentType.fromId(null))
        assertNull(EquipmentType.fromId("   "))
        assertSame(EquipmentType.SMITH_MACHINE, EquipmentType.fromId(" SMITH_MACHINE "))
        assertNull(EquipmentType.fromId("future_equipment"))

        assertNull(MassUnit.parse(null))
        assertNull(MassUnit.parse("   "))
        assertSame(MassUnit.LB, MassUnit.parse(" LB "))
        assertNull(MassUnit.parse("stone"))
    }

    @Test
    fun preservesLoadStateCapabilitySemantics() {
        assertTrue(LoadState.BODYWEIGHT.isBodyweightCapable())
        assertTrue(LoadState.ADDED_WEIGHT.isBodyweightCapable())
        assertTrue(LoadState.ASSISTED.isBodyweightCapable())
        assertTrue(LoadState.BAND_ASSISTED.isBodyweightCapable())
        assertFalse(LoadState.EXTERNAL_LOAD.isBodyweightCapable())
        assertFalse(LoadState.BAND_RESISTED.isBodyweightCapable())

        assertTrue(LoadState.BAND_ASSISTED.isBandSemantics())
        assertTrue(LoadState.BAND_RESISTED.isBandSemantics())
        assertFalse(LoadState.BODYWEIGHT.isBandSemantics())
    }

    @Test
    fun preservesAllIdentityFieldsAndDisplayFallbackOrder() {
        val identity = identity(
            presetNameKo = "프리셋 한국어",
            presetNameEn = "Preset English",
            legacyNameKo = "기존 한국어",
            legacyNameEn = "Legacy English"
        )

        assertEquals("legacy-id", identity.legacyExerciseId)
        assertEquals("family-id", identity.familyId)
        assertEquals("preset-id", identity.presetId)
        assertEquals("canonical-preset-id", identity.canonicalPresetId)
        assertEquals("프리셋 한국어", identity.presetNameKo)
        assertEquals("Preset English", identity.presetNameEn)
        assertEquals("기존 한국어", identity.legacyNameKo)
        assertEquals("Legacy English", identity.legacyNameEn)
        assertEquals("legs", identity.defaultUiPart)
        assertEquals("variant", identity.canonicalVariantKey)
        assertEquals("visual", identity.visualVariantKey)
        assertEquals("illustration", identity.illustrationKey)
        assertEquals("external_load", identity.defaultLoadState)
        assertEquals("weight_reps", identity.legacyRecordType)
        assertEquals("{json}", identity.variantJson)

        assertEquals("프리셋 한국어", identity.displayName())
        assertEquals("기존 한국어", identity.copyWithNames(presetNameKo = "  ").displayName())
        assertEquals("Preset English", identity.copyWithNames(presetNameKo = "  ", legacyNameKo = "").displayName())
        assertEquals("Legacy English", identity.copyWithNames(
            presetNameKo = "  ", legacyNameKo = "\t", presetNameEn = ""
        ).displayName())
        assertNotEquals(identity, this.identity())
    }

    @Test
    fun preservesDefaultLoadStateAndVariantIdentitySemantics() {
        val identity = identity(defaultLoadState = " ADDED_WEIGHT ")

        assertSame(LoadState.ADDED_WEIGHT, identity.defaultLoadStateValue())
        assertTrue(identity.hasVariantIdentity())
        assertFalse(identity.copyWithNames(familyId = " ").hasVariantIdentity())
        assertFalse(identity.copyWithNames(canonicalVariantKey = "\t").hasVariantIdentity())
        assertNull(identity.copyWithNames(defaultLoadState = "future").defaultLoadStateValue())
        assertSame(MassUnit.KG, MassUnit.orDefault(null))
    }

    @Test
    fun preservesPerformanceKeyEqualityComparabilityAndStableValue() {
        val identity = identity()
        val same = ExercisePerformanceKey.of(identity, LoadState.EXTERNAL_LOAD)
        val sameAgain = identity.performanceKey(LoadState.EXTERNAL_LOAD)
        val otherVariant = ExercisePerformanceKey.of(identity.copyWithNames(canonicalVariantKey = "other"), LoadState.EXTERNAL_LOAD)
        val otherLoadState = ExercisePerformanceKey.of(identity, LoadState.BODYWEIGHT)

        assertEquals(same, sameAgain)
        assertEquals(same?.hashCode(), sameAgain?.hashCode())
        assertEquals("family-id|variant|external_load", same?.stableValue())
        assertEquals(same?.stableValue(), same?.toString())
        assertTrue(same!!.isComparableTo(sameAgain))
        assertFalse(same.isComparableTo(otherVariant))
        assertFalse(same.isComparableTo(otherLoadState))
        assertFalse(same.isComparableTo(null))
        assertNull(ExercisePerformanceKey.of(null, LoadState.EXTERNAL_LOAD))
        assertNull(ExercisePerformanceKey.of(identity, null))
        assertNull(ExercisePerformanceKey.of(identity.copyWithNames(familyId = ""), LoadState.EXTERNAL_LOAD))
        assertNotEquals(same, otherVariant)
    }

    private fun identity(
        presetNameKo: String? = "프리셋 한국어",
        presetNameEn: String? = "Preset English",
        legacyNameKo: String? = "기존 한국어",
        legacyNameEn: String? = "Legacy English",
        familyId: String? = "family-id",
        canonicalVariantKey: String? = "variant",
        defaultLoadState: String? = "external_load"
    ) = ExerciseFamilyIdentity(
        "legacy-id",
        familyId,
        "preset-id",
        "canonical-preset-id",
        presetNameKo,
        presetNameEn,
        legacyNameKo,
        legacyNameEn,
        "legs",
        canonicalVariantKey,
        "visual",
        "illustration",
        defaultLoadState,
        "weight_reps",
        "{json}"
    )

    private fun ExerciseFamilyIdentity.copyWithNames(
        presetNameKo: String? = this.presetNameKo,
        presetNameEn: String? = this.presetNameEn,
        legacyNameKo: String? = this.legacyNameKo,
        legacyNameEn: String? = this.legacyNameEn,
        familyId: String? = this.familyId,
        canonicalVariantKey: String? = this.canonicalVariantKey,
        defaultLoadState: String? = this.defaultLoadState
    ) = ExerciseFamilyIdentity(
        legacyExerciseId,
        familyId,
        presetId,
        canonicalPresetId,
        presetNameKo,
        presetNameEn,
        legacyNameKo,
        legacyNameEn,
        defaultUiPart,
        canonicalVariantKey,
        visualVariantKey,
        illustrationKey,
        defaultLoadState,
        legacyRecordType,
        variantJson
    )
}
