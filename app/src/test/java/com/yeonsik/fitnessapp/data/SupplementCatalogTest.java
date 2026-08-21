package com.yeonsik.fitnessapp.data;

import com.yeonsik.fitnessapp.supplement.SupplementCatalog;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class SupplementCatalogTest {
    @Test
    public void catalogContainsOnlyUniqueBrandNeutralKinds() {
        Set<String> codes = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (SupplementCatalog.Kind kind : SupplementCatalog.KINDS) {
            assertFalse(kind.code.trim().isEmpty());
            assertFalse(kind.name.trim().isEmpty());
            codes.add(kind.code);
            names.add(kind.name);
        }
        assertEquals(29, SupplementCatalog.KINDS.size());
        assertEquals(SupplementCatalog.KINDS.size(), codes.size());
        assertEquals(SupplementCatalog.KINDS.size(), names.size());
        assertEquals("크레아틴", SupplementCatalog.require("creatine").name);
    }
}
