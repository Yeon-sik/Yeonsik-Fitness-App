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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local-first food catalog and recipe repository.
 *
 * <p>The app can record and search foods without a network connection. When a Supabase
 * connection is configured, public catalog rows are pulled and authenticated user rows are
 * pushed/pulled through the REST API.</p>
 */
public final class NutritionCatalogRepository {
    private final FitnessDatabaseHelper dbHelper;
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
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
                database.update(
                        "nutrition_foods",
                        values,
                        "owner_id = ?",
                        new String[]{SupabaseConfig.DEFAULT_USER_ID}
                );
                database.update(
                        "nutrition_food_components",
                        values,
                        "owner_id = ?",
                        new String[]{SupabaseConfig.DEFAULT_USER_ID}
                );
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }
        userId = normalizedNextUserId;
    }

    public void setSupabaseConfig(SupabaseConfig supabaseConfig) {
        this.supabaseConfig = supabaseConfig == null ? SupabaseConfig.empty() : supabaseConfig;
    }

    public List<NutritionFood> searchFoods(String query) {
        List<NutritionFood> foods = new ArrayList<>();
        String term = query == null ? "" : query.trim();
        String like = "%" + term + "%";
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        try (Cursor cursor = database.rawQuery(
                "SELECT id, owner_id, name, kind, basis_amount, basis_unit, " +
                        "calories_kcal, protein_grams, carbs_grams, fat_grams, " +
                        "source_type, source_reference " +
                        "FROM nutrition_foods " +
                        "WHERE deleted_at IS NULL " +
                        "AND (visibility = 'public' OR owner_id = ?) " +
                        "AND name LIKE ? COLLATE NOCASE " +
                        "ORDER BY kind ASC, name COLLATE NOCASE ASC LIMIT 100",
                new String[]{userId, like}
        )) {
            while (cursor.moveToNext()) {
                foods.add(foodFromCursor(cursor));
            }
        }
        return foods;
    }

    public NutritionFood saveFood(
            String name,
            String kind,
            double basisAmount,
            String basisUnit,
            double calories,
            double proteinGrams,
            double carbsGrams,
            double fatGrams,
            String sourceType,
            String sourceReference
    ) {
        String normalizedName = requireName(name);
        String normalizedKind = normalizeKind(kind);
        String normalizedUnit = requireName(basisUnit);
        validateBasisAndNutrition(
                basisAmount,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams
        );

        String id = UUID.randomUUID().toString();
        String timestamp = now();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("owner_id", userId);
        values.put("name", normalizedName);
        values.put("kind", normalizedKind);
        values.put("basis_amount", basisAmount);
        values.put("basis_unit", normalizedUnit);
        values.put("calories_kcal", calories);
        values.put("protein_grams", proteinGrams);
        values.put("carbs_grams", carbsGrams);
        values.put("fat_grams", fatGrams);
        values.put("source_type", emptyToDefault(sourceType, "manual"));
        putNullable(values, "source_reference", emptyToNull(sourceReference));
        values.put("visibility", "private");
        values.put("created_at", timestamp);
        values.put("updated_at", timestamp);
        values.putNull("deleted_at");

        dbHelper.getWritableDatabase().insertOrThrow("nutrition_foods", null, values);
        return new NutritionFood(
                id,
                userId,
                normalizedName,
                normalizedKind,
                basisAmount,
                normalizedUnit,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                emptyToDefault(sourceType, "manual"),
                emptyToNull(sourceReference)
        );
    }

    public NutritionFood saveRecipe(String name, List<MealCompositionItem> items) {
        String normalizedName = requireName(name);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Recipe needs at least one food.");
        }

        NutritionCalculator.NutritionValues total = NutritionCalculator.sum(items);
        String id = UUID.randomUUID().toString();
        String timestamp = now();
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            ContentValues recipe = new ContentValues();
            recipe.put("id", id);
            recipe.put("owner_id", userId);
            recipe.put("name", normalizedName);
            recipe.put("kind", NutritionFood.KIND_RECIPE);
            recipe.put("basis_amount", 1.0);
            recipe.put("basis_unit", "serving");
            recipe.put("calories_kcal", total.calories);
            recipe.put("protein_grams", total.proteinGrams);
            recipe.put("carbs_grams", total.carbsGrams);
            recipe.put("fat_grams", total.fatGrams);
            recipe.put("source_type", "manual_recipe");
            recipe.putNull("source_reference");
            recipe.put("visibility", "private");
            recipe.put("created_at", timestamp);
            recipe.put("updated_at", timestamp);
            recipe.putNull("deleted_at");
            database.insertOrThrow("nutrition_foods", null, recipe);

            int orderIndex = 0;
            for (MealCompositionItem item : items) {
                ContentValues component = new ContentValues();
                component.put("id", UUID.randomUUID().toString());
                component.put("owner_id", userId);
                component.put("parent_food_id", id);
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

        return new NutritionFood(
                id,
                userId,
                normalizedName,
                NutritionFood.KIND_RECIPE,
                1.0,
                "serving",
                total.calories,
                total.proteinGrams,
                total.carbsGrams,
                total.fatGrams,
                "manual_recipe",
                null
        );
    }

    public void syncRemoteAsync(SyncCallback callback) {
        SyncCallback safeCallback = callback == null ? new SyncCallback() {
            @Override
            public void onComplete(int pushedRows, int pulledRows) {
            }

            @Override
            public void onError(Exception error) {
            }
        } : callback;
        SupabaseConfig config = supabaseConfig;
        syncExecutor.execute(() -> {
            try {
                if (config == null || !config.isConnectionConfigured()) {
                    safeCallback.onComplete(0, 0);
                    return;
                }

                int pushedRows = 0;
                if (config.isConfigured()) {
                    pushedRows += pushFoods(config);
                    pushedRows += pushComponents(config);
                }
                int pulledRows = pullFoods(config);
                pulledRows += pullComponents(config);
                safeCallback.onComplete(pushedRows, pulledRows);
            } catch (Exception error) {
                safeCallback.onError(error);
            }
        });
    }

    private int pushFoods(SupabaseConfig config) throws Exception {
        JSONArray rows = new JSONArray();
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        try (Cursor cursor = database.rawQuery(
                "SELECT id, owner_id, name, kind, basis_amount, basis_unit, calories_kcal, " +
                        "protein_grams, carbs_grams, fat_grams, source_type, source_reference, " +
                        "visibility, created_at, updated_at, deleted_at " +
                        "FROM nutrition_foods WHERE owner_id = ?",
                new String[]{userId}
        )) {
            while (cursor.moveToNext()) {
                JSONObject row = new JSONObject();
                putCursorValue(row, "id", cursor, 0);
                putCursorValue(row, "owner_id", cursor, 1);
                putCursorValue(row, "name", cursor, 2);
                putCursorValue(row, "kind", cursor, 3);
                putCursorValue(row, "basis_amount", cursor, 4);
                putCursorValue(row, "basis_unit", cursor, 5);
                putCursorValue(row, "calories_kcal", cursor, 6);
                putCursorValue(row, "protein_grams", cursor, 7);
                putCursorValue(row, "carbs_grams", cursor, 8);
                putCursorValue(row, "fat_grams", cursor, 9);
                putCursorValue(row, "source_type", cursor, 10);
                putCursorValue(row, "source_reference", cursor, 11);
                putCursorValue(row, "visibility", cursor, 12);
                putCursorValue(row, "created_at", cursor, 13);
                putCursorValue(row, "updated_at", cursor, 14);
                putCursorValue(row, "deleted_at", cursor, 15);
                rows.put(row);
            }
        }
        return postRows(config, "nutrition_foods", rows);
    }

    private int pushComponents(SupabaseConfig config) throws Exception {
        JSONArray rows = new JSONArray();
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        try (Cursor cursor = database.rawQuery(
                "SELECT id, owner_id, parent_food_id, child_food_id, quantity, unit, order_index, " +
                        "created_at, updated_at, deleted_at " +
                        "FROM nutrition_food_components WHERE owner_id = ?",
                new String[]{userId}
        )) {
            while (cursor.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int index = 0; index < 10; index++) {
                    putCursorValue(row, componentColumn(index), cursor, index);
                }
                rows.put(row);
            }
        }
        return postRows(config, "nutrition_food_components", rows);
    }

    private int pullFoods(SupabaseConfig config) throws Exception {
        int rows = 0;
        JSONArray publicRows = getRows(
                config,
                "/rest/v1/nutrition_foods?visibility=eq.public&deleted_at=is.null&select=*"
        );
        rows += upsertFoodRows(publicRows);

        if (config.isConfigured()) {
            String encodedUserId = encode(config.effectiveUserId());
            JSONArray ownRows = getRows(
                    config,
                    "/rest/v1/nutrition_foods?owner_id=eq." + encodedUserId +
                            "&deleted_at=is.null&select=*"
            );
            rows += upsertFoodRows(ownRows);
        }
        return rows;
    }

    private int pullComponents(SupabaseConfig config) throws Exception {
        int rows = 0;
        JSONArray publicRows = getRows(
                config,
                "/rest/v1/nutrition_food_components?owner_id=is.null&deleted_at=is.null&select=*"
        );
        rows += upsertComponentRows(publicRows);

        if (config.isConfigured()) {
            String encodedUserId = encode(config.effectiveUserId());
            JSONArray ownRows = getRows(
                    config,
                    "/rest/v1/nutrition_food_components?owner_id=eq." + encodedUserId +
                            "&deleted_at=is.null&select=*"
            );
            rows += upsertComponentRows(ownRows);
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
                putNullable(values, "id", id);
                putNullable(values, "owner_id", nullableString(row, "owner_id"));
                values.put("name", name);
                values.put("kind", normalizeKind(row.optString("kind", NutritionFood.KIND_EXTERNAL_MENU)));
                values.put("basis_amount", row.optDouble("basis_amount", 1.0));
                values.put("basis_unit", emptyToDefault(row.optString("basis_unit", "serving"), "serving"));
                values.put("calories_kcal", row.optDouble("calories_kcal", 0));
                values.put("protein_grams", row.optDouble("protein_grams", 0));
                values.put("carbs_grams", row.optDouble("carbs_grams", 0));
                values.put("fat_grams", row.optDouble("fat_grams", 0));
                values.put("source_type", emptyToDefault(row.optString("source_type", "manual"), "manual"));
                putNullable(values, "source_reference", nullableString(row, "source_reference"));
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

    private NutritionFood foodFromCursor(Cursor cursor) {
        return new NutritionFood(
                cursor.getString(0),
                cursor.isNull(1) ? null : cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getDouble(4),
                cursor.getString(5),
                cursor.getDouble(6),
                cursor.getDouble(7),
                cursor.getDouble(8),
                cursor.getDouble(9),
                cursor.getString(10),
                cursor.isNull(11) ? null : cursor.getString(11)
        );
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

    private String componentColumn(int index) {
        String[] columns = {
                "id", "owner_id", "parent_food_id", "child_food_id", "quantity",
                "unit", "order_index", "created_at", "updated_at", "deleted_at"
        };
        return columns[index];
    }

    private static String nullableString(JSONObject object, String key) {
        return !object.has(key) || object.isNull(key) ? null : object.optString(key, null);
    }

    private static String requireName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Name is required.");
        }
        return normalized;
    }

    private static String normalizeKind(String kind) {
        if (NutritionFood.KIND_INGREDIENT.equals(kind)
                || NutritionFood.KIND_EXTERNAL_MENU.equals(kind)
                || NutritionFood.KIND_RECIPE.equals(kind)) {
            return kind;
        }
        return NutritionFood.KIND_EXTERNAL_MENU;
    }

    private static void validateBasisAndNutrition(
            double basisAmount,
            double calories,
            double proteinGrams,
            double carbsGrams,
            double fatGrams
    ) {
        if (basisAmount <= 0) {
            throw new IllegalArgumentException("Basis amount must be greater than zero.");
        }
        if (calories < 0 || proteinGrams < 0 || carbsGrams < 0 || fatGrams < 0) {
            throw new IllegalArgumentException("Nutrition values cannot be negative.");
        }
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
}
