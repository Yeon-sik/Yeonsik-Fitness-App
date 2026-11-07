package com.yeonsik.fitnessapp.feature.information

import com.yeonsik.fitnessapp.feature.information.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.OffsetDateTime

class InformationProjectionTest {
    private val now = ZonedDateTime.parse("2026-11-06T23:00:00+09:00[Asia/Seoul]")
    private fun record(fields: String) = InformationRecordCodec.decode("""{"schema_version":"information-record.v1","record_id":"12345678-1234-4234-8234-1234567890ab","owner_app":"fitness","record_type":"fitness.observation","title":"범용 원문","captured_at":"2026-11-06T00:00:00Z","sources":[{"source_id":"manual","kind":"manual"}],"fields":$fields,"references":[]}""")
    private val meal = mapOf("date" to "2026-11-06", "time" to "12:30", "name" to "점심", "calories_kcal" to "500", "protein_grams" to "30", "carbs_grams" to "60", "fat_grams" to "15")

    @Test fun genericRecordCanBeExplicitlyMappedWithoutInventingMissingFacts() {
        val source = record("""[{"key":"weight_kg","value_type":"decimal","value":"72.40","unit":"kg"}]""")
        val draft = InformationProjection.draft(source, InformationProjectionKind.WEIGHT)
        assertEquals("72.40", draft.values["weight_kg"])
        assertNull(draft.values["date"]) // capture date is not the observation date
        assertThrows(IllegalStateException::class.java) { InformationProjection.preview(draft, now) }
        val preview = InformationProjection.preview(draft.copy(values = draft.values + ("date" to "2026-11-06")), now)
        assertEquals("72.40", preview.text("weight_kg"))
        assertTrue(preview.mappingJson.contains("fitness-native-projection.v1"))
    }

    @Test fun absentNutritionNeverBecomesZeroAndExplicitZeroIsRetained() {
        for (key in listOf("calories_kcal", "protein_grams", "carbs_grams", "fat_grams")) {
            assertThrows(IllegalArgumentException::class.java) { InformationProjection.preview(InformationProjectionDraft(InformationProjectionKind.MEAL, meal - key), now) }
        }
        val preview = InformationProjection.preview(InformationProjectionDraft(InformationProjectionKind.MEAL, meal + ("sugars_grams" to "0")), now)
        assertEquals(0.0, preview.optionalNumber("sugars_grams")!!, 0.0)
        assertNull(preview.optionalNumber("sodium_mg"))
        assertEquals("500", InformationProjection.preview(InformationProjectionDraft(InformationProjectionKind.MEAL, meal + ("calories_kcal" to "500.0")), now).text("calories_kcal"))
    }

    @Test fun remoteUnknownNutritionAndWrongUnitsStayBlankForReview() {
        val source = record("""[{"key":"meal_snapshot","value_type":"json","value":{"date":"2026-11-06","menu":"원격식사","calories":null,"protein_grams":12.30,"eaten_at":"2026-11-06T12:00:00+09:00"}},{"key":"weight_kg","value_type":"decimal","value":"150","unit":"lb"}]""")
        assertNull(InformationProjection.draft(source, InformationProjectionKind.WEIGHT).values["weight_kg"])
        val draft = InformationProjection.draft(source, InformationProjectionKind.MEAL, now.zone)
        assertNull(draft.values["calories_kcal"])
        assertEquals("12.30", draft.values["protein_grams"])
        assertEquals("12:00", draft.values["time"])
    }

    @Test fun ambiguousAliasesDoNotPickALastValue() {
        val source = record("""[{"key":"weight_kg","value_type":"decimal","value":"72"},{"key":"fitness.weight_kg","value_type":"decimal","value":"80"}]""")
        assertNull(InformationProjection.draft(source, InformationProjectionKind.WEIGHT).values["weight_kg"])
    }

