package com.yeonsik.fitnessapp.integration.wearable

import com.yeonsik.fitnessapp.core.database.*
import org.json.JSONArray
import java.time.*

/** Local observations only. No canonical workout writes, sync payloads or source deletions. */
class WearableWorkoutRepository(private val database: FitnessRoomDatabase) {
    private val dao get() = database.wearableWorkoutDao()
    fun list(owner: String) = dao.list(owner).map { row ->
        val json = JSONArray(row.samplesJson)
        WearableWorkout(row.origin, row.providerId, row.title, Instant.parse(row.startTime), Instant.parse(row.endTime),
            row.exerciseType, (0 until json.length()).map { i -> json.getJSONArray(i).let {
                MeasuredPulse(Instant.parse(it.getString(0)), it.getLong(1))
            } }, row.linkedRecordId, Instant.parse(row.importedAt))
    }
    fun save(owner: String, workout: WearableWorkout, target: String?, zone: ZoneId, ownerStillCurrent: () -> Boolean) {
        require(owner.isNotBlank() && workout.origin.isNotBlank() && workout.providerId.isNotBlank())
        val samples = measuredPulses(workout.start, workout.end, workout.samples)
        database.runInTransaction {
            check(ownerStillCurrent())
            val old = dao.find(owner, workout.origin, workout.providerId)
            if (target != null) {
                check(dao.targetDate(owner, target) == workout.start.atZone(zone).toLocalDate().toString()) {
                    "운동 날짜와 계정을 확인하세요. 같은 날짜의 Fitness 운동에만 연결할 수 있습니다."
                }
                check(old?.linkedRecordId == null || old.linkedRecordId == target) {
                    "이미 다른 Fitness 운동에 연결된 기록입니다."
                }
            }
            val json = JSONArray()
            samples.forEach { json.put(JSONArray().put(it.time.toString()).put(it.bpm)) }
            dao.save(WearableWorkoutEntity(owner, workout.origin, workout.providerId, workout.title,
                workout.start.toString(), workout.end.toString(), workout.exerciseType, json.toString(),
                target ?: old?.linkedRecordId, Instant.now().toString()))
            check(ownerStillCurrent())
        }
    }
}
