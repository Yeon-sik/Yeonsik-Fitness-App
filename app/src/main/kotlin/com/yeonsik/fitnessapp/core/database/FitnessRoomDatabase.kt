package com.yeonsik.fitnessapp.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update

@Entity(tableName = "body_profiles")
data class BodyProfileEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "height_cm") val heightCm: Int,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(
    tableName = "weight_records",
    indices = [
        Index(
            name = "weight_records_user_scope_date_idx",
            value = ["user_id", "scope", "date"]
        ),
        Index(
            name = "weight_records_user_sync_push_idx",
            value = ["user_id", "updated_at", "id"]
        )
    ]
)
data class WeightRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val date: String,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "is_backfilled") val isBackfilled: Boolean,
    @ColumnInfo(name = "backfilled_at") val backfilledAt: String?,
    @ColumnInfo(name = "backfill_reason") val backfillReason: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "source_app") val sourceApp: String,
    val scope: String,
    val metadata: String,
    @ColumnInfo(name = "contract_version", defaultValue = "1") val contractVersion: Int
)

@Entity(
    tableName = "routines",
    indices = [Index(name = "routines_user_default_idx", value = ["user_id", "is_default"])]
)
data class RoutineEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val name: String,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?
)

@Entity(
    tableName = "routine_exercises",
    indices = [
        Index(name = "routine_exercises_routine_order_idx", value = ["routine_id", "order_index"]),
        Index(
            name = "routine_exercises_family_variant_idx",
            value = ["user_id", "family_id", "canonical_variant_key"]
        )
    ]
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "routine_id") val routineId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "name_ko") val nameKo: String,
    @ColumnInfo(name = "ui_part") val uiPart: String,
    @ColumnInfo(name = "primary_sub_part") val primarySubPart: String,
    val equipment: String,
    @ColumnInfo(name = "record_type") val recordType: String,
    @ColumnInfo(name = "family_id") val familyId: String?,
    @ColumnInfo(name = "preset_id") val presetId: String?,
    @ColumnInfo(name = "canonical_variant_key") val canonicalVariantKey: String?,
    @ColumnInfo(name = "visual_variant_key") val visualVariantKey: String?,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?
)

@Dao
interface BodyRoomDao {
    @Query(
        "SELECT * FROM weight_records WHERE user_id = :userId AND deleted_at IS NULL " +
            "AND scope IN ('fitness', 'both') ORDER BY date DESC, updated_at DESC LIMIT :limit"
    )
    fun visibleWeights(userId: String, limit: Int = 20): List<WeightRecordEntity>

    @Query(
        "SELECT * FROM weight_records WHERE id = :id AND user_id = :userId " +
            "AND deleted_at IS NULL LIMIT 1"
    )
    fun visibleWeight(id: String, userId: String): WeightRecordEntity?

    @Query(
        "SELECT * FROM weight_records WHERE user_id = :userId AND deleted_at IS NULL " +
            "AND scope IN ('fitness', 'both') AND date = :date " +
            "ORDER BY date DESC, updated_at DESC LIMIT 20"
    )
    fun visibleWeightsForDate(userId: String, date: String): List<WeightRecordEntity>

    @Query(
        "SELECT * FROM weight_records WHERE user_id = :userId AND deleted_at IS NULL " +
            "AND scope IN ('fitness', 'both') AND date <= :date " +
            "ORDER BY date DESC, updated_at DESC LIMIT 1"
    )
    fun latestVisibleWeightOnOrBefore(userId: String, date: String): WeightRecordEntity?

    @Query(
        "SELECT COUNT(DISTINCT date) FROM weight_records WHERE user_id = :userId " +
            "AND deleted_at IS NULL AND scope IN ('fitness', 'both') " +
            "AND date BETWEEN :startDate AND :endDate"
    )
    fun visibleWeightRecordedDays(userId: String, startDate: String, endDate: String): Int

    @Query(
        "SELECT DISTINCT date FROM weight_records WHERE user_id = :userId " +
            "AND deleted_at IS NULL AND scope IN ('fitness', 'both') " +
            "AND date BETWEEN :startDate AND :endDate ORDER BY date"
    )
    fun visibleWeightDates(userId: String, startDate: String, endDate: String): List<String>

    @Query(
        "SELECT * FROM weight_records WHERE user_id = :userId AND deleted_at IS NULL " +
            "AND scope IN ('fitness', 'both') AND date BETWEEN :startDate AND :endDate " +
            "ORDER BY date, updated_at DESC"
    )
    fun visibleWeightsBetween(userId: String, startDate: String, endDate: String): List<WeightRecordEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(record: WeightRecordEntity)

    @Query(
        "UPDATE weight_records SET date = :date, weight_kg = :weightKg, metadata = :metadata, " +
            "updated_at = :updatedAt WHERE id = :id AND user_id = :userId AND deleted_at IS NULL"
    )
    fun updateVisibleWeight(
        id: String,
        userId: String,
        date: String,
        weightKg: Double,
        metadata: String,
        updatedAt: String
    ): Int

    @Query(
        "UPDATE weight_records SET deleted_at = :deletedAt, updated_at = :updatedAt " +
            "WHERE id = :id AND user_id = :userId AND deleted_at IS NULL"
    )
    fun tombstoneVisibleWeight(id: String, userId: String, deletedAt: String, updatedAt: String): Int

    @Query("SELECT * FROM body_profiles WHERE user_id = :userId LIMIT 1")
    fun bodyProfile(userId: String): BodyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun replaceBodyProfile(profile: BodyProfileEntity)

    @Query("DELETE FROM body_profiles WHERE user_id = :userId")
    fun deleteBodyProfile(userId: String): Int
}

@Dao
interface DeviceRoomDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(device: DevicesRoomEntity)
}

@Dao
interface RoutineRoomDao {
    @Query(
        "SELECT * FROM routines WHERE user_id = :userId AND deleted_at IS NULL " +
            "ORDER BY is_default DESC, updated_at DESC"
    )
    fun visibleRoutines(userId: String): List<RoutineEntity>

    @Query(
        "SELECT * FROM routine_exercises WHERE routine_id = :routineId AND user_id = :userId " +
            "AND deleted_at IS NULL ORDER BY order_index ASC"
    )
    fun visibleExercises(routineId: String, userId: String): List<RoutineExerciseEntity>

    data class RoutineSummaryProjection(
        val id: String,
        val name: String,
        val exerciseCount: Int
    )

    @Query(
        "SELECT r.id AS id, r.name AS name, COUNT(re.id) AS exerciseCount " +
            "FROM routines r LEFT JOIN routine_exercises re ON re.routine_id = r.id " +
            "AND re.user_id = r.user_id AND re.deleted_at IS NULL " +
            "WHERE r.user_id = :userId AND r.deleted_at IS NULL " +
            "GROUP BY r.id, r.name, r.is_default, r.created_at " +
            "ORDER BY r.is_default DESC, r.created_at"
    )
    fun visibleRoutineSummaries(userId: String): List<RoutineSummaryProjection>

    @Query(
        "SELECT * FROM routines WHERE id = :routineId AND user_id = :userId " +
            "AND deleted_at IS NULL LIMIT 1"
    )
    fun visibleRoutine(routineId: String, userId: String): RoutineEntity?

    @Query(
        "SELECT COALESCE(MAX(order_index), 0) + 1 FROM routine_exercises " +
            "WHERE routine_id = :routineId AND user_id = :userId AND deleted_at IS NULL"
    )
    fun nextExerciseOrder(routineId: String, userId: String): Int

    @Query(
        "UPDATE routines SET user_id = :nextUserId WHERE user_id = :sourceUserId"
    )
    fun claimRoutines(sourceUserId: String, nextUserId: String): Int

