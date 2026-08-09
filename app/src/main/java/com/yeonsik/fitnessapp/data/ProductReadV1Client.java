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
    private static final String PRODUCT_READ_RPC = "/rest/v1/rpc/get_product_read_v1";

    private volatile SupabaseConfig config;

    public ProductReadV1Client(SupabaseConfig config) {
        setConfig(config);
    }

    public void setConfig(SupabaseConfig config) {
        this.config = config == null ? SupabaseConfig.empty() : config;
    }

    public List<ProductReadV1> searchProducts(String query) throws Exception {
        return ProductReadV1.search(fetch(null, query, 50), query, 50);
    }

    public ProductReadV1 findProduct(String catalogProductId) throws Exception {
        List<ProductReadV1> products = fetch(catalogProductId, null, 1);
        if (products.isEmpty()) {
            return null;
        }
        ProductReadV1 product = products.get(0);
        if (!product.catalogProductId.equals(catalogProductId)) {
            throw new IllegalStateException(
                    "product-read.v1 RPC가 요청하지 않은 catalogProductId를 반환했습니다."
            );
        }
        return product;
    }

    private List<ProductReadV1> fetch(
            String catalogProductId,
            String query,
            int limit
    ) throws Exception {
        SupabaseConfig active = config;
        if (!active.isConnectionConfigured()) {
            throw new IllegalStateException("PriceTrace 상품 DB 연결을 먼저 설정하세요.");
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(
                joinUrl(active.supabaseUrl, PRODUCT_READ_RPC)
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
        JSONObject arguments = new JSONObject();
        arguments.put(
                "p_catalog_product_id",
                catalogProductId == null ? JSONObject.NULL : catalogProductId
        );
        arguments.put("p_query", query == null || query.trim().isEmpty() ? JSONObject.NULL : query.trim());
        arguments.put("p_limit", Math.max(1, Math.min(limit, 100)));
        try (OutputStream output = connection.getOutputStream()) {
            output.write(arguments.toString().getBytes(StandardCharsets.UTF_8));
        }

        String body = readResponseOrThrow(connection);
        if (body.isEmpty()) {
            throw new IOException("PriceTrace product-read.v1 응답이 비어 있습니다.");
        }
        return ProductReadV1.fromContractMap(toMap(new JSONObject(body)));
    }

    private static Map<String, Object> toMap(JSONObject row) {
        Map<String, Object> values = new LinkedHashMap<>();
        Iterator<String> keys = row.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            values.put(key, toValue(row.opt(key)));
        }
        return values;
    }

    private static List<Object> toList(JSONArray array) {
        List<Object> values = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            values.add(toValue(array.opt(index)));
        }
        return values;
    }

    private static Object toValue(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof JSONObject) {
            return toMap((JSONObject) value);
        }
        if (value instanceof JSONArray) {
            return toList((JSONArray) value);
        }
        return value;
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
