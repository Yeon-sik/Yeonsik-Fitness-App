package com.yeonsik.fitnessapp.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Generated from the v50 CREATE TABLE declarations; v51 PK metadata is normalized before validation. */

@Entity(tableName = "devices", primaryKeys = ["user_id", "id"], indices = [Index(name = "devices_user_last_seen_at_idx", value = ["user_id", "last_seen_at"]), Index(name = "devices_user_sync_push_idx", value = ["user_id", "last_seen_at", "id"])])
data class DevicesRoomEntity(
    val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val name: String,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: String,
    @ColumnInfo(name = "app_version") val appVersion: String?
)

@Entity(tableName = "workout_records", indices = [Index(name = "workout_records_user_scope_date_idx", value = ["user_id", "scope", "date"]), Index(name = "workout_records_user_sync_push_idx", value = ["user_id", "updated_at", "id"])])
data class WorkoutRecordsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val date: String,
    @ColumnInfo(name = "workout_type") val workoutType: String,
    val category: String,
    @ColumnInfo(name = "exercise_name") val exerciseName: String,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long?,
    @ColumnInfo(name = "total_volume_kg", defaultValue = "0") val totalVolumeKg: Double,
    @ColumnInfo(name = "average_heart_rate") val averageHeartRate: Double?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "is_backfilled") val isBackfilled: Long,
    @ColumnInfo(name = "backfilled_at") val backfilledAt: String?,
    @ColumnInfo(name = "backfill_reason") val backfillReason: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "source_app") val sourceApp: String,
    val scope: String,
    val metadata: String,
    @ColumnInfo(name = "contract_version", defaultValue = "1") val contractVersion: Long
)

@Entity(tableName = "workout_exercises", indices = [Index(name = "workout_exercises_record_order_idx", value = ["record_id", "order_index"]), Index(name = "workout_exercises_family_variant_idx", value = ["user_id", "family_id", "canonical_variant_key"]), Index(name = "workout_exercises_user_sync_push_idx", value = ["user_id", "updated_at", "id"])])
data class WorkoutExercisesRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "order_index") val orderIndex: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "exercise_name_snapshot") val exerciseNameSnapshot: String,
    @ColumnInfo(name = "ui_part") val uiPart: String,
    @ColumnInfo(name = "primary_sub_part_snapshot") val primarySubPartSnapshot: String?,
    @ColumnInfo(name = "equipment_snapshot") val equipmentSnapshot: String?,
    @ColumnInfo(name = "record_type") val recordType: String,
    @ColumnInfo(name = "family_id") val familyId: String?,
    @ColumnInfo(name = "preset_id") val presetId: String?,
    @ColumnInfo(name = "canonical_variant_key") val canonicalVariantKey: String?,
    @ColumnInfo(name = "visual_variant_key") val visualVariantKey: String?,
    val memo: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "contract_version", defaultValue = "1") val contractVersion: Long
)

@Entity(tableName = "workout_sets", indices = [Index(name = "workout_sets_exercise_order_idx", value = ["workout_exercise_id", "set_index"]), Index(name = "workout_sets_load_state_idx", value = ["user_id", "load_state"]), Index(name = "workout_sets_user_sync_push_idx", value = ["user_id", "updated_at", "id"])])
data class WorkoutSetsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "workout_exercise_id") val workoutExerciseId: String,
    @ColumnInfo(name = "set_index") val setIndex: Long,
    @ColumnInfo(name = "target_reps") val targetReps: Long?,
    @ColumnInfo(name = "actual_reps") val actualReps: Long?,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    @ColumnInfo(name = "volume_kg") val volumeKg: Double?,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long?,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double?,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Long?,
    @ColumnInfo(name = "assisted_weight_kg") val assistedWeightKg: Double?,
    @ColumnInfo(name = "added_weight_kg") val addedWeightKg: Double?,
    @ColumnInfo(name = "input_load_value") val inputLoadValue: Double?,
    @ColumnInfo(name = "input_load_unit") val inputLoadUnit: String?,
    @ColumnInfo(name = "load_state") val loadState: String?,
    @ColumnInfo(name = "is_completed") val isCompleted: Long,
    val rpe: Long?,
    val rir: Long?,
    val memo: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "contract_version", defaultValue = "1") val contractVersion: Long
)

