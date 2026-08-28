package com.yeonsik.fitnessapp.data;

import java.util.UUID;

/**
 * Exact PriceTrace restaurant identity carried by a dining-out meal.
 * Names remain snapshots for display; IDs are the only cross-app link keys.
 */
public final class DiningOutIdentity {
    public static final String CONTRACT_VERSION = "dining-out-identity.v1";
    public static final String NAMESPACE = "pricetrace";

    public final String restaurantId;
    public final String restaurantName;
    public final String restaurantLocationId;
    public final String sourceNamespace;
    public final String sourceLocationCode;
    public final String branchName;
    public final String restaurantMenuId;
    public final String menuName;
    public final String catalogProductId;

    private DiningOutIdentity(
            String restaurantId,
            String restaurantName,
            String restaurantLocationId,
            String sourceNamespace,
            String sourceLocationCode,
            String branchName,
            String restaurantMenuId,
            String menuName,
            String catalogProductId
    ) {
        this.restaurantId = requireUuid(restaurantId, "restaurantId");
        this.restaurantName = requireText(restaurantName, "restaurantName");
        this.restaurantLocationId = requireUuid(restaurantLocationId, "restaurantLocationId");
        this.sourceNamespace = requireText(sourceNamespace, "sourceNamespace");
        this.sourceLocationCode = optionalText(sourceLocationCode);
        this.branchName = optionalText(branchName);
        this.restaurantMenuId = requireUuid(restaurantMenuId, "restaurantMenuId");
        this.menuName = requireText(menuName, "menuName");
        this.catalogProductId = requireUuid(catalogProductId, "catalogProductId");
    }

    public static DiningOutIdentity fromPriceTrace(
            String restaurantId,
            String restaurantName,
            String restaurantLocationId,
            String branchName,
            String restaurantMenuId,
            String menuName,
            String catalogProductId
    ) {
        return new DiningOutIdentity(
                restaurantId,
                restaurantName,
                restaurantLocationId,
                NAMESPACE,
                null,
                branchName,
                restaurantMenuId,
                menuName,
                catalogProductId
        );
    }

    public static DiningOutIdentity fromPriceTrace(
            String restaurantId,
            String restaurantName,
            String restaurantLocationId,
            String sourceNamespace,
            String sourceLocationCode,
            String branchName,
            String restaurantMenuId,
            String menuName,
            String catalogProductId
    ) {
        return new DiningOutIdentity(
                restaurantId,
                restaurantName,
                restaurantLocationId,
                sourceNamespace,
                sourceLocationCode,
                branchName,
                restaurantMenuId,
                menuName,
                catalogProductId
        );
    }

    public String metadataJson() {
        return "{"
                + "\"schema_version\":\"" + CONTRACT_VERSION + "\","
                + "\"namespace\":\"" + escape(sourceNamespace) + "\","
                + "\"restaurant_id\":\"" + restaurantId + "\","
                + "\"restaurant_name\":\"" + escape(restaurantName) + "\","
                + "\"restaurant_location_id\":\"" + restaurantLocationId + "\","
                + "\"source_location_code\":" + nullableJson(sourceLocationCode) + ","
                + "\"branch_name\":" + nullableJson(branchName) + ","
                + "\"restaurant_menu_id\":\"" + restaurantMenuId + "\","
                + "\"menu_name\":\"" + escape(menuName) + "\","
                + "\"catalog_product_id\":\"" + catalogProductId + "\""
                + "}";
    }

    /** Restaurant and location belong to one dining-out record; menu IDs are per item. */
    public boolean hasSameRestaurantLocation(DiningOutIdentity other) {
        return other != null
                && restaurantId.equals(other.restaurantId)
                && restaurantLocationId.equals(other.restaurantLocationId)
                && sourceNamespace.equals(other.sourceNamespace)
                && java.util.Objects.equals(sourceLocationCode, other.sourceLocationCode);
    }

    private static String requireUuid(String value, String label) {
        String normalized = requireText(value, label);
        try {
            return UUID.fromString(normalized).toString();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(label + "는 UUID여야 합니다.", error);
        }
    }

    private static String requireText(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + "은 필수입니다.");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String nullableJson(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
