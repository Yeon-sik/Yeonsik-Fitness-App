package com.yeonsik.fitnessapp.data;

import com.yeonsik.fitnessapp.config.SupabaseConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HttpURLConnection consumer for the read-only PriceTrace product-read.v1 adapter. */
public final class ProductReadV1Client {
    private static final String CATALOG_RPC =
            "/rest/v1/rpc/get_public_exact_standard_product_catalog_v2";

    private volatile SupabaseConfig config;

    public ProductReadV1Client(SupabaseConfig config) {
        setConfig(config);
    }

    public void setConfig(SupabaseConfig config) {
        this.config = config == null ? SupabaseConfig.empty() : config;
    }

    public List<ProductReadV1> searchProducts(String query) throws Exception {
        return ProductReadV1.search(fetchAll(), query, 50);
    }

    public ProductReadV1 findProduct(String catalogProductId) throws Exception {
        for (ProductReadV1 product : fetchAll()) {
            if (product.catalogProductId.equals(catalogProductId)) {
                return product;
            }
        }
        return null;
    }

    private List<ProductReadV1> fetchAll() throws Exception {
        SupabaseConfig active = config;
        if (!active.isConnectionConfigured()) {
            throw new IllegalStateException("PriceTrace 상품 DB 연결을 먼저 설정하세요.");
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(
                joinUrl(active.supabaseUrl, CATALOG_RPC)
        ).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("apikey", active.supabaseAnonKey);
        connection.setRequestProperty("Authorization", "Bearer " + active.supabaseAnonKey);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("X-Contract-Version", ProductReadV1.CONTRACT_VERSION);
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write("{}".getBytes(StandardCharsets.UTF_8));
        }

        String body = readResponseOrThrow(connection);
        JSONArray rows = body.isEmpty() ? new JSONArray() : new JSONArray(body);
        List<ProductReadV1> products = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            products.add(ProductReadV1.fromMap(toMap(rows.getJSONObject(index))));
        }
        return products;
    }

    private static Map<String, Object> toMap(JSONObject row) {
        Map<String, Object> values = new LinkedHashMap<>();
        Iterator<String> keys = row.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = row.opt(key);
            values.put(key, value == JSONObject.NULL ? null : value);
        }
        return values;
    }

    private static String readResponseOrThrow(HttpURLConnection connection) throws IOException {
        int statusCode = connection.getResponseCode();
        if (statusCode >= 200 && statusCode < 300) {
            return readStream(connection.getInputStream());
        }
        throw new IOException(
                "PriceTrace product-read.v1 조회 실패 (" + statusCode + "): "
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

    private static String joinUrl(String baseUrl, String path) {
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) + path
                : baseUrl + path;
    }
}