@Entity(tableName = "sync_state", primaryKeys = ["scope_key", "table_name", "direction"])
data class SyncStateRoomEntity(
    @ColumnInfo(name = "scope_key") val scopeKey: String,
    @ColumnInfo(name = "table_name") val tableName: String,
    val direction: String,
    @ColumnInfo(name = "cursor_version") val cursorVersion: String?,
    @ColumnInfo(name = "cursor_id", defaultValue = "''") val cursorId: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(tableName = "meal_records", indices = [Index(name = "meal_records_user_scope_date_idx", value = ["user_id", "scope", "date"]), Index(name = "meal_records_user_kind_date_idx", value = ["user_id", "meal_kind", "date"]), Index(name = "meal_records_user_sync_push_idx", value = ["user_id", "updated_at", "id"])])
data class MealRecordsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val date: String,
    val menu: String,
    @ColumnInfo(name = "meal_kind", defaultValue = "'food'") val mealKind: String,
    @ColumnInfo(name = "fulfillment_mode") val fulfillmentMode: String?,
    @ColumnInfo(name = "store_name") val storeName: String?,
    @ColumnInfo(name = "branch_name") val branchName: String?,
    @ColumnInfo(name = "menu_name") val menuName: String?,
    @ColumnInfo(name = "restaurant_id") val restaurantId: String?,
    @ColumnInfo(name = "restaurant_location_id") val restaurantLocationId: String?,
    @ColumnInfo(name = "restaurant_menu_id") val restaurantMenuId: String?,
    @ColumnInfo(name = "catalog_product_id") val catalogProductId: String?,
    @ColumnInfo(name = "composition_template_id") val compositionTemplateId: String?,
    @ColumnInfo(name = "composition_template_revision") val compositionTemplateRevision: Long?,
    @ColumnInfo(name = "nutrition_calculation_contract") val nutritionCalculationContract: String?,
    val calories: Long,
    @ColumnInfo(name = "protein_grams") val proteinGrams: Double,
    @ColumnInfo(name = "carbs_grams") val carbsGrams: Double?,
    @ColumnInfo(name = "fat_grams") val fatGrams: Double?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "is_backfilled") val isBackfilled: Long,
    @ColumnInfo(name = "backfilled_at") val backfilledAt: String?,
    @ColumnInfo(name = "backfill_reason") val backfillReason: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "source_app") val sourceApp: String,
    val scope: String,
    val metadata: String,
    @ColumnInfo(name = "contract_version", defaultValue = "1") val contractVersion: Long
)

@Entity(tableName = "exercise_picker_preferences", primaryKeys = ["user_id", "canonical_preset_id"])
data class ExercisePickerPreferencesRoomEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "canonical_preset_id") val canonicalPresetId: String,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(tableName = "cardio_sessions", indices = [Index(name = "cardio_sessions_user_status_started_idx", value = ["user_id", "status", "started_at_epoch_ms"])])
data class CardioSessionsRoomEntity(
    @PrimaryKey @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "activity_type") val activityType: String,
    val status: String,
    @ColumnInfo(name = "started_at_epoch_ms") val startedAtEpochMs: Long,
    @ColumnInfo(name = "last_resumed_at_epoch_ms") val lastResumedAtEpochMs: Long?,
    @ColumnInfo(name = "active_duration_ms", defaultValue = "0") val activeDurationMs: Long,
    @ColumnInfo(name = "distance_meters", defaultValue = "0") val distanceMeters: Double,
    @ColumnInfo(name = "accepted_point_count", defaultValue = "0") val acceptedPointCount: Long,
    @ColumnInfo(name = "last_latitude") val lastLatitude: Double?,
    @ColumnInfo(name = "last_longitude") val lastLongitude: Double?,
    @ColumnInfo(name = "last_location_time_ms") val lastLocationTimeMs: Long?,
    @ColumnInfo(name = "last_accuracy_meters") val lastAccuracyMeters: Double?,
    @ColumnInfo(name = "gps_status", defaultValue = "'searching'") val gpsStatus: String,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long
)

@Entity(tableName = "cardio_route_points", indices = [Index(name = "cardio_route_points_record_time_idx", value = ["record_id", "captured_at_epoch_ms"])])
data class CardioRoutePointsRoomEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "captured_at_epoch_ms") val capturedAtEpochMs: Long,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "accuracy_meters") val accuracyMeters: Double,
    @ColumnInfo(name = "speed_mps") val speedMps: Double?,
    @ColumnInfo(name = "segment_distance_meters", defaultValue = "0") val segmentDistanceMeters: Double
)

