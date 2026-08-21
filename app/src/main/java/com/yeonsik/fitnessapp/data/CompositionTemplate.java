package com.yeonsik.fitnessapp.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reusable definition for a composed meal/menu.
 *
 * <p>A template is mutable product data. A recorded meal must copy its resolved members into
 * {@link MealItemSnapshot}; changing a template must never rewrite a historical meal.</p>
 */
public final class CompositionTemplate {
    public static final String CONTRACT_VERSION = "composition-template.v1";
    public static final String KIND_DINING_OUT = "dining_out";
    public static final String KIND_MEAL_PRESET = "meal_preset";
    public static final String KIND_RECIPE = "recipe";
    public static final String KIND_MEAL = "meal";

    public final String id;
    public final String userId;
    public final String name;
    public final String kind;
    public final String rootFoodId;
    public final String sourceReference;
    public final int revision;
    public final List<CompositionGroup> groups;

    public CompositionTemplate(
            String id,
            String userId,
            String name,
            String kind,
            String rootFoodId,
            String sourceReference,
            int revision,
            List<CompositionGroup> groups
    ) {
        this.id = requireText(id, "Composition template id");
        this.userId = requireText(userId, "Composition template user id");
        this.name = requireText(name, "Composition template name");
        this.kind = requireText(kind, "Composition template kind");
        this.rootFoodId = blankToNull(rootFoodId);
        this.sourceReference = blankToNull(sourceReference);
        if (revision <= 0) {
            throw new IllegalArgumentException("Composition template revision must be positive.");
        }
        this.revision = revision;
        List<CompositionGroup> copied = new ArrayList<>();
        Set<String> groupKeys = new HashSet<>();
        if (groups != null) {
            for (CompositionGroup group : groups) {
                if (group == null || !groupKeys.add(group.key)) {
                    throw new IllegalArgumentException("Composition template contains duplicate group keys.");
                }
                copied.add(group);
            }
        }
        this.groups = Collections.unmodifiableList(copied);
    }

    public boolean hasSelectableGroups() {
        return !groups.isEmpty();
    }

    private static String requireText(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
