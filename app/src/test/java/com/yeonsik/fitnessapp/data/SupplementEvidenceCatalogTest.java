package com.yeonsik.fitnessapp.data;

import com.yeonsik.fitnessapp.supplement.SupplementCatalog;
import com.yeonsik.fitnessapp.supplement.SupplementEvidence;
import com.yeonsik.fitnessapp.supplement.SupplementEvidenceCatalog;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SupplementEvidenceCatalogTest {
    @Test
    public void everySelectableKindHasAnEvidenceBoundary() {
        assertEquals(SupplementCatalog.KINDS.size(), SupplementEvidenceCatalog.all().size());
        for (SupplementCatalog.Kind kind : SupplementCatalog.KINDS) {
            SupplementEvidence evidence = SupplementEvidenceCatalog.forType(kind.code);
            assertEquals(kind.code, evidence.supplementTypeCode);
            assertFalse(evidence.summaryKo.isEmpty());
            assertFalse(evidence.limitationsKo.isEmpty());
            assertFalse(evidence.safetyKo.isEmpty());
            assertFalse(evidence.sources.isEmpty());
        }
    }

    @Test
    public void onlyReviewedPerformanceKindsClaimDirectEvidence() {
        Set<String> direct = new HashSet<>();
        for (SupplementCatalog.Kind kind : SupplementCatalog.KINDS) {
            if (SupplementEvidenceCatalog.forType(kind.code).status
                    == SupplementEvidence.Status.VERIFIED_DIRECT) {
                direct.add(kind.code);
            }
        }
        assertEquals(3, direct.size());
        assertTrue(direct.contains("creatine"));
        assertTrue(direct.contains("caffeine"));
        assertTrue(direct.contains("beta_alanine"));
    }

    @Test
    public void directEvidenceKeepsStableOriginalSourceIdentifiers() {
        SupplementEvidence creatine = SupplementEvidenceCatalog.forType("creatine");
        assertEquals("08#3", creatine.sources.get(0).evidenceId);
        assertTrue(creatine.sources.get(0).url.contains("28615996"));
        assertEquals("08#4", creatine.sources.get(1).evidenceId);
        assertTrue(creatine.sources.get(1).url.contains("37432300"));

        SupplementEvidence caffeine = SupplementEvidenceCatalog.forType("caffeine");
        assertTrue(caffeine.sources.get(0).url.contains("30926628"));
        assertTrue(caffeine.sources.get(1).url.contains("33388079"));

        SupplementEvidence betaAlanine = SupplementEvidenceCatalog.forType("beta_alanine");
        assertTrue(betaAlanine.sources.get(0).url.contains("26175657"));
    }
}
