package com.yeonsik.fitnessapp.integration.nutrition;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.DiningOutIdentity;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.integration.pricetrace.RestaurantMenuReadV1Client;

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
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Network adapter for Nutrition publication and the PriceTrace dining-out publication flow.
 *
 * <p>The adapter owns RPC paths, request JSON, response validation, and remote identity
 * orchestration. It returns typed publication state so the local catalog API never receives
 * Supabase JSON.</p>
 */
public final class NutritionPublicationClient {
    public PublicationState publishProductNutrition(
            SupabaseConfig config,
            String nutritionFoodId,
            String catalogProductId,
            boolean publish
    ) throws Exception {
        requireConfiguredNutrition(config);
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
        writeJson(connection, request);

        JSONArray rows = readRows(connection, "영양 공개 RPC");
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
        return stateFromRow(
                normalizedFoodId,
                normalizedCatalogProductId,
                row,
                visibility,
                null,
                null
        );
    }

    public PublicationState publishDiningOut(
            SupabaseConfig nutritionConfig,
            SupabaseConfig priceTraceConfig,
            NutritionFood food,
            boolean publish
    ) throws Exception {
        requireConfiguredNutrition(nutritionConfig);
        String normalizedFoodId = requireName(food == null ? null : food.id);
        if (food == null || !food.isDiningOutMenu()) {
            throw new IllegalArgumentException("공개할 FT 식당 메뉴를 찾을 수 없습니다.");
        }
        if (!publish) {
            return publishDiningOutNutrition(nutritionConfig, normalizedFoodId, false);
        }
        if (priceTraceConfig == null || !priceTraceConfig.isConfigured()) {
            throw new IllegalStateException("PT 관리자 계정 로그인이 필요합니다.");
        }

        JSONObject identity = parseIdentity(food.sourceReference);
        String restaurantName = emptyToDefault(
                nullableString(identity, "restaurant_name"),
                emptyToDefault(food.brand, "식당명 미기록")
        );
        String menuName = emptyToDefault(nullableString(identity, "menu_name"), food.name);
        String restaurantId = nullableString(identity, "restaurant_id");
        String locationId = nullableString(identity, "restaurant_location_id");
        String menuId = nullableString(identity, "restaurant_menu_id");
        String catalogProductId = nullableString(identity, "catalog_product_id");
        boolean hasAnyPriceTraceId = restaurantId != null
                || locationId != null
                || menuId != null
                || catalogProductId != null;
        boolean hasAllPriceTraceIds = restaurantId != null
                && locationId != null
                && menuId != null
                && catalogProductId != null;
        if (hasAnyPriceTraceId && !hasAllPriceTraceIds) {
            throw new IllegalStateException("PT 식당·지점·메뉴 identity가 일부만 저장되어 있습니다.");
        }

        String locationSourceNamespace = nullableString(identity, "source_namespace");
        if (locationSourceNamespace == null) {
            locationSourceNamespace = nullableString(identity, "location_source_namespace");
        }
        if (locationSourceNamespace == null) {
            String legacyNamespace = nullableString(identity, "namespace");
            locationSourceNamespace = DiningOutIdentity.NAMESPACE.equals(legacyNamespace)
                    ? null : legacyNamespace;
        }
        String sourceLocationCode = nullableString(identity, "source_location_code");
        if (hasAllPriceTraceIds) {
            SourceLocationIdentity sourceLocation = resolvePriceTraceLocation(
                    priceTraceConfig,
                    restaurantId,
                    locationId,
                    locationSourceNamespace,
                    sourceLocationCode
            );
            locationSourceNamespace = emptyToDefault(
                    sourceLocation.locationSourceNamespace,
                    DiningOutIdentity.NAMESPACE
            );
            sourceLocationCode = sourceLocation.code;
            if (sourceLocationCode == null) {
                throw new IllegalStateException(
                        "PriceTrace 지점 응답에 source identity가 없습니다. PT 지점 설정을 확인하세요."
                );
            }
        } else {
            locationSourceNamespace = "fitnessapp";
            sourceLocationCode = "restaurant:" + restaurantName.trim().toLowerCase(Locale.US);
        }

        JSONObject request = new JSONObject();
        request.put("p_idempotency_key", "fitnessapp:dining-out:" + normalizedFoodId);
        request.put("p_nutrition_food_id", normalizedFoodId);
        request.put("p_nutrition_revision", Math.max(1, food.revision));
        putNullable(request, "p_restaurant_id", restaurantId);
        request.put("p_restaurant_name", restaurantName);
        putNullable(request, "p_restaurant_location_id", locationId);
        request.put("p_source_location_namespace", locationSourceNamespace);
        request.put("p_source_location_code", sourceLocationCode);
        putNullable(request, "p_location_label", nullableString(identity, "branch_name"));
        putNullable(request, "p_restaurant_menu_id", menuId);
        putNullable(request, "p_catalog_product_id", catalogProductId);
        request.put("p_menu_name", menuName);
        request.put("p_menu_category_label", NutritionFood.categoryLabel(food.category));
        request.put("p_serving_label", food.basisLabel());

        HttpURLConnection priceTraceConnection = openConnection(
                joinUrl(priceTraceConfig.supabaseUrl,
                        "/rest/v1/rpc/admin_publish_fitness_dining_out_v1"),
                "POST",
                priceTraceConfig
        );
        priceTraceConnection.setRequestProperty("Content-Type", "application/json");
        priceTraceConnection.setRequestProperty("Accept", "application/json");
        priceTraceConnection.setDoOutput(true);
        writeJson(priceTraceConnection, request);

        JSONArray priceTraceRows = readRows(priceTraceConnection, "PT 식당 메뉴 등록 RPC");
        JSONObject priceTraceRow = priceTraceRows.getJSONObject(0);
        String registeredRestaurantId = requireReturnedUuid(priceTraceRow, "restaurant_id");
        String registeredLocationId = requireReturnedUuid(priceTraceRow, "restaurant_location_id");
        String registeredMenuId = requireReturnedUuid(priceTraceRow, "restaurant_menu_id");
        String registeredCatalogProductId = requireReturnedUuid(priceTraceRow, "catalog_product_id");

        AttachedIdentity attachedIdentity = attachDiningOutMenuIdentity(
                nutritionConfig,
                normalizedFoodId,
                registeredRestaurantId,
                registeredLocationId,
                registeredMenuId,
                registeredCatalogProductId
        );
        attachDiningOutMenuNutritionLink(
                nutritionConfig,
                normalizedFoodId,
                registeredCatalogProductId
        );
        return publishDiningOutNutrition(
                nutritionConfig,
                normalizedFoodId,
                true,
                attachedIdentity.sourceReference,
                attachedIdentity.updatedAt
        );
    }

