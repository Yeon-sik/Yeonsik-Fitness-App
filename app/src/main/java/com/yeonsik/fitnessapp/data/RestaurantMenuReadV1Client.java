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
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Read-only consumer for PriceTrace restaurant-directory.v1/detail.v1 contracts. */
public final class RestaurantMenuReadV1Client {
    private static final String DIRECTORY_RPC = "/rest/v1/rpc/get_restaurant_directory_v1";
    private static final String DETAIL_RPC = "/rest/v1/rpc/get_restaurant_detail_v1";
    private static final String CONTRACT_NAMESPACE = "pricetrace";

    private volatile SupabaseConfig config;

    public RestaurantMenuReadV1Client(SupabaseConfig config) {
        setConfig(config);
    }

    public void setConfig(SupabaseConfig config) {
        this.config = config == null ? SupabaseConfig.empty() : config;
    }

    public List<RestaurantSummary> searchRestaurants(String query) throws Exception {
        JSONObject arguments = new JSONObject();
        arguments.put("p_query", query == null || query.trim().isEmpty()
                ? JSONObject.NULL : query.trim());
        arguments.put("p_limit", 50);
        JSONObject payload = new JSONObject(post(DIRECTORY_RPC, arguments));
        requireContract(payload, "restaurant-directory.v1");
        JSONArray rows = payload.optJSONArray("restaurants");
        if (rows == null) {
            throw new IOException("PriceTrace 식당 목록 응답에 restaurants가 없습니다.");
        }
        List<RestaurantSummary> result = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.optJSONObject(index);
            if (row == null) {
                throw new IOException("PriceTrace 식당 목록 항목이 올바르지 않습니다.");
            }
            JSONObject restaurant = requiredObject(row, "restaurant");
            result.add(new RestaurantSummary(
                    requiredUuid(restaurant, "id"),
                    requiredText(restaurant, "brand"),
                    parseLocations(row.optJSONArray("locations"))
            ));
        }
        return Collections.unmodifiableList(result);
    }

    public RestaurantDetail loadRestaurant(String restaurantId) throws Exception {
        String requestedId = requireUuid(restaurantId);
        JSONObject arguments = new JSONObject();
        arguments.put("p_restaurant_id", requestedId);
        JSONObject payload = new JSONObject(post(DETAIL_RPC, arguments));
        requireContract(payload, "restaurant-detail.v1");
        JSONObject restaurant = requiredObject(payload, "restaurant");
        String responseId = requiredUuid(restaurant, "id");
        if (!requestedId.equals(responseId)) {
            throw new IOException("PriceTrace 식당 상세 응답의 identity가 요청과 다릅니다.");
        }
        JSONArray menus = payload.optJSONArray("menus");
        if (menus == null) {
            throw new IOException("PriceTrace 식당 상세 응답에 menus가 없습니다.");
        }
        List<RestaurantMenu> parsedMenus = new ArrayList<>();
        for (int index = 0; index < menus.length(); index++) {
            JSONObject menu = menus.optJSONObject(index);
            if (menu == null) {
                throw new IOException("PriceTrace 메뉴 항목이 올바르지 않습니다.");
            }
            parsedMenus.add(new RestaurantMenu(
                    requiredUuid(menu, "id"),
                    requiredUuid(menu, "catalogProductId"),
                    requiredText(menu, "name")
            ));
        }
        return new RestaurantDetail(
                responseId,
                requiredText(restaurant, "brand"),
                parseLocations(payload.optJSONArray("locations")),
                Collections.unmodifiableList(parsedMenus)
        );
    }

    private String post(String path, JSONObject arguments) throws Exception {
        SupabaseConfig active = config;
        if (!active.isConnectionConfigured()) {
            throw new IllegalStateException("PriceTrace 식당 DB 연결을 먼저 설정하세요.");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(joinUrl(active.supabaseUrl, path)).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("apikey", active.supabaseAnonKey);
            connection.setRequestProperty("Authorization", "Bearer " + active.supabaseAnonKey);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(arguments.toString().getBytes(StandardCharsets.UTF_8));
            }
            int statusCode = connection.getResponseCode();
            String body = readStream(statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("PriceTrace 식당 조회에 실패했습니다. (HTTP " + statusCode + ")");
            }
            if (body.trim().isEmpty()) {
                throw new IOException("PriceTrace 식당 조회 응답이 비어 있습니다.");
            }
            return body;
        } catch (SocketTimeoutException error) {
            throw new IOException("PriceTrace 식당 조회 시간이 초과되었습니다.", error);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void requireContract(JSONObject payload, String expected) throws IOException {
        if (!expected.equals(payload.optString("schemaVersion", ""))
                || !CONTRACT_NAMESPACE.equals(payload.optString("namespace", ""))) {
            throw new IOException("지원하지 않는 PriceTrace 식당 계약입니다.");
        }
    }

    private static List<RestaurantLocation> parseLocations(JSONArray rows) throws IOException {
        if (rows == null) {
            return Collections.emptyList();
        }
        List<RestaurantLocation> locations = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.optJSONObject(index);
            if (row == null) {
                throw new IOException("PriceTrace 지점 항목이 올바르지 않습니다.");
            }
            locations.add(new RestaurantLocation(
                    requiredUuid(row, "id"),
                    optionalText(row, "locationLabel"),
                    optionalText(row, "sourceLabel"),
                    optionalText(row, "sourceRestaurantCode")
            ));
        }
        return Collections.unmodifiableList(locations);
    }

    private static JSONObject requiredObject(JSONObject parent, String key) throws IOException {
        JSONObject value = parent.optJSONObject(key);
        if (value == null) {
            throw new IOException("PriceTrace 응답에 " + key + "가 없습니다.");
        }
        return value;
    }

    private static String requiredUuid(JSONObject object, String key) throws IOException {
        return requireUuid(object.optString(key, ""));
    }

    private static String requireUuid(String value) throws IOException {
        String normalized = value == null ? "" : value.trim();
        try {
            return UUID.fromString(normalized).toString();
        } catch (IllegalArgumentException error) {
            throw new IOException("PriceTrace 식별자가 UUID가 아닙니다.", error);
        }
    }

    private static String requiredText(JSONObject object, String key) throws IOException {
        String value = object.optString(key, "").trim();
        if (value.isEmpty()) {
            throw new IOException("PriceTrace 응답에 " + key + "가 없습니다.");
        }
        return value;
    }

    private static String optionalText(JSONObject object, String key) {
        String value = object.optString(key, "").trim();
        return value.isEmpty() ? null : value;
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
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

    public static final class RestaurantSummary {
        public final String restaurantId;
        public final String restaurantName;
        public final List<RestaurantLocation> locations;

        private RestaurantSummary(String restaurantId, String restaurantName,
                                   List<RestaurantLocation> locations) {
            this.restaurantId = restaurantId;
            this.restaurantName = restaurantName;
            this.locations = locations;
        }
    }

    public static final class RestaurantDetail {
        public final String restaurantId;
        public final String restaurantName;
        public final List<RestaurantLocation> locations;
        public final List<RestaurantMenu> menus;

        private RestaurantDetail(String restaurantId, String restaurantName,
                                 List<RestaurantLocation> locations,
                                 List<RestaurantMenu> menus) {
            this.restaurantId = restaurantId;
            this.restaurantName = restaurantName;
            this.locations = locations;
            this.menus = menus;
        }
    }

    public static final class RestaurantLocation {
        public final String restaurantLocationId;
        public final String branchName;
        /** Location-data provenance mapped from PriceTrace sourceLabel. */
        public final String locationSourceNamespace;
        /**
         * @deprecated Use {@link #locationSourceNamespace}; retained for source compatibility.
         */
        @Deprecated
        public final String sourceNamespace;
        public final String sourceLocationCode;

        private RestaurantLocation(
                String restaurantLocationId,
                String branchName,
                String locationSourceNamespace,
                String sourceLocationCode
        ) {
            this.restaurantLocationId = restaurantLocationId;
            this.branchName = branchName;
            this.locationSourceNamespace = locationSourceNamespace;
            this.sourceNamespace = locationSourceNamespace;
            this.sourceLocationCode = sourceLocationCode;
        }
    }

    public static final class RestaurantMenu {
        public final String restaurantMenuId;
        public final String catalogProductId;
        public final String menuName;

        private RestaurantMenu(String restaurantMenuId, String catalogProductId, String menuName) {
            this.restaurantMenuId = restaurantMenuId;
            this.catalogProductId = catalogProductId;
            this.menuName = menuName;
        }
    }
}
