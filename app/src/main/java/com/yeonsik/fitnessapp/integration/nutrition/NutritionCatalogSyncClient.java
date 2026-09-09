package com.yeonsik.fitnessapp.integration.nutrition;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.NutrientCode;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.ProductNutritionLink;
import com.yeonsik.fitnessapp.data.ProductReadV1;
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogSyncStore;
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionCatalogSyncSnapshot;
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionComponentSyncRow;
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionFoodSyncRow;
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionNutrientSyncRow;
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionProductLinkSyncRow;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP/RPC and JSON adapter for the Nutrition catalog sync contract.
 *
 * <p>The local catalog is accessed only through {@link NutritionCatalogSyncStore}; no JSON or
 * Supabase transport details cross that feature boundary.</p>
 */
public final class NutritionCatalogSyncClient {
    private static final int PAGE_SIZE = 500;

    public SyncResult sync(
            SupabaseConfig config,
            NutritionCatalogSyncStore store
    ) throws Exception {
        if (config == null || !config.isConnectionConfigured()) {
            return new SyncResult(0, 0);
        }

        int pulledRows = pullFoods(config, store);
        pulledRows += pullNutrients(config, store);
        pulledRows += pullComponents(config, store);
        if (config.isConfigured()) {
            pulledRows += pullProductLinks(config, store);
        }

        int pushedRows = 0;
        if (config.isConfigured()) {
            NutritionCatalogSyncSnapshot snapshot = store.exportSyncSnapshot(
                    config.effectiveUserId()
            );
            pushedRows += postRows(config, "nutrition_foods", toFoodJson(snapshot.getFoods()));
            pushedRows += postRows(
                    config,
                    "nutrition_food_nutrients",
                    toNutrientJson(snapshot.getNutrients())
            );
            pushedRows += postRows(
                    config,
                    "nutrition_food_components",
                    toComponentJson(snapshot.getComponents())
            );
            pushedRows += pushProductLinks(config, snapshot.getProductLinks());

            // Conditional writes can lose a race without overwriting it. Pull once more to
            // converge on the remote winner and to receive deletion tombstones.
            pulledRows += pullFoods(config, store);
            pulledRows += pullNutrients(config, store);
            pulledRows += pullComponents(config, store);
            pulledRows += pullProductLinks(config, store);
        }
        return new SyncResult(pushedRows, pulledRows);
    }

    private int pullFoods(SupabaseConfig config, NutritionCatalogSyncStore store) throws Exception {
        int rows = 0;
        rows += store.applyRemoteFoodRows(parseFoodRows(getRows(
                config,
                "/rest/v1/nutrition_foods?visibility=eq.public&deleted_at=is.null&select=*"
        )));
        if (config.isConfigured()) {
            rows += store.applyRemoteFoodRows(parseFoodRows(getRows(
                    config,
                    "/rest/v1/nutrition_foods?owner_id=eq."
                            + encode(config.effectiveUserId()) + "&select=*"
            )));
        }
        return rows;
    }

    private int pullNutrients(SupabaseConfig config, NutritionCatalogSyncStore store)
            throws Exception {
        int rows = 0;
        rows += store.applyRemoteNutrientRows(parseNutrientRows(getRows(
                config,
                "/rest/v1/nutrition_food_nutrients?owner_id=is.null&deleted_at=is.null&select=*"
        )));
        if (config.isConfigured()) {
            rows += store.applyRemoteNutrientRows(parseNutrientRows(getRows(
                    config,
                    "/rest/v1/nutrition_food_nutrients?owner_id=eq."
                            + encode(config.effectiveUserId()) + "&select=*"
            )));
        }
        return rows;
    }

    private int pullComponents(SupabaseConfig config, NutritionCatalogSyncStore store)
            throws Exception {
        int rows = 0;
        rows += store.applyRemoteComponentRows(parseComponentRows(getRows(
                config,
                "/rest/v1/nutrition_food_components?owner_id=is.null&deleted_at=is.null&select=*"
        )));
        if (config.isConfigured()) {
            rows += store.applyRemoteComponentRows(parseComponentRows(getRows(
                    config,
                    "/rest/v1/nutrition_food_components?owner_id=eq."
                            + encode(config.effectiveUserId()) + "&select=*"
            )));
        }
        return rows;
    }