    private PublicationState publishDiningOutNutrition(
            SupabaseConfig config,
            String nutritionFoodId,
            boolean publish
    ) throws Exception {
        return publishDiningOutNutrition(config, nutritionFoodId, publish, null, null);
    }

    private PublicationState publishDiningOutNutrition(
            SupabaseConfig config,
            String nutritionFoodId,
            boolean publish,
            String sourceReference,
            String sourceReferenceUpdatedAt
    ) throws Exception {
        HttpURLConnection connection = openConnection(
                joinUrl(config.supabaseUrl,
                        "/rest/v1/rpc/set_dining_out_menu_publication_v1"),
                "POST",
                config
        );
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        JSONObject request = new JSONObject();
        request.put("p_nutrition_food_id", nutritionFoodId);
        request.put("p_publish", publish);
        writeJson(connection, request);

        JSONArray rows = readRows(connection, "식당 메뉴 공개 RPC");
        JSONObject row = rows.getJSONObject(0);
        String returnedFoodId = nullableString(row, "nutrition_food_id");
        String visibility = nullableString(row, "visibility");
        if (!nutritionFoodId.equals(returnedFoodId)
                || !("public".equals(visibility) || "private".equals(visibility))
                || publish != "public".equals(visibility)) {
            throw new IOException("식당 메뉴 공개 RPC 응답이 요청한 항목과 일치하지 않습니다.");
        }
        return stateFromRow(
                nutritionFoodId,
                nullableString(row, "catalog_product_id"),
                row,
                visibility,
                sourceReference,
                sourceReferenceUpdatedAt
        );
    }

