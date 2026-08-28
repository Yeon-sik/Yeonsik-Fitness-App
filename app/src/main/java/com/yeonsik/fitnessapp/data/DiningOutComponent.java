package com.yeonsik.fitnessapp.data;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Canonical model for one reusable dining-out component.
 *
 * <p>{@link DiningOutOption} remains as the source-compatible name used by older callers. The
 * provision type describes one actual meal selection and is intentionally not part of a
 * menu-to-component possibility link.</p>
 */
public final class DiningOutComponent extends DiningOutOption {
    public DiningOutComponent(
            String name,
            NutritionProfile profile,
            String catalogFoodId,
            String sourceReference,
            String groupKey,
            String groupType,
            String groupLabel,
            String role,
            String memberId,
            String provisionType,
            double consumedFraction
    ) {
        super(
                name,
                profile,
                catalogFoodId,
                sourceReference,
                groupKey,
                groupType,
                groupLabel,
                role,
                memberId,
                provisionType,
                consumedFraction
        );
    }

    public DiningOutComponent(
            String name,
            NutritionProfile profile,
            String catalogFoodId,
            String sourceReference,
            String groupKey,
            String groupType,
            String groupLabel,
            String role,
            String memberId,
            double consumedFraction
    ) {
        this(
                name,
                profile,
                catalogFoodId,
                sourceReference,
                groupKey,
                groupType,
                groupLabel,
                role,
                memberId,
                DEFAULT_PROVISION_TYPE,
                consumedFraction
        );
    }

    public static DiningOutComponent fromOption(DiningOutOption option) {
        if (option == null) {
            throw new IllegalArgumentException("Dining-out component is required.");
        }
        if (option instanceof DiningOutComponent) {
            return (DiningOutComponent) option;
        }
        return new DiningOutComponent(
                option.name,
                option.profile,
                option.catalogFoodId,
                option.sourceReference,
                option.groupKey,
                option.groupType,
                option.groupLabel,
                option.role,
                option.memberId,
                option.provisionType,
                option.consumedFraction
        );
    }

    public static DiningOutComponent descriptive(String name) {
        return new DiningOutComponent(
                name,
                NutritionProfile.empty(),
                null,
                null,
                DEFAULT_GROUP_KEY,
                DEFAULT_GROUP_TYPE,
                DEFAULT_GROUP_LABEL,
                DEFAULT_ROLE,
                null,
                DEFAULT_PROVISION_TYPE,
                1d
        );
    }

    public static DiningOutComponent descriptive(String name, String provisionType) {
        DiningOutComponent component = descriptive(name);
        return component.withProvisionType(provisionType);
    }

    public static DiningOutComponent withProfile(String name, NutritionProfile profile) {
        return new DiningOutComponent(
                name,
                profile,
                null,
                null,
                DEFAULT_GROUP_KEY,
                DEFAULT_GROUP_TYPE,
                DEFAULT_GROUP_LABEL,
                DEFAULT_ROLE,
                null,
                DEFAULT_PROVISION_TYPE,
                1d
        );
    }

    public static DiningOutComponent withProfile(
            String name,
            NutritionProfile profile,
            String provisionType
    ) {
        return new DiningOutComponent(
                name,
                profile,
                null,
                null,
                DEFAULT_GROUP_KEY,
                DEFAULT_GROUP_TYPE,
                DEFAULT_GROUP_LABEL,
                DEFAULT_ROLE,
                null,
                provisionType,
                1d
        );
    }

    public static DiningOutComponent fromFood(NutritionFood food) {
        return fromFood(food, DEFAULT_PROVISION_TYPE);
    }

    public static DiningOutComponent fromFood(NutritionFood food, String provisionType) {
        if (food == null) {
            throw new IllegalArgumentException("Component food is required.");
        }
        JSONObject source = sourceObject(food.sourceReference);
        return new DiningOutComponent(
                food.name,
                food.profile,
                food.id,
                food.sourceReference,
                sourceText(source, "composition_group_key", DEFAULT_GROUP_KEY),
                sourceText(source, "composition_group_type", null),
                sourceText(source, "composition_group_label", DEFAULT_GROUP_LABEL),
                sourceText(source, "composition_role", DEFAULT_ROLE),
                sourceText(source, "composition_member_id", null),
                provisionType,
                1d
        );
    }

