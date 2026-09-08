package com.yeonsik.fitnessapp.feature.meal.data;

import androidx.annotation.Nullable;

import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider;
import com.yeonsik.fitnessapp.core.database.MealRecordItemNutrientsRoomEntity;
import com.yeonsik.fitnessapp.core.database.MealRecordItemsRoomEntity;
import com.yeonsik.fitnessapp.core.database.MealRecordsRoomEntity;
import com.yeonsik.fitnessapp.core.database.MealRoomDao;
import com.yeonsik.fitnessapp.core.database.FitnessDatabaseConnection;
import com.yeonsik.fitnessapp.data.CompositionTemplate;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.MealCompositionItem;
import com.yeonsik.fitnessapp.data.MealEntryPolicy;
import com.yeonsik.fitnessapp.data.MealItemSnapshot;
import com.yeonsik.fitnessapp.data.MealRecordKind;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionUnit;
import com.yeonsik.fitnessapp.feature.meal.api.MealRecordRepositoryApi;
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/** Owns account-scoped runtime meal-record writes for the meal feature. */
public final class MealRecordRepository implements MealRecordRepositoryApi {
    private static final String DEVICE_ID = "android-local";

    private final FitnessRoomDatabase roomDatabase;
    private final MealRoomDao mealDao;
    private final NutritionCatalogRepositoryApi nutritionCatalog;
    private String userId;

    public MealRecordRepository(
            FitnessRoomDatabase roomDatabase,
            NutritionCatalogRepositoryApi nutritionCatalog,
            String userId
    ) {
        this.roomDatabase = roomDatabase;
        this.mealDao = roomDatabase.mealRoomDao();
        this.nutritionCatalog = nutritionCatalog;
        setUserId(userId);
    }

    /** Temporary source-compatible adapter for legacy repository tests. */
    @Deprecated
    public MealRecordRepository(
            FitnessDatabaseConnection legacyDatabase,
            NutritionCatalogRepositoryApi nutritionCatalog,
            String userId
    ) {
        this(
                FitnessRoomDatabaseProvider.get(legacyDatabase.applicationContext()),
                nutritionCatalog,
                userId
        );
    }

    @Override
    public void setUserId(String userId) {
        String normalized = userId == null ? "" : userId.trim();
        this.userId = normalized.isEmpty() ? "local-user" : normalized;
    }

    @Override
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
        String now = now();
        String recordId = UUID.randomUUID().toString();
        int mealIndex = safeInt(mealDao.mealCountForDate(ownerId, recordDate.toString()));
        String mealLabel = MealEntryPolicy.labelForIndex(mealIndex);

        String metadata = foodMetadata(mealLabel, eatenAt);
        MealRecordsRoomEntity record = new MealRecordsRoomEntity(
                recordId,
                ownerId,
                recordDate.toString(),
                MealEntryPolicy.previewTitle(food.displayName(), 1, mealLabel + " 식사"),
                MealRecordKind.FOOD,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Math.round(item.calories),
                item.proteinGrams,
                item.carbsGrams,
                item.fatGrams,
                now,
                backfilled ? 1L : 0L,
                backfilled ? now : null,
                backfilled ? "manual past meal entry" : null,
                now,
                null,
                DEVICE_ID,
                "fitness",
                "fitness",
                metadata,
                1L
        );

