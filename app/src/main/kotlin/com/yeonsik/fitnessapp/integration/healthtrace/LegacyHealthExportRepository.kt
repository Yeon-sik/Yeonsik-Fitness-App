package com.yeonsik.fitnessapp.integration.healthtrace

import android.database.Cursor
import androidx.sqlite.db.SimpleSQLiteQuery
import com.yeonsik.fitnessapp.core.database.*
import com.yeonsik.fitnessapp.feature.information.data.InformationProfile
import com.yeonsik.fitnessapp.feature.information.model.*
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class LegacyHealthRow(val table: String, val id: String, val payload: JsonObject) {
    val label get() = when (table) {
        "supplement_items" -> "보충제 제품"
        "supplement_schedules" -> "복용 계획"
        "supplement_schedule_slots" -> "복용 일정"
        "supplement_intake_records" -> "복용 기록"
        "supplement_effect_checkins" -> "복용 경과"
        else -> "이전 건강 체크인"
    }
}

/** Reads legacy owner-scoped facts; writes only an immutable outgoing export receipt. */
class LegacyHealthExportRepository(private val db: FitnessRoomDatabase, private val profile: InformationProfile) {
    private val tables = listOf("supplement_items", "supplement_schedules", "supplement_schedule_slots",
        "supplement_intake_records", "supplement_effect_checkins", "nutrition_daily_checkins")

    fun rows(): List<LegacyHealthRow> = buildList {
        for (table in tables) {
            // Check-ins mix activity and health. Water/readiness remain Fitness-owned.
            val columns = if (table == "nutrition_daily_checkins")
                "id,date,sleep_hours,energy_score,hunger_score,digestion_score,note,created_at,updated_at" else "*"
            db.openHelper.readableDatabase.query(SimpleSQLiteQuery(
                "SELECT $columns FROM $table WHERE user_id = ? ORDER BY id", arrayOf(profile.userId))).use { cursor ->
                while (cursor.moveToNext()) {
                    val payload = buildJsonObject {
                        for (i in 0 until cursor.columnCount) {
                            if (cursor.getColumnName(i) == "user_id") continue
                            put(cursor.getColumnName(i), when (cursor.getType(i)) {
                                Cursor.FIELD_TYPE_NULL -> JsonNull
                                Cursor.FIELD_TYPE_INTEGER, Cursor.FIELD_TYPE_FLOAT -> Json.parseToJsonElement(cursor.getString(i))
                                else -> JsonPrimitive(cursor.getString(i))
                            })
                        }
                    }
                    if (table == "nutrition_daily_checkins" && listOf("sleep_hours", "energy_score", "hunger_score", "digestion_score")
                        .all { payload[it] == JsonNull } && (payload["note"] as? JsonPrimitive)?.content.orEmpty().isBlank()) continue
                    add(LegacyHealthRow(table, payload.getValue("id").jsonPrimitive.content, payload))
                }
            }
        }
    }

    /** null means full snapshot; a row is a bounded fallback when the complete export exceeds 1 MiB. */
    fun export(selection: LegacyHealthRow? = null): InformationRecord {
        lateinit var result: InformationRecord
        db.runInTransaction {
            val current = rows()
            val selected = if (selection == null) current else listOf(current.firstOrNull {
                it.table == selection.table && it.id == selection.id
            } ?: error("이전 기록을 찾지 못했습니다. 목록을 다시 여세요."))
            require(selected.isNotEmpty()) { "내보낼 이전 건강 기록이 없습니다." }
            val snapshot = buildJsonObject {
                for ((table, entries) in selected.groupBy { it.table }) put(table, JsonArray(entries.map { it.payload }))
            }
            val canonicalSnapshot = InformationRecordCodec.canonical(snapshot)
            val key = sha256(canonicalSnapshot)
            val dao = db.healthExportReceiptDao()
            val existing = dao.find(profile.userId, profile.project, key)
            if (existing != null) {
                result = InformationRecordCodec.decode(existing.payloadJson, InformationOwner.HEALTHTRACE)
            } else {
                val id = UUID.nameUUIDFromBytes(("fitness-legacy-health|${profile.userId}|${profile.project}|$key").toByteArray(Charsets.UTF_8)).toString()
                val captured = Instant.now().toString()
                val payload = buildJsonObject {
                    put("schema_version", "information-record.v1"); put("record_id", id)
                    put("owner_app", "healthtrace"); put("record_type", "healthtrace.legacy_fitness_health_snapshot")
                    put("title", if (selection == null) "Fitness 이전 건강 기록 ${selected.size}건" else "Fitness 이전 ${selection.label}")
                    put("captured_at", captured)
                    putJsonArray("sources") { add(buildJsonObject {
                        put("source_id", "fitness-legacy"); put("kind", "device"); put("name", "Fitness 로컬 이전 기록")
                        put("text", "사용자가 내보낸 이전 건강 기록입니다. 원본 ID와 저장 당시 값·시각을 보존하며 HealthTrace의 진단이나 복용 집계로 자동 반영하지 않습니다.")
                    }) }
                    putJsonArray("fields") { add(buildJsonObject {
                        put("key", "legacy_rows"); put("label", "이전 건강 기록 원문"); put("value_type", "json"); put("value", snapshot)
                        putJsonArray("source_ids") { add("fitness-legacy") }
                    }) }
                    putJsonArray("references") { add(buildJsonObject {
                        put("owner_app", "fitness"); put("record_id", "legacy-health-snapshot:$key")
                        put("record_type", "fitness.legacy_health_archive"); put("relation", "exported_from_legacy")
                        put("project", profile.project)
                    }) }
                }
                require(payload.toString().toByteArray(Charsets.UTF_8).size <= InformationRecordCodec.MAX_BYTES) {
                    "전체 자료가 1 MiB를 넘습니다. 목록에서 개별 기록을 선택해 내보내세요."
                }
                result = InformationRecordCodec.decode(payload.toString(), InformationOwner.HEALTHTRACE)
                dao.insert(HealthExportReceiptEntity(profile.userId, profile.project, key, id, result.canonical, captured))
            }
        }
        return result
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
