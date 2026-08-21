package com.yeonsik.fitnessapp.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A named selection group inside a reusable composition template. */
public final class CompositionGroup {
    public static final String MODE_EXACTLY_ONE = "exactly_one";
    public static final String MODE_ZERO_OR_ONE = "zero_or_one";
    public static final String MODE_OPTIONAL_MANY = "optional_many";
    public static final String MODE_QUANTITY = "quantity";

    public final String id;
    public final String key;
    public final String label;
    public final String selectionMode;
    public final int minSelected;
    public final int maxSelected;
    public final int orderIndex;
    public final List<CompositionMember> members;

    public CompositionGroup(
            String id,
            String key,
            String label,
            String selectionMode,
            int minSelected,
            int maxSelected,
            int orderIndex,
            List<CompositionMember> members
    ) {
        this.id = requireText(id, "Composition group id");
        this.key = requireText(key, "Composition group key");
        this.label = requireText(label, "Composition group label");
        this.selectionMode = requireText(selectionMode, "Composition group selection mode");
        if (!isSupportedMode(this.selectionMode)) {
            throw new IllegalArgumentException("Unsupported composition selection mode: " + selectionMode);
        }
        if (minSelected < 0 || maxSelected < minSelected) {
            throw new IllegalArgumentException("Invalid composition group selection limits.");
        }
        this.minSelected = minSelected;
        this.maxSelected = maxSelected;
        if (orderIndex < 0) {
            throw new IllegalArgumentException("Composition group order cannot be negative.");
        }
        this.orderIndex = orderIndex;
        List<CompositionMember> copied = new ArrayList<>();
        Set<String> memberIds = new HashSet<>();
        if (members != null) {
            for (CompositionMember member : members) {
                if (member == null || !memberIds.add(member.id)) {
                    throw new IllegalArgumentException("Composition group contains a duplicate member.");
                }
                copied.add(member);
            }
        }
        this.members = Collections.unmodifiableList(copied);
    }

    public static CompositionGroup optionalMany(
            String id,
            String key,
            String label,
            int orderIndex,
            List<CompositionMember> members
    ) {
        return new CompositionGroup(
                id,
                key,
                label,
                MODE_OPTIONAL_MANY,
                0,
                Math.max(99, members == null ? 0 : members.size()),
                orderIndex,
                members
        );
    }

    public boolean isSatisfiedBy(List<String> selectedMemberIds) {
        Set<String> allowedIds = new HashSet<>();
        for (CompositionMember member : members) {
            allowedIds.add(member.id);
        }
        Set<String> selected = new HashSet<>();
        if (selectedMemberIds != null) {
            for (String id : selectedMemberIds) {
                if (id != null && allowedIds.contains(id)) {
                    selected.add(id);
                }
            }
        }
        return selected.size() >= minSelected && selected.size() <= maxSelected;
    }

    public boolean isSupportedMode() {
        return isSupportedMode(selectionMode);
    }

    private static boolean isSupportedMode(String mode) {
        return MODE_EXACTLY_ONE.equals(mode)
                || MODE_ZERO_OR_ONE.equals(mode)
                || MODE_OPTIONAL_MANY.equals(mode)
                || MODE_QUANTITY.equals(mode);
    }

    private static String requireText(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return normalized;
    }
}
