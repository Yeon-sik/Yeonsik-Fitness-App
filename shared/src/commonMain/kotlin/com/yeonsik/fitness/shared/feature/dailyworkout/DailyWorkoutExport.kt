package com.yeonsik.fitness.shared.feature.dailyworkout

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Instant

private val reportJson = Json { prettyPrint = true }

fun DailyWorkoutReport.toJson(): String = reportJson.encodeToString(JsonObject.serializer(), buildJsonObject {
    put("schema_version", 1)
    put("date", day.date.toString())
    put("time_zone", day.zone.id)
    put("session_count", sessions.size)
    put("pending_review_count", pendingCount)
    putJsonArray("sessions") {
        for (session in sessions) add(buildJsonObject {
            val (sources, timing, heartRate) = session
            val kind = sources.firstOrNull { it.kind != DailyWorkoutKind.CARDIO && it.kind != DailyWorkoutKind.OTHER }?.kind
                ?: session.primary.kind
            val duration = timing.takeIf { it.durationSeconds != null } ?: sources.firstOrNull { it.durationSeconds != null }
            val distance = timing.takeIf { it.distanceMeters != null } ?: sources.firstOrNull { it.distanceMeters != null }
            val energy = timing.takeIf { it.energyKcal != null } ?: sources.firstOrNull { it.energyKcal != null }
            put("type", when (kind) {
                DailyWorkoutKind.STRENGTH -> "strength"
                DailyWorkoutKind.OTHER -> "other"
                else -> "cardio"
            })
            put("activity", kind.name.lowercase())
            put("title", session.primary.title)
            put("started_at", timing.startMillis?.let { Instant.fromEpochMilliseconds(it).toString() })
            put("ended_at", timing.endMillis?.let { Instant.fromEpochMilliseconds(it).toString() })
            put("time_precision", when {
                timing.interval != null -> "interval"
                timing.startMillis != null -> "start"
                else -> "date"
            })
            put("duration_seconds", duration?.durationSeconds)
            put("distance_m", distance?.distanceMeters)
            put("active_energy_kcal", energy?.energyKcal)
            putJsonObject("metric_sources") {
                if (timing.startMillis != null) put("time", timing.writer)
                duration?.let { put("duration", it.writer) }
                distance?.let { put("distance", it.writer) }
                energy?.let { put("active_energy", it.writer) }
                session.details?.let { put("exercises", it.writer) }
            }
            putJsonArray("sources") {
                for ((origin, writer) in sources.map { it.origin to it.writer }.distinct()) add(buildJsonObject {
                    put("provider", origin.name.lowercase())
                    put("writer", writer)
                })
            }
            if (heartRate == null) put("heart_rate", JsonNull) else put("heart_rate", heartRate.toJson())
            putJsonArray("manual_heart_rate") {
                for (source in sources.filter { it.manualHeartRateBpm != null }) add(buildJsonObject {
                    put("source", source.writer)
                    put("average_bpm", source.manualHeartRateBpm)
                })
            }
            putJsonArray("exercises") {
                for (exercise in session.details?.exercises.orEmpty()) add(buildJsonObject {
                    put("name", exercise.name)
                    putJsonArray("sets") {
                        for (set in exercise.sets) add(buildJsonObject {
                            put("weight_kg", set.weightKg)
                            put("reps", set.reps)
                            put("rir", set.rir)
                            put("rpe", set.rpe)
                            put("duration_seconds", set.durationSeconds)
                            put("distance_m", set.distanceMeters)
                            put("load_state", set.loadState)
                            put("set_type", set.setType)
                        })
                    }
                })
            }
        })
    }
    putJsonArray("unlinked_heart_rate") {
        for (summary in unlinkedHeartRate) add(summary.toJson())
    }
})

private fun DailyHeartRateSummary.toJson(): JsonObject = buildJsonObject {
    put("source", writer)
    put("average_bpm", averageBpm)
    put("max_observed_bpm", maxBpm)
    put("sample_count", sampleCount)
}
