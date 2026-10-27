package com.yeonsik.fitnessapp.integration.pricetrace;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Strict restaurant-menu-read.v1 wire parser, separate from legacy directory/detail reads. */
final class RestaurantMenuReadV1Parser {
    static List<RestaurantMenuReadV1Client.DiningTarget> parse(String body) throws IOException {
        try {
            JSONObject payload = new JSONObject(body);
            keys(payload, fields("schemaVersion", "namespace", "revision", "restaurants"));
            if (!"restaurant-menu-read.v1".equals(text(payload, "schemaVersion"))
                    || !"pricetrace".equals(text(payload, "namespace"))) throw invalid();
            revision(payload);
            JSONArray restaurants = array(payload, "restaurants");
            Set<String> restaurantIds = new HashSet<>(), menuIds = new HashSet<>(), productIds = new HashSet<>(), observationIds = new HashSet<>();
            List<RestaurantMenuReadV1Client.DiningTarget> result = new ArrayList<>();
            for (int index = 0; index < restaurants.length(); index++) {
                JSONObject entry = object(restaurants, index);
                keys(entry, fields("restaurant", "locations", "menus", "revision"));
                revision(entry);
                JSONObject restaurant = object(entry, "restaurant");
                keys(restaurant, fields("id", "brandId", "brand", "legalName", "cuisineType", "officialSiteUrl", "updatedAt"),
                        fields("category", "fulfillmentModes"));
                String restaurantId = uuid(restaurant, "id"), restaurantName = text(restaurant, "brand");
                unique(restaurantIds, restaurantId);
                if (nullableText(restaurant, "brandId") != null) uuid(restaurant, "brandId");
                nullableText(restaurant, "legalName"); nullableText(restaurant, "cuisineType");
                nullableUrl(restaurant, "officialSiteUrl"); timestamp(restaurant, "updatedAt");
                profileExtensions(restaurant);
                List<RestaurantMenuReadV1Client.RestaurantLocation> locations = new ArrayList<>();
                Set<String> locationIds = new HashSet<>(), sources = new HashSet<>();
                JSONArray locationRows = array(entry, "locations");
                for (int locationIndex = 0; locationIndex < locationRows.length(); locationIndex++) {
                    JSONObject location = object(locationRows, locationIndex);
                    keys(location, fields("id", "sourceLabel", "sourceRestaurantCode", "locationLabel", "sourceUrl"));
                    String id = uuid(location, "id"), source = text(location, "sourceLabel"), code = text(location, "sourceRestaurantCode");
                    unique(locationIds, id); unique(sources, source + "\u0000" + code);
                    String label = nullableText(location, "locationLabel"); nullableUrl(location, "sourceUrl");
                    locations.add(new RestaurantMenuReadV1Client.RestaurantLocation(id, label, source, code));
                }
                JSONArray menus = array(entry, "menus");
                if (menus.length() == 0) throw invalid();
                for (int menuIndex = 0; menuIndex < menus.length(); menuIndex++) {
                    JSONObject menu = object(menus, menuIndex);
                    keys(menu, fields("id", "catalogProductId", "standardProductId", "name", "categoryLabel", "servingLabel",
                            "officialUrl", "updatedAt", "observations", "revision"));
                    String menuId = uuid(menu, "id"), productId = uuid(menu, "catalogProductId"), menuName = text(menu, "name");
                    unique(menuIds, menuId); unique(productIds, productId);
                    uuid(menu, "standardProductId"); nullableText(menu, "categoryLabel"); text(menu, "servingLabel");
                    nullableUrl(menu, "officialUrl"); timestamp(menu, "updatedAt"); revision(menu);
                    observations(array(menu, "observations"), locationIds, observationIds);
                    result.add(new RestaurantMenuReadV1Client.DiningTarget(restaurantId, restaurantName,
                            Collections.unmodifiableList(new ArrayList<>(locations)), menuId, menuName, productId));
                }
            }
            return Collections.unmodifiableList(result);
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("PriceTrace 메뉴 검색 응답이 올바르지 않습니다.", error);
        }
    }

