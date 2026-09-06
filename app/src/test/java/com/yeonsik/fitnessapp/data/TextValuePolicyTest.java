package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TextValuePolicyTest {
    @Test
    public void treatsBlankAndLiteralNullAsMissing() {
        assertTrue(TextValuePolicy.isMissing(null));
        assertTrue(TextValuePolicy.isMissing("  "));
        assertTrue(TextValuePolicy.isMissing(" NULL "));
        assertFalse(TextValuePolicy.isMissing("memo"));
    }
}
