package com.yeonsik.fitnessapp.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Local settings survive session deletion; each account and exact variation owns its note. */
@Entity(tableName = "workout_setup_notes", primaryKeys = ["user_id", "exercise_key"])
data class WorkoutSetupNoteRoomEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "exercise_key") val exerciseKey: String,
    val note: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)
