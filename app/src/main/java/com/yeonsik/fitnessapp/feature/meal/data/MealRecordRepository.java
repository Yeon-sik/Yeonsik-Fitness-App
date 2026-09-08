package com.yeonsik.fitnessapp.feature.meal.data;

import android.content.ContentValues;

import com.yeonsik.fitnessapp.core.database.FitnessDatabaseConnection;
import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.data.CompositionTemplate;
import com.yeonsik.fitnessapp.data.MealCompositionItem;
import com.yeonsik.fitnessapp.data.MealEntryPolicy;
import com.yeonsik.fitnessapp.data.MealItemSnapshot;
import com.yeonsik.fitnessapp.data.MealRecordKind;
import com.yeonsik.fitnessapp.data.NutritionCatalogRepository;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionUnit;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/** Owns account-scoped runtime meal-record writes for the meal feature. */
public final class MealRecordRepository {
    private static final String DEVICE_ID = "android-local";

    private final FitnessDatabaseConnection database;
    private final NutritionCatalogRepository nutritionCatalog;
    private String userId;

    public MealRecordRepository(
            FitnessDatabaseConnection database,
            NutritionCatalogRepository nutritionCatalog,
            String userId
    ) {
        this.database = database;
        this.nutritionCatalog = nutritionCatalog;
        setUserId(userId);
    }

    public void setUserId(String userId) {
        String normalized = userId == null ? "" : userId.trim();
        this.userId = normalized.isEmpty() ? "local-user" : normalized;
    }

    public String saveFoodMeal(AccountScope scope, String date, String mealTime, String foodId, double quantity) {
        String ownerId = requireActiveOwner(scope);
        NutritionFood food = nutritionCatalog.findFoodById(foodId);
        if (food == null) {
            throw new IllegalArgumentException("선택한 식품을 찾지 못했습니다.");
        }
        MealCompositionItem item = MealCompositionItem.from(food, quantity, food.basisUnit);
        MealItemSnapshot snapshot = MealItemSnapshot.of(item, 0);
        LocalDate today = LocalDate.now();
        LocalDate recordDate = MealEntryPolicy.requireRecordDate(date, today);
        String eatenAt = MealEntryPolicy.eatenAt(recordDate, mealTime, ZoneId.systemDefault());
        boolean backfilled = MealEntryPolicy.isBackfilled(recordDate, today);
        String now = DateTimeFormatter.ISO_INSTANT.format(OffsetDateTime.now(ZoneOffset.UTC));
        String recordId = UUID.randomUUID().toString();
        int mealIndex = (int) database.longForQuery(
                "SELECT COUNT(*) FROM meal_records WHERE deleted_at IS NULL "
                        + "AND user_id = ? AND scope IN ('fitness', 'both') AND date = ?",
                new String[]{ownerId, recordDate.toString()}
        );
        String mealLabel = MealEntryPolicy.labelForIndex(mealIndex);

        ContentValues values = baseValues(recordId, now, ownerId);
        values.put("date", recordDate.toString());
        values.put("menu", MealEntryPolicy.previewTitle(food.displayName(), 1, mealLabel + " 식사"));
        values.put("meal_kind", MealRecordKind.FOOD);
        values.putNull("fulfillment_mode");
        values.putNull("store_name");
        values.putNull("branch_name");
        values.putNull("menu_name");
        values.putNull("restaurant_id");
        values.putNull("restaurant_location_id");
        values.putNull("restaurant_menu_id");
        values.putNull("catalog_product_id");
        values.putNull("composition_template_id");
        values.putNull("composition_template_revision");
        values.putNull("nutrition_calculation_contract");
        values.put("calories", (int) Math.round(item.calories));
        values.put("protein_grams", item.proteinGrams);
        values.put("carbs_grams", item.carbsGrams);
        values.put("fat_grams", item.fatGrams);
        values.put("is_backfilled", backfilled ? 1 : 0);
        if (backfilled) {
            values.put("backfilled_at", now);
            values.put("backfill_reason", "manual past meal entry");
        } else {
            values.putNull("backfilled_at");
            values.putNull("backfill_reason");
        }
        values.put("source_app", "fitness");
        values.put("scope", "fitness");
        JSONObject metadata = new JSONObject();
        try {
            metadata.put("item_type", "meal");
            metadata.put("meal_kind", MealRecordKind.FOOD);
            metadata.put("meal_type", mealLabel);
            metadata.put("eaten_at", eatenAt);
            metadata.put("estimated", "false");
            metadata.put("composition_version", "2");
            metadata.put("item_count", "1");
        } catch (Exception error) {
            throw new IllegalStateException("식단 메타데이터를 만들지 못했습니다.", error);
        }
        values.put("metadata", metadata.toString());
        values.put("contract_version", 1);

        database.beginTransaction();
        try {
            database.insertOrThrow("meal_records", null, values);
            insertItemSnapshot(recordId, snapshot, now, ownerId);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return recordId;
    }
    public String saveManualDiningOut(
            AccountScope scope,
            String date,
            String mealTime,
            String storeName,
            String branchName,
            String menuName,
            int calories,
            double proteinGrams,
            double carbsGrams,
            double fatGrams,
            double sodiumMg,
            double sugarsGrams,
            double saturatedFatGrams
    ) {
        String ownerId = requireActiveOwner(scope);
        String store = MealEntryPolicy.requireDiningOutStoreName(storeName);
        String menu = MealEntryPolicy.requireDiningOutMenuName(menuName);
        MealEntryPolicy.requireDiningOutMenuNutrition(
                calories, proteinGrams, carbsGrams, fatGrams,
                sodiumMg, sugarsGrams, saturatedFatGrams
        );
        NutritionProfile profile = NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, (double) calories)
                .value(NutritionProfile.PROTEIN_GRAMS, proteinGrams)
                .value(NutritionProfile.CARBS_GRAMS, carbsGrams)
                .value(NutritionProfile.FAT_GRAMS, fatGrams)
                .value(NutritionProfile.SODIUM_MG, sodiumMg)
                .value(NutritionProfile.SUGARS_GRAMS, sugarsGrams)
                .value(NutritionProfile.SATURATED_FAT_GRAMS, saturatedFatGrams)
                .build();
        NutritionFood food = NutritionFood.builder()
                .id(null)
                .ownerId(ownerId)
                .name(menu)
                .brand(store)
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .category(NutritionFood.CATEGORY_OTHER)
                .basis(1d, NutritionUnit.SERVING)
                .prepState(NutritionFood.PREP_AS_SERVED)
                .profile(profile)
                .source("manual_estimate", "dining_out")
                .dataVersion(NutritionFood.DATA_VERSION_REQUIRED_SEVEN)
                .build();
        MealItemSnapshot snapshot = MealItemSnapshot.of(MealCompositionItem.from(food, 1d), 0);
        return insertDiningOutRecord(
                ownerId, date, mealTime, store, optional(branchName), menu,
                calories, proteinGrams, carbsGrams, fatGrams, snapshot
        );
    }