@Entity(tableName = "meal_menu_presets", indices = [Index(name = "meal_menu_presets_updated_idx", value = ["user_id", "updated_at"], orders = [Index.Order.ASC, Index.Order.DESC])])
data class MealMenuPresetsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val name: String,
    val calories: Long?,
    @ColumnInfo(name = "protein_grams") val proteinGrams: Double?,
    @ColumnInfo(name = "carbs_grams") val carbsGrams: Double?,
    @ColumnInfo(name = "fat_grams") val fatGrams: Double?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(tableName = "nutrition_foods", indices = [Index(name = "nutrition_foods_owner_name_idx", value = ["owner_id", "name"]), Index(name = "nutrition_foods_owner_brand_name_idx", value = ["owner_id", "brand", "name"]), Index(name = "nutrition_foods_owner_product_hierarchy_idx", value = ["owner_id", "manufacturer_name", "brand_name", "sub_brand_name", "product_name"]), Index(name = "nutrition_foods_owner_category_idx", value = ["owner_id", "category", "cooking_method", "name"]), Index(name = "nutrition_foods_visibility_name_idx", value = ["visibility", "name"])])
data class NutritionFoodsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String?,
    val name: String,
    val brand: String?,
    @ColumnInfo(name = "manufacturer_name") val manufacturerName: String?,
    @ColumnInfo(name = "brand_name") val brandName: String?,
    @ColumnInfo(name = "sub_brand_name") val subBrandName: String?,
    @ColumnInfo(name = "product_name") val productName: String?,
    @ColumnInfo(name = "package_amount") val packageAmount: Double?,
    @ColumnInfo(name = "package_unit") val packageUnit: String?,
    @ColumnInfo(name = "package_count") val packageCount: Long?,
    val kind: String,
    @ColumnInfo(name = "category", defaultValue = "'other'") val category: String,
    @ColumnInfo(name = "basis_amount") val basisAmount: Double,
    @ColumnInfo(name = "basis_unit") val basisUnit: String,
    @ColumnInfo(name = "prep_state", defaultValue = "'unspecified'") val prepState: String,
    @ColumnInfo(name = "cooking_method", defaultValue = "'unspecified'") val cookingMethod: String,
    @ColumnInfo(name = "calories_kcal") val caloriesKcal: Double?,
    @ColumnInfo(name = "protein_grams") val proteinGrams: Double?,
    @ColumnInfo(name = "carbs_grams") val carbsGrams: Double?,
    @ColumnInfo(name = "fat_grams") val fatGrams: Double?,
    @ColumnInfo(name = "sodium_mg") val sodiumMg: Double?,
    @ColumnInfo(name = "saturated_fat_grams") val saturatedFatGrams: Double?,
    @ColumnInfo(name = "sugars_grams") val sugarsGrams: Double?,
    @ColumnInfo(name = "fiber_grams") val fiberGrams: Double?,
    @ColumnInfo(name = "added_sugars_grams") val addedSugarsGrams: Double?,
    @ColumnInfo(name = "trans_fat_grams") val transFatGrams: Double?,
    @ColumnInfo(name = "cholesterol_mg") val cholesterolMg: Double?,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "source_reference") val sourceReference: String?,
    @ColumnInfo(name = "source_version") val sourceVersion: String?,
    @ColumnInfo(name = "data_version", defaultValue = "1") val dataVersion: Long,
    @ColumnInfo(name = "revision", defaultValue = "1") val revision: Long,
    @ColumnInfo(name = "visibility", defaultValue = "'private'") val visibility: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?
)

@Entity(tableName = "nutrition_food_nutrients", indices = [Index(name = "nutrition_food_nutrients_food_idx", value = ["food_id", "nutrient_code"])])
data class NutritionFoodNutrientsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String?,
    @ColumnInfo(name = "food_id") val foodId: String,
    @ColumnInfo(name = "nutrient_code") val nutrientCode: String,
    val amount: Double?,
    val unit: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?
)

@Entity(tableName = "nutrition_food_components", indices = [Index(name = "nutrition_food_components_parent_order_idx", value = ["parent_food_id", "order_index"])])
data class NutritionFoodComponentsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String?,
    @ColumnInfo(name = "parent_food_id") val parentFoodId: String,
    @ColumnInfo(name = "child_food_id") val childFoodId: String,
    val quantity: Double,
    val unit: String,
    @ColumnInfo(name = "order_index") val orderIndex: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?
)