    private int pullProductLinks(SupabaseConfig config, NutritionCatalogSyncStore store)
            throws Exception {
        return store.applyRemoteProductLinkRows(parseProductLinkRows(getRows(
                config,
                "/rest/v1/product_nutrition_links?owner_id=eq."
                        + encode(config.effectiveUserId()) + "&select=*"
        )));
    }

    private int pushProductLinks(
            SupabaseConfig config,
            List<NutritionProductLinkSyncRow> rows
    ) throws Exception {
        JSONArray deletedManualRows = new JSONArray();
        JSONArray activeManualRows = new JSONArray();
        List<JSONObject> deletedSuggestionRows = new ArrayList<>();
        List<JSONObject> activeSuggestionDecisions = new ArrayList<>();
        for (NutritionProductLinkSyncRow typedRow : rows) {
            JSONObject row = toJson(typedRow);
            boolean deleted = typedRow.getDeletedAt() != null;
            if (ProductNutritionLink.SOURCE_MANUAL.equals(typedRow.getSourceType())) {
                (deleted ? deletedManualRows : activeManualRows).put(row);
                continue;
            }
            if (!ProductNutritionLink.STATUS_SUGGESTED.equals(typedRow.getStatus()) || deleted) {
                (deleted ? deletedSuggestionRows : activeSuggestionDecisions).add(row);
            }
        }

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
        return patchRowIfUnchanged(config, "product_nutrition_links", row, remote, "revision");
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
                    insertPayload = directInsertProductLinkPayload(local);
                }
                int inserted = insertRowIfAbsent(config, table, insertPayload);
                pushed += inserted;
                if (inserted > 0 && "product_nutrition_links".equals(table)) {
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
        JSONArray allRows = new JSONArray();
        for (int offset = 0; ; offset += PAGE_SIZE) {
            String separator = path.contains("?") ? "&" : "?";
            String pagedPath = path + separator + "order=id.asc&limit=" + PAGE_SIZE
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
            if (page.length() < PAGE_SIZE) {
                return allRows;
            }
        }
    }

    private int insertRowIfAbsent(SupabaseConfig config, String table, JSONObject row)
            throws Exception {
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
        write(connection, payload.toString());
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
        write(connection, patch.toString());
        String body = readResponseOrThrow(connection, 200);
        return body.isEmpty() ? 0 : new JSONArray(body).length();
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
        write(connection, patch.toString());
        String body = readResponseOrThrow(connection, 200);
        return body.isEmpty() ? 0 : new JSONArray(body).length();
    }

    private static boolean hasActiveApprovedLink(JSONObject row) {
        return ProductNutritionLink.STATUS_APPROVED.equals(nullableString(row, "status"))
                && nullableString(row, "deleted_at") == null;
    }

    private static JSONObject approvedSlotConflict(JSONArray remoteRows, JSONObject local)
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

