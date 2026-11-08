package com.yeonsik.fitnessapp.core.database

import androidx.room.*

@Entity(tableName = "wearable_workouts", primaryKeys = ["user_id", "origin", "provider_id"])
data class WearableWorkoutEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    val origin: String,
    @ColumnInfo(name = "provider_id") val providerId: String,
    val title: String,
    @ColumnInfo(name = "start_time") val startTime: String,
    @ColumnInfo(name = "end_time") val endTime: String,
    @ColumnInfo(name = "exercise_type") val exerciseType: Int,
    @ColumnInfo(name = "samples_json") val samplesJson: String,
    @ColumnInfo(name = "linked_record_id") val linkedRecordId: String?,
    @ColumnInfo(name = "imported_at") val importedAt: String
)

@Dao
interface WearableWorkoutDao {
    @Query("SELECT * FROM wearable_workouts WHERE user_id=:owner ORDER BY start_time DESC")
    fun list(owner: String): List<WearableWorkoutEntity>
    @Query("SELECT * FROM wearable_workouts WHERE user_id=:owner AND origin=:origin AND provider_id=:id")
    fun find(owner: String, origin: String, id: String): WearableWorkoutEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(row: WearableWorkoutEntity)
    @Query("SELECT date FROM workout_records WHERE user_id=:owner AND id=:id AND deleted_at IS NULL")
    fun targetDate(owner: String, id: String): String?
}
