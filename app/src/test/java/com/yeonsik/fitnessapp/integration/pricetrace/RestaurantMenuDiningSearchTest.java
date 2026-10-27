package com.yeonsik.fitnessapp.integration.pricetrace;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.Assert.*;

public class RestaurantMenuDiningSearchTest {
    private static final String RESTAURANT = "11111111-1111-4111-8111-111111111111";
    private static final String LOCATION = "22222222-2222-4222-8222-222222222222";
    private static final String MENU = "33333333-3333-4333-8333-333333333333";
    private static final String PRODUCT = "44444444-4444-4444-8444-444444444444";
    private static final String STANDARD = "55555555-5555-4555-8555-555555555555";
    private static final String REVISION = "sha256:" + "a".repeat(64);
    private static final String TIME = "2026-10-27T01:00:00+00:00";
    private static final SupabaseConfig CONFIG = new SupabaseConfig("https://fixture.supabase.co", "test-public-key", "", "", "", "", "test");

    @Test public void restaurantAndMenuQueriesUseExistingRpcWithNullIdentityFilters() throws Exception {
        for (String query : new String[]{"맥도날드", "빅맥"}) {
            RestaurantMenuReadV1Client client = new RestaurantMenuReadV1Client(CONFIG, (config, path, arguments) -> {
                assertSame(CONFIG, config);
                assertEquals("/rest/v1/rpc/get_restaurant_menu_read_v1", path);
                assertEquals(4, arguments.length());
                assertTrue(arguments.has("p_restaurant_id")); assertTrue(arguments.isNull("p_restaurant_id"));
                assertTrue(arguments.has("p_catalog_product_id")); assertTrue(arguments.isNull("p_catalog_product_id"));
                assertEquals(query, arguments.getString("p_query"));
                assertEquals(50, arguments.getInt("p_limit"));
                return payload().toString();
            });
            assertEquals(1, client.searchDiningTargets("  " + query + "  ").size());
        }
    }

    @Test public void flattenedTargetRetainsAllExactIdsAndVerifiedLocations() throws Exception {
        JSONObject payload = payload();
        entry(payload).getJSONArray("locations").put(location("66666666-6666-4666-8666-666666666666", "시청점"));
        RestaurantMenuReadV1Client.DiningTarget target = parse(payload).get(0);
        assertEquals(RESTAURANT, target.restaurantId); assertEquals("맥도날드", target.restaurantName);
        assertEquals(MENU, target.restaurantMenuId); assertEquals("빅맥", target.menuName); assertEquals(PRODUCT, target.catalogProductId);
        assertEquals(2, target.locations.size()); assertEquals(LOCATION, target.locations.get(0).restaurantLocationId);
        assertEquals("강남점", target.locations.get(0).branchName); assertEquals("시청점", target.locations.get(1).branchName);
        assertEquals("manual_review", target.locations.get(0).locationSourceNamespace);
    }

    @Test public void zeroLocationsAndUnknownLocationLabelRemainAbsentWithoutInventedIdentity() throws Exception {
        JSONObject payload = payload(); entry(payload).put("locations", new JSONArray());
        assertTrue(parse(payload).get(0).locations.isEmpty());
        payload = payload(); entry(payload).getJSONArray("locations").getJSONObject(0).put("locationLabel", JSONObject.NULL);
        assertNull(parse(payload).get(0).locations.get(0).branchName);
    }

    @Test public void successfulNoResultIsDistinctFromTransportFailure() throws Exception {
        JSONObject empty = payload().put("restaurants", new JSONArray());
        assertTrue(parse(empty).isEmpty());
        RestaurantMenuReadV1Client client = new RestaurantMenuReadV1Client(CONFIG, (config, path, arguments) -> { throw new IOException("offline"); });
        IOException error = assertThrows(IOException.class, () -> client.searchDiningTargets("빅맥"));
        assertEquals("offline", error.getMessage());
    }

    @Test public void versionAndNamespaceMustMatchExactly() throws Exception {
        reject(payload().put("schemaVersion", "restaurant-directory.v1"));
        reject(payload().put("schemaVersion", 1));
        reject(payload().put("namespace", "fitness"));
    }

    @Test public void missingUnknownAndWrongTypeFieldsAreRejected() throws Exception {
        JSONObject value = payload(); value.remove("revision"); reject(value);
        reject(payload().put("unexpected", true));
        value = payload(); entry(value).remove("locations"); reject(value);
        value = payload(); entry(value).put("locations", JSONObject.NULL); reject(value);
        value = payload(); menu(value).put("name", 42); reject(value);
        value = payload(); menu(value).put("extraIdentity", PRODUCT); reject(value);
        value = payload(); entry(value).getJSONObject("restaurant").put("brand", "  "); reject(value);
        value = payload(); menu(value).put("observations", new JSONObject()); reject(value);
    }