@Entity(tableName = "meal_record_items", indices = [Index(name = "meal_record_items_meal_order_idx", value = ["meal_record_id", "order_index"])])
data class MealRecordItemsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "meal_record_id") val mealRecordId: String,
    @ColumnInfo(name = "composition_template_id") val compositionTemplateId: String?,
    @ColumnInfo(name = "composition_template_revision_snapshot") val compositionTemplateRevisionSnapshot: Long?,
    @ColumnInfo(name = "food_id") val foodId: String?,
    @ColumnInfo(name = "food_name_snapshot") val foodNameSnapshot: String,
    @ColumnInfo(name = "brand_snapshot") val brandSnapshot: String?,
    @ColumnInfo(name = "manufacturer_name_snapshot") val manufacturerNameSnapshot: String?,
    @ColumnInfo(name = "brand_name_snapshot") val brandNameSnapshot: String?,
    @ColumnInfo(name = "sub_brand_name_snapshot") val subBrandNameSnapshot: String?,
    @ColumnInfo(name = "product_name_snapshot") val productNameSnapshot: String?,
    @ColumnInfo(name = "package_amount_snapshot") val packageAmountSnapshot: Double?,
    @ColumnInfo(name = "package_unit_snapshot") val packageUnitSnapshot: String?,
    @ColumnInfo(name = "package_count_snapshot") val packageCountSnapshot: Long?,
    @ColumnInfo(name = "food_kind_snapshot") val foodKindSnapshot: String?,
    val quantity: Double,
    val unit: String,
    @ColumnInfo(name = "basis_amount_snapshot") val basisAmountSnapshot: Double?,
    @ColumnInfo(name = "basis_unit_snapshot") val basisUnitSnapshot: String?,
    @ColumnInfo(name = "portion_basis_snapshot") val portionBasisSnapshot: String?,
    @ColumnInfo(name = "nominal_servings_snapshot") val nominalServingsSnapshot: Double?,
    @ColumnInfo(name = "prep_state_snapshot") val prepStateSnapshot: String?,
    @ColumnInfo(name = "calories", defaultValue = "0") val calories: Double,
    @ColumnInfo(name = "protein_grams", defaultValue = "0") val proteinGrams: Double,
    @ColumnInfo(name = "carbs_grams", defaultValue = "0") val carbsGrams: Double,
    @ColumnInfo(name = "fat_grams", defaultValue = "0") val fatGrams: Double,
    @ColumnInfo(name = "sodium_mg") val sodiumMg: Double?,
    @ColumnInfo(name = "saturated_fat_grams") val saturatedFatGrams: Double?,
    @ColumnInfo(name = "sugars_grams") val sugarsGrams: Double?,
    @ColumnInfo(name = "fiber_grams") val fiberGrams: Double?,
    @ColumnInfo(name = "added_sugars_grams") val addedSugarsGrams: Double?,
    @ColumnInfo(name = "trans_fat_grams") val transFatGrams: Double?,
    @ColumnInfo(name = "cholesterol_mg") val cholesterolMg: Double?,
    @ColumnInfo(name = "source_type_snapshot") val sourceTypeSnapshot: String?,
    @ColumnInfo(name = "source_reference_snapshot") val sourceReferenceSnapshot: String?,
    @ColumnInfo(name = "source_version_snapshot") val sourceVersionSnapshot: String?,
    @ColumnInfo(name = "food_data_version_snapshot") val foodDataVersionSnapshot: Long?,
    @ColumnInfo(name = "order_index") val orderIndex: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "meal_record_item_nutrients", indices = [Index(name = "meal_record_item_nutrients_item_idx", value = ["meal_record_item_id", "nutrient_code"]), Index(name = "meal_record_item_nutrients_meal_idx", value = ["meal_record_id", "nutrient_code"])])
data class MealRecordItemNutrientsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "meal_record_id") val mealRecordId: String,
    @ColumnInfo(name = "meal_record_item_id") val mealRecordItemId: String,
    @ColumnInfo(name = "nutrient_code") val nutrientCode: String,
    val amount: Double,
    val unit: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "meal_record_item_components", indices = [Index(name = "meal_record_item_components_parent_order_idx", value = ["meal_record_item_id", "order_index"]), Index(name = "meal_record_item_components_meal_idx", value = ["meal_record_id", "meal_record_item_id"])])
