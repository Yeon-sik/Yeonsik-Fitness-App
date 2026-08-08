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
import java.util.UUID;

/**
 * Local-first food catalog and recipe repository.
 *
 * <p>The app can record and search foods without a network connection. When a Supabase
 * connection is configured, public catalog rows are pulled and authenticated user rows are
 * pushed/pulled through the REST API.</p>
 *
 * <p>이 저장소는 <b>음식·레시피·영양성분만</b> 다룬다. 원격 테이블은 Personal OS 공통
 * Supabase 프로젝트 안의 별도 도메인 경계이며, meal_records 같은 사용자 섭취 기록은 이
 * 카탈로그에 절대 들어가지 않는다.</p>
 */
public final class NutritionCatalogRepository {
    /** 공통 DB의 카탈로그 테이블. 사용자 기록 테이블은 여기 들어올 수 없다. */
    static final List<String> CATALOG_TABLES = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList(
                    "nutrition_foods",
                    "nutrition_food_nutrients",
                    "nutrition_food_components"
            )
    );

    private static final String[] FOOD_COLUMNS = {
            "id",
            "owner_id",
            "name",
            "kind",
            "basis_amount",
            "basis_unit",
            "prep_state",
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
            "data_version"
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

    /**
     * Rebinds local rows created under the retired Nutrition Supabase project.
     * The caller must first verify that the legacy and shared accounts use the same email.
     */
    public int migrateLegacyOwner(String previousUserId, String nextUserId) {
        String previous = normalizeUserId(previousUserId);
        String next = normalizeUserId(nextUserId);
        if (SupabaseConfig.DEFAULT_USER_ID.equals(previous)
                || SupabaseConfig.DEFAULT_USER_ID.equals(next)) {
            throw new IllegalArgumentException("Legacy nutrition owners must be authenticated users.");
        }
        if (previous.equals(next)) {
            userId = next;
            return 0;
        }

        int updatedRows = 0;
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("owner_id", next);
            for (String table : CATALOG_TABLES) {
                updatedRows += database.update(
                        table,
                        values,
                        "owner_id = ?",
                        new String[]{previous}
                );
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        userId = next;
        return updatedRows;
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
                        "AND name LIKE ? COLLATE NOCASE " +
                        "ORDER BY kind ASC, name COLLATE NOCASE ASC LIMIT 100",
                new String[]{userId, like}
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
        String normalizedName = requireName(name);
        String normalizedKind = NutritionFood.normalizeKind(kind);
        String normalizedUnit = requireName(basisUnit);
        String normalizedPrepState = NutritionFood.normalizePrepState(prepState);
        if (basisAmount <= 0) {
            throw new IllegalArgumentException("Basis amount must be greater than zero.");
        }
        NutritionProfile normalizedProfile = requireRequiredNutrients(profile);

        NutritionFood food = NutritionFood.builder()
                .id(UUID.randomUUID().toString())
                .ownerId(userId)
                .name(normalizedName)
                .kind(normalizedKind)
                .basis(basisAmount, normalizedUnit)
                .prepState(normalizedPrepState)
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
     * 구성 재료로 레시피를 만든다.
     *
     * <p>레시피의 영양성분은 개별 음식과 같은 규칙으로 합산된다. 재료 중 하나라도 모르는
     * 영양소는 레시피에서도 모름으로 남으며, 0으로 합산되지 않는다.</p>
     */
    public NutritionFood saveRecipe(String name, List<MealCompositionItem> items) {
        String normalizedName = requireName(name);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Recipe needs at least one food.");
        }

        NutritionProfile total = NutritionCalculator.recipeProfile(items);
        NutritionFood recipe = NutritionFood.builder()
                .id(UUID.randomUUID().toString())
                .ownerId(userId)
                .name(normalizedName)
                .kind(NutritionFood.KIND_RECIPE)
                .basis(1.0, "serving")
                .prepState(NutritionFood.PREP_AS_SERVED)
                .profile(total)
                .source("manual_recipe", null)
                .dataVersion(total.hasAllRequired()
                        ? NutritionFood.DATA_VERSION_REQUIRED_SEVEN
                        : NutritionFood.DATA_VERSION_MACROS_ONLY)
                .build();

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

    public synchronized CatalogSyncResult syncRemote() throws Exception {
        SupabaseConfig config = supabaseConfig;
        if (config == null || !config.isConnectionConfigured()) {
            return new CatalogSyncResult(0, 0);
        }

        int pushedRows = 0;
        if (config.isConfigured()) {
            pushedRows += pushTable(config, "nutrition_foods", FOOD_SYNC_COLUMNS);
            pushedRows += pushTable(config, "nutrition_food_nutrients", NUTRIENT_SYNC_COLUMNS);
            pushedRows += pushTable(config, "nutrition_food_components", COMPONENT_SYNC_COLUMNS);
        }
        int pulledRows = pullFoods(config);
        pulledRows += pullNutrients(config);
        pulledRows += pullComponents(config);
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
                "SELECT " + String.join(", ", columns) + " FROM " + table + " WHERE owner_id = ?",
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
                            "&deleted_at=is.null&select=*"
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
                            encode(config.effectiveUserId()) + "&deleted_at=is.null&select=*"
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
                            encode(config.effectiveUserId()) + "&deleted_at=is.null&select=*"
            ));
        }
        return rows;
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
                ContentValues values = new ContentValues();
                values.put("id", id);
                putNullable(values, "owner_id", nullableString(row, "owner_id"));
                values.put("name", name);
                values.put("kind", NutritionFood.normalizeKind(
                        row.optString("kind", NutritionFood.KIND_EXTERNAL_MENU)));
                values.put("basis_amount", positiveOrDefault(row.optDouble("basis_amount", 1.0)));
                values.put("basis_unit", emptyToDefault(row.optString("basis_unit", "serving"), "serving"));
                values.put("prep_state", NutritionFood.normalizePrepState(
                        row.optString("prep_state", NutritionFood.PREP_UNSPECIFIED)));
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

    private ContentValues foodValues(NutritionFood food, String timestamp) {
        ContentValues values = new ContentValues();
        values.put("id", food.id);
        values.put("owner_id", food.ownerId);
        values.put("name", food.name);
        values.put("kind", food.kind);
        values.put("basis_amount", food.basisAmount);
        values.put("basis_unit", food.basisUnit);
        values.put("prep_state", food.prepState);
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
        Object[] row = new Object[FOOD_COLUMNS.length];
        for (int index = 0; index < FOOD_COLUMNS.length; index++) {
            if (cursor.isNull(index)) {
                row[index] = null;
            } else if (cursor.getType(index) == Cursor.FIELD_TYPE_STRING) {
                row[index] = cursor.getString(index);
            } else {
                row[index] = cursor.getDouble(index);
            }
        }
        return row;
    }

    private NutritionFood buildFood(Object[] row, Map<String, Double> micronutrients) {
        NutritionProfile.Builder profile = NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, doubleAt(row, 7))
                .value(NutritionProfile.PROTEIN_GRAMS, doubleAt(row, 8))
                .value(NutritionProfile.CARBS_GRAMS, doubleAt(row, 9))
                .value(NutritionProfile.FAT_GRAMS, doubleAt(row, 10));
        int columnIndex = 11;
        for (String key : nullableTypedKeys()) {
            profile.value(key, doubleAt(row, columnIndex++));
        }
        if (micronutrients != null) {
            for (Map.Entry<String, Double> entry : micronutrients.entrySet()) {
                profile.micronutrient(entry.getKey(), entry.getValue());
            }
        }

        Double dataVersion = doubleAt(row, 21);
        return NutritionFood.builder()
                .id(stringAt(row, 0))
                .ownerId(stringAt(row, 1))
                .name(stringAt(row, 2))
                .kind(stringAt(row, 3))
                .basis(positiveOrDefault(doubleAt(row, 4)), emptyToDefault(stringAt(row, 5), "serving"))
                .prepState(stringAt(row, 6))
                .profile(profile.build())
                .source(emptyToDefault(stringAt(row, 18), "manual"), stringAt(row, 19))
                .sourceVersion(stringAt(row, 20))
                .dataVersion(dataVersion == null
                        ? NutritionFood.DATA_VERSION_MACROS_ONLY
                        : (int) Math.round(dataVersion))
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

    private int postRows(SupabaseConfig config, String table, JSONArray rows) throws Exception {
        if (rows.length() == 0) {
            return 0;
        }
        HttpURLConnection connection = openConnection(
                joinUrl(config.supabaseUrl, "/rest/v1/" + table + "?on_conflict=id"),
                "POST",
                config
        );
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal");
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(rows.toString().getBytes(StandardCharsets.UTF_8));
        }
        readResponseOrThrow(connection, 200, 201, 204);
        return rows.length();
    }

    private JSONArray getRows(SupabaseConfig config, String path) throws Exception {
        HttpURLConnection connection = openConnection(joinUrl(config.supabaseUrl, path), "GET", config);
        connection.setRequestProperty("Accept", "application/json");
        String body = readResponseOrThrow(connection, 200);
        return body.isEmpty() ? new JSONArray() : new JSONArray(body);
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

    public interface SyncCallback {
        void onComplete(int pushedRows, int pulledRows);

        void onError(Exception error);
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
