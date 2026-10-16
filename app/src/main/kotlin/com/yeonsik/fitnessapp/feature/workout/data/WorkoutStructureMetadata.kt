package com.yeonsik.fitnessapp.feature.workout.data

import org.json.JSONObject
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseGroupType

/** Versioned, additive record metadata travels with the existing SQLite backup record. */
internal class WorkoutStructureMetadata(raw: String) {
    val record: JSONObject = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    private val structure = record.optJSONObject("workout_structure_v1") ?: JSONObject()
    val sets: JSONObject = structure.optJSONObject("sets") ?: JSONObject()
    val supersets: JSONObject = structure.optJSONObject("supersets") ?: JSONObject()
    val groupTypes: JSONObject = structure.optJSONObject("group_types") ?: JSONObject()

    fun groupType(groupId: String?): WorkoutExerciseGroupType =
        WorkoutExerciseGroupType.fromId(groupId?.let { groupTypes.optString(it) })

    fun encoded(): String {
        structure.put("sets", sets)
        structure.put("supersets", supersets)
        structure.put("group_types", groupTypes)
        record.put("workout_structure_v1", structure)
        return record.toString()
    }
}