    private String insertDiningOutRecord(
            String ownerId,
            String date,
            String mealTime,
            String store,
            String branch,
            String menu,
            int calories,
            double protein,
            double carbs,
            double fat,
            MealItemSnapshot snapshot
    ) {
        LocalDate today = LocalDate.now();
        LocalDate recordDate = MealEntryPolicy.requireRecordDate(date, today);
        String eatenAt = MealEntryPolicy.eatenAt(recordDate, mealTime, ZoneId.systemDefault());
        boolean backfilled = MealEntryPolicy.isBackfilled(recordDate, today);
        String now = DateTimeFormatter.ISO_INSTANT.format(OffsetDateTime.now(ZoneOffset.UTC));
        String recordId = UUID.randomUUID().toString();

        ContentValues values = baseValues(recordId, now, ownerId);
        values.put("date", recordDate.toString());
        values.put("menu", menu);
        values.put("meal_kind", MealRecordKind.DINING_OUT);
        values.putNull("fulfillment_mode");
        values.put("store_name", store);
        putNullable(values, "branch_name", branch);
        values.put("menu_name", menu);
        values.putNull("restaurant_id");
        values.putNull("restaurant_location_id");
        values.putNull("restaurant_menu_id");
        values.putNull("catalog_product_id");
        values.putNull("composition_template_id");
        values.putNull("composition_template_revision");
        values.putNull("nutrition_calculation_contract");
        values.put("calories", calories);
        values.put("protein_grams", protein);
        values.put("carbs_grams", carbs);
        values.put("fat_grams", fat);
        values.put("is_backfilled", backfilled ? 1 : 0);
        if (backfilled) {
            values.put("backfilled_at", now);
            values.put("backfill_reason", "manual past dining-out entry");
        } else {
            values.putNull("backfilled_at");
            values.putNull("backfill_reason");
        }
        values.put("source_app", "fitness");
        values.put("scope", "fitness");
        values.put("metadata", diningMetadata(ownerId, recordDate.toString(), eatenAt, store, branch, menu));
        values.put("contract_version", 1);

        database.beginTransaction();
        try {
            database.insertOrThrow("meal_records", null, values);
            insertItemSnapshot(recordId, snapshot, now, ownerId);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return recordId;
    }

    private void insertItemSnapshot(String recordId, MealItemSnapshot snapshot, String now, String ownerId) {
        String itemId = UUID.randomUUID().toString();
        ContentValues values = new ContentValues();
        values.put("id", itemId);
        values.put("user_id", ownerId);
        values.put("meal_record_id", recordId);
        values.putNull("composition_template_id");
        values.putNull("composition_template_revision_snapshot");
        putNullable(values, "food_id", snapshot.foodId);
        values.put("food_name_snapshot", snapshot.foodNameSnapshot);
        putNullable(values, "brand_snapshot", snapshot.brandSnapshot);
        putNullable(values, "manufacturer_name_snapshot", snapshot.manufacturerNameSnapshot);
        putNullable(values, "brand_name_snapshot", snapshot.brandNameSnapshot);
        putNullable(values, "sub_brand_name_snapshot", snapshot.subBrandNameSnapshot);
        putNullable(values, "product_name_snapshot", snapshot.productNameSnapshot);
        putNullable(values, "package_amount_snapshot", snapshot.packageAmountSnapshot);
        putNullable(values, "package_unit_snapshot", snapshot.packageUnitSnapshot);
        putNullable(values, "package_count_snapshot", snapshot.packageCountSnapshot);
        values.put("food_kind_snapshot", snapshot.foodKindSnapshot);
        values.put("quantity", snapshot.quantity);
        values.put("unit", snapshot.unit);
        values.put("basis_amount_snapshot", snapshot.basisAmountSnapshot);
        values.put("basis_unit_snapshot", snapshot.basisUnitSnapshot);
        values.putNull("portion_basis_snapshot");
        values.putNull("nominal_servings_snapshot");
        values.put("prep_state_snapshot", snapshot.prepStateSnapshot);
        for (Map.Entry<String, Double> field : snapshot.typedNutritionColumns().entrySet()) {
            String column = NutritionProfile.CALORIES_KCAL.equals(field.getKey())
                    ? "calories" : field.getKey();
            if (field.getValue() == null) values.putNull(column);
            else values.put(column, field.getValue());
        }
        putNullable(values, "source_type_snapshot", snapshot.sourceTypeSnapshot);
        putNullable(values, "source_reference_snapshot", snapshot.sourceReferenceSnapshot);
        putNullable(values, "source_version_snapshot", snapshot.sourceVersionSnapshot);
        values.put("food_data_version_snapshot", snapshot.foodDataVersionSnapshot);
        values.put("order_index", snapshot.orderIndex);
        values.put("created_at", now);
        values.put("updated_at", now);
        values.putNull("deleted_at");
        values.put("device_id", DEVICE_ID);
        database.insertOrThrow("meal_record_items", null, values);

        for (MealItemSnapshot.MicronutrientRow row : snapshot.micronutrientRows()) {
            ContentValues nutrient = baseValues(UUID.randomUUID().toString(), now, ownerId);
            nutrient.put("meal_record_id", recordId);
            nutrient.put("meal_record_item_id", itemId);
            nutrient.put("nutrient_code", row.nutrientCode);
            nutrient.put("amount", row.amount);
            nutrient.put("unit", row.unit);
            database.insertOrThrow("meal_record_item_nutrients", null, nutrient);
        }
    }

    private ContentValues baseValues(String id, String now, String ownerId) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("user_id", ownerId);
        values.put("created_at", now);
        values.put("updated_at", now);
        values.putNull("deleted_at");
        values.put("device_id", DEVICE_ID);
        return values;
    }

