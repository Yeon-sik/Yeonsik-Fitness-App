package com.yeonsik.fitnessapp.integration.nutrition

import com.yeonsik.fitnessapp.core.database.*
import com.yeonsik.fitnessapp.feature.information.data.*
import com.yeonsik.fitnessapp.feature.information.model.*
import com.yeonsik.fitnessapp.integration.nutrition.NutritionMealReviewClient.Companion.requiredText
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class NutritionMealSource(val project: String, val owner: String)
data class NutritionMealImportResult(val inserted: Int, val duplicates: Int)

object NutritionMealReviewEnvelope {
    /** Content revisions are immutable; timestamps, nullable values and source IDs remain inside the snapshot. */
    fun id(source: NutritionMealSource, snapshot: JsonObject): String {
        val identity = buildJsonObject {
            put("project", source.project); put("owner", source.owner); put("snapshot", snapshot)
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(InformationRecordCodec.canonical(identity).toByteArray(Charsets.UTF_8))
        return UUID.nameUUIDFromBytes(hash).toString()
    }
    fun create(source: NutritionMealSource, snapshot: JsonObject, capturedAt: String): InformationRecord {
        val mealId = snapshot.requiredText("id")
        val payload = buildJsonObject {
            put("schema_version", "information-record.v1"); put("record_id", id(source, snapshot))
            put("owner_app", "fitness"); put("record_type", "fitness.remote_meal_review")
            put("title", ("원격 식사 · " + (snapshot["menu"]?.jsonPrimitive?.contentOrNull ?: mealId)).take(200))
            put("captured_at", capturedAt)
            putJsonArray("sources") { add(buildJsonObject {
                put("source_id", "nutrition-meal"); put("kind", "url")
                put("uri", source.project.trimEnd('/') + "/rest/v1/meal_records")
                put("name", "Nutrition 식사 원문")
            }) }
            putJsonArray("fields") {
                add(buildJsonObject {
                    put("key", "remote_account"); put("label", "원격 Nutrition 계정"); put("value_type", "text"); put("value", source.owner)
                })
                add(buildJsonObject {
                    put("key", "meal_snapshot"); put("label", "식사·항목·영양값·수신 출처 원문")
                    put("value_type", "json"); put("value", snapshot)
                    putJsonArray("source_ids") { add("nutrition-meal") }
                })
            }
            putJsonArray("references") { add(buildJsonObject {
                put("owner_app", "fitness"); put("record_type", "fitness.remote_meal")
                put("record_id", mealId); put("project", source.project); put("relation", "source_snapshot")
            }) }
        }
        return InformationRecordCodec.decode(payload.toString(), InformationOwner.FITNESS)
    }
}

/** A page either enters the captured local profile in full or leaves it unchanged. No native meal writes. */
class NutritionMealReviewImporter(private val db: FitnessRoomDatabase, private val profile: InformationProfile) {
    fun importPage(source: NutritionMealSource, page: NutritionMealPage, checkCurrentMapping: () -> Unit): NutritionMealImportResult {
        var inserted = 0; var duplicates = 0
        db.runInTransaction {
            checkCurrentMapping()
            val dao = db.informationInboxDao()
            page.meals.forEach { snapshot ->
                require(snapshot.requiredText("owner_id") == source.owner)
                val id = NutritionMealReviewEnvelope.id(source, snapshot)
                val existing = dao.find(profile.userId, profile.project, id)
                // Reuse the first actual capture time, so retry never manufactures a new conflict.
                val captured = existing?.let { InformationRecordCodec.decode(it.payloadJson).payload.getValue("captured_at").jsonPrimitive.content }
                    ?: Instant.now().toString()
                val record = NutritionMealReviewEnvelope.create(source, snapshot, captured)
                if (existing != null) {
                    require(existing.payloadJson == record.canonical) { "같은 원격 식사의 다른 원문이 이미 있습니다. 기존 기록을 확인하세요." }
                    duplicates++
                } else {
                    dao.insert(InformationInboxEntity(profile.userId, profile.project, record.id, record.title,
                        record.canonical, record.fingerprint, InformationReviewState.PENDING.wire, captured))
                    inserted++
                }
            }
            checkCurrentMapping()
        }
        return NutritionMealImportResult(inserted, duplicates)
    }
}
