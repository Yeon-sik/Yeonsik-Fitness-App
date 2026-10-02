package com.yeonsik.fitnessapp.feature.records.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordsMealPreviewTest {
    @Test fun missingOrInvalidAmountsStayDistinctFromZero() {
        assertEquals("미기록", recordsMealGrams(null))
        assertEquals("미기록", recordsMealGrams(Double.NaN))
        assertEquals("미기록", recordsMealGrams(Double.POSITIVE_INFINITY))
        assertEquals("미기록", recordsMealGrams(-1.0))
        assertEquals("0g", recordsMealGrams(0.0))
    }

    @Test fun amountsShowGramsWithAtMostOneDecimal() {
        assertEquals("25g", recordsMealGrams(25.0))
        assertEquals("33.4g", recordsMealGrams(33.4))
        assertEquals("12.3g", recordsMealGrams(12.34))
    }
}
