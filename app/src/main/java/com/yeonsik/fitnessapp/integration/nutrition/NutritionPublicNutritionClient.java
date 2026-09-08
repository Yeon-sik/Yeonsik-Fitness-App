package com.yeonsik.fitnessapp.integration.nutrition;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.NutrientCode;
import com.yeonsik.fitnessapp.data.NutritionProfile;

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
import java.util.UUID;

/** Reads the public Nutrition projection for an exact PriceTrace catalog product. */
public final class NutritionPublicNutritionClient {
    public PublicProductNutrition fetch(SupabaseConfig config, String catalogProductId)
            throws Exception {
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
        return parse(rows.getJSONObject(0));
    }

    private static PublicProductNutrition parse(JSONObject row) throws JSONException {
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

    private static String readResponseOrThrow(
            HttpURLConnection connection,
            int... okCodes
    ) throws IOException {
        int statusCode = connection.getResponseCode();
        for (int okCode : okCodes) {
            if (statusCode == okCode) {
                return readStream(connection.getInputStream());
            }
        }
        throw new IOException(
                "Nutrition public read failed (" + statusCode + "): "
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
        if (object == null || !object.has(key) || object.isNull(key)) {
            return null;
        }
        String value = object.optString(key, null);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static String joinUrl(String base, String path) {
        String normalizedBase = base == null ? "" : base.trim();
        if (normalizedBase.endsWith("/") && path.startsWith("/")) {
            return normalizedBase.substring(0, normalizedBase.length() - 1) + path;
        }
        if (!normalizedBase.endsWith("/") && !path.startsWith("/")) {
            return normalizedBase + "/" + path;
        }
        return normalizedBase + path;
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

        private PublicProductNutrition(
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
    }
}
