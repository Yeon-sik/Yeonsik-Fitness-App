package com.yeonsik.fitnessapp.data;

import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Read-only adapter for a PriceTrace standard product and its representative offer. */
public final class ProductReadV1 {
    public static final String CONTRACT_VERSION = "product-read.v1";

    public final String catalogProductId;
    public final String standardProductId;
    public final String name;
    public final String brand;
    /** Explicit PriceTrace company/manufacturer field; null when it is absent from the contract. */
    public final String manufacturerName;
    /** Explicit PriceTrace sub-brand field; nullable by contract. */
    public final String subBrandName;
    public final String sellerName;
    public final Integer latestObservedPriceKrw;
    public final String observedAt;
    /** Revision of this exact product document, not the top-level payload revision. */
    public final String revision;
    public final Double contentAmount;
    public final String contentUnit;
    public final Integer packageCount;
    private final List<ProductReadV1> catalogVariants;

    public ProductReadV1(
            String catalogProductId,
            String standardProductId,
            String name,
            String sellerName,
            Integer latestObservedPriceKrw,
            String observedAt,
            Double contentAmount,
            String contentUnit,
            Integer packageCount
    ) {
        this(
                catalogProductId,
                standardProductId,
                name,
                null,
                sellerName,
                latestObservedPriceKrw,
                observedAt,
                contentAmount,
                contentUnit,
                packageCount,
                null
        );
    }

    public ProductReadV1(
            String catalogProductId,
            String standardProductId,
            String name,
            String brand,
            String sellerName,
            Integer latestObservedPriceKrw,
            String observedAt,
            Double contentAmount,
            String contentUnit,
            Integer packageCount
    ) {
        this(
                catalogProductId,
                standardProductId,
                name,
                brand,
                sellerName,
                latestObservedPriceKrw,
                observedAt,
                contentAmount,
                contentUnit,
                packageCount,
                null
        );
    }

    public ProductReadV1(
            String catalogProductId,
            String standardProductId,
            String name,
            String brand,
            String sellerName,
            Integer latestObservedPriceKrw,
            String observedAt,
            Double contentAmount,
            String contentUnit,
            Integer packageCount,
            String revision
    ) {
        this(
                catalogProductId,
                standardProductId,
                name,
                brand,
                sellerName,
                latestObservedPriceKrw,
                observedAt,
                contentAmount,
                contentUnit,
                packageCount,
                revision,
                Collections.emptyList()
        );
    }

    /** Explicit hierarchy overload used when rebuilding a read-only product from local cache. */
    public ProductReadV1(
            String catalogProductId,
            String standardProductId,
            String name,
            String brand,
            String manufacturerName,
            String subBrandName,
            String sellerName,
            Integer latestObservedPriceKrw,
            String observedAt,
            Double contentAmount,
            String contentUnit,
            Integer packageCount,
            String revision
    ) {
        this(
                catalogProductId,
                standardProductId,
                name,
                brand,
                manufacturerName,
                subBrandName,
                sellerName,
                latestObservedPriceKrw,
                observedAt,
                contentAmount,
                contentUnit,
                packageCount,
                revision,
                Collections.emptyList()
        );
    }

    private ProductReadV1(
            String catalogProductId,
            String standardProductId,
            String name,
            String brand,
            String sellerName,
            Integer latestObservedPriceKrw,
            String observedAt,
            Double contentAmount,
            String contentUnit,
            Integer packageCount,
            String revision,
            List<ProductReadV1> catalogVariants
    ) {
        this(
                catalogProductId,
                standardProductId,
                name,
                brand,
                null,
                null,
                sellerName,
                latestObservedPriceKrw,
                observedAt,
                contentAmount,
                contentUnit,
                packageCount,
                revision,
                catalogVariants
        );
    }