    private String diningMetadata(
            String ownerId,
            String date,
            String eatenAt,
            String store,
            String branch,
            String menu
    ) {
        try {
            int index = (int) database.longForQuery(
                    "SELECT COUNT(*) FROM meal_records WHERE deleted_at IS NULL "
                            + "AND user_id = ? AND scope IN ('fitness', 'both') AND date = ?",
                    new String[]{ownerId, date}
            );
            JSONObject json = new JSONObject();
            json.put("item_type", "meal");
            json.put("meal_kind", MealRecordKind.DINING_OUT);
            json.put("meal_type", MealEntryPolicy.labelForIndex(index));
            json.put("eaten_at", eatenAt);
            json.put("store_name", store);
            if (branch != null) json.put("branch_name", branch);
            json.put("menu_name", menu);
            json.put("nutrition_status", "estimated");
            json.put("nutrition_source", "manual_estimate");
            json.put("estimated", "true");
            json.put("composition_version", "3");
            json.put("composition_contract", CompositionTemplate.CONTRACT_VERSION);
            json.put("composition_kind", "standalone");
            json.put("item_count", "1");
            json.put("option_count", "0");
            return json.toString();
        } catch (Exception error) {
            throw new IllegalStateException("외식 메타데이터를 만들지 못했습니다.", error);
        }
    }

    private String requireActiveOwner(AccountScope scope) {
        String requested = scope == null ? "" : scope.getOwnerId().trim();
        if (requested.isEmpty() || !requested.equals(userId)) {
            throw new IllegalStateException("계정이 전환되어 식사 저장을 취소했습니다.");
        }
        return requested;
    }

    private static String optional(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void putNullable(ContentValues values, String key, Object value) {
        if (value == null) values.putNull(key);
        else if (value instanceof String) values.put(key, (String) value);
        else if (value instanceof Double) values.put(key, (Double) value);
        else if (value instanceof Integer) values.put(key, (Integer) value);
        else throw new IllegalArgumentException("Unsupported ContentValues type for " + key);
    }
}