    private static PublicationState stateFromRow(
            String nutritionFoodId,
            String catalogProductId,
            JSONObject row,
            String visibility,
            String sourceReference,
            String sourceReferenceUpdatedAt
    ) {
        return new PublicationState(
                nutritionFoodId,
                catalogProductId,
                "public".equals(visibility),
                Math.max(1, row.optInt("publication_revision", 1)),
                nullableString(row, "published_at"),
                emptyToDefault(nullableString(row, "updated_at"), now()),
                sourceReference,
                sourceReferenceUpdatedAt
        );
    }

    private static JSONObject parseIdentity(String sourceReference) {
        if (sourceReference == null || sourceReference.trim().isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(sourceReference);
        } catch (JSONException ignored) {
            // Legacy dining_out rows are allowed to fall back to the FitnessApp identity.
            return new JSONObject();
        }
    }

    private static SourceLocationIdentity resolvePriceTraceLocation(
            SupabaseConfig priceTraceConfig,
            String restaurantId,
            String locationId,
            String storedLocationSourceNamespace,
            String storedSourceLocationCode
    ) throws Exception {
        if (storedSourceLocationCode != null && !storedSourceLocationCode.trim().isEmpty()) {
            return new SourceLocationIdentity(
                    storedLocationSourceNamespace,
                    storedSourceLocationCode.trim()
            );
        }

        RestaurantMenuReadV1Client.RestaurantDetail detail =
                new RestaurantMenuReadV1Client(priceTraceConfig).loadRestaurant(restaurantId);
        for (RestaurantMenuReadV1Client.RestaurantLocation location : detail.locations) {
            if (locationId.equals(location.restaurantLocationId)) {
                String sourceLocationCode = location.sourceLocationCode == null
                        ? ""
                        : location.sourceLocationCode.trim();
                return new SourceLocationIdentity(
                        location.locationSourceNamespace,
                        sourceLocationCode.isEmpty() ? null : sourceLocationCode
                );
            }
        }
        throw new IOException("PriceTrace 응답에서 요청한 지점 identity를 찾지 못했습니다.");
    }

    private static AttachedIdentity attachDiningOutMenuIdentity(
            SupabaseConfig config,
            String nutritionFoodId,
            String restaurantId,
            String locationId,
            String menuId,
            String catalogProductId
    ) throws Exception {
        HttpURLConnection connection = openConnection(
                joinUrl(config.supabaseUrl,
                        "/rest/v1/rpc/attach_dining_out_menu_identity_v1"),
                "POST",
                config
        );
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        JSONObject request = new JSONObject();
        request.put("p_nutrition_food_id", nutritionFoodId);
        request.put("p_restaurant_id", restaurantId);
        request.put("p_restaurant_location_id", locationId);
        request.put("p_restaurant_menu_id", menuId);
        request.put("p_catalog_product_id", catalogProductId);
        writeJson(connection, request);

        JSONArray rows = readRows(connection, "FT 식당 메뉴 identity 연결 RPC");
        JSONObject row = rows.getJSONObject(0);
        String returnedFoodId = nullableString(row, "nutrition_food_id");
        String sourceReference = nullableString(row, "source_reference");
        if (!nutritionFoodId.equals(returnedFoodId) || sourceReference == null) {
            throw new IOException("FT 식당 메뉴 identity 연결 응답이 요청과 일치하지 않습니다.");
        }
        return new AttachedIdentity(
                sourceReference,
                emptyToDefault(nullableString(row, "updated_at"), now())
        );
    }