data class MealRecordItemComponentsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "meal_record_id") val mealRecordId: String,
    @ColumnInfo(name = "meal_record_item_id") val mealRecordItemId: String,
    @ColumnInfo(name = "composition_group_key_snapshot") val compositionGroupKeySnapshot: String?,
    @ColumnInfo(name = "composition_group_type_snapshot") val compositionGroupTypeSnapshot: String?,
    @ColumnInfo(name = "provision_type_snapshot") val provisionTypeSnapshot: String?,
    @ColumnInfo(name = "composition_role_snapshot") val compositionRoleSnapshot: String?,
    @ColumnInfo(name = "composition_member_id_snapshot") val compositionMemberIdSnapshot: String?,
    @ColumnInfo(name = "food_id") val foodId: String?,
    @ColumnInfo(name = "food_name_snapshot") val foodNameSnapshot: String,
    @ColumnInfo(name = "brand_snapshot") val brandSnapshot: String?,
    @ColumnInfo(name = "food_kind_snapshot") val foodKindSnapshot: String?,
    val quantity: Double,
    val unit: String,
    @ColumnInfo(name = "basis_amount_snapshot") val basisAmountSnapshot: Double?,
    @ColumnInfo(name = "basis_unit_snapshot") val basisUnitSnapshot: String?,
    @ColumnInfo(name = "prep_state_snapshot") val prepStateSnapshot: String?,
    @ColumnInfo(name = "consumed_fraction") val consumedFraction: Double?,
    val calories: Double?,
    @ColumnInfo(name = "protein_grams") val proteinGrams: Double?,
    @ColumnInfo(name = "carbs_grams") val carbsGrams: Double?,
    @ColumnInfo(name = "fat_grams") val fatGrams: Double?,
    @ColumnInfo(name = "sodium_mg") val sodiumMg: Double?,
    @ColumnInfo(name = "saturated_fat_grams") val saturatedFatGrams: Double?,
    @ColumnInfo(name = "sugars_grams") val sugarsGrams: Double?,
    @ColumnInfo(name = "fiber_grams") val fiberGrams: Double?,
    @ColumnInfo(name = "added_sugars_grams") val addedSugarsGrams: Double?,
    @ColumnInfo(name = "trans_fat_grams") val transFatGrams: Double?,
    @ColumnInfo(name = "cholesterol_mg") val cholesterolMg: Double?,
    @ColumnInfo(name = "source_type_snapshot") val sourceTypeSnapshot: String?,
    @ColumnInfo(name = "source_reference_snapshot") val sourceReferenceSnapshot: String?,
    @ColumnInfo(name = "source_version_snapshot") val sourceVersionSnapshot: String?,
    @ColumnInfo(name = "food_data_version_snapshot") val foodDataVersionSnapshot: Long?,
    @ColumnInfo(name = "order_index") val orderIndex: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "meal_record_item_component_nutrients", indices = [Index(name = "meal_record_item_component_nutrients_component_idx", value = ["meal_record_item_component_id", "nutrient_code"]), Index(name = "meal_record_item_component_nutrients_meal_idx", value = ["meal_record_id", "nutrient_code"])])
data class MealRecordItemComponentNutrientsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "meal_record_id") val mealRecordId: String,
    @ColumnInfo(name = "meal_record_item_id") val mealRecordItemId: String,
    @ColumnInfo(name = "meal_record_item_component_id") val mealRecordItemComponentId: String,
    @ColumnInfo(name = "nutrient_code") val nutrientCode: String,
    val amount: Double,
    val unit: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "composition_templates", indices = [Index(name = "composition_templates_user_kind_updated_idx", value = ["user_id", "template_kind", "updated_at"], orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.DESC])])
data class CompositionTemplatesRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val name: String,
    @ColumnInfo(name = "template_kind") val templateKind: String,
    @ColumnInfo(name = "root_food_id") val rootFoodId: String?,
    @ColumnInfo(name = "source_reference") val sourceReference: String?,
    @ColumnInfo(name = "revision", defaultValue = "1") val revision: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "composition_groups", indices = [Index(name = "composition_groups_template_order_idx", value = ["template_id", "order_index"])])
data class CompositionGroupsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "template_id") val templateId: String,
    @ColumnInfo(name = "group_key") val groupKey: String,
    @ColumnInfo(name = "group_type", defaultValue = "'other'") val groupType: String,
    val label: String,
    @ColumnInfo(name = "selection_mode") val selectionMode: String,
    @ColumnInfo(name = "min_selected", defaultValue = "0") val minSelected: Long,
    @ColumnInfo(name = "max_selected", defaultValue = "99") val maxSelected: Long,
    @ColumnInfo(name = "order_index") val orderIndex: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "composition_members", indices = [Index(name = "composition_members_group_order_idx", value = ["group_id", "order_index"]), Index(name = "composition_members_food_idx", value = ["user_id", "nutrition_food_id"])])