    private static void observations(JSONArray rows, Set<String> locationIds, Set<String> observationIds) throws Exception {
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = object(rows, index);
            keys(row, fields("id", "restaurantSourceId", "locationLabel", "unitPriceKrw", "quantity", "totalPriceKrw",
                    "observedAt", "sourceType", "receiptReference", "sourceUrl", "verifiedAt"));
            unique(observationIds, uuid(row, "id"));
            if (!locationIds.contains(uuid(row, "restaurantSourceId"))) throw invalid();
            nullableText(row, "locationLabel"); nullableUrl(row, "sourceUrl");
            timestamp(row, "observedAt"); timestamp(row, "verifiedAt");
            long price = integer(row, "unitPriceKrw"), quantity = integer(row, "quantity"), total = integer(row, "totalPriceKrw");
            if (price < 0 || quantity <= 0 || total < 0 || Math.multiplyExact(price, quantity) != total) throw invalid();
            String type = text(row, "sourceType");
            if (!fields("receipt", "database_receipt", "official_menu", "manual_review").contains(type)) throw invalid();
            if (row.isNull("receiptReference")) { if ("receipt".equals(type)) throw invalid(); }
            else {
                JSONObject reference = object(row, "receiptReference");
                keys(reference, fields("receiptId", "receiptItemId", "receiptRevision"));
                text(reference, "receiptId"); text(reference, "receiptItemId"); text(reference, "receiptRevision");
            }
        }
    }

    private static void profileExtensions(JSONObject profile) throws Exception {
        if (profile.has("category") && !profile.isNull("category")) {
            JSONObject category = object(profile, "category");
            keys(category, fields("id", "slug", "name", "path"));
            String id = uuid(category, "id"), slug = text(category, "slug"), name = text(category, "name");
            JSONArray path = array(category, "path"); Set<String> ids = new HashSet<>();
            if (path.length() < 1 || path.length() > 3) throw invalid();
            for (int index = 0; index < path.length(); index++) {
                JSONObject node = object(path, index); keys(node, fields("id", "slug", "name"));
                unique(ids, uuid(node, "id")); text(node, "slug"); text(node, "name");
            }
            JSONObject leaf = object(path, path.length() - 1);
            if (!id.equals(uuid(leaf, "id")) || !slug.equals(text(leaf, "slug")) || !name.equals(text(leaf, "name"))) throw invalid();
        }
        if (profile.has("fulfillmentModes")) {
            JSONArray modes = array(profile, "fulfillmentModes");
            for (int index = 0; index < modes.length(); index++) {
                JSONObject mode = object(modes, index); keys(mode, fields("type", "evidence"));
                if (!fields("delivery", "takeout", "dine_in").contains(text(mode, "type"))
                        || !fields("receipt", "manual").contains(text(mode, "evidence"))) throw invalid();
            }
        }
    }

    private static Set<String> fields(String... names) { return new HashSet<>(Arrays.asList(names)); }
    private static void keys(JSONObject value, Set<String> required) throws IOException { keys(value, required, Collections.emptySet()); }
    private static void keys(JSONObject value, Set<String> required, Set<String> optional) throws IOException {
        for (String key : required) if (!value.has(key)) throw invalid();
        Iterator<String> iterator = value.keys();
        while (iterator.hasNext()) { String key = iterator.next(); if (!required.contains(key) && !optional.contains(key)) throw invalid(); }
    }
    private static JSONObject object(JSONObject value, String key) throws IOException {
        Object item = value.opt(key); if (!(item instanceof JSONObject)) throw invalid(); return (JSONObject) item;
    }
    private static JSONObject object(JSONArray value, int index) throws IOException {
        Object item = value.opt(index); if (!(item instanceof JSONObject)) throw invalid(); return (JSONObject) item;
    }
    private static JSONArray array(JSONObject value, String key) throws IOException {
        Object item = value.opt(key); if (!(item instanceof JSONArray)) throw invalid(); return (JSONArray) item;
    }
    private static String text(JSONObject value, String key) throws IOException {
        Object item = value.opt(key);
        if (!(item instanceof String) || ((String) item).trim().isEmpty()) throw invalid();
        return ((String) item).trim();
    }
    private static String nullableText(JSONObject value, String key) throws IOException {
        if (!value.has(key)) throw invalid(); return value.isNull(key) ? null : text(value, key);
    }
    private static String uuid(JSONObject value, String key) throws IOException {
        String id = text(value, key);
        if (!id.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")) throw invalid();
        return UUID.fromString(id).toString();
    }
    private static void revision(JSONObject value) throws IOException { if (!text(value, "revision").matches("sha256:[a-f0-9]{64}")) throw invalid(); }
    private static void timestamp(JSONObject value, String key) throws Exception { OffsetDateTime.parse(text(value, key)); }
    private static void nullableUrl(JSONObject value, String key) throws Exception {
        String url = nullableText(value, key); if (url == null) return;
        URI uri = new URI(url);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null) throw invalid();
    }
    private static long integer(JSONObject value, String key) throws Exception {
        Object number = value.opt(key); if (!(number instanceof Number)) throw invalid();
        return new BigDecimal(number.toString()).longValueExact();
    }
    private static void unique(Set<String> values, String value) throws IOException { if (!values.add(value)) throw invalid(); }
    private static IOException invalid() { return new IOException("지원하지 않거나 올바르지 않은 PriceTrace 메뉴 검색 응답입니다."); }
    private RestaurantMenuReadV1Parser() { }
}
