package com.yeonsik.fitnessapp.integration.pricetrace;

import com.yeonsik.fitnessapp.config.SupabaseConfig;

/** Separate Nutrition ownership and PT session identities; never invent a PT user ID. */
public interface DiningProposalAccount {
    boolean isDiningProposalConfigured(String ownerId);
    SupabaseConfig requireDiningProposalAccount(String ownerId) throws Exception;
    String configuredDiningProposalScope(String ownerId);
}