    @Test fun unitlessAndMismatchedQuantitiesRequireManualReview() {
        for ((key, unit) in mapOf("weight_kg" to "kg", "calories_kcal" to "kcal", "duration_seconds" to "s")) {
            for (missingOrWrong in listOf("", ",\"unit\":\"unknown\"")) {
                val source = record("""[{"key":"$key","value_type":"decimal","value":"72"$missingOrWrong}]""")
                assertNull(InformationProjection.draft(source, InformationProjectionKind.WEIGHT, now.zone).values[key])
            }
            val source = record("""[{"key":"$key","value_type":"decimal","value":"72","unit":"$unit"}]""")
            assertEquals("72", InformationProjection.draft(source, InformationProjectionKind.WEIGHT, now.zone).values[key])
        }
    }

    @Test fun offsetInstantsKeepTheirMomentWhenSuggestedAcrossMidnight() {
        val cases = listOf(
            Triple("2026-11-05T23:30:00Z", "Asia/Seoul", "2026-11-06T08:30"),
            Triple("2026-11-06T00:30:00+09:00", "UTC", "2026-11-05T15:30")
        )
        for ((timestamp, zoneName, expected) in cases) {
            val zone = ZoneId.of(zoneName)
            val base = record("""[{"key":"activity_name","value_type":"text","value":"walk"},{"key":"duration_seconds","value_type":"decimal","value":"60","unit":"s"}]""")
            val source = InformationRecordCodec.decode(JsonObject(base.payload + ("occurred_at" to JsonPrimitive(timestamp))).toString())
            val draft = InformationProjection.draft(source, InformationProjectionKind.WORKOUT, zone)
            assertEquals(expected, draft.values["date"] + "T" + draft.values["start_time"])
            val preview = InformationProjection.preview(draft, now.withZoneSameInstant(zone))
            assertEquals(OffsetDateTime.parse(timestamp).toInstant(), OffsetDateTime.parse(preview.text("started_at")).toInstant())
            val remote = record("""[{"key":"meal_snapshot","value_type":"json","value":{"date":"${timestamp.take(10)}","eaten_at":"$timestamp","menu":"meal"}}]""")
            val mealDraft = InformationProjection.draft(remote, InformationProjectionKind.MEAL, zone)
            assertEquals(expected, mealDraft.values["date"] + "T" + mealDraft.values["time"])
        }
        val explicit = record("""[{"key":"date","value_type":"date","value":"2026-11-01"}]""")
        val conflicting = InformationRecordCodec.decode(JsonObject(explicit.payload + ("occurred_at" to JsonPrimitive("2026-11-05T23:30:00Z"))).toString())
        val draft = InformationProjection.draft(conflicting, InformationProjectionKind.WORKOUT, now.zone)
        assertEquals("2026-11-01", draft.values["date"])
        assertNull(draft.values["start_time"])
    }
    @Test fun nativePrecisionUnknownAndFutureValuesAreBlocked() {
        for (weight in listOf("", "NaN", "Infinity", "-1", "0", "12345678901234567890.0010")) {
            assertThrows(IllegalArgumentException::class.java) { InformationProjection.preview(InformationProjectionDraft(InformationProjectionKind.WEIGHT, mapOf("date" to "2026-11-06", "weight_kg" to weight)), now) }
        }
        assertThrows(IllegalArgumentException::class.java) { InformationProjection.preview(InformationProjectionDraft(InformationProjectionKind.WEIGHT, mapOf("date" to "2026-11-07", "weight_kg" to "72")), now) }
    }

    @Test fun completedWorkoutPreviewHasExplicitTimeAndUniformSets() {
        val values = mapOf("date" to "2026-11-06", "activity_name" to "수동 운동", "start_time" to "10:00", "duration_seconds" to "600", "mode" to "resistance", "sets" to "3", "reps" to "10", "load_kg" to "20")
        val preview = InformationProjection.preview(InformationProjectionDraft(InformationProjectionKind.WORKOUT, values), now)
        assertEquals("2026-11-06T10:10+09:00", preview.text("ended_at"))
        assertTrue(preview.summary.contains("3 세트"))
        assertThrows(IllegalArgumentException::class.java) { InformationProjection.preview(InformationProjectionDraft(InformationProjectionKind.WORKOUT, values + ("duration_seconds" to "86401")), now) }
        assertThrows(IllegalArgumentException::class.java) { InformationProjection.preview(InformationProjectionDraft(InformationProjectionKind.WORKOUT, values + ("start_time" to "23:00")), now) }
    }
}