    public static DiningOutComponent grouped(
            String name,
            NutritionProfile profile,
            String catalogFoodId,
            String sourceReference,
            String groupKey,
            String groupLabel,
            String role,
            String memberId
    ) {
        return grouped(
                name,
                profile,
                catalogFoodId,
                sourceReference,
                groupKey,
                null,
                groupLabel,
                role,
                memberId,
                DEFAULT_PROVISION_TYPE,
                1d
        );
    }

    public static DiningOutComponent grouped(
            String name,
            NutritionProfile profile,
            String catalogFoodId,
            String sourceReference,
            String groupKey,
            String groupLabel,
            String role,
            String memberId,
            double consumedFraction
    ) {
        return grouped(
                name,
                profile,
                catalogFoodId,
                sourceReference,
                groupKey,
                null,
                groupLabel,
                role,
                memberId,
                DEFAULT_PROVISION_TYPE,
                consumedFraction
        );
    }

    public static DiningOutComponent grouped(
            String name,
            NutritionProfile profile,
            String catalogFoodId,
            String sourceReference,
            String groupKey,
            String groupType,
            String groupLabel,
            String role,
            String memberId
    ) {
        return grouped(
                name,
                profile,
                catalogFoodId,
                sourceReference,
                groupKey,
                groupType,
                groupLabel,
                role,
                memberId,
                DEFAULT_PROVISION_TYPE,
                1d
        );
    }

    public static DiningOutComponent grouped(
            String name,
            NutritionProfile profile,
            String catalogFoodId,
            String sourceReference,
            String groupKey,
            String groupType,
            String groupLabel,
            String role,
            String memberId,
            double consumedFraction
    ) {
        return grouped(
                name,
                profile,
                catalogFoodId,
                sourceReference,
                groupKey,
                groupType,
                groupLabel,
                role,
                memberId,
                DEFAULT_PROVISION_TYPE,
                consumedFraction
        );
    }

    public static DiningOutComponent grouped(
            String name,
            NutritionProfile profile,
            String catalogFoodId,
            String sourceReference,
            String groupKey,
            String groupType,
            String groupLabel,
            String role,
            String memberId,
            String provisionType
    ) {
        return grouped(
                name,
                profile,
                catalogFoodId,
                sourceReference,
                groupKey,
                groupType,
                groupLabel,
                role,
                memberId,
                provisionType,
                1d
        );
    }

    public static DiningOutComponent grouped(
            String name,
            NutritionProfile profile,
            String catalogFoodId,
            String sourceReference,
            String groupKey,
            String groupType,
            String groupLabel,
            String role,
            String memberId,
            String provisionType,
            double consumedFraction
    ) {
        return new DiningOutComponent(
                name,
                profile,
                catalogFoodId,
                sourceReference,
                groupKey,
                groupType,
                groupLabel,
                role,
                memberId,
                provisionType,
                consumedFraction
        );
    }

    @Override
    public DiningOutComponent withConsumedFraction(double fraction) {
        return new DiningOutComponent(
                name,
                profile,
                catalogFoodId,
                sourceReference,
                groupKey,
                groupType,
                groupLabel,
                role,
                memberId,
                provisionType,
                fraction
        );
    }

    @Override
    public DiningOutComponent withProvisionType(String type) {
        return new DiningOutComponent(
                name,
                profile,
                catalogFoodId,
                sourceReference,
                groupKey,
                groupType,
                groupLabel,
                role,
                memberId,
                type,
                consumedFraction
        );
    }

    private static JSONObject sourceObject(String sourceReference) {
        if (sourceReference == null || sourceReference.trim().isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(sourceReference);
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static String sourceText(JSONObject source, String key, String fallback) {
        if (source == null || !source.has(key) || source.isNull(key)) {
            return fallback;
        }
        String value = source.optString(key, "").trim();
        return value.isEmpty() ? fallback : value;
    }
}
