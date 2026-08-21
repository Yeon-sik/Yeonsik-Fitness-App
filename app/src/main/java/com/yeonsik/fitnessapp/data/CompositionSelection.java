package com.yeonsik.fitnessapp.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A resolved selection against a {@link CompositionTemplate}. */
public final class CompositionSelection {
    public final CompositionTemplate template;
    public final List<CompositionMember> fixedMembers;
    public final Map<String, List<CompositionMember>> selectedMembersByGroup;

    public CompositionSelection(
            CompositionTemplate template,
            List<CompositionMember> fixedMembers,
            Map<String, List<CompositionMember>> selectedMembersByGroup
    ) {
        if (template == null) {
            throw new IllegalArgumentException("Composition template is required.");
        }
        this.template = template;
        this.fixedMembers = immutableList(fixedMembers);
        Map<String, List<CompositionMember>> copied = new LinkedHashMap<>();
        if (selectedMembersByGroup != null) {
            for (Map.Entry<String, List<CompositionMember>> entry : selectedMembersByGroup.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().trim();
                if (key.isEmpty()) {
                    throw new IllegalArgumentException("Composition selection group key is required.");
                }
                copied.put(key, immutableList(entry.getValue()));
            }
        }
        this.selectedMembersByGroup = Collections.unmodifiableMap(copied);
        validate();
    }

    public NutritionProfile nutritionProfile() {
        NutritionTotals.Builder totals = NutritionTotals.builder();
        for (CompositionMember member : fixedMembers) {
            totals.add(member.profile);
        }
        for (List<CompositionMember> members : selectedMembersByGroup.values()) {
            for (CompositionMember member : members) {
                totals.add(member.profile);
            }
        }
        return totals.build().toCompleteProfile();
    }

    private void validate() {
        for (CompositionGroup group : template.groups) {
            List<CompositionMember> selected = selectedMembersByGroup.get(group.key);
            List<String> ids = new ArrayList<>();
            if (selected != null) {
                for (CompositionMember member : selected) {
                    ids.add(member.id);
                }
            }
            if (!group.isSatisfiedBy(ids)) {
                throw new IllegalArgumentException(
                        "Composition group selection is outside its limits: " + group.key
                );
            }
        }
    }

    private static List<CompositionMember> immutableList(List<CompositionMember> source) {
        List<CompositionMember> copied = new ArrayList<>();
        if (source != null) {
            for (CompositionMember member : source) {
                if (member == null) {
                    throw new IllegalArgumentException("Composition selection contains an empty member.");
                }
                copied.add(member);
            }
        }
        return Collections.unmodifiableList(copied);
    }
}