    @Query(
        "UPDATE routine_exercises SET user_id = :nextUserId WHERE user_id = :sourceUserId"
    )
    fun claimRoutineExercises(sourceUserId: String, nextUserId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertRoutine(routine: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertExercise(exercise: RoutineExerciseEntity)

    @Query(
        "UPDATE routines SET name = :name, updated_at = :updatedAt " +
            "WHERE id = :routineId AND user_id = :userId AND deleted_at IS NULL"
    )
    fun renameRoutine(routineId: String, userId: String, name: String, updatedAt: String): Int

    @Query(
        "UPDATE routine_exercises SET deleted_at = :deletedAt, updated_at = :updatedAt " +
            "WHERE routine_id = :routineId AND user_id = :userId AND deleted_at IS NULL"
    )
    fun tombstoneRoutineExercises(routineId: String, userId: String, deletedAt: String, updatedAt: String): Int

    @Query(
        "UPDATE routines SET deleted_at = :deletedAt, updated_at = :updatedAt " +
            "WHERE id = :routineId AND user_id = :userId AND deleted_at IS NULL"
    )
    fun tombstoneRoutine(routineId: String, userId: String, deletedAt: String, updatedAt: String): Int
}

@Dao
interface SupplementRoomDao {
    data class ActivePlanRow(
        @ColumnInfo(name = "item_id") val itemId: String,
        @ColumnInfo(name = "schedule_id") val scheduleId: String,
        @ColumnInfo(name = "type_code") val typeCode: String,
        @ColumnInfo(name = "type_name") val typeName: String,
        @ColumnInfo(name = "brand_name") val brandName: String,
        @ColumnInfo(name = "product_form") val productForm: String,
        @ColumnInfo(name = "purpose_code") val purposeCode: String,
        @ColumnInfo(name = "serving_amount") val servingAmount: Double,
        @ColumnInfo(name = "serving_unit") val servingUnit: String,
        @ColumnInfo(name = "active_ingredient_amount") val activeIngredientAmount: Double?,
        @ColumnInfo(name = "active_ingredient_unit") val activeIngredientUnit: String,
        @ColumnInfo(name = "ingredient_details") val ingredientDetails: String,
        @ColumnInfo(name = "times_per_day") val timesPerDay: Long,
        @ColumnInfo(name = "timing_label") val timingLabel: String,
        @ColumnInfo(name = "effective_from") val effectiveFrom: String,
        @ColumnInfo(name = "effective_to") val effectiveTo: String?,
        @ColumnInfo(name = "is_current") val currentlyActive: Long,
        @ColumnInfo(name = "taken_count") val takenCount: Long,
        @ColumnInfo(name = "skipped_count") val skippedCount: Long
    )

    data class PlanSnapshotRow(
        @ColumnInfo(name = "item_id") val itemId: String,
        @ColumnInfo(name = "type_code") val typeCode: String,
        @ColumnInfo(name = "type_name") val typeName: String,
        @ColumnInfo(name = "brand_name") val brandName: String,
        @ColumnInfo(name = "serving_amount") val servingAmount: Double,
        @ColumnInfo(name = "serving_unit") val servingUnit: String,
        @ColumnInfo(name = "active_ingredient_amount") val activeIngredientAmount: Double?,
        @ColumnInfo(name = "active_ingredient_unit") val activeIngredientUnit: String,
        @ColumnInfo(name = "ingredient_details") val ingredientDetails: String,
        @ColumnInfo(name = "times_per_day") val timesPerDay: Long,
        @ColumnInfo(name = "timing_label") val timingLabel: String
    )

    @Query(
        "SELECT i.id AS item_id, s.id AS schedule_id, " +
            "COALESCE(NULLIF(s.type_code_snapshot,''), i.supplement_type_code) AS type_code, " +
            "COALESCE(NULLIF(s.type_name_snapshot,''), i.supplement_type_name) AS type_name, " +
            "COALESCE(NULLIF(s.brand_name_snapshot,''), i.brand_name) AS brand_name, " +
            "COALESCE(NULLIF(s.product_form_snapshot,''), i.product_form) AS product_form, " +
            "COALESCE(NULLIF(s.purpose_code_snapshot,''), i.purpose_code) AS purpose_code, " +
            "COALESCE(s.serving_amount,s.dose_amount) AS serving_amount, " +
            "COALESCE(s.serving_unit,s.dose_unit) AS serving_unit, " +
            "s.active_ingredient_amount AS active_ingredient_amount, " +
            "COALESCE(s.active_ingredient_unit,'') AS active_ingredient_unit, " +
            "COALESCE(s.ingredient_details,'') AS ingredient_details, " +
            "s.times_per_day AS times_per_day, s.timing_label AS timing_label, " +
            "s.effective_from AS effective_from, s.effective_to AS effective_to, " +
            "CASE WHEN i.is_active=1 AND i.deleted_at IS NULL AND s.is_active=1 " +
            "AND s.effective_to IS NULL THEN 1 ELSE 0 END AS is_current, " +
            "COALESCE(SUM(CASE WHEN r.status='taken' THEN 1 ELSE 0 END),0) AS taken_count, " +
            "COALESCE(SUM(CASE WHEN r.status='skipped' THEN 1 ELSE 0 END),0) AS skipped_count " +
            "FROM supplement_schedules s INNER JOIN supplement_items i " +
            "ON i.id=s.supplement_item_id AND i.user_id=s.user_id " +
            "LEFT JOIN supplement_intake_records r ON r.schedule_id=s.id " +
            "AND r.user_id=s.user_id AND r.date=:date " +
            "WHERE s.user_id=:userId AND s.deleted_at IS NULL AND s.effective_from<=:date " +
            "AND (s.effective_to IS NULL OR s.effective_to>=:date) " +
            "GROUP BY i.id,s.id ORDER BY s.created_at ASC"
    )
    fun activePlans(userId: String, date: String): List<ActivePlanRow>

    @Query(
        "SELECT s.supplement_item_id AS item_id, " +
            "COALESCE(NULLIF(s.type_code_snapshot,''),i.supplement_type_code) AS type_code, " +
            "COALESCE(NULLIF(s.type_name_snapshot,''),i.supplement_type_name) AS type_name, " +
            "COALESCE(NULLIF(s.brand_name_snapshot,''),i.brand_name) AS brand_name, " +
            "COALESCE(s.serving_amount,s.dose_amount) AS serving_amount, " +
            "COALESCE(s.serving_unit,s.dose_unit) AS serving_unit, " +
            "s.active_ingredient_amount AS active_ingredient_amount, " +
            "COALESCE(s.active_ingredient_unit,'') AS active_ingredient_unit, " +
            "COALESCE(s.ingredient_details,'') AS ingredient_details, " +
            "s.times_per_day AS times_per_day, s.timing_label AS timing_label " +
            "FROM supplement_schedules s JOIN supplement_items i " +
            "ON i.id=s.supplement_item_id AND i.user_id=s.user_id " +
            "WHERE s.id=:scheduleId AND s.user_id=:userId AND s.deleted_at IS NULL " +
            "AND s.effective_from<=:date AND (s.effective_to IS NULL OR s.effective_to>=:date) LIMIT 1"
    )
    fun planSnapshot(scheduleId: String, userId: String, date: String): PlanSnapshotRow?

    @Query(
        "SELECT * FROM supplement_schedule_slots WHERE user_id=:userId AND schedule_id=:scheduleId " +
            "AND deleted_at IS NULL ORDER BY slot_index"
    )
    fun visibleSlots(userId: String, scheduleId: String): List<SupplementScheduleSlotsRoomEntity>

    @Query(
        "SELECT * FROM supplement_schedule_slots WHERE user_id=:userId AND schedule_id=:scheduleId " +
            "AND slot_index=:slotIndex LIMIT 1"
    )
    fun slot(userId: String, scheduleId: String, slotIndex: Int): SupplementScheduleSlotsRoomEntity?

    @Query(
        "SELECT dose_index FROM supplement_intake_records WHERE user_id=:userId " +
            "AND schedule_id=:scheduleId AND date=:date"
    )
    fun usedDoseIndexes(userId: String, scheduleId: String, date: String): List<Long>

    @Query(
        "SELECT * FROM supplement_intake_records WHERE user_id=:userId AND date BETWEEN :startDate AND :endDate " +
            "ORDER BY date DESC,created_at DESC,dose_index DESC"
    )
    fun history(userId: String, startDate: String, endDate: String): List<SupplementIntakeRecordsRoomEntity>

    @Query(
        "SELECT id FROM supplement_intake_records WHERE user_id=:userId AND schedule_id=:scheduleId " +
            "AND date=:date ORDER BY dose_index DESC LIMIT 1"
    )
    fun latestRecordId(userId: String, scheduleId: String, date: String): String?

    @Query(
        "SELECT id FROM supplement_effect_checkins WHERE user_id=:userId AND supplement_item_id=:itemId " +
            "AND date=:date LIMIT 1"
    )
    fun effectCheckinId(userId: String, itemId: String, date: String): String?

    @Query(
        "SELECT * FROM supplement_effect_checkins WHERE user_id=:userId AND supplement_item_id=:itemId " +
            "ORDER BY date DESC LIMIT 1"
    )
    fun latestEffectCheckin(userId: String, itemId: String): SupplementEffectCheckinsRoomEntity?

    @Query("SELECT 1 FROM supplement_items WHERE id=:itemId AND user_id=:userId LIMIT 1")
    fun ownedItem(itemId: String, userId: String): Int?

    @Query(
        "SELECT 1 FROM supplement_items i JOIN supplement_schedules s " +
            "ON s.supplement_item_id=i.id AND s.user_id=i.user_id " +
            "WHERE i.id=:itemId AND s.id=:scheduleId AND i.user_id=:userId " +
            "AND i.is_active=1 AND i.deleted_at IS NULL AND s.is_active=1 " +
            "AND s.deleted_at IS NULL LIMIT 1"
    )
    fun ownedActivePlan(itemId: String, scheduleId: String, userId: String): Int?

    @Query("SELECT 1 FROM supplement_intake_records WHERE user_id=:userId AND schedule_id=:scheduleId AND date=:date LIMIT 1")
    fun hasRecords(userId: String, scheduleId: String, date: String): Int?

    @Query("SELECT COALESCE(MAX(revision),0) FROM supplement_schedules WHERE user_id=:userId AND supplement_item_id=:itemId")
    fun maxRevision(userId: String, itemId: String): Long

    @Query("UPDATE supplement_items SET user_id=:nextUserId WHERE user_id=:sourceUserId")
    fun claimItems(sourceUserId: String, nextUserId: String): Int

    @Query("UPDATE supplement_schedules SET user_id=:nextUserId WHERE user_id=:sourceUserId")
    fun claimSchedules(sourceUserId: String, nextUserId: String): Int

    @Query("UPDATE supplement_schedule_slots SET user_id=:nextUserId WHERE user_id=:sourceUserId")
    fun claimSlots(sourceUserId: String, nextUserId: String): Int

    @Query("UPDATE supplement_intake_records SET user_id=:nextUserId WHERE user_id=:sourceUserId")
    fun claimIntakeRecords(sourceUserId: String, nextUserId: String): Int

    @Query("UPDATE supplement_effect_checkins SET user_id=:nextUserId WHERE user_id=:sourceUserId")
    fun claimEffectCheckins(sourceUserId: String, nextUserId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertItem(item: SupplementItemsRoomEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSchedule(schedule: SupplementSchedulesRoomEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSlot(slot: SupplementScheduleSlotsRoomEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertIntakeRecord(record: SupplementIntakeRecordsRoomEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertEffectCheckin(checkin: SupplementEffectCheckinsRoomEntity)

    @Query(
        "UPDATE supplement_schedules SET is_active=0, effective_to=:effectiveTo, updated_at=:updatedAt " +
            "WHERE id=:scheduleId AND user_id=:userId"
    )
    fun closeSchedule(scheduleId: String, userId: String, effectiveTo: String, updatedAt: String): Int

    @Query(
        "UPDATE supplement_items SET supplement_type_code=:typeCode, supplement_type_name=:typeName, " +
            "brand_name=:brandName, product_form=:productForm, purpose_code=:purposeCode, " +
            "is_active=1, deleted_at=NULL, updated_at=:updatedAt WHERE id=:itemId AND user_id=:userId"
    )
    fun refreshItem(
        itemId: String, userId: String, typeCode: String, typeName: String, brandName: String,
        productForm: String, purposeCode: String, updatedAt: String
    ): Int

    @Query(
        "UPDATE supplement_intake_records SET status=:status, taken_at=:takenAt, updated_at=:updatedAt " +
            "WHERE id=:recordId AND user_id=:userId"
    )
    fun updateRecordStatus(recordId: String, userId: String, status: String, takenAt: String?, updatedAt: String): Int

    @Query("DELETE FROM supplement_intake_records WHERE id=:recordId AND user_id=:userId")
    fun deleteRecord(recordId: String, userId: String): Int

    @Query(
        "UPDATE supplement_items SET is_active=0, updated_at=:updatedAt, deleted_at=:deletedAt " +
            "WHERE id=:itemId AND user_id=:userId AND deleted_at IS NULL"
    )
    fun archiveItem(itemId: String, userId: String, updatedAt: String, deletedAt: String): Int

    @Query(
        "UPDATE supplement_schedules SET is_active=0, effective_to=:effectiveTo, updated_at=:updatedAt " +
            "WHERE supplement_item_id=:itemId AND user_id=:userId AND is_active=1 AND deleted_at IS NULL"
    )
    fun archiveSchedules(itemId: String, userId: String, effectiveTo: String, updatedAt: String): Int

    @Query(
        "UPDATE supplement_effect_checkins SET supplement_item_id=:itemId, date=:date, effect_score=:effectScore, " +
            "adverse_effects=:adverseEffects, note=:note, updated_at=:updatedAt " +
            "WHERE id=:checkinId AND user_id=:userId"
    )
    fun updateEffectCheckin(
        checkinId: String, userId: String, itemId: String, date: String, effectScore: Int,
        adverseEffects: String, note: String, updatedAt: String
    ): Int
}

@Dao
interface WorkoutRoomDao {
    data class WeekProgress(
        @ColumnInfo(name = "completed_sessions") val completedSessions: Int,
        @ColumnInfo(name = "completed_days") val completedDays: Int
    )

    data class BodyPartSetCount(
        @ColumnInfo(name = "ui_part") val uiPart: String?,
        @ColumnInfo(name = "set_count") val setCount: Int
    )

    data class ExerciseHistoryCandidate(
        @ColumnInfo(name = "record_id") val recordId: String,
        val date: String,
        @ColumnInfo(name = "exercise_name") val exerciseName: String
    )

    data class BestSetRow(
        @ColumnInfo(name = "record_id") val recordId: String,
        val date: String,
        @ColumnInfo(name = "weight_kg") val weightKg: Double?,
        @ColumnInfo(name = "actual_reps") val actualReps: Long?,
        @ColumnInfo(name = "added_weight_kg") val addedWeightKg: Double?,
        @ColumnInfo(name = "assisted_weight_kg") val assistedWeightKg: Double?,
        @ColumnInfo(name = "load_state") val loadState: String?
    )

    @Query(
        "SELECT * FROM workout_records WHERE id=:recordId AND user_id=:userId AND deleted_at IS NULL LIMIT 1"
    )
    fun visibleRecord(recordId: String, userId: String): WorkoutRecordsRoomEntity?

    @Query(
        "SELECT * FROM workout_records WHERE user_id=:userId AND deleted_at IS NULL " +
            "AND scope IN ('fitness','both') AND date=:date"
    )
    fun visibleRecordsForDate(userId: String, date: String): List<WorkoutRecordsRoomEntity>

    @Query(
        "SELECT * FROM workout_records WHERE user_id=:userId AND deleted_at IS NULL " +
            "AND scope IN ('fitness','both') ORDER BY updated_at DESC LIMIT :limit"
    )
    fun recentVisibleRecords(userId: String, limit: Int): List<WorkoutRecordsRoomEntity>

    @Query(
        "SELECT * FROM workout_records WHERE user_id=:userId AND deleted_at IS NULL " +
            "AND scope IN ('fitness','both') AND workout_type='strength' " +
            "ORDER BY date DESC, updated_at DESC"
    )
    fun strengthRecords(userId: String): List<WorkoutRecordsRoomEntity>

    @Query(
        "SELECT * FROM workout_records WHERE user_id=:userId AND deleted_at IS NULL " +
            "AND scope IN ('fitness','both') AND id != :currentRecordId " +
            "ORDER BY date DESC, updated_at DESC LIMIT :limit"
    )
    fun recentRecordsExcept(userId: String, currentRecordId: String, limit: Int): List<WorkoutRecordsRoomEntity>

    @Query(
        "SELECT * FROM workout_exercises WHERE record_id=:recordId AND user_id=:userId " +
            "AND deleted_at IS NULL ORDER BY order_index"
    )
    fun visibleExercises(recordId: String, userId: String): List<WorkoutExercisesRoomEntity>

    @Query(
        "SELECT * FROM workout_exercises WHERE id=:exerciseId AND user_id=:userId " +
            "AND deleted_at IS NULL LIMIT 1"
    )
    fun visibleExercise(exerciseId: String, userId: String): WorkoutExercisesRoomEntity?

    @Query(
        "SELECT * FROM workout_sets WHERE workout_exercise_id=:exerciseId AND user_id=:userId " +
            "AND deleted_at IS NULL ORDER BY set_index"
    )
    fun visibleSets(exerciseId: String, userId: String): List<WorkoutSetsRoomEntity>

    @Query(
        "SELECT * FROM workout_sets WHERE id=:setId AND user_id=:userId AND deleted_at IS NULL LIMIT 1"
    )
    fun visibleSet(setId: String, userId: String): WorkoutSetsRoomEntity?

    @Query(
        "SELECT we.record_id AS record_id, wr.date AS date, wr.exercise_name AS exercise_name " +
            "FROM workout_exercises we INNER JOIN workout_records wr ON wr.id=we.record_id " +
            "AND wr.deleted_at IS NULL WHERE wr.user_id=:userId AND we.user_id=:userId " +
            "AND we.deleted_at IS NULL AND we.record_id != :currentRecordId " +
            "AND wr.scope IN ('fitness','both') AND ((we.exercise_id != 'manual' AND we.exercise_id=:exerciseId) " +
            "OR (we.exercise_id='manual' AND we.exercise_name_snapshot=:exerciseName)) " +
            "ORDER BY wr.date DESC, wr.updated_at DESC LIMIT 100"
    )
    fun exerciseHistoryCandidates(
        userId: String, currentRecordId: String, exerciseId: String, exerciseName: String
    ): List<ExerciseHistoryCandidate>

    @Query(
        "SELECT we.id AS record_id, wr.date AS date, wr.exercise_name AS exercise_name " +
            "FROM workout_exercises we INNER JOIN workout_records wr ON wr.id=we.record_id " +
            "AND wr.deleted_at IS NULL WHERE wr.user_id=:userId AND we.user_id=:userId " +
            "AND we.deleted_at IS NULL AND we.record_id != :currentRecordId " +
            "AND wr.scope IN ('fitness','both') AND ((we.exercise_id != 'manual' AND we.exercise_id=:exerciseId) " +
            "OR (we.exercise_id='manual' AND we.exercise_name_snapshot=:exerciseName)) " +
            "ORDER BY wr.date DESC, wr.updated_at DESC LIMIT 1"
    )
    fun lastExerciseCandidate(
        userId: String, currentRecordId: String, exerciseId: String, exerciseName: String
    ): ExerciseHistoryCandidate?

    @Query(
        "SELECT we.record_id AS record_id, wr.date AS date, ws.weight_kg AS weight_kg, " +
            "ws.actual_reps AS actual_reps, ws.added_weight_kg AS added_weight_kg, " +
            "ws.assisted_weight_kg AS assisted_weight_kg, ws.load_state AS load_state " +
            "FROM workout_sets ws INNER JOIN workout_exercises we ON we.id=ws.workout_exercise_id " +
            "INNER JOIN workout_records wr ON wr.id=we.record_id WHERE wr.user_id=:userId " +
            "AND we.user_id=:userId AND ws.user_id=:userId AND we.record_id != :currentRecordId " +
            "AND we.deleted_at IS NULL AND ws.deleted_at IS NULL AND ws.is_completed=1 " +
            "AND ((we.exercise_id != 'manual' AND we.exercise_id=:exerciseId) " +
            "OR (we.exercise_id='manual' AND we.exercise_name_snapshot=:exerciseName))"
    )
    fun bestSetRows(
        userId: String, currentRecordId: String, exerciseId: String, exerciseName: String
    ): List<BestSetRow>

    @Query(
        "SELECT ui_part FROM workout_exercises WHERE record_id=:recordId AND user_id=:userId " +
            "AND deleted_at IS NULL ORDER BY order_index"
    )
    fun visibleUiParts(recordId: String, userId: String): List<String>

    @Query(
        "SELECT COALESCE(MAX(order_index),0)+1 FROM workout_exercises WHERE record_id=:recordId " +
            "AND user_id=:userId AND deleted_at IS NULL"
    )
    fun nextExerciseOrder(recordId: String, userId: String): Int

    @Query("SELECT 1 FROM workout_records WHERE id=:recordId AND user_id=:userId AND deleted_at IS NULL LIMIT 1")
    fun ownsRecord(recordId: String, userId: String): Int?

    @Query(
        "SELECT 1 FROM workout_exercises WHERE id=:exerciseId AND record_id=:recordId AND user_id=:userId " +
            "AND deleted_at IS NULL LIMIT 1"
    )
    fun ownsExercise(exerciseId: String, recordId: String, userId: String): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertRecord(record: WorkoutRecordsRoomEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertExercise(exercise: WorkoutExercisesRoomEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSet(set: WorkoutSetsRoomEntity)

    @Update
    fun updateRecord(record: WorkoutRecordsRoomEntity): Int

    @Update
    fun updateExercise(exercise: WorkoutExercisesRoomEntity): Int

    @Update
    fun updateSet(set: WorkoutSetsRoomEntity): Int

    @Query(
        "UPDATE workout_records SET duration_seconds=:durationSeconds, is_backfilled=1, " +
            "backfilled_at=:backfilledAt, backfill_reason='manual_entry', metadata=:metadata " +
            "WHERE id=:recordId AND user_id=:userId AND deleted_at IS NULL"
    )
    fun updateManualPastSession(
        recordId: String, userId: String, durationSeconds: Int, backfilledAt: String, metadata: String
    ): Int

    @Query("UPDATE workout_records SET metadata=:metadata WHERE id=:recordId AND user_id=:userId AND deleted_at IS NULL")
    fun updateRecordMetadata(recordId: String, userId: String, metadata: String): Int

    @Query(
        "UPDATE workout_sets SET deleted_at=:deletedAt, updated_at=:updatedAt " +
            "WHERE workout_exercise_id=:exerciseId AND user_id=:userId AND deleted_at IS NULL"
    )
    fun tombstoneSetsForExercise(exerciseId: String, userId: String, deletedAt: String, updatedAt: String): Int

    @Query(
        "UPDATE workout_exercises SET deleted_at=:deletedAt, updated_at=:updatedAt " +
            "WHERE record_id=:recordId AND user_id=:userId AND deleted_at IS NULL"
    )
    fun tombstoneExercisesForRecord(recordId: String, userId: String, deletedAt: String, updatedAt: String): Int

    @Query(
        "UPDATE workout_records SET deleted_at=:deletedAt, updated_at=:updatedAt " +
            "WHERE id=:recordId AND user_id=:userId AND deleted_at IS NULL"
    )
    fun tombstoneRecord(recordId: String, userId: String, deletedAt: String, updatedAt: String): Int

    @Query(
        "UPDATE workout_sets SET target_reps=:targetReps, actual_reps=:actualReps, weight_kg=:weightKg, " +
            "duration_seconds=:durationSeconds, distance_meters=:distanceMeters, rest_seconds=:restSeconds, " +
            "assisted_weight_kg=:assistedWeightKg, added_weight_kg=:addedWeightKg, load_state=:loadState, " +
            "input_load_value=:inputLoadValue, input_load_unit=:inputLoadUnit, is_completed=:isCompleted, " +
            "volume_kg=:volumeKg, rir=:rir, updated_at=:updatedAt WHERE id=:setId AND user_id=:userId " +
            "AND workout_exercise_id IN (SELECT id FROM workout_exercises WHERE record_id=:recordId AND user_id=:userId) " +
            "AND deleted_at IS NULL"
    )
    fun updateSet(
        setId: String, userId: String, recordId: String, targetReps: Int?, actualReps: Int?, weightKg: Double?,
        durationSeconds: Int?, distanceMeters: Double?, restSeconds: Int?, assistedWeightKg: Double?,
        addedWeightKg: Double?, loadState: String?, inputLoadValue: Double?, inputLoadUnit: String?,
        isCompleted: Int, volumeKg: Double, rir: Int?, updatedAt: String
    ): Int

    @Query(
        "UPDATE workout_sets SET target_reps=NULL, actual_reps=NULL, weight_kg=NULL, volume_kg=0, " +
            "duration_seconds=:durationSeconds, distance_meters=:distanceMeters, rest_seconds=NULL, " +
            "assisted_weight_kg=NULL, added_weight_kg=NULL, input_load_value=NULL, input_load_unit=NULL, " +
            "load_state=NULL, is_completed=1, rpe=NULL, rir=NULL, memo=NULL, updated_at=:updatedAt " +
            "WHERE id=:setId AND user_id=:userId AND deleted_at IS NULL"
    )
    fun updateCardioSet(
        setId: String, userId: String, durationSeconds: Int, distanceMeters: Double, updatedAt: String
    ): Int

    @Query(
        "UPDATE workout_sets SET deleted_at=:deletedAt, updated_at=:updatedAt WHERE id=:setId AND user_id=:userId " +
            "AND workout_exercise_id IN (SELECT id FROM workout_exercises WHERE record_id=:recordId AND user_id=:userId) " +
            "AND deleted_at IS NULL"
    )
    fun tombstoneSet(setId: String, userId: String, recordId: String, deletedAt: String, updatedAt: String): Int

    @Query(
        "UPDATE workout_exercises SET deleted_at=:deletedAt, updated_at=:updatedAt " +
            "WHERE id=:exerciseId AND record_id=:recordId AND user_id=:userId AND deleted_at IS NULL"
    )
    fun tombstoneExercise(exerciseId: String, recordId: String, userId: String, deletedAt: String, updatedAt: String): Int

    @Query(
        "UPDATE workout_exercises SET exercise_id=:exerciseId, exercise_name_snapshot=:exerciseName, " +
            "ui_part=:uiPart, primary_sub_part_snapshot=:primarySubPart, equipment_snapshot=:equipment, " +
            "record_type=:recordType, family_id=:familyId, preset_id=:presetId, " +
            "canonical_variant_key=:canonicalVariantKey, visual_variant_key=:visualVariantKey, updated_at=:updatedAt " +
            "WHERE id=:oldExerciseId AND record_id=:recordId AND user_id=:userId AND deleted_at IS NULL"
    )
    fun replaceExercise(
        oldExerciseId: String, recordId: String, userId: String, exerciseId: String, exerciseName: String,
        uiPart: String, primarySubPart: String, equipment: String?, recordType: String, familyId: String?,
        presetId: String?, canonicalVariantKey: String?, visualVariantKey: String?, updatedAt: String
    ): Int

    @Query(
        "UPDATE workout_records SET total_volume_kg=:totalVolumeKg, updated_at=:updatedAt " +
            "WHERE id=:recordId AND user_id=:userId AND deleted_at IS NULL"
    )
    fun updateRecordTotal(recordId: String, userId: String, totalVolumeKg: Double, updatedAt: String): Int

    @Query(
        "UPDATE workout_records SET metadata=:metadata, duration_seconds=:durationSeconds, " +
            "total_volume_kg=:totalVolumeKg, updated_at=:updatedAt, category=:category, scope=:scope " +
            "WHERE id=:recordId AND user_id=:userId AND deleted_at IS NULL"
    )
    fun completeRecord(
        recordId: String, userId: String, metadata: String, durationSeconds: Int,
        totalVolumeKg: Double, updatedAt: String, category: String, scope: String
    ): Int

    @Query(
        "UPDATE workout_records SET metadata=:metadata, duration_seconds=:durationSeconds, " +
            "total_volume_kg=0, average_heart_rate=:averageHeartRate, exercise_name=:exerciseName, " +
            "category=:category, scope='both', updated_at=:updatedAt " +
            "WHERE id=:recordId AND user_id=:userId AND workout_type='cardio' AND deleted_at IS NULL"
    )
    fun completeCardioRecord(
        recordId: String,
        userId: String,
        metadata: String,
        durationSeconds: Int,
        averageHeartRate: Double?,
        exerciseName: String,
        category: String,
        updatedAt: String
    ): Int

    @Query(
        "UPDATE workout_records SET average_heart_rate=:averageHeartRate, metadata=:metadata, " +
            "updated_at=:updatedAt WHERE id=:recordId AND user_id=:userId " +
            "AND workout_type='cardio' AND deleted_at IS NULL"
    )
    fun updateCardioHeartRate(
        recordId: String, userId: String, averageHeartRate: Double?, metadata: String, updatedAt: String
    ): Int

    @Query(
        "SELECT COUNT(*) AS completed_sessions, COUNT(DISTINCT date) AS completed_days " +
            "FROM workout_records WHERE user_id=:userId AND deleted_at IS NULL " +
            "AND scope IN ('fitness','both') AND date BETWEEN :startDate AND :endDate " +
            "AND (source_app = 'os' OR metadata LIKE '%\"status\":\"completed\"%')"
    )
    fun completedWeekProgress(userId: String, startDate: String, endDate: String): WeekProgress

    @Query(
        "SELECT we.ui_part AS ui_part, COUNT(ws.id) AS set_count FROM workout_sets ws " +
            "INNER JOIN workout_exercises we ON we.id=ws.workout_exercise_id " +
            "AND we.user_id=ws.user_id AND we.deleted_at IS NULL " +
            "INNER JOIN workout_records wr ON wr.id=we.record_id " +
            "AND wr.user_id=we.user_id AND wr.deleted_at IS NULL " +
            "WHERE ws.user_id=:userId AND ws.deleted_at IS NULL AND ws.is_completed=1 " +
            "AND wr.scope IN ('fitness','both') AND wr.source_app='fitness' " +
            "AND wr.metadata LIKE '%\"status\":\"completed\"%' AND wr.workout_type='strength' " +
            "AND wr.date BETWEEN :startDate AND :endDate GROUP BY we.ui_part"
    )
    fun recentStrengthSetsByBodyPart(
        userId: String, startDate: String, endDate: String
    ): List<BodyPartSetCount>

    @Query(
        "SELECT MAX(wr.date) FROM workout_sets ws " +
            "INNER JOIN workout_exercises we ON we.id=ws.workout_exercise_id " +
            "AND we.user_id=ws.user_id AND we.deleted_at IS NULL " +
            "INNER JOIN workout_records wr ON wr.id=we.record_id " +
            "AND wr.user_id=we.user_id AND wr.deleted_at IS NULL " +
            "WHERE ws.user_id=:userId AND ws.deleted_at IS NULL AND ws.is_completed=1 " +
            "AND wr.scope IN ('fitness','both') AND wr.source_app='fitness' " +
            "AND wr.metadata LIKE '%\"status\":\"completed\"%' AND wr.workout_type='strength' " +
            "AND wr.date <= :referenceDate AND lower(trim(we.ui_part)) IN (:bodyPartAliases)"
    )
    fun latestDetailedTrainingDateForBodyPart(
        userId: String, referenceDate: String, bodyPartAliases: List<String>
    ): String?

    @Query(
        "SELECT COUNT(DISTINCT date) FROM workout_records WHERE user_id=:userId " +
            "AND deleted_at IS NULL AND scope IN ('fitness','both') " +
            "AND date BETWEEN :startDate AND :endDate " +
            "AND (source_app = 'os' OR metadata LIKE '%\"status\":\"completed\"%')"
    )
    fun completedWorkoutRecordedDays(userId: String, startDate: String, endDate: String): Int

    @Query(
        "SELECT DISTINCT date FROM workout_records WHERE user_id=:userId " +
            "AND deleted_at IS NULL AND scope IN ('fitness','both') " +
            "AND date BETWEEN :startDate AND :endDate " +
            "AND (source_app = 'os' OR metadata LIKE '%\"status\":\"completed\"%') ORDER BY date"
    )
    fun completedWorkoutDates(userId: String, startDate: String, endDate: String): List<String>

    @Query(
        "SELECT COUNT(*) FROM workout_records WHERE user_id=:userId AND deleted_at IS NULL " +
            "AND scope IN ('fitness','both') AND workout_type='strength' " +
            "AND date BETWEEN :startDate AND :endDate " +
            "AND (source_app = 'os' OR metadata LIKE '%\"status\":\"completed\"%')"
    )
    fun completedResistanceSessions(userId: String, startDate: String, endDate: String): Int
}

@Dao
interface CardioRoomDao {
    data class SessionRow(
        @ColumnInfo(name = "record_id") val recordId: String,
        @ColumnInfo(name = "activity_type") val activityType: String,
        val status: String,
        @ColumnInfo(name = "started_at_epoch_ms") val startedAtEpochMs: Long,
        @ColumnInfo(name = "last_resumed_at_epoch_ms") val lastResumedAtEpochMs: Long?,
        @ColumnInfo(name = "active_duration_ms") val activeDurationMs: Long,
        @ColumnInfo(name = "distance_meters") val distanceMeters: Double,
        @ColumnInfo(name = "accepted_point_count") val acceptedPointCount: Long,
        @ColumnInfo(name = "last_latitude") val lastLatitude: Double?,
        @ColumnInfo(name = "last_longitude") val lastLongitude: Double?,
        @ColumnInfo(name = "last_location_time_ms") val lastLocationTimeMs: Long?,
        @ColumnInfo(name = "last_accuracy_meters") val lastAccuracyMeters: Double?,
        @ColumnInfo(name = "gps_status") val gpsStatus: String,
        @ColumnInfo(name = "average_heart_rate") val averageHeartRate: Double?
    )

    data class RoutePointRow(
        @ColumnInfo(name = "captured_at_epoch_ms") val capturedAtEpochMs: Long,
        val latitude: Double,
        val longitude: Double
    )

    @Query(
        "SELECT record_id FROM cardio_sessions WHERE user_id=:userId " +
            "AND status IN ('tracking','paused') " +
            "ORDER BY started_at_epoch_ms DESC LIMIT 1"
    )
    fun activeRecordId(userId: String): String?

    @Query(
        "SELECT cs.record_id AS record_id, cs.activity_type AS activity_type, " +
            "cs.status AS status, cs.started_at_epoch_ms AS started_at_epoch_ms, " +
            "cs.last_resumed_at_epoch_ms AS last_resumed_at_epoch_ms, " +
            "cs.active_duration_ms AS active_duration_ms, cs.distance_meters AS distance_meters, " +
            "cs.accepted_point_count AS accepted_point_count, " +
            "cs.last_latitude AS last_latitude, cs.last_longitude AS last_longitude, " +
            "cs.last_location_time_ms AS last_location_time_ms, " +
            "cs.last_accuracy_meters AS last_accuracy_meters, cs.gps_status AS gps_status, " +
            "wr.average_heart_rate AS average_heart_rate " +
            "FROM cardio_sessions cs LEFT JOIN workout_records wr " +
            "ON wr.id=cs.record_id AND wr.user_id=cs.user_id " +
            "WHERE cs.record_id=:recordId AND cs.user_id=:userId LIMIT 1"
    )
    fun session(recordId: String, userId: String): SessionRow?

    @Query("SELECT 1 FROM cardio_sessions WHERE record_id=:recordId AND user_id=:userId LIMIT 1")
    fun ownsSession(recordId: String, userId: String): Int?

    @Query("SELECT COUNT(*) FROM cardio_route_points WHERE record_id=:recordId AND user_id=:userId")
    fun routePointCount(recordId: String, userId: String): Int

    @Query(
        "SELECT captured_at_epoch_ms AS captured_at_epoch_ms, latitude AS latitude, " +
            "longitude AS longitude FROM cardio_route_points " +
            "WHERE record_id=:recordId AND user_id=:userId " +
            "ORDER BY captured_at_epoch_ms ASC, id ASC"
    )
    fun routePoints(recordId: String, userId: String): List<RoutePointRow>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSession(session: CardioSessionsRoomEntity)

    @Query(
        "INSERT INTO cardio_route_points " +
            "(record_id,user_id,captured_at_epoch_ms,latitude,longitude,accuracy_meters,speed_mps,segment_distance_meters) " +
            "VALUES (:recordId,:userId,:capturedAtEpochMs,:latitude,:longitude,:accuracyMeters,:speedMps,:segmentDistanceMeters)"
    )
    fun insertRoutePoint(
        recordId: String,
        userId: String,
        capturedAtEpochMs: Long,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        speedMps: Double?,
        segmentDistanceMeters: Double
    )

    @Query(
        "UPDATE cardio_sessions SET status='paused', active_duration_ms=:activeDurationMs, " +
            "last_resumed_at_epoch_ms=NULL, gps_status='stopped', updated_at_epoch_ms=:updatedAtEpochMs " +
            "WHERE record_id=:recordId AND user_id=:userId AND status='tracking'"
    )
    fun pause(
        recordId: String, userId: String, activeDurationMs: Long, updatedAtEpochMs: Long
    ): Int

    @Query(
        "UPDATE cardio_sessions SET status='tracking', last_resumed_at_epoch_ms=:lastResumedAtEpochMs, " +
            "last_latitude=NULL, last_longitude=NULL, last_location_time_ms=NULL, " +
            "last_accuracy_meters=NULL, gps_status='searching', updated_at_epoch_ms=:updatedAtEpochMs " +
            "WHERE record_id=:recordId AND user_id=:userId AND status='paused'"
    )
    fun resume(recordId: String, userId: String, lastResumedAtEpochMs: Long, updatedAtEpochMs: Long): Int

    @Query(
        "UPDATE cardio_sessions SET distance_meters=:distanceMeters, accepted_point_count=:acceptedPointCount, " +
            "last_latitude=:lastLatitude, last_longitude=:lastLongitude, " +
            "last_location_time_ms=:lastLocationTimeMs, last_accuracy_meters=:lastAccuracyMeters, " +
            "gps_status='ready', updated_at_epoch_ms=:updatedAtEpochMs " +
            "WHERE record_id=:recordId AND user_id=:userId AND status='tracking'"
    )
    fun acceptLocation(
        recordId: String,
        userId: String,
        distanceMeters: Double,
        acceptedPointCount: Long,
        lastLatitude: Double,
        lastLongitude: Double,
        lastLocationTimeMs: Long,
        lastAccuracyMeters: Double,
        updatedAtEpochMs: Long
    ): Int

    @Query(
        "UPDATE cardio_sessions SET gps_status=:gpsStatus, updated_at_epoch_ms=:updatedAtEpochMs " +
            "WHERE record_id=:recordId AND user_id=:userId AND status IN ('tracking','paused')"
    )
    fun updateGpsStatus(recordId: String, userId: String, gpsStatus: String, updatedAtEpochMs: Long): Int

    @Query(
        "UPDATE cardio_sessions SET status='completed', active_duration_ms=:activeDurationMs, " +
            "last_resumed_at_epoch_ms=NULL, gps_status='stopped', updated_at_epoch_ms=:updatedAtEpochMs " +
            "WHERE record_id=:recordId AND user_id=:userId"
    )
    fun complete(recordId: String, userId: String, activeDurationMs: Long, updatedAtEpochMs: Long): Int

    @Query("DELETE FROM cardio_route_points WHERE record_id=:recordId AND user_id=:userId")
    fun deleteRoutePoints(recordId: String, userId: String): Int

    @Query("DELETE FROM cardio_sessions WHERE record_id=:recordId AND user_id=:userId")
    fun deleteSession(recordId: String, userId: String): Int
}

@Dao
interface MealRoomDao {
    data class MealReadRow(
        val id: String,
        val date: String,
        val menu: String,
        val calories: Long,
        @ColumnInfo(name = "protein_grams") val proteinGrams: Double?,
        @ColumnInfo(name = "carbs_grams") val carbsGrams: Double?,
        @ColumnInfo(name = "fat_grams") val fatGrams: Double?,
        val metadata: String,
        @ColumnInfo(name = "created_at") val createdAt: String,
        @ColumnInfo(name = "meal_kind") val mealKind: String,
        @ColumnInfo(name = "fulfillment_mode") val fulfillmentMode: String?,
        @ColumnInfo(name = "store_name") val storeName: String?,
        @ColumnInfo(name = "branch_name") val branchName: String?,
        @ColumnInfo(name = "menu_name") val menuName: String?,
        @ColumnInfo(name = "first_food_name") val firstFoodName: String?,
        @ColumnInfo(name = "composition_count") val compositionCount: Int,
        @ColumnInfo(name = "device_id") val deviceId: String
    )

    @Query(
        "SELECT COUNT(*) FROM meal_records WHERE deleted_at IS NULL " +
            "AND user_id=:userId AND scope IN ('fitness','both') AND date=:date"
    )
    fun mealCountForDate(userId: String, date: String): Long

    @Query(
        "SELECT r.id, r.date, r.menu, r.calories, r.protein_grams, r.carbs_grams, " +
            "r.fat_grams, r.metadata, r.created_at, r.meal_kind, r.fulfillment_mode, " +
            "r.store_name, r.branch_name, r.menu_name, " +
            "(SELECT i.food_name_snapshot FROM meal_record_items i " +
            "WHERE i.meal_record_id=r.id AND i.user_id=r.user_id AND i.deleted_at IS NULL " +
            "ORDER BY i.order_index, i.id LIMIT 1) AS first_food_name, " +
            "(SELECT COUNT(*) FROM meal_record_items i " +
            "WHERE i.meal_record_id=r.id AND i.user_id=r.user_id AND i.deleted_at IS NULL) " +
            "AS composition_count, r.device_id FROM meal_records r " +
            "WHERE r.user_id=:userId AND r.deleted_at IS NULL " +
            "AND r.scope IN ('fitness','both') AND r.date=:date " +
            "ORDER BY r.created_at, r.id"
    )
    fun visibleMealReadRows(userId: String, date: String): List<MealReadRow>

    @Query(
        "SELECT r.id, r.date, r.menu, r.calories, r.protein_grams, r.carbs_grams, " +
            "r.fat_grams, r.metadata, r.created_at, r.meal_kind, r.fulfillment_mode, " +
            "r.store_name, r.branch_name, r.menu_name, " +
            "(SELECT i.food_name_snapshot FROM meal_record_items i " +
            "WHERE i.meal_record_id=r.id AND i.user_id=r.user_id AND i.deleted_at IS NULL " +
            "ORDER BY i.order_index, i.id LIMIT 1) AS first_food_name, " +
            "(SELECT COUNT(*) FROM meal_record_items i " +
            "WHERE i.meal_record_id=r.id AND i.user_id=r.user_id AND i.deleted_at IS NULL) " +
            "AS composition_count, r.device_id FROM meal_records r " +
            "WHERE r.user_id=:userId AND r.deleted_at IS NULL " +
            "AND r.scope IN ('fitness','both') AND r.date BETWEEN :startDate AND :endDate " +
            "ORDER BY r.created_at, r.id"
    )
    fun visibleMealReadRowsBetween(
        userId: String,
        startDate: String,
        endDate: String
    ): List<MealReadRow>

    @Query(
        "SELECT COUNT(DISTINCT date) FROM meal_records WHERE user_id=:userId " +
            "AND deleted_at IS NULL AND scope IN ('fitness','both') " +
            "AND date BETWEEN :startDate AND :endDate"
    )
    fun visibleMealRecordedDays(userId: String, startDate: String, endDate: String): Int

    @Query(
        "SELECT DISTINCT date FROM meal_records WHERE user_id=:userId " +
            "AND deleted_at IS NULL AND scope IN ('fitness','both') " +
            "AND date BETWEEN :startDate AND :endDate ORDER BY date"
    )
    fun visibleMealDates(userId: String, startDate: String, endDate: String): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertRecord(record: MealRecordsRoomEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertItem(item: MealRecordItemsRoomEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertNutrient(nutrient: MealRecordItemNutrientsRoomEntity)
}

@Dao
interface DevelopmentRoomDao {
    data class CheckInStats(
        @ColumnInfo(name = "recorded_days") val recordedDays: Int,
        @ColumnInfo(name = "low_energy_or_readiness_days") val lowEnergyOrReadinessDays: Int
    )

    data class CheckInSummary(
        @ColumnInfo(name = "average_sleep_hours") val averageSleepHours: Double?,
        @ColumnInfo(name = "sleep_recorded_days") val sleepRecordedDays: Int,
        @ColumnInfo(name = "low_energy_or_readiness_days") val lowEnergyOrReadinessDays: Int
    )

    data class LatestCheckIn(
        @ColumnInfo(name = "energy_score") val energyScore: Long?,
        @ColumnInfo(name = "training_readiness_score") val trainingReadinessScore: Long?
    )

    @Query("SELECT * FROM development_goals WHERE user_id=:userId LIMIT 1")
    fun goal(userId: String): DevelopmentGoalsRoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun replaceGoal(goal: DevelopmentGoalsRoomEntity)

    @Query("DELETE FROM development_goals WHERE user_id=:userId")
    fun deleteGoal(userId: String): Int

    @Query("SELECT * FROM nutrition_goals WHERE user_id=:userId LIMIT 1")
    fun nutritionGoal(userId: String): NutritionGoalsRoomEntity?

    @Query(
        "SELECT COUNT(*) AS recorded_days, COALESCE(SUM(CASE " +
            "WHEN ((energy_score IS NOT NULL AND energy_score <= 2) " +
            "OR (training_readiness_score IS NOT NULL AND training_readiness_score <= 2)) " +
            "THEN 1 ELSE 0 END), 0) AS low_energy_or_readiness_days " +
            "FROM nutrition_daily_checkins WHERE user_id=:userId " +
            "AND date BETWEEN :startDate AND :endDate"
    )
    fun recentCheckInStats(userId: String, startDate: String, endDate: String): CheckInStats

    @Query(
        "SELECT AVG(sleep_hours) AS average_sleep_hours, COUNT(sleep_hours) AS sleep_recorded_days, " +
            "COALESCE(SUM(CASE WHEN ((energy_score IS NOT NULL AND energy_score <= 2) " +
            "OR (training_readiness_score IS NOT NULL AND training_readiness_score <= 2)) " +
            "THEN 1 ELSE 0 END), 0) AS low_energy_or_readiness_days " +
            "FROM nutrition_daily_checkins WHERE user_id=:userId " +
            "AND date BETWEEN :startDate AND :endDate"
    )
    fun recentCheckInSummary(userId: String, startDate: String, endDate: String): CheckInSummary

    @Query(
        "SELECT energy_score, training_readiness_score FROM nutrition_daily_checkins " +
            "WHERE user_id=:userId AND date BETWEEN :startDate AND :endDate " +
            "ORDER BY date DESC, updated_at DESC LIMIT 1"
    )
    fun latestCheckIn(userId: String, startDate: String, endDate: String): LatestCheckIn?

    @Query(
        "SELECT DISTINCT date FROM nutrition_daily_checkins WHERE user_id=:userId " +
            "AND date BETWEEN :startDate AND :endDate ORDER BY date"
    )
    fun checkInDates(userId: String, startDate: String, endDate: String): List<String>
}

@Dao
interface NutritionRoomDao {
    data class ProductLinkRow(
        val id: String,
        @ColumnInfo(name = "owner_id") val ownerId: String,
        @ColumnInfo(name = "nutrition_food_id") val nutritionFoodId: String,
        @ColumnInfo(name = "catalog_product_id") val catalogProductId: String,
        @ColumnInfo(name = "standard_product_id") val standardProductId: String?,
        val status: String,
        @ColumnInfo(name = "source_type") val sourceType: String,
        @ColumnInfo(name = "proposal_reference") val proposalReference: String?,
        val revision: Long,
        @ColumnInfo(name = "reviewed_at") val reviewedAt: String?,
        @ColumnInfo(name = "catalog_product_revision") val catalogProductRevision: String?,
        @ColumnInfo(name = "catalog_content_amount") val catalogContentAmount: Double?,
        @ColumnInfo(name = "catalog_content_unit") val catalogContentUnit: String?,
        @ColumnInfo(name = "catalog_package_count") val catalogPackageCount: Long?,
        @ColumnInfo(name = "cache_standard_product_id") val cacheStandardProductId: String?,
        @ColumnInfo(name = "product_name") val productName: String?,
        @ColumnInfo(name = "brand_name") val brandName: String?,
        @ColumnInfo(name = "manufacturer_name") val manufacturerName: String?,
        @ColumnInfo(name = "sub_brand_name") val subBrandName: String?,
        @ColumnInfo(name = "seller_name") val sellerName: String?,
        @ColumnInfo(name = "latest_price_krw") val latestPriceKrw: Long?,
        @ColumnInfo(name = "price_observed_at") val priceObservedAt: String?,
        @ColumnInfo(name = "content_amount") val contentAmount: Double?,
        @ColumnInfo(name = "content_unit") val contentUnit: String?,
        @ColumnInfo(name = "package_count") val packageCount: Long?,
        @ColumnInfo(name = "cache_catalog_product_revision") val cacheCatalogProductRevision: String?
    )

    @Query(
        "SELECT * FROM nutrition_foods WHERE deleted_at IS NULL " +
            "AND (visibility='public' OR owner_id=:userId) " +
            "AND COALESCE(source_type,'') <> :excludedSourceType " +
            "AND (name LIKE :like COLLATE NOCASE OR brand LIKE :like COLLATE NOCASE) " +
            "ORDER BY kind ASC, brand COLLATE NOCASE ASC, name COLLATE NOCASE ASC LIMIT 100"
    )
    fun searchFoods(userId: String, excludedSourceType: String, like: String): List<NutritionFoodsRoomEntity>

    @Query(
        "SELECT * FROM nutrition_foods WHERE id=:foodId AND deleted_at IS NULL " +
            "AND (visibility='public' OR owner_id=:userId) LIMIT 1"
    )
    fun visibleFood(foodId: String, userId: String): NutritionFoodsRoomEntity?

    @Query(
        "SELECT * FROM nutrition_foods WHERE deleted_at IS NULL " +
            "AND (visibility='public' OR owner_id=:userId) " +
            "ORDER BY updated_at DESC, id ASC"
    )
    fun visibleFoods(userId: String): List<NutritionFoodsRoomEntity>

    @Query(
        "SELECT * FROM nutrition_foods WHERE owner_id=:userId AND deleted_at IS NULL " +
            "AND kind=:kind AND source_type=:sourceType " +
            "ORDER BY updated_at DESC, id ASC"
    )
    fun ownedFoodsByKindAndSource(
        userId: String, kind: String, sourceType: String
    ): List<NutritionFoodsRoomEntity>

    @Query(
        "SELECT * FROM nutrition_foods WHERE owner_id=:userId AND deleted_at IS NULL " +
            "AND kind=:kind ORDER BY updated_at DESC, id ASC"
    )
    fun ownedFoodsByKind(userId: String, kind: String): List<NutritionFoodsRoomEntity>

    @Query(
        "SELECT * FROM nutrition_foods WHERE owner_id=:userId AND deleted_at IS NULL " +
            "AND kind=:kind AND source_type IN (:sourceTypes) " +
            "ORDER BY updated_at DESC, id ASC"
    )
    fun ownedFoodsByKindAndSources(
        userId: String, kind: String, sourceTypes: List<String>
    ): List<NutritionFoodsRoomEntity>

    @Query(
        "SELECT * FROM nutrition_foods WHERE owner_id IS NULL AND visibility='public' " +
            "AND kind=:kind AND id LIKE :idPattern " +
            "AND ((source_type=:sourceType AND source_reference LIKE :sourceReferencePattern) " +
            "OR (source_type=:riceSourceType AND source_reference=:riceSourceReference)) " +
            "ORDER BY brand COLLATE NOCASE ASC, name COLLATE NOCASE ASC"
    )
    fun verifiedFoods(
        kind: String,
        idPattern: String,
        sourceType: String,
        sourceReferencePattern: String,
        riceSourceType: String,
        riceSourceReference: String
    ): List<NutritionFoodsRoomEntity>

    @Query(
        "SELECT * FROM nutrition_food_nutrients WHERE deleted_at IS NULL " +
            "AND amount IS NOT NULL AND food_id IN (:foodIds)"
    )
    fun nutrientsForFoods(foodIds: List<String>): List<NutritionFoodNutrientsRoomEntity>

    @Query(
        "SELECT * FROM nutrition_food_components WHERE parent_food_id=:parentFoodId " +
            "AND deleted_at IS NULL ORDER BY order_index ASC, created_at ASC"
    )
    fun componentsForFood(parentFoodId: String): List<NutritionFoodComponentsRoomEntity>

    @Query(
        "SELECT * FROM nutrition_foods WHERE id IN (:foodIds) AND deleted_at IS NULL " +
            "AND (visibility='public' OR owner_id=:userId)"
    )
    fun visibleFoodsByIds(foodIds: List<String>, userId: String): List<NutritionFoodsRoomEntity>

    @Query(
        "SELECT 1 FROM nutrition_foods WHERE id=:foodId AND owner_id=:userId " +
            "AND kind=:kind AND deleted_at IS NULL LIMIT 1"
    )
    fun ownsActiveFood(foodId: String, userId: String, kind: String): Int?

    @Query("SELECT * FROM nutrition_foods WHERE id=:foodId AND owner_id=:userId AND kind=:kind AND deleted_at IS NULL LIMIT 1")
    fun ownedActiveFood(foodId: String, userId: String, kind: String): NutritionFoodsRoomEntity?

    @Query("DELETE FROM nutrition_food_nutrients WHERE food_id=:foodId")
    fun deleteNutrients(foodId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertFood(food: NutritionFoodsRoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertFood(food: NutritionFoodsRoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertNutrient(nutrient: NutritionFoodNutrientsRoomEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertComponent(component: NutritionFoodComponentsRoomEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertComponentLink(link: DiningOutMenuComponentLinksRoomEntity)

    @Query(
        "SELECT component_food_id FROM dining_out_menu_component_links " +
            "WHERE user_id=:userId AND menu_food_id=:menuFoodId AND deleted_at IS NULL " +
            "AND (:groupType IS NULL OR group_type=:groupType) " +
            "ORDER BY created_at ASC, id ASC"
    )
    fun componentIdsForMenu(userId: String, menuFoodId: String, groupType: String?): List<String>

    @Query(
        "UPDATE nutrition_foods SET visibility=:visibility, updated_at=:updatedAt " +
            "WHERE id=:foodId AND owner_id=:userId AND deleted_at IS NULL"
    )
    fun updateVisibility(foodId: String, userId: String, visibility: String, updatedAt: String): Int

    @Query(
        "SELECT * FROM nutrition_foods WHERE owner_id=:userId AND kind=:kind " +
            "AND source_type IN (:sourceTypes) AND deleted_at IS NULL " +
            "ORDER BY updated_at DESC, id ASC"
    )
    fun ownedFoodsByKindAndSourceTypes(
        userId: String, kind: String, sourceTypes: List<String>
    ): List<NutritionFoodsRoomEntity>

    @Query("SELECT * FROM pricetrace_product_cache WHERE catalog_product_id=:catalogProductId LIMIT 1")
    fun cachedProduct(catalogProductId: String): PricetraceProductCacheRoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertProductCache(cache: PricetraceProductCacheRoomEntity)

    @Query(
        "SELECT l.id, l.owner_id, l.nutrition_food_id, l.catalog_product_id, l.standard_product_id, " +
            "l.status, l.source_type, l.proposal_reference, l.revision, l.reviewed_at, " +
            "l.catalog_product_revision, l.catalog_content_amount, l.catalog_content_unit, " +
            "l.catalog_package_count, c.standard_product_id AS cache_standard_product_id, " +
            "c.product_name, c.brand_name, c.manufacturer_name, c.sub_brand_name, c.seller_name, " +
            "c.latest_price_krw, c.price_observed_at, c.content_amount, c.content_unit, " +
            "c.package_count, c.catalog_product_revision AS cache_catalog_product_revision " +
            "FROM product_nutrition_links l LEFT JOIN pricetrace_product_cache c " +
            "ON c.catalog_product_id=l.catalog_product_id WHERE l.owner_id=:userId " +
            "AND l.nutrition_food_id=:nutritionFoodId AND l.status=:status AND l.deleted_at IS NULL " +
            "ORDER BY l.updated_at DESC, l.created_at DESC"
    )
    fun productLinks(userId: String, nutritionFoodId: String, status: String): List<ProductLinkRow>

    @Query(
        "SELECT * FROM product_nutrition_links WHERE id=:linkId AND owner_id=:userId " +
            "AND status='suggested' AND deleted_at IS NULL LIMIT 1"
    )
    fun suggestedLink(linkId: String, userId: String): ProductNutritionLinksRoomEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertProductLink(link: ProductNutritionLinksRoomEntity)

    @Query(
        "UPDATE product_nutrition_links SET deleted_at=:updatedAt, updated_at=:updatedAt, " +
            "revision=revision+1 WHERE owner_id=:userId AND nutrition_food_id=:nutritionFoodId " +
            "AND status='approved' AND deleted_at IS NULL AND (:exceptId IS NULL OR id != :exceptId)"
    )
    fun softDeleteApprovedLinks(
        userId: String, nutritionFoodId: String, exceptId: String?, updatedAt: String
    ): Int

    @Query(
        "UPDATE product_nutrition_links SET status='approved', reviewed_at=:reviewedAt, " +
            "catalog_product_revision=:catalogProductRevision, catalog_content_amount=:catalogContentAmount, " +
            "catalog_content_unit=:catalogContentUnit, catalog_package_count=:catalogPackageCount, " +
            "updated_at=:updatedAt, revision=revision+1 WHERE id=:linkId AND owner_id=:userId " +
            "AND status='suggested' AND deleted_at IS NULL"
    )
    fun approveSuggestedLink(
        linkId: String,
        userId: String,
        reviewedAt: String,
        catalogProductRevision: String?,
        catalogContentAmount: Double?,
        catalogContentUnit: String?,
        catalogPackageCount: Long?,
        updatedAt: String
    ): Int

    @Query(
        "UPDATE product_nutrition_links SET status='rejected', reviewed_at=:reviewedAt, " +
            "updated_at=:updatedAt, revision=revision+1 WHERE id=:linkId AND owner_id=:userId " +
            "AND status='suggested' AND deleted_at IS NULL"
    )
    fun rejectSuggestedLink(linkId: String, userId: String, reviewedAt: String, updatedAt: String): Int

    @Query(
        "UPDATE product_nutrition_links SET deleted_at=:updatedAt, updated_at=:updatedAt, " +
            "revision=revision+1 WHERE owner_id=:userId AND nutrition_food_id=:nutritionFoodId " +
            "AND status='approved' AND deleted_at IS NULL"
    )
    fun unlinkApprovedProduct(userId: String, nutritionFoodId: String, updatedAt: String): Int
}

@Database(
    entities = [
        BodyProfileEntity::class,
        WeightRecordEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        DevicesRoomEntity::class,
        WorkoutRecordsRoomEntity::class,
        WorkoutExercisesRoomEntity::class,
        WorkoutSetsRoomEntity::class,
        SyncStateRoomEntity::class,
        MealRecordsRoomEntity::class,
        ExercisePickerPreferencesRoomEntity::class,
        CardioSessionsRoomEntity::class,
        CardioRoutePointsRoomEntity::class,
        MealMenuPresetsRoomEntity::class,
        NutritionFoodsRoomEntity::class,
        NutritionFoodNutrientsRoomEntity::class,
        NutritionFoodComponentsRoomEntity::class,
        MealRecordItemsRoomEntity::class,
        MealRecordItemNutrientsRoomEntity::class,
        MealRecordItemComponentsRoomEntity::class,
        MealRecordItemComponentNutrientsRoomEntity::class,
        CompositionTemplatesRoomEntity::class,
        CompositionGroupsRoomEntity::class,
        CompositionMembersRoomEntity::class,
        DiningOutMenuAddOnLinksRoomEntity::class,
        DiningOutMenuComponentLinksRoomEntity::class,
        MealRecordItemConsumptionsRoomEntity::class,
        ProductNutritionLinksRoomEntity::class,
        PricetraceProductCacheRoomEntity::class,
        NutritionGoalsRoomEntity::class,
        NutritionDailyCheckinsRoomEntity::class,
        DevelopmentGoalsRoomEntity::class,
        SupplementItemsRoomEntity::class,
        SupplementSchedulesRoomEntity::class,
        SupplementScheduleSlotsRoomEntity::class,
        SupplementIntakeRecordsRoomEntity::class,
        SupplementEffectCheckinsRoomEntity::class,
        VerifiedReceiptItemsRoomEntity::class
    ],
    version = FitnessDatabaseContract.ROOM_VERSION,
    exportSchema = true
)
abstract class FitnessRoomDatabase : RoomDatabase() {
    abstract fun bodyRoomDao(): BodyRoomDao
    abstract fun deviceRoomDao(): DeviceRoomDao
    abstract fun routineRoomDao(): RoutineRoomDao
    abstract fun supplementRoomDao(): SupplementRoomDao
    abstract fun workoutRoomDao(): WorkoutRoomDao
    abstract fun cardioRoomDao(): CardioRoomDao
    abstract fun mealRoomDao(): MealRoomDao
    abstract fun nutritionRoomDao(): NutritionRoomDao
    abstract fun developmentRoomDao(): DevelopmentRoomDao
}
