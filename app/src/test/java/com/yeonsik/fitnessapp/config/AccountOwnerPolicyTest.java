package com.yeonsik.fitnessapp.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AccountOwnerPolicyTest {

    @Test
    public void logoutAlwaysResetsTheInMemoryOwnerToLocalUser() {
        assertEquals(
                SupabaseConfig.DEFAULT_USER_ID,
                AccountOwnerPolicy.loggedOutOwnerId()
        );
    }

    @Test
    public void localRowsAreClaimedByTheFirstAuthenticatedAccount() {
        assertTrue(AccountOwnerPolicy.shouldClaimLocalRows(
                SupabaseConfig.DEFAULT_USER_ID,
                "user-a"
        ));
    }

    @Test
    public void authenticatedRowsAreNotTransferredDuringAccountSwitch() {
        assertFalse(AccountOwnerPolicy.shouldClaimLocalRows("user-a", "user-b"));
    }

    @Test
    public void logoutDoesNotTransferAuthenticatedRowsBackToLocalUser() {
        assertFalse(AccountOwnerPolicy.shouldClaimLocalRows(
                "user-a",
                AccountOwnerPolicy.loggedOutOwnerId()
        ));
    }
}