data class CompositionMembersRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "template_id") val templateId: String,
    @ColumnInfo(name = "group_id") val groupId: String?,
    @ColumnInfo(name = "nutrition_food_id") val nutritionFoodId: String?,
    @ColumnInfo(name = "name_snapshot") val nameSnapshot: String,
    @ColumnInfo(name = "brand_snapshot") val brandSnapshot: String?,
    @ColumnInfo(name = "quantity", defaultValue = "1") val quantity: Double,
    @ColumnInfo(name = "unit", defaultValue = "'serving'") val unit: String,
    @ColumnInfo(name = "default_selected", defaultValue = "0") val defaultSelected: Long,
    @ColumnInfo(name = "order_index") val orderIndex: Long,
    @ColumnInfo(name = "source_reference_snapshot") val sourceReferenceSnapshot: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "dining_out_menu_add_on_links", indices = [Index(name = "dining_out_menu_add_on_links_menu_idx", value = ["user_id", "menu_food_id", "deleted_at"]), Index(name = "dining_out_menu_add_on_links_add_on_idx", value = ["user_id", "add_on_food_id", "deleted_at"])])
data class DiningOutMenuAddOnLinksRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "menu_food_id") val menuFoodId: String,
    @ColumnInfo(name = "add_on_food_id") val addOnFoodId: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "dining_out_menu_component_links", indices = [Index(name = "dining_out_menu_component_links_menu_idx", value = ["user_id", "menu_food_id", "group_type", "deleted_at"]), Index(name = "dining_out_menu_component_links_component_idx", value = ["user_id", "component_food_id", "group_type", "deleted_at"])])
data class DiningOutMenuComponentLinksRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "menu_food_id") val menuFoodId: String,
    @ColumnInfo(name = "component_food_id") val componentFoodId: String,
    @ColumnInfo(name = "group_type", defaultValue = "'other'") val groupType: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "meal_record_item_consumptions", indices = [Index(name = "meal_record_item_consumptions_item_idx", value = ["meal_record_item_id", "user_id", "deleted_at"]), Index(name = "meal_record_item_consumptions_record_idx", value = ["meal_record_id", "user_id", "deleted_at"])])
data class MealRecordItemConsumptionsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "meal_record_id") val mealRecordId: String,
    @ColumnInfo(name = "meal_record_item_id") val mealRecordItemId: String,
    @ColumnInfo(name = "contract_version") val contractVersion: String,
    @ColumnInfo(name = "consumer_scope", defaultValue = "'self'") val consumerScope: String,
    @ColumnInfo(name = "diner_count", defaultValue = "1") val dinerCount: Long,
    @ColumnInfo(name = "consumed_fraction") val consumedFraction: Double,
    @ColumnInfo(name = "share_method") val shareMethod: String,
    @ColumnInfo(name = "confidence", defaultValue = "'estimated'") val confidence: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "product_nutrition_links", indices = [Index(name = "product_nutrition_links_one_approved_idx", value = ["owner_id", "nutrition_food_id"], unique = true), Index(name = "product_nutrition_links_pending_idx", value = ["owner_id", "nutrition_food_id", "created_at"], orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.DESC]), Index(name = "product_nutrition_links_catalog_idx", value = ["catalog_product_id"])])
data class ProductNutritionLinksRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "nutrition_food_id") val nutritionFoodId: String,
    @ColumnInfo(name = "catalog_product_id") val catalogProductId: String,
    @ColumnInfo(name = "standard_product_id") val standardProductId: String?,
    val status: String,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "proposal_reference") val proposalReference: String?,
    @ColumnInfo(name = "product_contract_version", defaultValue = "'product-read.v1'") val productContractVersion: String,
    @ColumnInfo(name = "catalog_product_revision") val catalogProductRevision: String?,
    @ColumnInfo(name = "catalog_content_amount") val catalogContentAmount: Double?,
    @ColumnInfo(name = "catalog_content_unit") val catalogContentUnit: String?,
    @ColumnInfo(name = "catalog_package_count") val catalogPackageCount: Long?,
    @ColumnInfo(name = "revision", defaultValue = "1") val revision: Long,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?
)

@Entity(tableName = "pricetrace_product_cache")
data class PricetraceProductCacheRoomEntity(
    @PrimaryKey @ColumnInfo(name = "catalog_product_id") val catalogProductId: String,
    @ColumnInfo(name = "standard_product_id") val standardProductId: String?,
    @ColumnInfo(name = "product_name") val productName: String,
    @ColumnInfo(name = "brand_name") val brandName: String?,
    @ColumnInfo(name = "manufacturer_name") val manufacturerName: String?,
    @ColumnInfo(name = "sub_brand_name") val subBrandName: String?,
    @ColumnInfo(name = "seller_name") val sellerName: String?,
    @ColumnInfo(name = "latest_price_krw") val latestPriceKrw: Long?,
    @ColumnInfo(name = "price_observed_at") val priceObservedAt: String?,
    @ColumnInfo(name = "content_amount") val contentAmount: Double?,
    @ColumnInfo(name = "content_unit") val contentUnit: String?,
    @ColumnInfo(name = "package_count") val packageCount: Long?,
    @ColumnInfo(name = "catalog_product_revision") val catalogProductRevision: String?,
    @ColumnInfo(name = "contract_version") val contractVersion: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: String
)

