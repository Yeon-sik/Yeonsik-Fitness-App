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
}
