package com.yeonsik.fitnessapp.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The ID is the workout exercise occurrence ID. Original workout rows are never rewritten. */
@Entity(tableName = "workout_manual_exercise_links", indices = [
    Index(name = "workout_manual_links_identity_idx", value = ["user_id", "family_id", "canonical_variant_key"])
])
data class WorkoutManualExerciseLinkRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "canonical_preset_id") val canonicalPresetId: String,
    @ColumnInfo(name = "family_id") val familyId: String,
    @ColumnInfo(name = "canonical_variant_key") val canonicalVariantKey: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)