    private ProductReadV1(
            String catalogProductId,
            String standardProductId,
            String name,
            String brand,
            String manufacturerName,
            String subBrandName,
            String sellerName,
            Integer latestObservedPriceKrw,
            String observedAt,
            Double contentAmount,
            String contentUnit,
            Integer packageCount,
            String revision,
            List<ProductReadV1> catalogVariants
    ) {
        this.catalogProductId = requireUuid(catalogProductId, "catalogProductId");
        this.standardProductId = optionalUuid(standardProductId, "standardProductId");
        this.name = requireText(name, "상품명");
        this.brand = optionalText(brand);
        this.manufacturerName = optionalText(manufacturerName);
        this.subBrandName = optionalText(subBrandName);
        this.sellerName = optionalText(sellerName);
        if ((latestObservedPriceKrw == null) != (optionalText(observedAt) == null)) {
            throw new IllegalArgumentException("최근 관측가와 관측시각은 함께 있거나 함께 null이어야 합니다.");
        }
        if (latestObservedPriceKrw != null && latestObservedPriceKrw < 0) {
            throw new IllegalArgumentException("최근 관측가는 음수일 수 없습니다.");
        }
        this.latestObservedPriceKrw = latestObservedPriceKrw;
        this.observedAt = optionalText(observedAt);
        this.revision = validateRevision(revision);
        this.contentAmount = contentAmount;
        this.contentUnit = optionalText(contentUnit);
        this.packageCount = packageCount;
        this.catalogVariants = catalogVariants == null || catalogVariants.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(catalogVariants));
    }

    /**
     * Parses the stable product-read.v1 names and the currently deployed PriceTrace RPC names.
     * The adapter is explicit so Android does not infer identity from a product title.
     */
    public static ProductReadV1 fromMap(Map<String, ?> row) {
        String contract = stringValue(first(row, "contractVersion", "contract_version"));
        if (contract != null && !CONTRACT_VERSION.equals(contract)) {
            throw new IllegalArgumentException("지원하지 않는 PriceTrace 계약입니다: " + contract);
        }

        Map<String, ?> standardProduct = mapValue(first(
                row,
                "standardProduct",
                "standard_product"
        ));
        Map<String, ?> catalogProduct = mapValue(first(
                row,
                "catalogProduct",
                "catalog_product"
        ));
        if (standardProduct != null || catalogProduct != null) {
            if (standardProduct == null || catalogProduct == null) {
                throw new IllegalArgumentException("PriceTrace 표준상품과 규격상품 정보가 모두 필요합니다.");
            }
            return fromVersionedProduct(row, standardProduct, catalogProduct);
        }

        Object priceValue = first(
                row,
                "latestObservedPriceKrw",
                "latest_price_krw",
                "coupang_listed_price_krw"
        );
        Integer price = integerValue(priceValue);
        String observedAt = stringValue(first(
                row,
                "observedAt",
                "observed_at",
                "coupang_observed_at"
        ));
        String seller = stringValue(first(row, "sellerName", "seller_name"));
        if (seller == null
                && row != null
                && row.get("coupang_listed_price_krw") != null) {
            seller = "쿠팡";
        }
        if (seller == null) {
            seller = stringValue(first(row, "sourceLabel", "source_label"));
        }
        String brand = stringValue(first(row, "brand", "brandName", "brand_name"));
        String manufacturerName = stringValue(first(
                row,
                "manufacturerName",
                "manufacturer_name",
                "companyName",
                "company_name"
        ));
        String subBrandName = stringValue(first(
                row,
                "subBrandName",
                "sub_brand_name",
                "subBrand",
                "sub_brand"
        ));
        String name = productNameWithoutBrand(stringValue(first(
                row,
                "standardProductName",
                "standard_product_name",
                "standardName",
                "standard_name",
                "name",
                "productName",
                "product_name"
        )), brand);

        return new ProductReadV1(
                stringValue(first(row, "catalogProductId", "catalog_product_id")),
                stringValue(first(row, "standardProductId", "standard_product_id")),
                name,
                brand,
                manufacturerName,
                subBrandName,
                seller,
                price,
                observedAt,
                doubleValue(first(row, "contentAmount", "content_amount")),
                stringValue(first(row, "contentUnit", "content_unit")),
                integerValue(first(row, "packageCount", "package_count")),
                stringValue(first(row, "revision", "catalogProductRevision", "catalog_product_revision")),
                Collections.emptyList()
        );
    }

    /** Parses and validates the top-level product-read.v1 payload. */
    public static List<ProductReadV1> fromContractMap(Map<String, ?> payload) {
        String contract = stringValue(first(payload, "schemaVersion", "schema_version"));
        if (!CONTRACT_VERSION.equals(contract)) {
            throw new IllegalArgumentException("지원하지 않는 PriceTrace 계약입니다: " + contract);
        }
        String namespace = stringValue(first(payload, "namespace"));
        if (!"pricetrace".equals(namespace)) {
            throw new IllegalArgumentException("PriceTrace namespace가 올바르지 않습니다: " + namespace);
        }
        List<?> rows = listValue(first(payload, "products"));
        if (rows == null) {
            throw new IllegalArgumentException("PriceTrace 상품 목록이 필요합니다.");
        }

        List<ProductReadV1> products = new ArrayList<>();
        for (Object value : rows) {
            Map<String, ?> row = mapValue(value);
            if (row == null) {
                throw new IllegalArgumentException("PriceTrace 상품 형식이 올바르지 않습니다.");
            }
            products.add(fromMap(row));
        }
        return products;
    }

    private static ProductReadV1 fromVersionedProduct(
            Map<String, ?> row,
            Map<String, ?> standardProduct,
            Map<String, ?> catalogProduct
    ) {
        DisplayNameParts displayName = displayNameParts(
                stringValue(first(standardProduct, "name", "standardName", "standard_name")),
                stringValue(first(standardProduct, "brand", "brandName", "brand_name")),
                stringValue(first(catalogProduct, "name", "productName", "product_name"))
        );
        String manufacturerName = firstExplicitText(
                standardProduct,
                row,
                catalogProduct,
                "manufacturerName",
                "manufacturer_name",
                "companyName",
                "company_name"
        );
        String subBrandName = firstExplicitText(
                standardProduct,
                row,
                catalogProduct,
                "subBrandName",
                "sub_brand_name",
                "subBrand",
                "sub_brand"
        );
        Map<String, ?> observation = firstMap(listValue(first(row, "observations")));
        Map<String, ?> sellerProduct = firstMap(listValue(first(
                row,
                "sellerProducts",
                "seller_products"
        )));
        String seller = observation == null
                ? null
                : stringValue(first(observation, "sellerLabel", "seller_label"));
        if (seller == null && sellerProduct != null) {
            seller = stringValue(first(sellerProduct, "sellerLabel", "seller_label"));
        }

        return new ProductReadV1(
                stringValue(first(catalogProduct, "id", "catalogProductId", "catalog_product_id")),
                stringValue(first(standardProduct, "id", "standardProductId", "standard_product_id")),
                displayName.productName,
                displayName.brand,
                manufacturerName,
                subBrandName,
                seller,
                observation == null
                        ? null
                        : integerValue(first(observation, "listedPriceKrw", "listed_price_krw")),
                observation == null
                        ? null
                        : stringValue(first(observation, "observedAt", "observed_at")),
                doubleValue(first(catalogProduct, "contentAmount", "content_amount")),
                stringValue(first(catalogProduct, "contentUnit", "content_unit")),
                integerValue(first(catalogProduct, "packageCount", "package_count")),
                stringValue(first(row, "revision", "catalogProductRevision", "catalog_product_revision")),
                Collections.emptyList()
        );
    }

    /**
     * Case-insensitive standard-product search.
     *
     * <p>The PriceTrace read projection may contain multiple package specifications for one
     * standard product. Search results expose one standard product only. Exact catalog children
     * remain internal so a nutrition link can be created only when one exact child is known.</p>
     */
    public static List<ProductReadV1> search(List<ProductReadV1> products, String query, int limit) {
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int safeLimit = limit <= 0 ? Integer.MAX_VALUE : limit;
        Map<String, Map<String, ProductReadV1>> variantsByStandard = new LinkedHashMap<>();
        if (products != null) {
            for (ProductReadV1 product : products) {
                if (product == null || product.standardProductId == null) {
                    continue;
                }
                String searchable = product.manufacturerName + " "
                        + product.brand + " "
                        + product.subBrandName + " "
                        + product.name;
                if (!searchable.toLowerCase(Locale.ROOT).contains(term)) {
                    continue;
                }
                Map<String, ProductReadV1> variants = variantsByStandard.computeIfAbsent(
                        product.standardProductId,
                        ignored -> new LinkedHashMap<>()
                );
                ProductReadV1 previous = variants.get(product.catalogProductId);
                if (previous == null || hasNewerObservation(product, previous)) {
                    variants.put(product.catalogProductId, product);
                }
            }
        }
        List<ProductReadV1> results = new ArrayList<>();
        for (Map<String, ProductReadV1> variants : variantsByStandard.values()) {
            ProductReadV1 representative = null;
            for (ProductReadV1 variant : variants.values()) {
                if (representative == null || hasNewerObservation(variant, representative)) {
                    representative = variant;
                }
            }
            if (representative == null) {
                continue;
            }
            if (variants.size() == 1) {
                results.add(representative);
            } else {
                results.add(representative.asStandardResult(new ArrayList<>(variants.values())));
            }
        }
        if (results.size() <= safeLimit) {
            return results;
        }
        return new ArrayList<>(results.subList(0, safeLimit));
    }

    private ProductReadV1 asStandardResult(List<ProductReadV1> variants) {
        return new ProductReadV1(
                catalogProductId,
                standardProductId,
                name,
                brand,
                manufacturerName,
                subBrandName,
                sellerName,
                latestObservedPriceKrw,
                observedAt,
                contentAmount,
                contentUnit,
                packageCount,
                revision,
                variants
        );
    }

    /** True only for an exact product-read.v1 catalog row, never for an aggregated search row. */
    public boolean isExactCatalogProduct() {
        return catalogVariants.isEmpty();
    }

    /** True only when all four link metadata values are present and match PriceTrace v1. */
    public boolean hasValidPriceTraceCatalogMetadata() {
        return revision != null
                && revision.matches("^sha256:[0-9a-f]{64}$")
                && contentAmount != null
                && !contentAmount.isNaN()
                && !contentAmount.isInfinite()
                && contentAmount > 0
                && ("g".equals(contentUnit)
                || "ml".equals(contentUnit)
                || "each".equals(contentUnit))
                && packageCount != null
                && packageCount > 0;
    }

    public int catalogVariantCount() {
        return catalogVariants.isEmpty() ? 1 : catalogVariants.size();
    }

    /** Exact rows backing a standard-only search result; child labels stay out of the UI. */
    public List<ProductReadV1> exactCatalogVariants() {
        return catalogVariants.isEmpty()
                ? Collections.singletonList(this)
                : catalogVariants;
    }

    /**
     * Resolves an exact child only when the standard has one child or one child uniquely matches
     * the entered nutrition basis. This prevents a 130 g child being linked to a 210 g entry.
     */
    public ProductReadV1 exactVariantForBasis(double basisAmount, String basisUnit) {
        if (isExactCatalogProduct()) {
            return this;
        }
        ProductReadV1 match = null;
        for (ProductReadV1 variant : catalogVariants) {
            if (variant.contentAmount == null || variant.contentUnit == null
                    || !NutritionUnit.areCompatible(variant.contentUnit, basisUnit)) {
                continue;
            }
            double converted;
            try {
                converted = NutritionUnit.convert(
                        variant.contentAmount,
                        variant.contentUnit,
                        basisUnit
                );
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            double tolerance = Math.max(0.000001, Math.abs(basisAmount) * 0.000001);
            if (Math.abs(converted - basisAmount) > tolerance) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = variant;
        }
        return match;
    }

    private static boolean hasNewerObservation(
            ProductReadV1 candidate,
            ProductReadV1 previous
    ) {
        if (candidate.latestObservedPriceKrw == null) {
            return false;
        }
        if (previous.latestObservedPriceKrw == null) {
            return true;
        }
        try {
            return OffsetDateTime.parse(candidate.observedAt).toInstant()
                    .isAfter(OffsetDateTime.parse(previous.observedAt).toInstant());
        } catch (Exception ignored) {
            return candidate.observedAt.compareTo(previous.observedAt) > 0;
        }
    }

    public String specificationLabel() {
        if (contentAmount == null || contentUnit == null) {
            return "규격 정보 없음";
        }
        String amount = NutritionCalculator.trim(contentAmount);
        return amount + contentUnit + (packageCount == null ? "" : " × " + packageCount);
    }

    public String priceObservationLabel() {
        if (latestObservedPriceKrw == null) {
            return "판매처 " + (sellerName == null ? "미상" : sellerName)
                    + " · 최근 관측가 없음 · 관측시각 없음";
        }
        return "판매처 " + (sellerName == null ? "미상" : sellerName)
                + " · 최근 관측가 "
                + NumberFormat.getIntegerInstance(Locale.KOREA).format(latestObservedPriceKrw)
                + "원 · 관측 " + observedAt;
    }

    /** The only standard-product identity shown in the Fitness app UI. */
    public String standardProductLabel() {
        List<String> parts = new ArrayList<>();
        addLabelPart(parts, manufacturerName);
        addLabelPart(parts, brand);
        addLabelPart(parts, subBrandName);
        addLabelPart(parts, name);
        return String.join(" · ", parts);
    }

    public String exactSelectionLabel() {
        return standardProductLabel() + " · " + specificationLabel()
                + "\n" + priceObservationLabel();
    }

    private static String validateRevision(String value) {
        String normalized = optionalText(value);
        if (normalized != null && !normalized.matches("^sha256:[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("PriceTrace 상품 revision 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private static String firstExplicitText(
            Map<String, ?> primary,
            Map<String, ?> secondary,
            Map<String, ?> tertiary,
            String... keys
    ) {
        String value = stringValue(first(primary, keys));
        if (value != null) {
            return value;
        }
        value = stringValue(first(secondary, keys));
        if (value != null) {
            return value;
        }
        return stringValue(first(tertiary, keys));
    }

    private static void addLabelPart(List<String> parts, String value) {
        String normalized = optionalText(value);
        if (normalized != null && !parts.contains(normalized)) {
            parts.add(normalized);
        }
    }

    private static Object first(Map<String, ?> row, String... keys) {
        if (row == null) {
            return null;
        }
        for (String key : keys) {
            if (row.containsKey(key) && row.get(key) != null) {
                return row.get(key);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> mapValue(Object value) {
        return value instanceof Map ? (Map<String, ?>) value : null;
    }

    private static List<?> listValue(Object value) {
        return value instanceof List ? (List<?>) value : null;
    }

    private static Map<String, ?> firstMap(List<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return mapValue(values.get(0));
    }

    /** Uses only the explicit PriceTrace brand field; it never guesses a brand from a title. */
    private static String productNameWithoutBrand(String rawName, String rawBrand) {
        String name = requireText(rawName, "상품명");
        String brand = optionalText(rawBrand);
        if (brand == null) {
            return name;
        }

        String[] prefixes = {
                brand,
                "[" + brand + "]",
                "(" + brand + ")",
                "【" + brand + "】"
        };
        for (String prefix : prefixes) {
            if (!name.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }
            int start = prefix.length();
            while (start < name.length() && isBrandSeparator(name.charAt(start))) {
                start++;
            }
            String productName = name.substring(start).trim();
            if (!productName.isEmpty()) {
                return productName;
            }
        }
        return name;
    }

    /**
     * Uses the explicit brand first. If it is absent, the catalog-product name must independently
     * corroborate the product-name suffix before the standard-name prefix is treated as a brand.
     */
    private static DisplayNameParts displayNameParts(
            String rawStandardName,
            String rawBrand,
            String rawCatalogName
    ) {
        String standardName = requireText(rawStandardName, "상품명");
        String explicitBrand = optionalText(rawBrand);
        if (explicitBrand != null) {
            return new DisplayNameParts(
                    explicitBrand,
                    productNameWithoutBrand(standardName, explicitBrand)
            );
        }

        String catalogName = optionalText(rawCatalogName);
        if (catalogName == null) {
            return new DisplayNameParts(null, standardName);
        }
        for (int index = 1; index < standardName.length(); index++) {
            if (!isBrandSeparator(standardName.charAt(index - 1))) {
                continue;
            }
            String inferredBrand = trimBrandSeparators(standardName.substring(0, index));
            String candidateProductName = trimBrandSeparators(standardName.substring(index));
            if (inferredBrand.isEmpty() || candidateProductName.isEmpty()) {
                continue;
            }
            if (catalogProductStartsWith(catalogName, candidateProductName)) {
                return new DisplayNameParts(inferredBrand, candidateProductName);
            }
        }
        return new DisplayNameParts(null, standardName);
    }

    private static boolean catalogProductStartsWith(String catalogName, String productName) {
        if (!catalogName.regionMatches(true, 0, productName, 0, productName.length())) {
            return false;
        }
        if (catalogName.length() == productName.length()) {
            return true;
        }
        char next = catalogName.charAt(productName.length());
        return isBrandSeparator(next)
                || Character.isDigit(next)
                || next == '('
                || next == '[';
    }

    private static String trimBrandSeparators(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isBrandSeparator(value.charAt(start))) {
            start++;
        }
        while (end > start && isBrandSeparator(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end).trim();
    }

    private static final class DisplayNameParts {
        private final String brand;
        private final String productName;

        private DisplayNameParts(String brand, String productName) {
            this.brand = brand;
            this.productName = productName;
        }
    }

    private static boolean isBrandSeparator(char value) {
        return Character.isWhitespace(value)
                || value == '·'
                || value == '•'
                || value == '-'
                || value == '–'
                || value == '—'
                || value == ':'
                || value == '/'
                || value == '|'
                || value == '_';
    }

    private static String stringValue(Object value) {
        return value == null ? null : optionalText(String.valueOf(value));
    }

    private static Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("정수 계약 값을 읽지 못했습니다: " + value, error);
        }
    }

    private static Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("숫자 계약 값을 읽지 못했습니다: " + value, error);
        }
    }

    private static String requireUuid(String value, String label) {
        String normalized = requireText(value, label);
        try {
            return UUID.fromString(normalized).toString();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(label + "는 UUID여야 합니다.", error);
        }
    }

    private static String optionalUuid(String value, String label) {
        String normalized = optionalText(value);
        return normalized == null ? null : requireUuid(normalized, label);
    }

    private static String requireText(String value, String label) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + "이(가) 필요합니다.");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
