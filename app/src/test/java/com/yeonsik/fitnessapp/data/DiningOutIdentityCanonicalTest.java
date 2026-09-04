package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DiningOutIdentityCanonicalTest {
    @Test
    public void sourceLocationProvenanceDoesNotChangeCanonicalRestaurantLocation() {
        DiningOutIdentity withoutSourceCode = DiningOutIdentity.fromPriceTrace(
                "11111111-1111-4111-8111-111111111111",
                "식당",
                "22222222-2222-4222-8222-222222222222",
                null,
                null,
                "본점",
                "33333333-3333-4333-8333-333333333333",
                "메뉴 A",
                "44444444-4444-4444-8444-444444444444"
        );
        DiningOutIdentity withSourceCode = DiningOutIdentity.fromPriceTrace(
                "11111111-1111-4111-8111-111111111111",
                "식당",
                "22222222-2222-4222-8222-222222222222",
                "other-provenance",
                "branch-code",
                "본점",
                "55555555-5555-4555-8555-555555555555",
                "메뉴 B",
                "66666666-6666-4666-8666-666666666666"
        );
        DiningOutIdentity otherLocation = DiningOutIdentity.fromPriceTrace(
                "11111111-1111-4111-8111-111111111111",
                "식당",
                "77777777-7777-4777-8777-777777777777",
                null,
                null,
                "본점",
                "88888888-8888-4888-8888-888888888888",
                "메뉴 C",
                "99999999-9999-4999-8999-999999999999"
        );

        assertTrue(withoutSourceCode.hasSameRestaurantLocation(withSourceCode));
        assertFalse(withoutSourceCode.hasSameRestaurantLocation(otherLocation));
    }

    @Test
    public void metadataSeparatesPriceTraceNamespaceFromLocationSourceLabel() throws Exception {
        String sourceLabel = "public-receipt";
        DiningOutIdentity identity = DiningOutIdentity.fromPriceTrace(
                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                "식당 snapshot",
                "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                sourceLabel,
                "receipt-location-42",
                "영등포점 snapshot",
                "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                "메뉴 snapshot",
                "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
        );

        String metadata = identity.metadataJson();
        assertTrue(metadata.contains("\"namespace\":\"" + DiningOutIdentity.NAMESPACE + "\""));
        assertEquals(sourceLabel, identity.locationSourceNamespace);
        assertTrue(metadata.contains("\"source_namespace\":\"" + sourceLabel + "\""));
        assertTrue(metadata.contains("\"source_location_code\":\"receipt-location-42\""));
        assertTrue(metadata.contains("\"restaurant_id\":\"" + identity.restaurantId + "\""));
        assertTrue(metadata.contains("\"restaurant_location_id\":\"" + identity.restaurantLocationId + "\""));
        assertTrue(metadata.contains("\"restaurant_menu_id\":\"" + identity.restaurantMenuId + "\""));
        assertTrue(metadata.contains("\"catalog_product_id\":\"" + identity.catalogProductId + "\""));
    }
}