    private static Map<String, JSONObject> rowsById(JSONArray rows) throws JSONException {
        Map<String, JSONObject> indexed = new HashMap<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            String id = nullableString(row, "id");
            if (id != null) {
                indexed.put(id, row);
            }
        }
        return indexed;
    }

    private static boolean usesRevision(String table) {
        return "nutrition_foods".equals(table)
                || "product_nutrition_links".equals(table);
    }

    private static int compareRowVersions(JSONObject local, JSONObject remote, String versionKey) {
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

    static JSONObject directInsertProductLinkPayload(JSONObject row) throws JSONException {
        JSONObject payload = new JSONObject(row.toString());
        payload.put("catalog_product_revision", JSONObject.NULL);
        payload.put("catalog_content_amount", JSONObject.NULL);
        payload.put("catalog_content_unit", JSONObject.NULL);
        payload.put("catalog_package_count", JSONObject.NULL);
        return payload;
    }

    private static HttpURLConnection openConnection(
            String endpoint,
            String method,
            SupabaseConfig config
    ) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("apikey", config.supabaseAnonKey);
        String bearer = config.accessToken.isEmpty()
                ? config.supabaseAnonKey
                : config.accessToken;
        connection.setRequestProperty("Authorization", "Bearer " + bearer);
        return connection;
    }

    private static String readResponseOrThrow(HttpURLConnection connection, int... okCodes)
            throws IOException {
        int statusCode = connection.getResponseCode();
        for (int okCode : okCodes) {
            if (statusCode == okCode) {
                return readStream(connection.getInputStream());
            }
        }
        throw new IOException(
                "Nutrition catalog sync failed (" + statusCode + "): "
                        + readStream(connection.getErrorStream())
        );
    }

    private static String readStream(InputStream stream) throws IOException {
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

    private static void write(HttpURLConnection connection, String payload) throws IOException {
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.getBytes(StandardCharsets.UTF_8));
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

    private static Long nullableLong(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) {
            return null;
        }
        return object.optLong(key, 0L);
    }

    private static void putNullable(JSONObject object, String key, String value)
            throws JSONException {
        object.put(key, value == null ? JSONObject.NULL : value);
    }

    private static void putNullable(JSONObject object, String key, Double value)
            throws JSONException {
        object.put(key, value == null ? JSONObject.NULL : value);
    }

    private static void putNullable(JSONObject object, String key, Long value)
            throws JSONException {
        object.put(key, value == null ? JSONObject.NULL : value);
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static double positiveOrDefault(Double value) {
        return value == null || value <= 0 ? 1.0 : value;
    }

    private static List<NutritionFoodSyncRow> parseFoodRows(JSONArray rows) throws JSONException {
        List<NutritionFoodSyncRow> parsed = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            String id = nullableString(row, "id");
            String name = nullableString(row, "name");
            if (id == null || name == null) {
                continue;
            }
            String kind = NutritionFood.normalizeKind(
                    emptyToDefault(nullableString(row, "kind"), NutritionFood.KIND_EXTERNAL_MENU)
            );
            parsed.add(new NutritionFoodSyncRow(
                    id,
                    nullableString(row, "owner_id"),
                    name,
                    nullableString(row, "brand"),
                    nullableString(row, "manufacturer_name"),
                    nullableString(row, "brand_name"),
                    nullableString(row, "sub_brand_name"),
                    nullableString(row, "product_name"),
                    nullableDouble(row, "package_amount"),
                    nullableString(row, "package_unit"),
                    nullableLong(row, "package_count"),
                    kind,
                    NutritionFood.normalizeCategory(
                            emptyToDefault(
                                    nullableString(row, "category"),
                                    NutritionFood.categoryForKind(kind)
                            )
                    ),
                    positiveOrDefault(nullableDouble(row, "basis_amount")),
                    emptyToDefault(nullableString(row, "basis_unit"), "serving"),
                    NutritionFood.normalizePrepState(
                            emptyToDefault(nullableString(row, "prep_state"), NutritionFood.PREP_UNSPECIFIED)
                    ),
                    NutritionFood.normalizeCookingMethod(
                            emptyToDefault(nullableString(row, "cooking_method"), NutritionFood.COOKING_METHOD_UNSPECIFIED)
                    ),
                    nullableDouble(row, "calories_kcal"),
                    nullableDouble(row, "protein_grams"),
                    nullableDouble(row, "carbs_grams"),
                    nullableDouble(row, "fat_grams"),
                    nullableDouble(row, "sodium_mg"),
                    nullableDouble(row, "saturated_fat_grams"),
                    nullableDouble(row, "sugars_grams"),
                    nullableDouble(row, "fiber_grams"),
                    nullableDouble(row, "added_sugars_grams"),
                    nullableDouble(row, "trans_fat_grams"),
                    nullableDouble(row, "cholesterol_mg"),
                    emptyToDefault(nullableString(row, "source_type"), "manual"),
                    nullableString(row, "source_reference"),
                    nullableString(row, "source_version"),
                    row.optInt("data_version", NutritionFood.DATA_VERSION_MACROS_ONLY),
                    Math.max(1, row.optInt("revision", 1)),
                    emptyToDefault(nullableString(row, "visibility"), "public"),
                    emptyToDefault(nullableString(row, "created_at"), now()),
                    emptyToDefault(nullableString(row, "updated_at"), now()),
                    nullableString(row, "deleted_at")
            ));
        }
        return parsed;
    }

    private static List<NutritionNutrientSyncRow> parseNutrientRows(JSONArray rows)
            throws JSONException {
        List<NutritionNutrientSyncRow> parsed = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            String id = nullableString(row, "id");
            String foodId = nullableString(row, "food_id");
            String code = NutrientCode.normalize(nullableString(row, "nutrient_code"));
            if (id == null || foodId == null || !NutrientCode.isKnown(code)) {
                continue;
            }
            parsed.add(new NutritionNutrientSyncRow(
                    id,
                    nullableString(row, "owner_id"),
                    foodId,
                    code,
                    nullableDouble(row, "amount"),
                    NutrientCode.unitOf(code),
                    emptyToDefault(nullableString(row, "created_at"), now()),
                    emptyToDefault(nullableString(row, "updated_at"), now()),
                    nullableString(row, "deleted_at")
            ));
        }
        return parsed;
    }

    private static List<NutritionComponentSyncRow> parseComponentRows(JSONArray rows)
            throws JSONException {
        List<NutritionComponentSyncRow> parsed = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            String id = nullableString(row, "id");
            String parentId = nullableString(row, "parent_food_id");
            String childId = nullableString(row, "child_food_id");
            if (id == null || parentId == null || childId == null) {
                continue;
            }
            parsed.add(new NutritionComponentSyncRow(
                    id,
                    nullableString(row, "owner_id"),
                    parentId,
                    childId,
                    row.optDouble("quantity", 1.0),
                    emptyToDefault(nullableString(row, "unit"), "serving"),
                    row.optLong("order_index", 0L),
                    emptyToDefault(nullableString(row, "created_at"), now()),
                    emptyToDefault(nullableString(row, "updated_at"), now()),
                    nullableString(row, "deleted_at")
            ));
        }
        return parsed;
    }

    private static List<NutritionProductLinkSyncRow> parseProductLinkRows(JSONArray rows)
            throws JSONException {
        List<NutritionProductLinkSyncRow> parsed = new ArrayList<>();
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
            parsed.add(new NutritionProductLinkSyncRow(
                    id,
                    ownerId,
                    foodId,
                    catalogProductId,
                    nullableString(row, "standard_product_id"),
                    status,
                    sourceType,
                    nullableString(row, "proposal_reference"),
                    emptyToDefault(
                            nullableString(row, "product_contract_version"),
                            ProductReadV1.CONTRACT_VERSION
                    ),
                    nullableString(row, "catalog_product_revision"),
                    nullableDouble(row, "catalog_content_amount"),
                    nullableString(row, "catalog_content_unit"),
                    nullableLong(row, "catalog_package_count"),
                    Math.max(1, row.optInt("revision", 1)),
                    nullableString(row, "reviewed_at"),
                    emptyToDefault(nullableString(row, "created_at"), now()),
                    emptyToDefault(nullableString(row, "updated_at"), now()),
                    nullableString(row, "deleted_at")
            ));
        }
        return parsed;
    }

    private static JSONArray toFoodJson(List<NutritionFoodSyncRow> rows) throws JSONException {
        JSONArray json = new JSONArray();
        for (NutritionFoodSyncRow row : rows) {
            JSONObject value = new JSONObject();
            value.put("id", row.getId());
            putNullable(value, "owner_id", row.getOwnerId());
            value.put("name", row.getName());
            putNullable(value, "brand", row.getBrand());
            putNullable(value, "manufacturer_name", row.getManufacturerName());
            putNullable(value, "brand_name", row.getBrandName());
            putNullable(value, "sub_brand_name", row.getSubBrandName());
            putNullable(value, "product_name", row.getProductName());
            putNullable(value, "package_amount", row.getPackageAmount());
            putNullable(value, "package_unit", row.getPackageUnit());
            putNullable(value, "package_count", row.getPackageCount());
            value.put("kind", row.getKind());
            value.put("category", row.getCategory());
            value.put("basis_amount", row.getBasisAmount());
            value.put("basis_unit", row.getBasisUnit());
            value.put("prep_state", row.getPrepState());
            value.put("cooking_method", row.getCookingMethod());
            putNullable(value, "calories_kcal", row.getCaloriesKcal());
            putNullable(value, "protein_grams", row.getProteinGrams());
            putNullable(value, "carbs_grams", row.getCarbsGrams());
            putNullable(value, "fat_grams", row.getFatGrams());
            putNullable(value, "sodium_mg", row.getSodiumMg());
            putNullable(value, "saturated_fat_grams", row.getSaturatedFatGrams());
            putNullable(value, "sugars_grams", row.getSugarsGrams());
            putNullable(value, "fiber_grams", row.getFiberGrams());
            putNullable(value, "added_sugars_grams", row.getAddedSugarsGrams());
            putNullable(value, "trans_fat_grams", row.getTransFatGrams());
            putNullable(value, "cholesterol_mg", row.getCholesterolMg());
            value.put("source_type", row.getSourceType());
            putNullable(value, "source_reference", row.getSourceReference());
            putNullable(value, "source_version", row.getSourceVersion());
            value.put("data_version", row.getDataVersion());
            value.put("revision", row.getRevision());
            value.put("visibility", row.getVisibility());
            value.put("created_at", row.getCreatedAt());
            value.put("updated_at", row.getUpdatedAt());
            putNullable(value, "deleted_at", row.getDeletedAt());
            json.put(value);
        }
        return json;
    }

    private static JSONArray toNutrientJson(List<NutritionNutrientSyncRow> rows)
            throws JSONException {
        JSONArray json = new JSONArray();
        for (NutritionNutrientSyncRow row : rows) {
            JSONObject value = new JSONObject();
            value.put("id", row.getId());
            putNullable(value, "owner_id", row.getOwnerId());
            value.put("food_id", row.getFoodId());
            value.put("nutrient_code", row.getNutrientCode());
            putNullable(value, "amount", row.getAmount());
            value.put("unit", row.getUnit());
            value.put("created_at", row.getCreatedAt());
            value.put("updated_at", row.getUpdatedAt());
            putNullable(value, "deleted_at", row.getDeletedAt());
            json.put(value);
        }
        return json;
    }

    private static JSONArray toComponentJson(List<NutritionComponentSyncRow> rows)
            throws JSONException {
        JSONArray json = new JSONArray();
        for (NutritionComponentSyncRow row : rows) {
            JSONObject value = new JSONObject();
            value.put("id", row.getId());
            putNullable(value, "owner_id", row.getOwnerId());
            value.put("parent_food_id", row.getParentFoodId());
            value.put("child_food_id", row.getChildFoodId());
            value.put("quantity", row.getQuantity());
            value.put("unit", row.getUnit());
            value.put("order_index", row.getOrderIndex());
            value.put("created_at", row.getCreatedAt());
            value.put("updated_at", row.getUpdatedAt());
            putNullable(value, "deleted_at", row.getDeletedAt());
            json.put(value);
        }
        return json;
    }

    private static JSONObject toJson(NutritionProductLinkSyncRow row) throws JSONException {
        JSONObject value = new JSONObject();
        value.put("id", row.getId());
        value.put("owner_id", row.getOwnerId());
        value.put("nutrition_food_id", row.getNutritionFoodId());
        value.put("catalog_product_id", row.getCatalogProductId());
        putNullable(value, "standard_product_id", row.getStandardProductId());
        value.put("status", row.getStatus());
        value.put("source_type", row.getSourceType());
        putNullable(value, "proposal_reference", row.getProposalReference());
        putNullable(value, "product_contract_version", row.getProductContractVersion());
        putNullable(value, "catalog_product_revision", row.getCatalogProductRevision());
        putNullable(value, "catalog_content_amount", row.getCatalogContentAmount());
        putNullable(value, "catalog_content_unit", row.getCatalogContentUnit());
        putNullable(value, "catalog_package_count", row.getCatalogPackageCount());
        value.put("revision", row.getRevision());
        putNullable(value, "reviewed_at", row.getReviewedAt());
        value.put("created_at", row.getCreatedAt());
        value.put("updated_at", row.getUpdatedAt());
        putNullable(value, "deleted_at", row.getDeletedAt());
        return value;
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

    public static final class SyncResult {
        public final int pushedRows;
        public final int pulledRows;

        public SyncResult(int pushedRows, int pulledRows) {
            this.pushedRows = pushedRows;
            this.pulledRows = pulledRows;
        }
    }
}
