package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.config.AccountOwnerPolicy;
import com.yeonsik.fitnessapp.config.SupabaseConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Local-first food catalog and recipe repository.
 *
 * <p>The app can record and search foods without a network connection. When a Supabase
 * connection is configured, public catalog rows are pulled and authenticated user rows are
 * pushed/pulled through the REST API.</p>
 *
 * <p>이 저장소는 <b>음식·레시피·영양성분만</b> 다룬다. 원격 테이블은 FitnessApp 전용
 * Nutrition Supabase 프로젝트에 있으며, Personal OS 공통 DB의 meal_records 같은 사용자
 * 섭취 기록은 이 카탈로그에 절대 들어가지 않는다.</p>
 */
public final class NutritionCatalogRepository {
    /** 영양 전용 DB의 카탈로그 테이블. 공통 사용자 기록 테이블은 여기 들어올 수 없다. */
    static final List<String> CATALOG_TABLES = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList(
                    "nutrition_foods",
                    "nutrition_food_nutrients",
                    "nutrition_food_components",
                    "product_nutrition_links"
            )
    );

    private static final String[] FOOD_COLUMNS = {
            "id",
            "owner_id",
            "name",
            "brand",
            "kind",
            "category",
            "basis_amount",
            "basis_unit",
            "prep_state",
            "cooking_method",
            "calories_kcal",
            "protein_grams",
            "carbs_grams",
            "fat_grams",
            "sodium_mg",
            "saturated_fat_grams",
            "sugars_grams",
            "fiber_grams",
            "added_sugars_grams",
            "trans_fat_grams",
            "cholesterol_mg",
            "source_type",
            "source_reference",
            "source_version",
            "data_version",
            "revision"
    };

    private static final String[] FOOD_SYNC_COLUMNS = syncColumns();

    private static final String[] NUTRIENT_SYNC_COLUMNS = {
            "id", "owner_id", "food_id", "nutrient_code", "amount", "unit",
            "created_at", "updated_at", "deleted_at"
    };

    private static final String[] COMPONENT_SYNC_COLUMNS = {
            "id", "owner_id", "parent_food_id", "child_food_id", "quantity", "unit",
            "order_index", "created_at", "updated_at", "deleted_at"
    };

    static final String[] PRODUCT_LINK_SYNC_COLUMNS = {
            "id", "owner_id", "nutrition_food_id", "catalog_product_id", "standard_product_id", "status",
            "source_type", "proposal_reference", "product_contract_version",
            "catalog_product_revision", "catalog_content_amount", "catalog_content_unit",
            "catalog_package_count", "revision",
            "reviewed_at", "created_at", "updated_at", "deleted_at"
    };

    private static final int VERIFIED_FOOD_SEARCH_LIMIT_MAX = 50;
    private static final String VERIFIED_FOOD_ID_PREFIX =
            VerifiedFoodCatalogSeed.FOOD_ID_PREFIX + "%";
    private static final String VERIFIED_FOOD_SOURCE_REFERENCE_PREFIX =
            VerifiedFoodCatalogSeed.SOURCE_REFERENCE_PREFIX + "%";

    private final FitnessDatabaseHelper dbHelper;
    private volatile String userId;
    private volatile SupabaseConfig supabaseConfig;

    public NutritionCatalogRepository(
            FitnessDatabaseHelper dbHelper,
            String userId,
            SupabaseConfig supabaseConfig
    ) {
        this.dbHelper = dbHelper;
        this.userId = normalizeUserId(userId);
        this.supabaseConfig = supabaseConfig == null ? SupabaseConfig.empty() : supabaseConfig;
    }

    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
    }

    public void normalizeLocalUserId(String nextUserId) {
        String normalizedNextUserId = normalizeUserId(nextUserId);
        String previousUserId = userId;
        if (AccountOwnerPolicy.shouldClaimLocalRows(
                previousUserId,
                normalizedNextUserId
        )) {
            SQLiteDatabase database = dbHelper.getWritableDatabase();
            database.beginTransaction();
            try {
                resolveApprovedLinkClaimConflicts(database, normalizedNextUserId);
                ContentValues values = new ContentValues();
                values.put("owner_id", normalizedNextUserId);
                for (String table : CATALOG_TABLES) {
                    database.update(
                            table,
                            values,
                            "owner_id = ?",
                            new String[]{SupabaseConfig.DEFAULT_USER_ID}
                    );
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }
        userId = normalizedNextUserId;
    }

    private void resolveApprovedLinkClaimConflicts(
            SQLiteDatabase database,
            String nextUserId
    ) {
        List<String[]> conflicts = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT source.id, source.updated_at, target.id, target.updated_at " +
                        "FROM product_nutrition_links source " +
                        "INNER JOIN product_nutrition_links target " +
                        "ON target.nutrition_food_id = source.nutrition_food_id " +
                        "AND target.owner_id = ? AND target.status = 'approved' " +
                        "AND target.deleted_at IS NULL " +
                        "WHERE source.owner_id = ? AND source.status = 'approved' " +
                        "AND source.deleted_at IS NULL",
                new String[]{nextUserId, SupabaseConfig.DEFAULT_USER_ID}
        )) {
            while (cursor.moveToNext()) {
                conflicts.add(new String[]{
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3)
                });
            }
        }

        for (String[] conflict : conflicts) {
            boolean localWins = compareVersions(conflict[1], conflict[3]) > 0;
            String losingId = localWins ? conflict[2] : conflict[0];
            String winningTimestamp = localWins ? conflict[1] : conflict[3];
            database.execSQL(
                    "UPDATE product_nutrition_links SET deleted_at = ?, updated_at = ?, " +
                            "revision = revision + 1 WHERE id = ? AND deleted_at IS NULL",
                    new Object[]{winningTimestamp, winningTimestamp, losingId}
            );
        }
    }

    public void setSupabaseConfig(SupabaseConfig supabaseConfig) {
        this.supabaseConfig = supabaseConfig == null ? SupabaseConfig.empty() : supabaseConfig;
    }

    public List<NutritionFood> searchFoods(String query) {
        List<NutritionFood> foods = new ArrayList<>();
        String term = query == null ? "" : query.trim();
        String like = "%" + term + "%";
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        List<String> ids = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT " + String.join(", ", FOOD_COLUMNS) + " " +
                        "FROM nutrition_foods " +
                        "WHERE deleted_at IS NULL " +
                        "AND (visibility = 'public' OR owner_id = ?) " +
                        "AND (name LIKE ? COLLATE NOCASE " +
                        "OR brand LIKE ? COLLATE NOCASE) " +
                        "ORDER BY kind ASC, brand COLLATE NOCASE ASC, name COLLATE NOCASE ASC LIMIT 100",
                new String[]{userId, like, like}
        )) {
            while (cursor.moveToNext()) {
                rows.add(readFoodRow(cursor));
                ids.add(cursor.getString(0));
            }
        }

        // 확장 영양소는 한 번에 모아 읽는다. 결과 건수만큼 질의하면 검색이 느려진다.
        Map<String, Map<String, Double>> micronutrients = loadMicronutrients(database, ids);
        for (Object[] row : rows) {
            foods.add(buildFood(row, micronutrients.get((String) row[0])));
        }
        return foods;
    }

    public List<NutritionFood> searchVerifiedFoods(String query, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, VERIFIED_FOOD_SEARCH_LIMIT_MAX));
        String term = query == null ? "" : query.trim();
        String like = "%" + term + "%";
        Set<String> curatedIds = VerifiedFoodCatalogSeed.curatedFoodIds();
        StringBuilder curatedPlaceholders = new StringBuilder();
        for (int index = 0; index < curatedIds.size(); index++) {
            if (index > 0) {
                curatedPlaceholders.append(", ");
            }
            curatedPlaceholders.append('?');
        }
        List<String> arguments = new ArrayList<>();
        arguments.add(NutritionFood.KIND_INGREDIENT);
        arguments.add(VERIFIED_FOOD_ID_PREFIX);
        arguments.add(VerifiedFoodCatalogSeed.SOURCE_TYPE);
        arguments.add(VERIFIED_FOOD_SOURCE_REFERENCE_PREFIX);
        arguments.add(VerifiedFoodCatalogSeed.RICE_SOURCE_TYPE);
        arguments.add(VerifiedFoodCatalogSeed.RICE_SOURCE_REFERENCE);
        arguments.addAll(curatedIds);
        arguments.add(like);
        arguments.add(like);
        List<NutritionFood> candidates = readFoods(
                "owner_id IS NULL " +
                        "AND visibility = 'public' " +
                        "AND kind = ? " +
                        "AND id LIKE ? " +
                        "AND ((source_type = ? AND source_reference LIKE ?) " +
                        "OR (source_type = ? AND source_reference = ?)) " +
                        "AND id IN (" + curatedPlaceholders + ") " +
                        "AND (name LIKE ? COLLATE NOCASE " +
                        "OR COALESCE(brand, '') LIKE ? COLLATE NOCASE)",
                arguments.toArray(new String[0]),
                "brand COLLATE NOCASE ASC, name COLLATE NOCASE ASC",
                String.valueOf(safeLimit)
        );
        List<NutritionFood> verified = new ArrayList<>();
        for (NutritionFood candidate : candidates) {
            if (VerifiedFoodCatalogSeed.isVerifiedSeedFood(candidate)) {
                verified.add(candidate);
            }
        }
        return verified;
    }

    /** Saved recipes for the menu browser. */
    public List<NutritionFood> savedRecipes() {
        return readFoods(
                "kind = ? AND (visibility = 'public' OR owner_id = ?)",
                new String[]{NutritionFood.KIND_RECIPE, userId},
                "updated_at DESC, name COLLATE NOCASE ASC",
                null
        );
    }

    /** Components of a saved recipe, returned in the order used when it was saved. */
    public List<RecipeComponent> recipeComponents(String recipeId) {
        if (recipeId == null || recipeId.trim().isEmpty()) {
            return new ArrayList<>();
        }

        SQLiteDatabase database = dbHelper.getReadableDatabase();
        List<Object[]> foodRows = new ArrayList<>();
        List<Double> quantities = new ArrayList<>();
        List<String> units = new ArrayList<>();
        List<String> foodIds = new ArrayList<>();
        String prefixedFoodColumns = "f." + String.join(", f.", FOOD_COLUMNS);
        String sql = "SELECT c.quantity, c.unit, " + prefixedFoodColumns + " " +
                "FROM nutrition_food_components c " +
                "JOIN nutrition_foods f ON f.id = c.child_food_id " +
                "WHERE c.parent_food_id = ? " +
                "AND c.deleted_at IS NULL " +
                "AND f.deleted_at IS NULL " +
                "AND (f.visibility = 'public' OR f.owner_id = ?) " +
                "ORDER BY c.order_index ASC, c.created_at ASC";
        try (Cursor cursor = database.rawQuery(sql, new String[]{recipeId, userId})) {
            while (cursor.moveToNext()) {
                quantities.add(cursor.getDouble(0));
                units.add(cursor.getString(1));
                Object[] foodRow = readFoodRow(cursor, 2);
                foodRows.add(foodRow);
                foodIds.add((String) foodRow[0]);
            }
        }

        Map<String, Map<String, Double>> micronutrients =
                loadMicronutrients(database, foodIds);
        List<RecipeComponent> components = new ArrayList<>();
        for (int index = 0; index < foodRows.size(); index++) {
            Object[] foodRow = foodRows.get(index);
            NutritionFood food = buildFood(foodRow, micronutrients.get((String) foodRow[0]));
            components.add(new RecipeComponent(food, quantities.get(index), units.get(index)));
        }
        return components;
    }

    private List<NutritionFood> readFoods(
            String selection,
            String[] selectionArgs,
            String orderBy,
            String limit
    ) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        List<String> ids = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        String limitClause = limit == null ? "" : " LIMIT " + limit;
        try (Cursor cursor = database.rawQuery(
                "SELECT " + String.join(", ", FOOD_COLUMNS) + " " +
                        "FROM nutrition_foods " +
                        "WHERE deleted_at IS NULL AND " + selection + " " +
                        "ORDER BY " + orderBy + limitClause,
                selectionArgs
        )) {
            while (cursor.moveToNext()) {
                rows.add(readFoodRow(cursor));
                ids.add(cursor.getString(0));
            }
        }

        Map<String, Map<String, Double>> micronutrients = loadMicronutrients(database, ids);
        List<NutritionFood> foods = new ArrayList<>();
        for (Object[] row : rows) {
            foods.add(buildFood(row, micronutrients.get((String) row[0])));
        }
        return foods;
    }

    /**
     * 새 음식/재료를 저장한다.
     *
     * <p>필수 7종이 모두 있어야 저장된다. 권고 영양소는 비워 두면 NULL(모름)로 남고,
     * 0으로 채워지지 않는다.</p>
     */
    public NutritionFood saveFood(
            String name,
            String kind,
            double basisAmount,
            String basisUnit,
            String prepState,
            NutritionProfile profile,
            String sourceType,
            String sourceReference,
            String sourceVersion
    ) {
        return saveFood(
                name,
                null,
                kind,
                NutritionFood.categoryForKind(kind),
                basisAmount,
                basisUnit,
                NutritionFood.cookingMethodForPrepState(prepState),
                profile,
                sourceType,
                sourceReference,
                sourceVersion
        );
    }

    /** 기존 호출부와 레거시 입력을 위한 호환 오버로드. */
    public NutritionFood saveFood(
            String name,
            String brand,
            String kind,
            double basisAmount,
            String basisUnit,
            String prepState,
            NutritionProfile profile,
            String sourceType,
            String sourceReference,
            String sourceVersion
    ) {
        return saveFood(
                name,
                brand,
                kind,
                NutritionFood.categoryForKind(kind),
                basisAmount,
                basisUnit,
                NutritionFood.cookingMethodForPrepState(prepState),
                profile,
                sourceType,
                sourceReference,
                sourceVersion
        );
    }

    public NutritionFood saveFood(
            String name,
            String brand,
            String kind,
            String category,
            double basisAmount,
            String basisUnit,
            String cookingMethod,
            NutritionProfile profile,
            String sourceType,
            String sourceReference,
            String sourceVersion
    ) {
        String normalizedName = requireName(name);
        String normalizedBrand = emptyToNull(brand);
        String normalizedKind = NutritionFood.normalizeKind(kind);
        String normalizedCategory = NutritionFood.normalizeCategory(category);
        String normalizedUnit = NutritionUnit.requireSupported(basisUnit);
        String normalizedCookingMethod = NutritionFood.normalizeCookingMethod(cookingMethod);
        String normalizedPrepState = NutritionFood.prepStateForCookingMethod(normalizedCookingMethod);
        if (basisAmount <= 0) {
            throw new IllegalArgumentException("Basis amount must be greater than zero.");
        }
        NutritionProfile normalizedProfile = requireRequiredNutrients(profile);

        NutritionFood food = NutritionFood.builder()
                .id(UUID.randomUUID().toString())
                .ownerId(userId)
                .name(normalizedName)
                .brand(normalizedBrand)
                .kind(normalizedKind)
                .category(normalizedCategory)
                .basis(basisAmount, normalizedUnit)
                .prepState(normalizedPrepState)
                .cookingMethod(normalizedCookingMethod)
                .profile(normalizedProfile)
                .source(emptyToDefault(sourceType, "manual"), emptyToNull(sourceReference))
                .sourceVersion(emptyToNull(sourceVersion))
                .dataVersion(NutritionFood.DATA_VERSION_REQUIRED_SEVEN)
                .build();

        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            database.insertOrThrow("nutrition_foods", null, foodValues(food, now()));
            replaceMicronutrients(database, food);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return food;
    }

    /**
     * Saves a dining-out menu as a private external-menu catalog row.
     *
     * <p>The macro values are user-entered estimates, so this path intentionally stores the
     * macros-only data version and keeps unknown micronutrients as NULL.</p>
     */
    public NutritionFood saveDiningOutMenu(
            String storeName,
            String menuName,
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams
    ) {
        String normalizedStoreName = MealEntryPolicy.requireDiningOutStoreName(storeName);
        String normalizedMenuName = MealEntryPolicy.requireDiningOutMenuName(menuName);
        MealEntryPolicy.requireDiningOutEstimatedMacros(carbsGrams, proteinGrams, fatGrams);
        if (!MealEntryPolicy.hasDiningOutEstimatedMacros(
                carbsGrams,
                proteinGrams,
                fatGrams
        )) {
            throw new IllegalArgumentException(
                    "메뉴로 저장하려면 추정 탄수화물·단백질·지방을 입력하세요."
            );
        }

        return saveDiningOutMenuCatalogRow(
                normalizedStoreName,
                normalizedMenuName,
                NutritionProfile.ofMacros(
                        MealEntryPolicy.estimatedDiningOutCalories(
                                carbsGrams,
                                proteinGrams,
                                fatGrams
                        ),
                        proteinGrams,
                        carbsGrams,
                        fatGrams
                ),
                NutritionFood.DATA_VERSION_MACROS_ONLY
        );
    }

    /** Saves a complete user-estimated dining-out nutrition profile for reuse. */
    public NutritionFood saveDiningOutMenuWithNutrition(
            String storeName,
            String menuName,
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams
    ) {
        String normalizedStoreName = MealEntryPolicy.requireDiningOutStoreName(storeName);
        String normalizedMenuName = MealEntryPolicy.requireDiningOutMenuName(menuName);
        MealEntryPolicy.requireDiningOutEstimatedNutrition(
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sodiumMg,
                sugarsGrams,
                saturatedFatGrams
        );
        if (!MealEntryPolicy.hasDiningOutEstimatedNutrition(
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sodiumMg,
                sugarsGrams,
                saturatedFatGrams
        )) {
            throw new IllegalArgumentException(
                    "메뉴로 저장하려면 외식 영양성분을 입력하세요."
            );
        }

        NutritionProfile profile = NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, calories.doubleValue())
                .value(NutritionProfile.PROTEIN_GRAMS, proteinGrams)
                .value(NutritionProfile.CARBS_GRAMS, carbsGrams)
                .value(NutritionProfile.FAT_GRAMS, fatGrams)
                .value(NutritionProfile.SODIUM_MG, sodiumMg)
                .value(NutritionProfile.SUGARS_GRAMS, sugarsGrams)
                .value(NutritionProfile.SATURATED_FAT_GRAMS, saturatedFatGrams)
                .build();
        return saveDiningOutMenuCatalogRow(
                normalizedStoreName,
                normalizedMenuName,
                profile,
                NutritionFood.DATA_VERSION_REQUIRED_SEVEN
        );
    }

    /** Saves one menu option as a separate reusable Nutrition catalog row. */
    public NutritionFood saveDiningOutOption(
            String storeName,
            String menuName,
            DiningOutOption option
    ) {
        return saveDiningOutOption(storeName, menuName, null, option);
    }

    /** Saves one option while retaining the exact restaurant -> location -> menu identity. */
    public NutritionFood saveDiningOutOption(
            String storeName,
            String menuName,
            DiningOutIdentity identity,
            DiningOutOption option
    ) {
        String normalizedStoreName = MealEntryPolicy.requireDiningOutStoreName(storeName);
        String normalizedMenuName = MealEntryPolicy.requireDiningOutMenuName(menuName);
        if (option == null || !option.hasNutrition() || !option.hasCompleteMacros()) {
            throw new IllegalArgumentException(
                    "옵션 영양성분은 칼로리와 탄수화물·단백질·지방을 모두 입력해야 합니다."
            );
        }
        Double protein = option.profile.value(NutritionProfile.PROTEIN_GRAMS);
        Double carbs = option.profile.value(NutritionProfile.CARBS_GRAMS);
        Double fat = option.profile.value(NutritionProfile.FAT_GRAMS);
        MealEntryPolicy.requireDiningOutEstimatedMacros(carbs, protein, fat);
        double calories = option.profile.isKnown(NutritionProfile.CALORIES_KCAL)
                ? option.profile.calories()
                : MealEntryPolicy.estimatedDiningOutCalories(carbs, protein, fat);
        NutritionProfile profile = NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, calories)
                .value(NutritionProfile.PROTEIN_GRAMS, protein)
                .value(NutritionProfile.CARBS_GRAMS, carbs)
                .value(NutritionProfile.FAT_GRAMS, fat)
                .build();
        return saveDiningOutOptionCatalogRow(
                normalizedStoreName,
                normalizedMenuName,
                option.name,
                profile,
                diningOutOptionSourceReference(normalizedStoreName, normalizedMenuName, identity)
        );
    }

    private String diningOutOptionSourceReference(
            String storeName,
            String menuName,
            DiningOutIdentity identity
    ) {
        JSONObject reference = new JSONObject();
        try {
            reference.put("contract_version", "dining-out-option.v1");
            reference.put("restaurant_name", storeName);
            reference.put("menu_name", menuName);
            if (identity == null) {
                reference.put("restaurant_id", JSONObject.NULL);
                reference.put("restaurant_location_id", JSONObject.NULL);
                reference.put("restaurant_menu_id", JSONObject.NULL);
            } else {
                reference.put("restaurant_id", identity.restaurantId);
                reference.put("restaurant_location_id", identity.restaurantLocationId);
                reference.put("restaurant_menu_id", identity.restaurantMenuId);
            }
            return reference.toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Dining-out option identity could not be encoded.", error);
        }
    }

    private NutritionFood saveDiningOutMenuCatalogRow(
            String normalizedStoreName,
            String normalizedMenuName,
        NutritionProfile profile,
        int dataVersion
    ) {
        return saveDiningOutCatalogRow(
                normalizedStoreName,
                normalizedMenuName,
                normalizedMenuName,
                profile,
                dataVersion,
                "manual_estimate",
                "dining_out"
        );
    }

    private NutritionFood saveDiningOutOptionCatalogRow(
            String normalizedStoreName,
            String normalizedMenuName,
            String normalizedOptionName,
            NutritionProfile profile,
            String sourceReference
    ) {
        return saveDiningOutCatalogRow(
                normalizedStoreName,
                normalizedOptionName,
                normalizedMenuName,
                profile,
                NutritionFood.DATA_VERSION_MACROS_ONLY,
                "manual_option",
                sourceReference
        );
    }

    private NutritionFood saveDiningOutCatalogRow(
            String normalizedStoreName,
            String normalizedFoodName,
            String normalizedMenuName,
            NutritionProfile profile,
            int dataVersion,
            String sourceType,
            String sourceReference
    ) {

        SQLiteDatabase database = dbHelper.getWritableDatabase();
        String existingId = null;
        String existingCreatedAt = null;
        try (Cursor cursor = database.rawQuery(
                "SELECT id, created_at FROM nutrition_foods " +
                        "WHERE owner_id = ? AND kind = ? AND name = ? COLLATE NOCASE " +
                        "AND brand = ? COLLATE NOCASE AND source_type = ? " +
                        "AND source_reference = ? " +
                        "AND deleted_at IS NULL ORDER BY updated_at DESC LIMIT 1",
                new String[]{
                        userId,
                        NutritionFood.KIND_EXTERNAL_MENU,
                        normalizedFoodName,
                        normalizedStoreName,
                        sourceType,
                        sourceReference
                }
        )) {
            if (cursor.moveToFirst()) {
                existingId = cursor.getString(0);
                existingCreatedAt = cursor.getString(1);
            }
        }

        String timestamp = now();
        NutritionFood food = NutritionFood.builder()
                .id(existingId == null ? UUID.randomUUID().toString() : existingId)
                .ownerId(userId)
                .name(normalizedFoodName)
                .brand(normalizedStoreName)
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .category(NutritionFood.CATEGORY_OTHER)
                .basis(1.0, NutritionUnit.SERVING)
                .prepState(NutritionFood.PREP_AS_SERVED)
                .profile(profile)
                .source(sourceType, sourceReference)
                .dataVersion(dataVersion)
                .build();

        ContentValues values = foodValues(food, timestamp);
        if (existingCreatedAt != null && !existingCreatedAt.trim().isEmpty()) {
            values.put("created_at", existingCreatedAt);
        }
        database.beginTransaction();
        try {
            if (existingId == null) {
                database.insertOrThrow("nutrition_foods", null, values);
            } else {
                database.update(
                        "nutrition_foods",
                        values,
                        "id = ? AND owner_id = ?",
                        new String[]{existingId, userId}
                );
            }
            replaceMicronutrients(database, food);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return food;
    }

    /**
     * 구성 재료로 레시피를 만든다.
     *
     * <p>레시피의 영양성분은 개별 음식과 같은 규칙으로 합산된다. 재료 중 하나라도 모르는
     * 영양소는 레시피에서도 모름으로 남으며, 0으로 합산되지 않는다.</p>
     */
    /** Builds a composed menu for one meal without adding it to the reusable catalog. */
    public NutritionFood buildRecipeForMeal(String name, List<MealCompositionItem> items) {
        String normalizedName = requireName(name);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Recipe needs at least one food.");
        }

        return recipeFood(null, normalizedName, items);
    }

    public NutritionFood saveRecipe(String name, List<MealCompositionItem> items) {
        String normalizedName = requireName(name);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Recipe needs at least one food.");
        }

        NutritionFood recipe = recipeFood(UUID.randomUUID().toString(), normalizedName, items);

        String timestamp = now();
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            database.insertOrThrow("nutrition_foods", null, foodValues(recipe, timestamp));
            replaceMicronutrients(database, recipe);

            int orderIndex = 0;
            for (MealCompositionItem item : items) {
                ContentValues component = new ContentValues();
                component.put("id", UUID.randomUUID().toString());
                component.put("owner_id", userId);
                component.put("parent_food_id", recipe.id);
                component.put("child_food_id", item.food.id);
                component.put("quantity", item.quantity);
                component.put("unit", item.food.basisUnit);
                component.put("order_index", orderIndex++);
                component.put("created_at", timestamp);
                component.put("updated_at", timestamp);
                component.putNull("deleted_at");
                database.insertOrThrow("nutrition_food_components", null, component);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return recipe;
    }

    private NutritionFood recipeFood(
            String id,
            String normalizedName,
            List<MealCompositionItem> items
    ) {

        NutritionProfile total = NutritionCalculator.recipeProfile(items);
        return NutritionFood.builder()
                .id(id)
                .ownerId(userId)
                .name(normalizedName)
                .kind(NutritionFood.KIND_RECIPE)
                .category(NutritionFood.CATEGORY_RECIPE)
                .basis(1.0, "serving")
                .prepState(NutritionFood.PREP_AS_SERVED)
                .profile(total)
                .source("manual_recipe", null)
                .dataVersion(total.hasAllRequired()
                        ? NutritionFood.DATA_VERSION_REQUIRED_SEVEN
                        : NutritionFood.DATA_VERSION_MACROS_ONLY)
                .build();
    }

    /** Active user-approved PriceTrace link, enriched only from the local read cache. */
    public ProductNutritionLink approvedProductLink(String nutritionFoodId) {
        List<ProductNutritionLink> links = readProductLinks(
                nutritionFoodId,
                ProductNutritionLink.STATUS_APPROVED
        );
        return links.isEmpty() ? null : links.get(0);
    }

    /** Public means explicitly published for the approved PriceTrace product link. */
    public boolean isFoodPublic(String nutritionFoodId) {
        if (nutritionFoodId == null || nutritionFoodId.trim().isEmpty()) {
            return false;
        }
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        try (Cursor cursor = database.rawQuery(
                "SELECT visibility FROM nutrition_foods " +
                        "WHERE id = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{nutritionFoodId}
        )) {
            return cursor.moveToFirst() && "public".equals(cursor.getString(0));
        }
    }

    /**
     * Publishes or unpublishes only through the authenticated, validated Nutrition RPC.
     * The local visibility is updated from the authoritative RPC response.
     */
    public PublicationState setProductNutritionPublication(
            String nutritionFoodId,
            String catalogProductId,
            boolean publish
    ) throws Exception {
        SupabaseConfig config = supabaseConfig;
        if (config == null || !config.isConfigured()) {
            throw new IllegalStateException("영양 DB 계정 로그인이 필요합니다.");
        }
        String normalizedFoodId = requireName(nutritionFoodId);
        String normalizedCatalogProductId;
        try {
            normalizedCatalogProductId = UUID.fromString(catalogProductId).toString();
        } catch (Exception error) {
            throw new IllegalArgumentException("PriceTrace 정확 규격 ID가 올바르지 않습니다.", error);
        }

        HttpURLConnection connection = openConnection(
                joinUrl(config.supabaseUrl,
                        "/rest/v1/rpc/set_product_nutrition_publication_v1"),
                "POST",
                config
        );
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        JSONObject request = new JSONObject();
        request.put("p_nutrition_food_id", normalizedFoodId);
        request.put("p_catalog_product_id", normalizedCatalogProductId);
        request.put("p_publish", publish);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }

        String body = readResponseOrThrow(connection, 200);
        JSONArray rows = body.isEmpty() ? new JSONArray() : new JSONArray(body);
        if (rows.length() != 1) {
            throw new IOException("영양 공개 RPC가 정확히 한 행을 반환하지 않았습니다.");
        }
        JSONObject row = rows.getJSONObject(0);
        String returnedFoodId = nullableString(row, "nutrition_food_id");
        String returnedCatalogProductId = nullableString(row, "catalog_product_id");
        String visibility = nullableString(row, "visibility");
        if (!normalizedFoodId.equals(returnedFoodId)
                || !normalizedCatalogProductId.equals(returnedCatalogProductId)
                || !("public".equals(visibility) || "private".equals(visibility))
                || publish != "public".equals(visibility)) {
            throw new IOException("영양 공개 RPC 응답이 요청한 항목과 일치하지 않습니다.");
        }

        String updatedAt = emptyToDefault(nullableString(row, "updated_at"), now());
        ContentValues values = new ContentValues();
        values.put("visibility", visibility);
        values.put("updated_at", updatedAt);
        int changed = dbHelper.getWritableDatabase().update(
                "nutrition_foods",
                values,
                "id = ? AND owner_id = ? AND deleted_at IS NULL",
                new String[]{normalizedFoodId, config.effectiveUserId()}
        );
        if (changed != 1) {
            throw new IOException("공개된 영양정보를 기기 카탈로그에 반영하지 못했습니다.");
        }
        return new PublicationState(
                normalizedFoodId,
                normalizedCatalogProductId,
                "public".equals(visibility),
                Math.max(1, row.optInt("publication_revision", 1)),
                nullableString(row, "published_at")
        );
    }

    /** Pending owner-specific suggestions written by a trusted PriceTrace integration. */
    public List<ProductNutritionLink> pendingProductLinkSuggestions(String nutritionFoodId) {
        return readProductLinks(nutritionFoodId, ProductNutritionLink.STATUS_SUGGESTED);
    }

    public void cachePriceTraceProducts(List<ProductReadV1> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            String fetchedAt = now();
            for (ProductReadV1 product : products) {
                if (product != null) {
                    for (ProductReadV1 exactVariant : product.exactCatalogVariants()) {
                        cachePriceTraceProduct(database, exactVariant, fetchedAt);
                    }
                }
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /**
     * Reads the public Nutrition projection for one exact PriceTrace catalog product.
     * PriceTrace search returns product identity/specification only; this separate call resolves
     * the Nutrition publication before a meal-entry form can prefill nutrition values.
     */
    public PublicProductNutrition fetchPublicProductNutrition(String catalogProductId)
            throws Exception {
        SupabaseConfig config = supabaseConfig;
        if (config == null || !config.isConnectionConfigured()) {
            throw new IllegalStateException("Nutrition DB connection is required.");
        }
        String normalizedCatalogProductId;
        try {
            normalizedCatalogProductId = UUID.fromString(catalogProductId).toString();
        } catch (Exception error) {
            throw new IllegalArgumentException("PriceTrace catalog product ID is invalid.", error);
        }

        HttpURLConnection connection = openConnection(
                joinUrl(config.supabaseUrl,
                        "/rest/v1/rpc/get_public_product_nutrition_v1"),
                "POST",
                config.withoutSessionIdentity()
        );
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        JSONObject request = new JSONObject();
        request.put("p_namespace", "pricetrace");
        request.put("p_catalog_product_id", normalizedCatalogProductId);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }

        String body = readResponseOrThrow(connection, 200);
        JSONArray rows = body.isEmpty() ? new JSONArray() : new JSONArray(body);
        if (rows.length() == 0) {
            return null;
        }
        return parsePublicProductNutrition(rows.getJSONObject(0));
    }

    public static PublicProductNutrition parsePublicProductNutrition(JSONObject row)
            throws JSONException {
        if (row == null) {
            throw new IllegalArgumentException("Public nutrition response is required.");
        }
        String catalogProductId = nullableString(row, "catalog_product_id");
        if (catalogProductId == null) {
            throw new IllegalArgumentException("Public nutrition response has no catalog product ID.");
        }

        NutritionProfile.Builder profile = NutritionProfile.builder();
        JSONObject nutritionValues = row.optJSONObject("nutrition_values");
        if (nutritionValues != null) {
            for (String key : NutritionProfile.REQUIRED_KEYS) {
                putNumericNutritionValue(profile, key, nutritionValues.opt(key));
            }
            for (String key : NutritionProfile.RECOMMENDED_TYPED_KEYS) {
                putNumericNutritionValue(profile, key, nutritionValues.opt(key));
            }
        }

        JSONObject micronutrients = row.optJSONObject("micronutrients");
        if (micronutrients != null) {
            for (NutrientCode nutrient : NutrientCode.all()) {
                Object value = micronutrients.opt(nutrient.code);
                if (value instanceof JSONObject) {
                    value = ((JSONObject) value).opt("amount");
                }
                putNumericNutritionValue(profile, nutrient.code, value);
            }
        }

        return new PublicProductNutrition(
                catalogProductId,
                nullableString(row, "nutrition_food_id"),
                nullableString(row, "name"),
                nullableString(row, "basis_unit"),
                row.optDouble("basis_amount", 0),
                profile.build(),
                nullableString(row, "catalog_product_revision"),
                row.optDouble("catalog_content_amount", 0),
                nullableString(row, "catalog_content_unit"),
                row.optInt("catalog_package_count", 0)
        );
    }

    private static void putNumericNutritionValue(
            NutritionProfile.Builder profile,
            String key,
            Object raw
    ) {
        if (raw == null || raw == JSONObject.NULL) {
            return;
        }
        double value;
        if (raw instanceof Number) {
            value = ((Number) raw).doubleValue();
        } else {
            try {
                value = Double.parseDouble(String.valueOf(raw));
            } catch (NumberFormatException ignored) {
                return;
            }
        }
        if (!Double.isNaN(value) && !Double.isInfinite(value) && value >= 0) {
            profile.value(key, value);
        }
    }

    /**
     * Creates an immediately approved manual link only after the caller selected an exact ID.
     * No name-based match is accepted here.
     */
    public ProductNutritionLink linkProduct(String nutritionFoodId, ProductReadV1 product) {
        if (product == null) {
            throw new IllegalArgumentException("연결할 표준상품을 선택하세요.");
        }
        if (!product.isExactCatalogProduct()) {
            throw new IllegalArgumentException(
                    "표준상품의 여러 규격 중 하나를 임의로 연결할 수 없습니다."
            );
        }
        requirePriceTraceCatalogMetadata(product);
        requireLinkableFood(nutritionFoodId);
        String timestamp = now();
        String id = UUID.randomUUID().toString();
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            cachePriceTraceProduct(database, product, timestamp);
            softDeleteApprovedLinks(database, nutritionFoodId, null, timestamp);

            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("owner_id", userId);
            values.put("nutrition_food_id", nutritionFoodId);
            values.put("catalog_product_id", product.catalogProductId);
            putNullable(values, "standard_product_id", product.standardProductId);
            putPriceTraceCatalogMetadata(values, product);
            values.put("status", ProductNutritionLink.STATUS_APPROVED);
            values.put("source_type", ProductNutritionLink.SOURCE_MANUAL);
            values.putNull("proposal_reference");
            values.put("product_contract_version", ProductReadV1.CONTRACT_VERSION);
            values.put("revision", 1);
            values.put("reviewed_at", timestamp);
            values.put("created_at", timestamp);
            values.put("updated_at", timestamp);
            values.putNull("deleted_at");
            database.insertOrThrow("product_nutrition_links", null, values);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return approvedProductLink(nutritionFoodId);
    }

    /** Approves only the catalogProductId carried by the selected suggestion row. */
    public ProductNutritionLink approveProductSuggestion(
            String suggestionId,
            ProductReadV1 exactProduct
    ) {
        if (exactProduct == null) {
            throw new IllegalArgumentException("제안된 catalogProductId를 확인할 수 없습니다.");
        }
        requirePriceTraceCatalogMetadata(exactProduct);
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        String nutritionFoodId;
        String suggestedCatalogProductId;
        try (Cursor cursor = database.rawQuery(
                "SELECT nutrition_food_id, catalog_product_id " +
                        "FROM product_nutrition_links " +
                        "WHERE id = ? AND owner_id = ? AND status = 'suggested' " +
                        "AND deleted_at IS NULL LIMIT 1",
                new String[]{suggestionId, userId}
        )) {
            if (!cursor.moveToFirst()) {
                throw new IllegalArgumentException("승인할 PriceTrace 제안을 찾지 못했습니다.");
            }
            nutritionFoodId = cursor.getString(0);
            suggestedCatalogProductId = cursor.getString(1);
        }
        if (!exactProduct.catalogProductId.equals(suggestedCatalogProductId)) {
            throw new IllegalArgumentException("제안 ID와 선택한 catalogProductId가 다릅니다.");
        }

        String timestamp = now();
        database.beginTransaction();
        try {
            cachePriceTraceProduct(database, exactProduct, timestamp);
            softDeleteApprovedLinks(database, nutritionFoodId, suggestionId, timestamp);
            database.execSQL(
                    "UPDATE product_nutrition_links SET status = 'approved', reviewed_at = ?, " +
                            "catalog_product_revision = ?, catalog_content_amount = ?, " +
                            "catalog_content_unit = ?, catalog_package_count = ?, " +
                            "updated_at = ?, revision = revision + 1 " +
                            "WHERE id = ? AND owner_id = ? AND status = 'suggested' " +
                            "AND deleted_at IS NULL",
                    new Object[]{
                            timestamp,
                            exactProduct.revision,
                            exactProduct.contentAmount,
                            exactProduct.contentUnit,
                            exactProduct.packageCount,
                            timestamp,
                            suggestionId,
                            userId
                    }
            );
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return approvedProductLink(nutritionFoodId);
    }

    public boolean rejectProductSuggestion(String suggestionId) {
        String timestamp = now();
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        int nextRevision;
        try (Cursor cursor = database.rawQuery(
                "SELECT revision FROM product_nutrition_links WHERE id = ? AND owner_id = ? " +
                        "AND status = 'suggested' AND deleted_at IS NULL LIMIT 1",
                new String[]{suggestionId, userId}
        )) {
            if (!cursor.moveToFirst()) {
                return false;
            }
            nextRevision = Math.max(1, cursor.getInt(0)) + 1;
        }
        int changed;
        ContentValues values = new ContentValues();
        values.put("status", ProductNutritionLink.STATUS_REJECTED);
        values.put("reviewed_at", timestamp);
        values.put("updated_at", timestamp);
        values.put("revision", nextRevision);
        changed = database.update(
                "product_nutrition_links",
                values,
                "id = ? AND owner_id = ? AND status = 'suggested' AND deleted_at IS NULL",
                new String[]{suggestionId, userId}
        );
        return changed > 0;
    }

    /** Soft-unlinks without deleting either the Nutrition entry or any meal snapshot. */
    public boolean unlinkProduct(String nutritionFoodId) {
        ProductNutritionLink existing = approvedProductLink(nutritionFoodId);
        if (existing == null) {
            return false;
        }
        String timestamp = now();
        dbHelper.getWritableDatabase().execSQL(
                "UPDATE product_nutrition_links SET deleted_at = ?, updated_at = ?, " +
                        "revision = revision + 1 " +
                        "WHERE owner_id = ? AND nutrition_food_id = ? " +
                        "AND status = 'approved' AND deleted_at IS NULL",
                new Object[]{timestamp, timestamp, userId, nutritionFoodId}
        );
        return true;
    }

    private List<ProductNutritionLink> readProductLinks(String nutritionFoodId, String status) {
        List<ProductNutritionLink> links = new ArrayList<>();
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        try (Cursor cursor = database.rawQuery(
                "SELECT l.id, l.owner_id, l.nutrition_food_id, l.catalog_product_id, " +
                        "l.standard_product_id, l.status, l.source_type, l.proposal_reference, " +
                        "l.revision, l.reviewed_at, l.catalog_product_revision, " +
                        "l.catalog_content_amount, l.catalog_content_unit, l.catalog_package_count, " +
                        "c.standard_product_id, c.product_name, c.brand_name, c.seller_name, " +
                        "c.latest_price_krw, c.price_observed_at, c.content_amount, c.content_unit, " +
                        "c.package_count, c.catalog_product_revision " +
                        "FROM product_nutrition_links l " +
                        "LEFT JOIN pricetrace_product_cache c " +
                        "ON c.catalog_product_id = l.catalog_product_id " +
                        "WHERE l.owner_id = ? AND l.nutrition_food_id = ? " +
                        "AND l.status = ? AND l.deleted_at IS NULL " +
                        "ORDER BY l.updated_at DESC, l.created_at DESC",
                new String[]{userId, nutritionFoodId, status}
        )) {
            while (cursor.moveToNext()) {
                ProductReadV1 product = null;
                if (!cursor.isNull(14)) {
                    try {
                        product = new ProductReadV1(
                                cursor.getString(3),
                                cursor.isNull(4) ? cursor.isNull(14) ? null : cursor.getString(14) : cursor.getString(4),
                                cursor.getString(15),
                                cursor.isNull(16) ? null : cursor.getString(16),
                                cursor.isNull(17) ? null : cursor.getString(17),
                                cursor.isNull(18) ? null : cursor.getInt(18),
                                cursor.isNull(19) ? null : cursor.getString(19),
                                cursor.isNull(20) ? null : cursor.getDouble(20),
                                cursor.isNull(21) ? null : cursor.getString(21),
                                cursor.isNull(22) ? null : cursor.getInt(22),
                                cursor.isNull(23) ? null : cursor.getString(23)
                        );
                    } catch (IllegalArgumentException ignored) {
                        // A corrupt cache must not hide the underlying exact link decision.
                    }
                }
                links.add(new ProductNutritionLink(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.isNull(4) ? null : cursor.getString(4),
                        cursor.getString(5),
                        cursor.getString(6),
                        cursor.isNull(7) ? null : cursor.getString(7),
                        cursor.getInt(8),
                        cursor.isNull(9) ? null : cursor.getString(9),
                        cursor.isNull(10) ? null : cursor.getString(10),
                        cursor.isNull(11) ? null : cursor.getDouble(11),
                        cursor.isNull(12) ? null : cursor.getString(12),
                        cursor.isNull(13) ? null : cursor.getInt(13),
                        product
                ));
            }
        }
        return links;
    }

    private void requireLinkableFood(String nutritionFoodId) {
        String normalized = nutritionFoodId == null ? "" : nutritionFoodId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("영양 음식 ID가 필요합니다.");
        }
        try (Cursor cursor = dbHelper.getReadableDatabase().rawQuery(
                "SELECT 1 FROM nutrition_foods WHERE id = ? AND deleted_at IS NULL " +
                        "AND (visibility = 'public' OR owner_id = ?) LIMIT 1",
                new String[]{normalized, userId}
        )) {
            if (!cursor.moveToFirst()) {
                throw new IllegalArgumentException("연결할 영양 음식을 찾지 못했습니다.");
            }
        }
    }

    private void softDeleteApprovedLinks(
            SQLiteDatabase database,
            String nutritionFoodId,
            String exceptId,
            String timestamp
    ) {
        String exceptClause = exceptId == null ? "" : " AND id <> ?";
        List<Object> arguments = new ArrayList<>();
        arguments.add(timestamp);
        arguments.add(timestamp);
        arguments.add(userId);
        arguments.add(nutritionFoodId);
        if (exceptId != null) {
            arguments.add(exceptId);
        }
        database.execSQL(
                "UPDATE product_nutrition_links SET deleted_at = ?, updated_at = ?, " +
                        "revision = revision + 1 WHERE owner_id = ? AND nutrition_food_id = ? " +
                        "AND status = 'approved' AND deleted_at IS NULL" + exceptClause,
                arguments.toArray()
        );
    }

    private void cachePriceTraceProduct(
            SQLiteDatabase database,
            ProductReadV1 product,
            String fetchedAt
    ) {
        ContentValues values = new ContentValues();
        values.put("catalog_product_id", product.catalogProductId);
        putNullable(values, "standard_product_id", product.standardProductId);
        values.put("product_name", product.name);
        putNullable(values, "brand_name", product.brand);
        putNullable(values, "seller_name", product.sellerName);
        if (product.latestObservedPriceKrw == null) {
            values.putNull("latest_price_krw");
            values.putNull("price_observed_at");
        } else {
            values.put("latest_price_krw", product.latestObservedPriceKrw);
            values.put("price_observed_at", product.observedAt);
        }
        putNullableDouble(values, "content_amount", product.contentAmount);
        putNullable(values, "content_unit", product.contentUnit);
        if (product.packageCount == null) {
            values.putNull("package_count");
        } else {
            values.put("package_count", product.packageCount);
        }
        putNullable(values, "catalog_product_revision", product.revision);
        values.put("contract_version", ProductReadV1.CONTRACT_VERSION);
        values.put("fetched_at", fetchedAt);
        database.insertWithOnConflict(
                "pricetrace_product_cache",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    private void requirePriceTraceCatalogMetadata(ProductReadV1 product) {
        if (!product.hasValidPriceTraceCatalogMetadata()) {
            throw new IllegalArgumentException(
                    "PriceTrace product-read.v1의 상품별 revision·규격값이 없거나 허용값이 아닙니다."
            );
        }
    }

    private void putPriceTraceCatalogMetadata(ContentValues values, ProductReadV1 product) {
        values.put("catalog_product_revision", product.revision);
        values.put("catalog_content_amount", product.contentAmount);
        values.put("catalog_content_unit", product.contentUnit);
        values.put("catalog_package_count", product.packageCount);
    }

    public synchronized CatalogSyncResult syncRemote() throws Exception {
        SupabaseConfig config = supabaseConfig;
        if (config == null || !config.isConnectionConfigured()) {
            return new CatalogSyncResult(0, 0);
        }

        int pulledRows = pullFoods(config);
        pulledRows += pullNutrients(config);
        pulledRows += pullComponents(config);
        if (config.isConfigured()) {
            pulledRows += pullProductLinks(config);
        }

        int pushedRows = 0;
        if (config.isConfigured()) {
            pushedRows += pushTable(config, "nutrition_foods", FOOD_SYNC_COLUMNS);
            pushedRows += pushTable(config, "nutrition_food_nutrients", NUTRIENT_SYNC_COLUMNS);
            pushedRows += pushTable(config, "nutrition_food_components", COMPONENT_SYNC_COLUMNS);
            pushedRows += pushProductLinks(config);

            // Conditional writes can lose a race without overwriting it. Pull once more to
            // converge on the remote winner and to receive deletion tombstones.
            pulledRows += pullFoods(config);
            pulledRows += pullNutrients(config);
            pulledRows += pullComponents(config);
            pulledRows += pullProductLinks(config);
        }
        return new CatalogSyncResult(pushedRows, pulledRows);
    }

    private int pushTable(SupabaseConfig config, String table, String[] columns) throws Exception {
        if (!CATALOG_TABLES.contains(table)) {
            throw new IllegalArgumentException(
                    "Nutrition catalog only syncs food data, not user records: " + table
            );
        }
        JSONArray rows = new JSONArray();
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        try (Cursor cursor = database.rawQuery(
                "SELECT " + String.join(", ", columns) + " FROM " + table +
                        publicationSafePushWhere(table),
                new String[]{userId}
        )) {
            while (cursor.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int index = 0; index < columns.length; index++) {
                    putCursorValue(row, columns[index], cursor, index);
                }
                rows.put(row);
            }
        }
        return postRows(config, table, rows);
    }

    /** Public rows are immutable through ordinary sync; publication RPCs own that transition. */
    static String publicationSafePushWhere(String table) {
        if ("nutrition_foods".equals(table)) {
            return " WHERE owner_id = ? AND visibility = 'private'";
        }
        if ("nutrition_food_nutrients".equals(table)) {
            return " WHERE owner_id = ? AND EXISTS (" +
                    "SELECT 1 FROM nutrition_foods parent " +
                    "WHERE parent.id = nutrition_food_nutrients.food_id " +
                    "AND parent.visibility = 'private')";
        }
        if ("nutrition_food_components".equals(table)) {
            return " WHERE owner_id = ? AND EXISTS (" +
                    "SELECT 1 FROM nutrition_foods parent " +
                    "WHERE parent.id = nutrition_food_components.parent_food_id " +
                    "AND parent.visibility = 'private')";
        }
        if ("product_nutrition_links".equals(table)) {
            return " WHERE owner_id = ? AND EXISTS (" +
                    "SELECT 1 FROM nutrition_foods parent " +
                    "WHERE parent.id = product_nutrition_links.nutrition_food_id " +
                    "AND parent.visibility = 'private')";
        }
        return " WHERE owner_id = ?";
    }

    /**
     * Manual rows can be inserted/upserted by the owner. Trusted PriceTrace suggestions are
     * service-role-created, so the app only PATCHes their review state and never re-inserts them.
     */
    private int pushProductLinks(SupabaseConfig config) throws Exception {
        JSONArray deletedManualRows = new JSONArray();
        JSONArray activeManualRows = new JSONArray();
        List<JSONObject> deletedSuggestionRows = new ArrayList<>();
        List<JSONObject> activeSuggestionDecisions = new ArrayList<>();
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        try (Cursor cursor = database.rawQuery(
                "SELECT " + String.join(", ", PRODUCT_LINK_SYNC_COLUMNS) +
                        " FROM product_nutrition_links" +
                        publicationSafePushWhere("product_nutrition_links"),
                new String[]{userId}
        )) {
            while (cursor.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int index = 0; index < PRODUCT_LINK_SYNC_COLUMNS.length; index++) {
                    putCursorValue(row, PRODUCT_LINK_SYNC_COLUMNS[index], cursor, index);
                }
                String sourceType = nullableString(row, "source_type");
                boolean deleted = nullableString(row, "deleted_at") != null;
                if (ProductNutritionLink.SOURCE_MANUAL.equals(sourceType)) {
                    (deleted ? deletedManualRows : activeManualRows).put(row);
                    continue;
                }
                String status = nullableString(row, "status");
                if (!ProductNutritionLink.STATUS_SUGGESTED.equals(status)
                        || deleted) {
                    (deleted ? deletedSuggestionRows : activeSuggestionDecisions).add(row);
                }
            }
        }

        // Release an existing approved slot before activating its replacement.
        int pushed = postRows(config, "product_nutrition_links", deletedManualRows);
        for (JSONObject decision : deletedSuggestionRows) {
            pushed += patchSuggestionDecision(config, decision);
        }
        pushed += postRows(config, "product_nutrition_links", activeManualRows);
        for (JSONObject decision : activeSuggestionDecisions) {
            pushed += patchSuggestionDecision(config, decision);
        }
        return pushed;
    }

    private int patchSuggestionDecision(SupabaseConfig config, JSONObject row) throws Exception {
        String id = nullableString(row, "id");
        if (id == null) {
            return 0;
        }
        JSONArray remoteRows = getRows(
                config,
                "/rest/v1/product_nutrition_links?owner_id=eq."
                        + encode(config.effectiveUserId()) + "&select=*"
        );
        JSONObject remote = rowsById(remoteRows).get(id);
        if (remote == null || compareRowVersions(row, remote, "revision") <= 0) {
            return 0;
        }
        if (hasActiveApprovedLink(row)) {
            JSONObject conflicting = approvedSlotConflict(remoteRows, row);
            if (conflicting != null
                    && (compareVersions(
                    nullableString(row, "updated_at"),
                    nullableString(conflicting, "updated_at")
            ) <= 0 || retireRemoteApprovedLink(config, conflicting, row) == 0)) {
                return 0;
            }
        }
        return patchRowIfUnchanged(
                config,
                "product_nutrition_links",
                row,
                remote,
                "revision"
        );
    }

    private int pullFoods(SupabaseConfig config) throws Exception {
        int rows = 0;
        rows += upsertFoodRows(getRows(
                config,
                "/rest/v1/nutrition_foods?visibility=eq.public&deleted_at=is.null&select=*"
        ));
        if (config.isConfigured()) {
            rows += upsertFoodRows(getRows(
                    config,
                    "/rest/v1/nutrition_foods?owner_id=eq." + encode(config.effectiveUserId()) +
                            "&select=*"
            ));
        }
        return rows;
    }

    private int pullNutrients(SupabaseConfig config) throws Exception {
        int rows = 0;
        rows += upsertNutrientRows(getRows(
                config,
                "/rest/v1/nutrition_food_nutrients?owner_id=is.null&deleted_at=is.null&select=*"
        ));
        if (config.isConfigured()) {
            rows += upsertNutrientRows(getRows(
                    config,
                    "/rest/v1/nutrition_food_nutrients?owner_id=eq." +
                            encode(config.effectiveUserId()) + "&select=*"
            ));
        }
        return rows;
    }

    private int pullComponents(SupabaseConfig config) throws Exception {
        int rows = 0;
        rows += upsertComponentRows(getRows(
                config,
                "/rest/v1/nutrition_food_components?owner_id=is.null&deleted_at=is.null&select=*"
        ));
        if (config.isConfigured()) {
            rows += upsertComponentRows(getRows(
                    config,
                    "/rest/v1/nutrition_food_components?owner_id=eq." +
                            encode(config.effectiveUserId()) + "&select=*"
            ));
        }
        return rows;
    }

    private int pullProductLinks(SupabaseConfig config) throws Exception {
        return upsertProductLinkRows(getRows(
                config,
                "/rest/v1/product_nutrition_links?owner_id=eq." +
                        encode(config.effectiveUserId()) + "&select=*"
        ));
    }

    private int upsertFoodRows(JSONArray rows) throws JSONException {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        int applied = 0;
        database.beginTransaction();
        try {
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.getJSONObject(index);
                String id = nullableString(row, "id");
                String name = nullableString(row, "name");
                if (id == null || name == null) {
                    continue;
                }
                int remoteRevision = Math.max(1, row.optInt("revision", 1));
                String remoteUpdatedAt = nullableString(row, "updated_at");
                if (!shouldApplyRemoteRow(
                        database,
                        "nutrition_foods",
                        id,
                        remoteRevision,
                        remoteUpdatedAt
                )) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("id", id);
                putNullable(values, "owner_id", nullableString(row, "owner_id"));
                values.put("name", name);
                putNullable(values, "brand", nullableString(row, "brand"));
                String kind = NutritionFood.normalizeKind(
                        row.optString("kind", NutritionFood.KIND_EXTERNAL_MENU));
                values.put("kind", kind);
                values.put("category", NutritionFood.normalizeCategory(
                        row.optString("category", NutritionFood.categoryForKind(kind))));
                values.put("basis_amount", positiveOrDefault(row.optDouble("basis_amount", 1.0)));
                values.put("basis_unit", emptyToDefault(row.optString("basis_unit", "serving"), "serving"));
                values.put("prep_state", NutritionFood.normalizePrepState(
                        row.optString("prep_state", NutritionFood.PREP_UNSPECIFIED)));
                values.put("cooking_method", NutritionFood.normalizeCookingMethod(
                        row.optString("cooking_method", NutritionFood.COOKING_METHOD_UNSPECIFIED)));
                values.put("calories_kcal", row.optDouble("calories_kcal", 0));
                values.put("protein_grams", row.optDouble("protein_grams", 0));
                values.put("carbs_grams", row.optDouble("carbs_grams", 0));
                values.put("fat_grams", row.optDouble("fat_grams", 0));
                // 원격에 값이 없으면 0이 아니라 NULL로 남겨 "모름"을 보존한다.
                for (String key : nullableTypedKeys()) {
                    putNullableDouble(values, key, nullableDouble(row, key));
                }
                values.put("source_type", emptyToDefault(row.optString("source_type", "manual"), "manual"));
                putNullable(values, "source_reference", nullableString(row, "source_reference"));
                putNullable(values, "source_version", nullableString(row, "source_version"));
                values.put("data_version", row.optInt(
                        "data_version", NutritionFood.DATA_VERSION_MACROS_ONLY));
                values.put("revision", remoteRevision);
                values.put("visibility", emptyToDefault(row.optString("visibility", "public"), "public"));
                values.put("created_at", emptyToDefault(row.optString("created_at", ""), now()));
                values.put("updated_at", emptyToDefault(row.optString("updated_at", ""), now()));
                putNullable(values, "deleted_at", nullableString(row, "deleted_at"));
                database.insertWithOnConflict(
                        "nutrition_foods",
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE
                );
                applied++;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return applied;
    }

    private int upsertNutrientRows(JSONArray rows) throws JSONException {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        int applied = 0;
        database.beginTransaction();
        try {
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.getJSONObject(index);
                String id = nullableString(row, "id");
                String foodId = nullableString(row, "food_id");
                String code = NutrientCode.normalize(nullableString(row, "nutrient_code"));
                if (id == null || foodId == null || !NutrientCode.isKnown(code)) {
                    continue;
                }
                if (!shouldApplyRemoteRow(
                        database,
                        "nutrition_food_nutrients",
                        id,
                        null,
                        nullableString(row, "updated_at")
                )) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("id", id);
                putNullable(values, "owner_id", nullableString(row, "owner_id"));
                values.put("food_id", foodId);
                values.put("nutrient_code", code);
                putNullableDouble(values, "amount", nullableDouble(row, "amount"));
                values.put("unit", NutrientCode.unitOf(code));
                values.put("created_at", emptyToDefault(row.optString("created_at", ""), now()));
                values.put("updated_at", emptyToDefault(row.optString("updated_at", ""), now()));
                putNullable(values, "deleted_at", nullableString(row, "deleted_at"));
                database.insertWithOnConflict(
                        "nutrition_food_nutrients",
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE
                );
                applied++;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return applied;
    }

    private int upsertComponentRows(JSONArray rows) throws JSONException {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        int applied = 0;
        database.beginTransaction();
        try {
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.getJSONObject(index);
                String id = nullableString(row, "id");
                String parentId = nullableString(row, "parent_food_id");
                String childId = nullableString(row, "child_food_id");
                if (id == null || parentId == null || childId == null) {
                    continue;
                }
                if (!shouldApplyRemoteRow(
                        database,
                        "nutrition_food_components",
                        id,
                        null,
                        nullableString(row, "updated_at")
                )) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("id", id);
                putNullable(values, "owner_id", nullableString(row, "owner_id"));
                values.put("parent_food_id", parentId);
                values.put("child_food_id", childId);
                values.put("quantity", row.optDouble("quantity", 1.0));
                values.put("unit", emptyToDefault(row.optString("unit", "serving"), "serving"));
                values.put("order_index", row.optInt("order_index", 0));
                values.put("created_at", emptyToDefault(row.optString("created_at", ""), now()));
                values.put("updated_at", emptyToDefault(row.optString("updated_at", ""), now()));
                putNullable(values, "deleted_at", nullableString(row, "deleted_at"));
                database.insertWithOnConflict(
                        "nutrition_food_components",
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE
                );
                applied++;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return applied;
    }

    private int upsertProductLinkRows(JSONArray rows) throws JSONException {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        int applied = 0;
        database.beginTransaction();
        try {
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.getJSONObject(index);
                String id = nullableString(row, "id");
                String ownerId = nullableString(row, "owner_id");
                String foodId = nullableString(row, "nutrition_food_id");
                String catalogProductId = nullableString(row, "catalog_product_id");
                String status = nullableString(row, "status");
                String sourceType = nullableString(row, "source_type");
                if (id == null || ownerId == null || foodId == null || catalogProductId == null
                        || !isKnownLinkStatus(status) || !isKnownLinkSource(sourceType)) {
                    continue;
                }
                int remoteRevision = Math.max(1, row.optInt("revision", 1));
                String remoteUpdatedAt = nullableString(row, "updated_at");
                if (ProductNutritionLink.STATUS_APPROVED.equals(status)
                        && nullableString(row, "deleted_at") == null) {
                    String localApprovedUpdatedAt = otherApprovedLinkUpdatedAt(
                            database,
                            foodId,
                            id
                    );
                    if (compareVersions(localApprovedUpdatedAt, remoteUpdatedAt) > 0) {
                        continue;
                    }
                    softDeleteApprovedLinks(
                            database,
                            foodId,
                            id,
                            emptyToDefault(remoteUpdatedAt, now())
                    );
                }
                if (!shouldApplyRemoteRow(
                        database,
                        "product_nutrition_links",
                        id,
                        remoteRevision,
                        remoteUpdatedAt
                )) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("id", id);
                values.put("owner_id", ownerId);
                values.put("nutrition_food_id", foodId);
                values.put("catalog_product_id", catalogProductId);
                putNullable(values, "standard_product_id", nullableString(row, "standard_product_id"));
                values.put("status", status);
                values.put("source_type", sourceType);
                putNullable(values, "proposal_reference", nullableString(row, "proposal_reference"));
                values.put(
                        "product_contract_version",
                        emptyToDefault(
                                nullableString(row, "product_contract_version"),
                                ProductReadV1.CONTRACT_VERSION
                        )
                );
                putNullable(values, "catalog_product_revision", nullableString(row, "catalog_product_revision"));
                putNullableDouble(values, "catalog_content_amount", nullableDouble(row, "catalog_content_amount"));
                putNullable(values, "catalog_content_unit", nullableString(row, "catalog_content_unit"));
                if (row.isNull("catalog_package_count")) {
                    values.putNull("catalog_package_count");
                } else {
                    values.put("catalog_package_count", row.optInt("catalog_package_count"));
                }
                values.put("revision", remoteRevision);
                putNullable(values, "reviewed_at", nullableString(row, "reviewed_at"));
                values.put("created_at", emptyToDefault(row.optString("created_at", ""), now()));
                values.put("updated_at", emptyToDefault(row.optString("updated_at", ""), now()));
                putNullable(values, "deleted_at", nullableString(row, "deleted_at"));
                database.insertWithOnConflict(
                        "product_nutrition_links",
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE
                );
                applied++;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return applied;
    }

    private String otherApprovedLinkUpdatedAt(
            SQLiteDatabase database,
            String nutritionFoodId,
            String exceptId
    ) {
        try (Cursor cursor = database.rawQuery(
                "SELECT updated_at FROM product_nutrition_links " +
                        "WHERE owner_id = ? AND nutrition_food_id = ? AND id <> ? " +
                        "AND status = 'approved' AND deleted_at IS NULL " +
                        "ORDER BY updated_at DESC LIMIT 1",
                new String[]{userId, nutritionFoodId, exceptId}
        )) {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getString(0) : null;
        }
    }

    private ContentValues foodValues(NutritionFood food, String timestamp) {
        ContentValues values = new ContentValues();
        values.put("id", food.id);
        values.put("owner_id", food.ownerId);
        values.put("name", food.name);
        putNullable(values, "brand", food.brand);
        values.put("kind", food.kind);
        values.put("category", food.category);
        values.put("basis_amount", food.basisAmount);
        values.put("basis_unit", food.basisUnit);
        values.put("prep_state", food.prepState);
        values.put("cooking_method", food.cookingMethod);
        values.put("calories_kcal", food.profile.calories());
        values.put("protein_grams", food.profile.proteinGrams());
        values.put("carbs_grams", food.profile.carbsGrams());
        values.put("fat_grams", food.profile.fatGrams());
        for (String key : nullableTypedKeys()) {
            putNullableDouble(values, key, food.profile.value(key));
        }
        values.put("source_type", food.sourceType);
        putNullable(values, "source_reference", food.sourceReference);
        putNullable(values, "source_version", food.sourceVersion);
        values.put("data_version", food.dataVersion);
        values.put("revision", food.revision);
        values.put("visibility", "private");
        values.put("created_at", timestamp);
        values.put("updated_at", timestamp);
        values.putNull("deleted_at");
        return values;
    }

    private void replaceMicronutrients(SQLiteDatabase database, NutritionFood food) {
        database.delete("nutrition_food_nutrients", "food_id = ?", new String[]{food.id});
        String timestamp = now();
        for (String code : food.profile.knownMicronutrientCodes()) {
            ContentValues values = new ContentValues();
            values.put("id", UUID.randomUUID().toString());
            values.put("owner_id", food.ownerId);
            values.put("food_id", food.id);
            values.put("nutrient_code", code);
            values.put("amount", food.profile.value(code));
            values.put("unit", NutrientCode.unitOf(code));
            values.put("created_at", timestamp);
            values.put("updated_at", timestamp);
            values.putNull("deleted_at");
            database.insertWithOnConflict(
                    "nutrition_food_nutrients",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
            );
        }
    }

    private Map<String, Map<String, Double>> loadMicronutrients(
            SQLiteDatabase database,
            List<String> foodIds
    ) {
        Map<String, Map<String, Double>> byFood = new LinkedHashMap<>();
        if (foodIds.isEmpty()) {
            return byFood;
        }
        StringBuilder placeholders = new StringBuilder();
        for (int index = 0; index < foodIds.size(); index++) {
            placeholders.append(index == 0 ? "?" : ", ?");
        }
        try (Cursor cursor = database.rawQuery(
                "SELECT food_id, nutrient_code, amount FROM nutrition_food_nutrients " +
                        "WHERE deleted_at IS NULL AND amount IS NOT NULL " +
                        "AND food_id IN (" + placeholders + ")",
                foodIds.toArray(new String[0])
        )) {
            while (cursor.moveToNext()) {
                String code = NutrientCode.normalize(cursor.getString(1));
                if (!NutrientCode.isKnown(code)) {
                    continue;
                }
                byFood.computeIfAbsent(cursor.getString(0), key -> new LinkedHashMap<>())
                        .put(code, cursor.getDouble(2));
            }
        }
        return byFood;
    }

    /** 커서 한 행을 그대로 담아 둔다. 확장 영양소를 한 번에 읽은 뒤 조립하기 위해서다. */
    private Object[] readFoodRow(Cursor cursor) {
        return readFoodRow(cursor, 0);
    }

    private Object[] readFoodRow(Cursor cursor, int offset) {
        Object[] row = new Object[FOOD_COLUMNS.length];
        for (int index = 0; index < FOOD_COLUMNS.length; index++) {
            int cursorIndex = offset + index;
            if (cursor.isNull(cursorIndex)) {
                row[index] = null;
            } else if (cursor.getType(cursorIndex) == Cursor.FIELD_TYPE_STRING) {
                row[index] = cursor.getString(cursorIndex);
            } else {
                row[index] = cursor.getDouble(cursorIndex);
            }
        }
        return row;
    }

    private NutritionFood buildFood(Object[] row, Map<String, Double> micronutrients) {
        NutritionProfile.Builder profile = NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, doubleAt(row, 10))
                .value(NutritionProfile.PROTEIN_GRAMS, doubleAt(row, 11))
                .value(NutritionProfile.CARBS_GRAMS, doubleAt(row, 12))
                .value(NutritionProfile.FAT_GRAMS, doubleAt(row, 13));
        int columnIndex = 14;
        for (String key : nullableTypedKeys()) {
            profile.value(key, doubleAt(row, columnIndex++));
        }
        if (micronutrients != null) {
            for (Map.Entry<String, Double> entry : micronutrients.entrySet()) {
                profile.micronutrient(entry.getKey(), entry.getValue());
            }
        }

        Double dataVersion = doubleAt(row, 24);
        Double revision = doubleAt(row, 25);
        return NutritionFood.builder()
                .id(stringAt(row, 0))
                .ownerId(stringAt(row, 1))
                .name(stringAt(row, 2))
                .brand(stringAt(row, 3))
                .kind(stringAt(row, 4))
                .category(stringAt(row, 5))
                .basis(positiveOrDefault(doubleAt(row, 6)), emptyToDefault(stringAt(row, 7), "serving"))
                .prepState(stringAt(row, 8))
                .cookingMethod(stringAt(row, 9))
                .profile(profile.build())
                .source(emptyToDefault(stringAt(row, 21), "manual"), stringAt(row, 22))
                .sourceVersion(stringAt(row, 23))
                .dataVersion(dataVersion == null
                        ? NutritionFood.DATA_VERSION_MACROS_ONLY
                        : (int) Math.round(dataVersion))
                .revision(revision == null ? 1 : (int) Math.round(revision))
                .build();
    }

    /** 필수 7종이 다 있는지 확인하고, 없으면 무엇이 빠졌는지 알려 준다. */
    private static NutritionProfile requireRequiredNutrients(NutritionProfile profile) {
        NutritionProfile safeProfile = profile == null ? NutritionProfile.empty() : profile;
        List<String> missing = safeProfile.missingRequiredKeys();
        if (!missing.isEmpty()) {
            List<String> labels = new ArrayList<>();
            for (String key : missing) {
                labels.add(NutritionProfile.labelOf(key));
            }
            throw new IllegalArgumentException(
                    "필수 영양소를 모두 입력하세요: " + String.join(", ", labels)
            );
        }
        return safeProfile;
    }

    private static List<String> nullableTypedKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(NutritionProfile.SODIUM_MG);
        keys.add(NutritionProfile.SATURATED_FAT_GRAMS);
        keys.add(NutritionProfile.SUGARS_GRAMS);
        keys.addAll(NutritionProfile.RECOMMENDED_TYPED_KEYS);
        return keys;
    }

    private static String[] syncColumns() {
        List<String> columns = new ArrayList<>(java.util.Arrays.asList(FOOD_COLUMNS));
        columns.add("visibility");
        columns.add("created_at");
        columns.add("updated_at");
        columns.add("deleted_at");
        return columns.toArray(new String[0]);
    }

    private boolean shouldApplyRemoteRow(
            SQLiteDatabase database,
            String table,
            String id,
            Integer remoteRevision,
            String remoteUpdatedAt
    ) {
        String select = remoteRevision == null ? "updated_at" : "revision, updated_at";
        try (Cursor cursor = database.rawQuery(
                "SELECT " + select + " FROM " + table + " WHERE id = ? LIMIT 1",
                new String[]{id}
        )) {
            if (!cursor.moveToFirst()) {
                return true;
            }
            int updatedAtIndex = remoteRevision == null ? 0 : 1;
            if (remoteRevision != null) {
                int localRevision = cursor.getInt(0);
                if (remoteRevision > localRevision) {
                    return true;
                }
                if (remoteRevision < localRevision) {
                    return false;
                }
            }
            String localUpdatedAt = cursor.isNull(updatedAtIndex)
                    ? null
                    : cursor.getString(updatedAtIndex);
            return compareVersions(remoteUpdatedAt, localUpdatedAt) > 0;
        }
    }

    static int compareVersions(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        try {
            return OffsetDateTime.parse(left).toInstant()
                    .compareTo(OffsetDateTime.parse(right).toInstant());
        } catch (Exception ignored) {
            return left.compareTo(right);
        }
    }

    private int postRows(SupabaseConfig config, String table, JSONArray rows) throws Exception {
        if (rows.length() == 0) {
            return 0;
        }
        JSONArray remoteRows = getRows(
                config,
                "/rest/v1/" + table + "?owner_id=eq."
                        + encode(config.effectiveUserId()) + "&select=*"
        );
        Map<String, JSONObject> remoteById = rowsById(remoteRows);
        int pushed = 0;
        for (int index = 0; index < rows.length(); index++) {
            JSONObject local = rows.getJSONObject(index);
            String id = nullableString(local, "id");
            if (id == null) {
                continue;
            }
            JSONObject remote = remoteById.get(id);
            if (remote == null) {
                if ("product_nutrition_links".equals(table)
                        && hasActiveApprovedLink(local)) {
                    JSONObject conflicting = approvedSlotConflict(remoteRows, local);
                    if (conflicting != null) {
                        if (compareVersions(
                                nullableString(local, "updated_at"),
                                nullableString(conflicting, "updated_at")
                        ) <= 0 || retireRemoteApprovedLink(config, conflicting, local) == 0) {
                            continue;
                        }
                    }
                }
                JSONObject insertPayload = local;
                if ("product_nutrition_links".equals(table)) {
                    // The remote RLS contract does not allow a client INSERT to claim
                    // trusted PriceTrace specification metadata. Insert the owner-approved
                    // link first, then apply the locally verified metadata through the
                    // authenticated UPDATE path below.
                    insertPayload = directInsertProductLinkPayload(local);
                }
                int inserted = insertRowIfAbsent(config, table, insertPayload);
                pushed += inserted;
                if (inserted > 0
                        && "product_nutrition_links".equals(table)) {
                    patchRowIfUnchanged(config, table, local, insertPayload, "revision");
                }
                continue;
            }

            String versionKey = usesRevision(table) ? "revision" : "updated_at";
            if (compareRowVersions(local, remote, versionKey) <= 0) {
                continue;
            }
            pushed += patchRowIfUnchanged(config, table, local, remote, versionKey);
        }
        return pushed;
    }

    private JSONArray getRows(SupabaseConfig config, String path) throws Exception {
        final int pageSize = 500;
        JSONArray allRows = new JSONArray();
        for (int offset = 0; ; offset += pageSize) {
            String separator = path.contains("?") ? "&" : "?";
            String pagedPath = path + separator + "order=id.asc&limit=" + pageSize
                    + "&offset=" + offset;
            HttpURLConnection connection = openConnection(
                    joinUrl(config.supabaseUrl, pagedPath),
                    "GET",
                    config
            );
            connection.setRequestProperty("Accept", "application/json");
            String body = readResponseOrThrow(connection, 200, 206);
            JSONArray page = body.isEmpty() ? new JSONArray() : new JSONArray(body);
            for (int index = 0; index < page.length(); index++) {
                allRows.put(page.get(index));
            }
            if (page.length() < pageSize) {
                return allRows;
            }
        }
    }

    private Map<String, JSONObject> rowsById(JSONArray rows) throws JSONException {
        Map<String, JSONObject> indexed = new LinkedHashMap<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            String id = nullableString(row, "id");
            if (id != null) {
                indexed.put(id, row);
            }
        }
        return indexed;
    }

    private boolean usesRevision(String table) {
        return "nutrition_foods".equals(table)
                || "product_nutrition_links".equals(table);
    }

    private int compareRowVersions(JSONObject local, JSONObject remote, String versionKey) {
        if ("revision".equals(versionKey)) {
            int revisionComparison = Integer.compare(
                    Math.max(1, local.optInt("revision", 1)),
                    Math.max(1, remote.optInt("revision", 1))
            );
            if (revisionComparison != 0) {
                return revisionComparison;
            }
        }
        return compareVersions(
                nullableString(local, "updated_at"),
                nullableString(remote, "updated_at")
        );
    }

    private int insertRowIfAbsent(
            SupabaseConfig config,
            String table,
            JSONObject row
    ) throws Exception {
        HttpURLConnection connection = openConnection(
                joinUrl(config.supabaseUrl, "/rest/v1/" + table + "?on_conflict=id"),
                "POST",
                config
        );
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty(
                "Prefer",
                "resolution=ignore-duplicates,return=representation"
        );
        connection.setDoOutput(true);
        JSONArray payload = new JSONArray();
        payload.put(row);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }
        String body = readResponseOrThrow(connection, 200, 201);
        return body.isEmpty() ? 0 : new JSONArray(body).length();
    }

    private int patchRowIfUnchanged(
            SupabaseConfig config,
            String table,
            JSONObject local,
            JSONObject remote,
            String versionKey
    ) throws Exception {
        String expected = "revision".equals(versionKey)
                ? String.valueOf(Math.max(1, remote.optInt("revision", 1)))
                : nullableString(remote, "updated_at");
        String filter = expected == null ? "is.null" : "eq." + encode(expected);
        String endpoint = joinUrl(
                config.supabaseUrl,
                "/rest/v1/" + table
                        + "?id=eq." + encode(local.getString("id"))
                        + "&owner_id=eq." + encode(config.effectiveUserId())
                        + "&" + versionKey + "=" + filter
        );
        HttpURLConnection connection = openConnection(endpoint, "PATCH", config);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Prefer", "return=representation");
        connection.setDoOutput(true);
        JSONObject patch = new JSONObject(local.toString());
        patch.remove("id");
        patch.remove("owner_id");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(patch.toString().getBytes(StandardCharsets.UTF_8));
        }
        String body = readResponseOrThrow(connection, 200);
        return body.isEmpty() ? 0 : new JSONArray(body).length();
    }

    private boolean hasActiveApprovedLink(JSONObject row) {
        return ProductNutritionLink.STATUS_APPROVED.equals(nullableString(row, "status"))
                && nullableString(row, "deleted_at") == null;
    }

    static JSONObject directInsertProductLinkPayload(JSONObject row) throws JSONException {
        JSONObject payload = new JSONObject(row.toString());
        payload.put("catalog_product_revision", JSONObject.NULL);
        payload.put("catalog_content_amount", JSONObject.NULL);
        payload.put("catalog_content_unit", JSONObject.NULL);
        payload.put("catalog_package_count", JSONObject.NULL);
        return payload;
    }

    private JSONObject approvedSlotConflict(JSONArray remoteRows, JSONObject local)
            throws JSONException {
        String foodId = nullableString(local, "nutrition_food_id");
        String localId = nullableString(local, "id");
        for (int index = 0; index < remoteRows.length(); index++) {
            JSONObject remote = remoteRows.getJSONObject(index);
            if (hasActiveApprovedLink(remote)
                    && foodId != null
                    && foodId.equals(nullableString(remote, "nutrition_food_id"))
                    && !localId.equals(nullableString(remote, "id"))) {
                return remote;
            }
        }
        return null;
    }

    private int retireRemoteApprovedLink(
            SupabaseConfig config,
            JSONObject remote,
            JSONObject localReplacement
    ) throws Exception {
        String replacementTimestamp = emptyToDefault(
                nullableString(localReplacement, "updated_at"),
                now()
        );
        String endpoint = joinUrl(
                config.supabaseUrl,
                "/rest/v1/product_nutrition_links"
                        + "?id=eq." + encode(remote.getString("id"))
                        + "&owner_id=eq." + encode(config.effectiveUserId())
                        + "&revision=eq." + Math.max(1, remote.optInt("revision", 1))
        );
        HttpURLConnection connection = openConnection(endpoint, "PATCH", config);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Prefer", "return=representation");
        connection.setDoOutput(true);
        JSONObject patch = new JSONObject();
        patch.put("deleted_at", replacementTimestamp);
        patch.put("updated_at", replacementTimestamp);
        patch.put("revision", Math.max(1, remote.optInt("revision", 1)) + 1);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(patch.toString().getBytes(StandardCharsets.UTF_8));
        }
        String body = readResponseOrThrow(connection, 200);
        return body.isEmpty() ? 0 : new JSONArray(body).length();
    }

    private HttpURLConnection openConnection(String endpoint, String method, SupabaseConfig config)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("apikey", config.supabaseAnonKey);
        String bearer = config.accessToken.isEmpty() ? config.supabaseAnonKey : config.accessToken;
        connection.setRequestProperty("Authorization", "Bearer " + bearer);
        return connection;
    }

    private String readResponseOrThrow(HttpURLConnection connection, int... okCodes) throws IOException {
        int statusCode = connection.getResponseCode();
        for (int okCode : okCodes) {
            if (statusCode == okCode) {
                return readStream(connection.getInputStream());
            }
        }
        throw new IOException(
                "Nutrition catalog sync failed (" + statusCode + "): " +
                        readStream(connection.getErrorStream())
        );
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private void putCursorValue(JSONObject row, String name, Cursor cursor, int index)
            throws JSONException {
        if (cursor.isNull(index)) {
            row.put(name, JSONObject.NULL);
            return;
        }
        switch (cursor.getType(index)) {
            case Cursor.FIELD_TYPE_INTEGER:
                row.put(name, cursor.getLong(index));
                break;
            case Cursor.FIELD_TYPE_FLOAT:
                row.put(name, cursor.getDouble(index));
                break;
            default:
                row.put(name, cursor.getString(index));
                break;
        }
    }

    private static String stringAt(Object[] row, int index) {
        Object value = row[index];
        if (value == null) {
            return null;
        }
        return value instanceof String ? (String) value : String.valueOf(value);
    }

    private static Double doubleAt(Object[] row, int index) {
        Object value = row[index];
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static double positiveOrDefault(Double value) {
        return value == null || value <= 0 ? 1.0 : value;
    }

    private static String nullableString(JSONObject object, String key) {
        return !object.has(key) || object.isNull(key) ? null : object.optString(key, null);
    }

    private static Double nullableDouble(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) {
            return null;
        }
        double value = object.optDouble(key, Double.NaN);
        return Double.isNaN(value) ? null : value;
    }

    private static String requireName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Name is required.");
        }
        return normalized;
    }

    private static String normalizeUserId(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : normalized;
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static boolean isKnownLinkStatus(String value) {
        return ProductNutritionLink.STATUS_SUGGESTED.equals(value)
                || ProductNutritionLink.STATUS_APPROVED.equals(value)
                || ProductNutritionLink.STATUS_REJECTED.equals(value);
    }

    private static boolean isKnownLinkSource(String value) {
        return ProductNutritionLink.SOURCE_MANUAL.equals(value)
                || ProductNutritionLink.SOURCE_PRICETRACE.equals(value);
    }

    private static void putNullable(ContentValues values, String key, String value) {
        if (value == null) {
            values.putNull(key);
        } else {
            values.put(key, value);
        }
    }

    private static void putNullableDouble(ContentValues values, String key, Double value) {
        if (value == null) {
            values.putNull(key);
        } else {
            values.put(key, value);
        }
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static String joinUrl(String baseUrl, String path) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        return baseUrl + path;
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }

    public static final class RecipeComponent {
        public final NutritionFood food;
        public final double quantity;
        public final String unit;

        private RecipeComponent(NutritionFood food, double quantity, String unit) {
            this.food = food;
            this.quantity = quantity;
            this.unit = unit;
        }
    }

    public interface SyncCallback {
        void onComplete(int pushedRows, int pulledRows);

        void onError(Exception error);
    }

    public interface PublicationCallback {
        void onComplete(PublicationState state);

        void onError(Exception error);
    }

    public static final class PublicationState {
        public final String nutritionFoodId;
        public final String catalogProductId;
        public final boolean isPublic;
        public final int publicationRevision;
        public final String publishedAt;

        PublicationState(
                String nutritionFoodId,
                String catalogProductId,
                boolean isPublic,
                int publicationRevision,
                String publishedAt
        ) {
            this.nutritionFoodId = nutritionFoodId;
            this.catalogProductId = catalogProductId;
            this.isPublic = isPublic;
            this.publicationRevision = publicationRevision;
            this.publishedAt = publishedAt;
        }
    }

    public static final class PublicProductNutrition {
        public final String catalogProductId;
        public final String nutritionFoodId;
        public final String name;
        public final String basisUnit;
        public final double basisAmount;
        public final NutritionProfile profile;
        public final String catalogProductRevision;
        public final double catalogContentAmount;
        public final String catalogContentUnit;
        public final int catalogPackageCount;

        PublicProductNutrition(
                String catalogProductId,
                String nutritionFoodId,
                String name,
                String basisUnit,
                double basisAmount,
                NutritionProfile profile,
                String catalogProductRevision,
                double catalogContentAmount,
                String catalogContentUnit,
                int catalogPackageCount
        ) {
            this.catalogProductId = catalogProductId;
            this.nutritionFoodId = nutritionFoodId;
            this.name = name;
            this.basisUnit = basisUnit;
            this.basisAmount = basisAmount;
            this.profile = profile;
            this.catalogProductRevision = catalogProductRevision;
            this.catalogContentAmount = catalogContentAmount;
            this.catalogContentUnit = catalogContentUnit;
            this.catalogPackageCount = catalogPackageCount;
        }

        public boolean hasRequiredNutrition() {
            return profile != null && profile.hasAllRequired();
        }
    }

    public static final class CatalogSyncResult {
        public final int pushedRows;
        public final int pulledRows;

        public CatalogSyncResult(int pushedRows, int pulledRows) {
            this.pushedRows = pushedRows;
            this.pulledRows = pulledRows;
        }
    }
}
