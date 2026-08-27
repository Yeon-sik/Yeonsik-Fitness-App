package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CompositionGroupTypeTest {
    @Test
    public void exposesOnlyTheSixSupportedDiningOutGroups() {
        assertEquals(6, CompositionGroupType.values().length);
        assertEquals(CompositionGroupType.OTHER.value(),
                CompositionGroupType.normalize("additional_menu"));
        assertEquals(CompositionGroupType.OTHER.value(),
                CompositionGroupType.normalize("substitution"));
    }
}