@Entity(tableName = "nutrition_goals")
data class NutritionGoalsRoomEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    val phase: String,
    @ColumnInfo(name = "calories_kcal") val caloriesKcal: Double,
    @ColumnInfo(name = "protein_grams") val proteinGrams: Double,
    @ColumnInfo(name = "carbs_grams") val carbsGrams: Double,
    @ColumnInfo(name = "fat_grams") val fatGrams: Double,
    @ColumnInfo(name = "fiber_grams") val fiberGrams: Double,
    @ColumnInfo(name = "sodium_mg") val sodiumMg: Double,
    @ColumnInfo(name = "water_ml") val waterMl: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(tableName = "nutrition_daily_checkins", indices = [Index(name = "nutrition_daily_checkins_user_date_idx", value = ["user_id", "date"], orders = [Index.Order.ASC, Index.Order.DESC])])
data class NutritionDailyCheckinsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val date: String,
    @ColumnInfo(name = "water_ml", defaultValue = "0") val waterMl: Long,
    @ColumnInfo(name = "sleep_hours") val sleepHours: Double?,
    @ColumnInfo(name = "energy_score") val energyScore: Long?,
    @ColumnInfo(name = "hunger_score") val hungerScore: Long?,
    @ColumnInfo(name = "digestion_score") val digestionScore: Long?,
    @ColumnInfo(name = "training_readiness_score") val trainingReadinessScore: Long?,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(tableName = "development_goals")
data class DevelopmentGoalsRoomEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    val objective: String,
    @ColumnInfo(name = "weekly_sessions_target") val weeklySessionsTarget: Long,
    @ColumnInfo(name = "focus_body_part") val focusBodyPart: String,
    @ColumnInfo(name = "effective_from") val effectiveFrom: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(tableName = "supplement_items", indices = [Index(name = "supplement_items_user_active_idx", value = ["user_id", "is_active", "created_at"])])
data class SupplementItemsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "supplement_type_code") val supplementTypeCode: String,
    @ColumnInfo(name = "supplement_type_name") val supplementTypeName: String,
    @ColumnInfo(name = "brand_name", defaultValue = "''") val brandName: String,
    @ColumnInfo(name = "product_form", defaultValue = "''") val productForm: String,
    @ColumnInfo(name = "purpose_code", defaultValue = "'general_health'") val purposeCode: String,
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "supplement_schedules", indices = [Index(name = "supplement_schedules_user_item_active_idx", value = ["user_id", "supplement_item_id", "is_active"]), Index(name = "supplement_schedules_user_effective_idx", value = ["user_id", "effective_from", "effective_to"])])
data class SupplementSchedulesRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "supplement_item_id") val supplementItemId: String,
    @ColumnInfo(name = "dose_amount") val doseAmount: Double,
    @ColumnInfo(name = "dose_unit") val doseUnit: String,
    @ColumnInfo(name = "serving_amount") val servingAmount: Double?,
    @ColumnInfo(name = "serving_unit") val servingUnit: String?,
    @ColumnInfo(name = "active_ingredient_amount") val activeIngredientAmount: Double?,
    @ColumnInfo(name = "active_ingredient_unit") val activeIngredientUnit: String?,
    @ColumnInfo(name = "ingredient_details", defaultValue = "''") val ingredientDetails: String,
    @ColumnInfo(name = "times_per_day") val timesPerDay: Long,
    @ColumnInfo(name = "timing_label") val timingLabel: String,
    @ColumnInfo(name = "effective_from", defaultValue = "'1970-01-01'") val effectiveFrom: String,
    @ColumnInfo(name = "effective_to") val effectiveTo: String?,
    @ColumnInfo(name = "type_code_snapshot", defaultValue = "''") val typeCodeSnapshot: String,
    @ColumnInfo(name = "type_name_snapshot", defaultValue = "''") val typeNameSnapshot: String,
    @ColumnInfo(name = "brand_name_snapshot", defaultValue = "''") val brandNameSnapshot: String,
    @ColumnInfo(name = "product_form_snapshot", defaultValue = "''") val productFormSnapshot: String,
    @ColumnInfo(name = "purpose_code_snapshot", defaultValue = "'general_health'") val purposeCodeSnapshot: String,
    @ColumnInfo(name = "revision", defaultValue = "1") val revision: Long,
    @ColumnInfo(name = "instructions", defaultValue = "''") val instructions: String,
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "supplement_schedule_slots", indices = [Index(name = "supplement_slots_user_schedule_idx", value = ["user_id", "schedule_id", "slot_index"])])
data class SupplementScheduleSlotsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "schedule_id") val scheduleId: String,
    @ColumnInfo(name = "slot_index") val slotIndex: Long,
    @ColumnInfo(name = "timing_label") val timingLabel: String,
    @ColumnInfo(name = "scheduled_time") val scheduledTime: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "supplement_intake_records", indices = [Index(name = "supplement_intakes_user_date_idx", value = ["user_id", "date", "created_at"], orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.DESC])])
