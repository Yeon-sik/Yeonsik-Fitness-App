package com.yeonsik.fitness.shared.feature.body.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BodyProfileTest {
    @Test
    fun emptyProfileKeepsUnconfiguredAndEmptyTimestampSemantics() {
        val profile = BodyProfile.empty()
        assertFalse(profile.isConfigured())
        assertEquals("미설정", profile.heightLabelKo())
        assertEquals("", profile.createdAt)
        assertEquals("", profile.updatedAt)
    }

    @Test
    fun configuredProfileNormalizesTimestampsAndLabelsHeight() {
        val profile = BodyProfile(178, "  created  ", null)
        assertTrue(profile.isConfigured())
        assertEquals("178cm", profile.heightLabelKo())
        assertEquals("created", profile.createdAt)
        assertEquals("", profile.updatedAt)
    }

    @Test
    fun profileRejectsOutOfRangeHeight() {
        assertFailsWith<IllegalArgumentException> { BodyProfile(49, "", "") }
        assertFailsWith<IllegalArgumentException> { BodyProfile(301, "", "") }
    }
}