    private static void attachDiningOutMenuNutritionLink(
            SupabaseConfig config,
            String nutritionFoodId,
            String catalogProductId
    ) throws Exception {
        HttpURLConnection connection = openConnection(
                joinUrl(config.supabaseUrl,
                        "/rest/v1/rpc/attach_dining_out_menu_nutrition_link_v1"),
                "POST",
                config
        );
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        JSONObject request = new JSONObject();
        request.put("p_nutrition_food_id", nutritionFoodId);
        request.put("p_catalog_product_id", catalogProductId);
        writeJson(connection, request);

        JSONArray rows = readRows(connection, "FT 식당 메뉴 영양 링크 RPC");
        JSONObject row = rows.getJSONObject(0);
        String returnedFoodId = nullableString(row, "nutrition_food_id");
        String returnedCatalogProductId = nullableString(row, "catalog_product_id");
        if (!nutritionFoodId.equals(returnedFoodId)
                || !catalogProductId.equals(returnedCatalogProductId)
                || !"approved".equals(nullableString(row, "status"))) {
            throw new IOException("FT 식당 메뉴 영양 링크 응답이 요청한 exact identity와 일치하지 않습니다.");
        }
    }

    private static JSONArray readRows(HttpURLConnection connection, String operation)
            throws Exception {
        String body = readResponseOrThrow(connection, 200);
        JSONArray rows = body.isEmpty() ? new JSONArray() : new JSONArray(body);
        if (rows.length() != 1) {
            throw new IOException(operation + "가 정확히 한 행을 반환하지 않았습니다.");
        }
        return rows;
    }

    private static void writeJson(HttpURLConnection connection, JSONObject request)
            throws IOException {
        try (OutputStream output = connection.getOutputStream()) {
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void requireConfiguredNutrition(SupabaseConfig config) {
        if (config == null || !config.isConfigured()) {
            throw new IllegalStateException("영양 DB 계정 로그인이 필요합니다.");
        }
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
                "Nutrition catalog publication failed (" + statusCode + "): "
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

    private static String nullableString(JSONObject object, String key) {
        return !object.has(key) || object.isNull(key) ? null : object.optString(key, null);
    }

    private static void putNullable(JSONObject object, String key, String value)
            throws JSONException {
        object.put(key, value == null ? JSONObject.NULL : value);
    }

    private static String requireReturnedUuid(JSONObject object, String key) throws IOException {
        String value = nullableString(object, key);
        if (value == null) {
            throw new IOException("PT 등록 응답에 " + key + "가 없습니다.");
        }
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException error) {
            throw new IOException("PT 등록 응답의 " + key + "가 UUID가 아닙니다.", error);
        }
    }

    private static String requireName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Name is required.");
        }
        return normalized;
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String joinUrl(String baseUrl, String path) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        return baseUrl + path;
    }

    private static String now() {
        return java.time.OffsetDateTime.now().toString();
    }

    public static final class PublicationState {
        public final String nutritionFoodId;
        public final String catalogProductId;
        public final boolean isPublic;
        public final int publicationRevision;
        public final String publishedAt;
        public final String updatedAt;
        public final String sourceReference;
        public final String sourceReferenceUpdatedAt;

        private PublicationState(
                String nutritionFoodId,
                String catalogProductId,
                boolean isPublic,
                int publicationRevision,
                String publishedAt,
                String updatedAt,
                String sourceReference,
                String sourceReferenceUpdatedAt
        ) {
            this.nutritionFoodId = nutritionFoodId;
            this.catalogProductId = catalogProductId;
            this.isPublic = isPublic;
            this.publicationRevision = publicationRevision;
            this.publishedAt = publishedAt;
            this.updatedAt = updatedAt;
            this.sourceReference = sourceReference;
            this.sourceReferenceUpdatedAt = sourceReferenceUpdatedAt;
        }
    }

    private static final class SourceLocationIdentity {
        private final String locationSourceNamespace;
        private final String code;

        private SourceLocationIdentity(String locationSourceNamespace, String code) {
            this.locationSourceNamespace = locationSourceNamespace;
            this.code = code;
        }
    }

    private static final class AttachedIdentity {
        private final String sourceReference;
        private final String updatedAt;

        private AttachedIdentity(String sourceReference, String updatedAt) {
            this.sourceReference = sourceReference;
            this.updatedAt = updatedAt;
        }
    }
}