data class SupplementIntakeRecordsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "schedule_id") val scheduleId: String,
    @ColumnInfo(name = "supplement_item_id") val supplementItemId: String,
    @ColumnInfo(name = "schedule_slot_id") val scheduleSlotId: String?,
    val date: String,
    @ColumnInfo(name = "dose_index") val doseIndex: Long,
    val status: String,
    @ColumnInfo(name = "taken_at") val takenAt: String?,
    @ColumnInfo(name = "type_code_snapshot") val typeCodeSnapshot: String,
    @ColumnInfo(name = "type_name_snapshot") val typeNameSnapshot: String,
    @ColumnInfo(name = "brand_name_snapshot") val brandNameSnapshot: String,
    @ColumnInfo(name = "dose_amount_snapshot") val doseAmountSnapshot: Double,
    @ColumnInfo(name = "dose_unit_snapshot") val doseUnitSnapshot: String,
    @ColumnInfo(name = "serving_amount_snapshot") val servingAmountSnapshot: Double?,
    @ColumnInfo(name = "serving_unit_snapshot") val servingUnitSnapshot: String?,
    @ColumnInfo(name = "active_ingredient_amount_snapshot") val activeIngredientAmountSnapshot: Double?,
    @ColumnInfo(name = "active_ingredient_unit_snapshot") val activeIngredientUnitSnapshot: String?,
    @ColumnInfo(name = "ingredient_details_snapshot", defaultValue = "''") val ingredientDetailsSnapshot: String,
    @ColumnInfo(name = "timing_label_snapshot") val timingLabelSnapshot: String,
    @ColumnInfo(name = "instructions_snapshot", defaultValue = "''") val instructionsSnapshot: String,
    @ColumnInfo(name = "record_source", defaultValue = "'on_date'") val recordSource: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "supplement_effect_checkins", indices = [Index(name = "supplement_effect_user_item_date_idx", value = ["user_id", "supplement_item_id", "date"], orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.DESC])])
data class SupplementEffectCheckinsRoomEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "supplement_item_id") val supplementItemId: String,
    val date: String,
    @ColumnInfo(name = "effect_score") val effectScore: Long,
    @ColumnInfo(name = "adverse_effects", defaultValue = "''") val adverseEffects: String,
    @ColumnInfo(name = "note", defaultValue = "''") val note: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "device_id") val deviceId: String
)

@Entity(tableName = "verified_receipt_items", primaryKeys = ["receipt_id", "receipt_item_id"], indices = [Index(name = "verified_receipt_items_user_status_idx", value = ["user_id", "status", "created_at"])])
data class VerifiedReceiptItemsRoomEntity(
    @ColumnInfo(name = "receipt_id") val receiptId: String,
    @ColumnInfo(name = "receipt_item_id") val receiptItemId: String,
    @ColumnInfo(name = "ledger_entry_id") val ledgerEntryId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "description_snapshot") val descriptionSnapshot: String,
    @ColumnInfo(name = "purchased_quantity") val purchasedQuantity: Double,
    val unit: String,
    @ColumnInfo(name = "total_price_krw") val totalPriceKrw: Long,
    @ColumnInfo(name = "catalog_product_id") val catalogProductId: String?,
    @ColumnInfo(name = "nutrition_food_id") val nutritionFoodId: String?,
    @ColumnInfo(name = "status", defaultValue = "'pending_consumption'") val status: String,
    @ColumnInfo(name = "consumed_quantity") val consumedQuantity: Double?,
    @ColumnInfo(name = "meal_record_id") val mealRecordId: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)