    @Test public void malformedUuidsAndRevisionsAreRejected() throws Exception {
        JSONObject value = payload(); menu(value).put("id", "1-1-1-1-1"); reject(value);
        value = payload(); menu(value).put("catalogProductId", "catalog"); reject(value);
        value = payload(); entry(value).getJSONArray("locations").getJSONObject(0).put("id", "location"); reject(value);
        reject(payload().put("revision", "sha256:short"));
    }

    @Test public void duplicateMenuCatalogAndLocationIdentitiesAreRejected() throws Exception {
        JSONObject value = payload(); entry(value).getJSONArray("menus").put(new JSONObject(menu(value).toString())); reject(value);
        value = payload(); entry(value).getJSONArray("locations").put(location(LOCATION, "중복점")); reject(value);
        value = payload(); JSONObject next = new JSONObject(menu(value).toString()).put("id", STANDARD);
        entry(value).getJSONArray("menus").put(next); reject(value);
    }

    @Test public void observationMustBelongToTheReturnedVerifiedLocationAndPreservePriceEquation() throws Exception {
        JSONObject value = payload(); menu(value).getJSONArray("observations").put(observation());
        assertEquals(1, parse(value).size());
        menu(value).getJSONArray("observations").getJSONObject(0).put("restaurantSourceId", STANDARD); reject(value);
        value = payload(); menu(value).getJSONArray("observations").put(observation().put("totalPriceKrw", 123)); reject(value);
    }

    @Test public void malformedJsonAndBlankQueryNeverBecomeSuccessfulEmptyRows() throws Exception {
        assertThrows(IOException.class, () -> RestaurantMenuReadV1Parser.parse("{broken"));
        RestaurantMenuReadV1Client client = new RestaurantMenuReadV1Client(CONFIG, (config, path, arguments) -> { fail("Blank query made a request"); return ""; });
        assertThrows(IllegalArgumentException.class, () -> client.searchDiningTargets("  "));
    }

    private static List<RestaurantMenuReadV1Client.DiningTarget> parse(JSONObject value) throws IOException {
        return RestaurantMenuReadV1Parser.parse(value.toString());
    }
    private static void reject(JSONObject value) { assertThrows(IOException.class, () -> parse(value)); }
    private static JSONObject entry(JSONObject value) throws Exception { return value.getJSONArray("restaurants").getJSONObject(0); }
    private static JSONObject menu(JSONObject value) throws Exception { return entry(value).getJSONArray("menus").getJSONObject(0); }
    private static JSONObject payload() throws Exception {
        JSONObject restaurant = new JSONObject().put("id", RESTAURANT).put("brandId", JSONObject.NULL).put("brand", "맥도날드")
                .put("legalName", JSONObject.NULL).put("cuisineType", JSONObject.NULL).put("officialSiteUrl", JSONObject.NULL).put("updatedAt", TIME);
        JSONObject menu = new JSONObject().put("id", MENU).put("catalogProductId", PRODUCT).put("standardProductId", STANDARD).put("name", "빅맥")
                .put("categoryLabel", "버거").put("servingLabel", "1개").put("officialUrl", JSONObject.NULL).put("updatedAt", TIME)
                .put("observations", new JSONArray()).put("revision", REVISION);
        JSONObject entry = new JSONObject().put("restaurant", restaurant).put("locations", new JSONArray().put(location(LOCATION, "강남점")))
                .put("menus", new JSONArray().put(menu)).put("revision", REVISION);
        return new JSONObject().put("schemaVersion", "restaurant-menu-read.v1").put("namespace", "pricetrace")
                .put("revision", REVISION).put("restaurants", new JSONArray().put(entry));
    }
    private static JSONObject location(String id, String label) throws Exception {
        return new JSONObject().put("id", id).put("sourceLabel", "manual_review").put("sourceRestaurantCode", id)
                .put("locationLabel", label).put("sourceUrl", JSONObject.NULL);
    }
    private static JSONObject observation() throws Exception {
        return new JSONObject().put("id", "77777777-7777-4777-8777-777777777777").put("restaurantSourceId", LOCATION)
                .put("locationLabel", "강남점").put("unitPriceKrw", 5000).put("quantity", 2).put("totalPriceKrw", 10000)
                .put("observedAt", TIME).put("sourceType", "database_receipt").put("receiptReference", JSONObject.NULL)
                .put("sourceUrl", JSONObject.NULL).put("verifiedAt", TIME);
    }
}
