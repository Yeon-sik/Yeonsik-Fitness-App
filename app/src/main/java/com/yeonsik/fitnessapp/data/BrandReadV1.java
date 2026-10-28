package com.yeonsik.fitnessapp.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** PriceTrace brand_read.v1: an alias is display evidence, never another identity. */
public final class BrandReadV1 {
    public static final String CONTRACT_VERSION = "brand_read.v1";
    public final String brandId;
    public final String canonicalName;
    public final String matchedAlias;

    public BrandReadV1(String brandId, String canonicalName, String matchedAlias) {
        if (brandId == null || !brandId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Invalid PriceTrace Brand UUID");
        if (canonicalName == null || canonicalName.trim().isEmpty())
            throw new IllegalArgumentException("PriceTrace Brand name is required");
        this.brandId = UUID.fromString(brandId).toString();
        this.canonicalName = canonicalName.trim();
        this.matchedAlias = matchedAlias == null || matchedAlias.trim().isEmpty() ? null : matchedAlias.trim();
    }

    public static List<BrandReadV1> fromContractMap(Map<String, ?> payload) {
        if (payload == null || !CONTRACT_VERSION.equals(payload.get("contract_version")))
            throw new IllegalArgumentException("Unsupported PriceTrace Brand contract");
        Object rows = payload.get("brands");
        if (!(rows instanceof List<?>)) throw new IllegalArgumentException("Brand list is required");
        List<BrandReadV1> brands = new ArrayList<>();
        for (Object value : (List<?>) rows) {
            if (!(value instanceof Map<?, ?>)) continue;
            Map<?, ?> row = (Map<?, ?>) value;
            Object id = row.get("brand_id"), name = row.get("canonical_name"), alias = row.get("matched_alias");
            if (!(id instanceof String) || !(name instanceof String) || (alias != null && !(alias instanceof String))) continue;
            try { brands.add(new BrandReadV1((String) id, (String) name, (String) alias)); }
            catch (IllegalArgumentException ignored) { /* One malformed row must not hide valid choices. */ }
        }
        return Collections.unmodifiableList(brands);
    }
}