        roomDatabase.runInTransaction(() -> {
            mealDao.insertRecord(record);
            insertItemSnapshot(recordId, snapshot, now, ownerId);
        });
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
        return saveManualDiningOut(
                scope, date, mealTime, storeName, branchName, menuName,
                calories, proteinGrams, carbsGrams, fatGrams, sodiumMg,
                sugarsGrams, saturatedFatGrams, null, null, null, null
        );
    }

    @Override
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
            double saturatedFatGrams,
            String restaurantId,
            String restaurantLocationId,
            String restaurantMenuId,
            String catalogProductId
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
                calories, proteinGrams, carbsGrams, fatGrams, snapshot,
                restaurantId, restaurantLocationId, restaurantMenuId, catalogProductId
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
            MealItemSnapshot snapshot,
            String restaurantId,
            String restaurantLocationId,
            String restaurantMenuId,
            String catalogProductId
    ) {
        LocalDate today = LocalDate.now();
        LocalDate recordDate = MealEntryPolicy.requireRecordDate(date, today);
        String eatenAt = MealEntryPolicy.eatenAt(recordDate, mealTime, ZoneId.systemDefault());
        boolean backfilled = MealEntryPolicy.isBackfilled(recordDate, today);
        String now = now();
        String recordId = UUID.randomUUID().toString();
        int mealIndex = safeInt(mealDao.mealCountForDate(ownerId, recordDate.toString()));

        MealRecordsRoomEntity record = new MealRecordsRoomEntity(
                recordId,
                ownerId,
                recordDate.toString(),
                menu,
                MealRecordKind.DINING_OUT,
                null,
                store,
                branch,
                menu,
                optional(restaurantId),
                optional(restaurantLocationId),
                optional(restaurantMenuId),
                optional(catalogProductId),
                null,
                null,
                null,
                calories,
                protein,
                carbs,
                fat,
                now,
                backfilled ? 1L : 0L,
                backfilled ? now : null,
                backfilled ? "manual past dining-out entry" : null,
                now,
                null,
                DEVICE_ID,
                "fitness",
                "fitness",
                diningMetadata(
                        mealIndex,
                        eatenAt,
                        store,
                        branch,
                        menu,
                        restaurantId,
                        restaurantLocationId,
                        restaurantMenuId,
                        catalogProductId
                ),
                1L
        );

        roomDatabase.runInTransaction(() -> {
            mealDao.insertRecord(record);
            insertItemSnapshot(recordId, snapshot, now, ownerId);
        });
        return recordId;
    }

    private void insertItemSnapshot(String recordId, MealItemSnapshot snapshot, String now, String ownerId) {
        String itemId = UUID.randomUUID().toString();
        Map<String, Double> typed = snapshot.typedNutritionColumns();
        MealRecordItemsRoomEntity item = new MealRecordItemsRoomEntity(
                itemId,
                ownerId,
                recordId,
                null,
                null,
                optional(snapshot.foodId),
                snapshot.foodNameSnapshot,
                snapshot.brandSnapshot,
                snapshot.manufacturerNameSnapshot,
                snapshot.brandNameSnapshot,
                snapshot.subBrandNameSnapshot,
                snapshot.productNameSnapshot,
                snapshot.packageAmountSnapshot,
                snapshot.packageUnitSnapshot,
                snapshot.packageCountSnapshot == null ? null : snapshot.packageCountSnapshot.longValue(),
                snapshot.foodKindSnapshot,
                snapshot.quantity,
                snapshot.unit,
                snapshot.basisAmountSnapshot,
                snapshot.basisUnitSnapshot,
                null,
                null,
                snapshot.prepStateSnapshot,
                required(typed, NutritionProfile.CALORIES_KCAL),
                required(typed, NutritionProfile.PROTEIN_GRAMS),
                required(typed, NutritionProfile.CARBS_GRAMS),
                required(typed, NutritionProfile.FAT_GRAMS),
                typed.get(NutritionProfile.SODIUM_MG),
                typed.get(NutritionProfile.SATURATED_FAT_GRAMS),
                typed.get(NutritionProfile.SUGARS_GRAMS),
                typed.get(NutritionProfile.FIBER_GRAMS),
                typed.get(NutritionProfile.ADDED_SUGARS_GRAMS),
                typed.get(NutritionProfile.TRANS_FAT_GRAMS),
                typed.get(NutritionProfile.CHOLESTEROL_MG),
                snapshot.sourceTypeSnapshot,
                snapshot.sourceReferenceSnapshot,
                snapshot.sourceVersionSnapshot,
                (long) snapshot.foodDataVersionSnapshot,
                (long) snapshot.orderIndex,
                now,
                now,
                null,
                DEVICE_ID
        );
        mealDao.insertItem(item);

        for (MealItemSnapshot.MicronutrientRow row : snapshot.micronutrientRows()) {
            mealDao.insertNutrient(new MealRecordItemNutrientsRoomEntity(
                    UUID.randomUUID().toString(),
                    ownerId,
                    recordId,
                    itemId,
                    row.nutrientCode,
                    row.amount,
                    row.unit,
                    now,
                    now,
                    null,
                    DEVICE_ID
            ));
        }
    }

    private String foodMetadata(String mealLabel, String eatenAt) {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("item_type", "meal");
            metadata.put("meal_kind", MealRecordKind.FOOD);
            metadata.put("meal_type", mealLabel);
            metadata.put("eaten_at", eatenAt);
            metadata.put("estimated", "false");
            metadata.put("composition_version", "2");
            metadata.put("item_count", "1");
            return metadata.toString();
        } catch (Exception error) {
            throw new IllegalStateException("식단 메타데이터를 만들지 못했습니다.", error);
        }
    }

    private String diningMetadata(
            int mealIndex,
            String eatenAt,
            String store,
            String branch,
            String menu,
            String restaurantId,
            String restaurantLocationId,
            String restaurantMenuId,
            String catalogProductId
    ) {
        try {
            JSONObject json = new JSONObject();
            json.put("item_type", "meal");
            json.put("meal_kind", MealRecordKind.DINING_OUT);
            json.put("meal_type", MealEntryPolicy.labelForIndex(mealIndex));
            json.put("eaten_at", eatenAt);
            json.put("store_name", store);
            if (branch != null) json.put("branch_name", branch);
            json.put("menu_name", menu);
            json.put("nutrition_status", "estimated");
            json.put("nutrition_source", "manual_estimate");
            json.put("estimated", "true");
            putOptional(json, "restaurant_id", restaurantId);
            putOptional(json, "restaurant_location_id", restaurantLocationId);
            putOptional(json, "restaurant_menu_id", restaurantMenuId);
            putOptional(json, "catalog_product_id", catalogProductId);
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

    private static Double required(Map<String, Double> values, String key) {
        Double value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("필수 영양소가 없습니다: " + key);
        }
        return value;
    }

    private static String now() {
        return DateTimeFormatter.ISO_INSTANT.format(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static String optional(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void putOptional(JSONObject values, String key, String value) throws Exception {
        String normalized = optional(value);
        if (normalized != null) values.put(key, normalized);
    }
}
