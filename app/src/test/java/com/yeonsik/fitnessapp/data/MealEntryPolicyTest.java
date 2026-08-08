package com.yeonsik.fitnessapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

public final class MealEntryPolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 9);

    @Test
    public void labelsFollowInputOrderWithoutATypeLimit() {
        assertEquals("1끼", MealEntryPolicy.labelForIndex(0));
        assertEquals("2끼", MealEntryPolicy.labelForIndex(1));
        assertEquals("4끼", MealEntryPolicy.labelForIndex(3));
        assertEquals("10000끼", MealEntryPolicy.labelForIndex(9999));
    }

    @Test
    public void todayAndPastDatesAreAccepted() {
        LocalDate today = MealEntryPolicy.requireRecordDate("2026-08-09", TODAY);
        LocalDate past = MealEntryPolicy.requireRecordDate("2026-07-31", TODAY);

        assertFalse(MealEntryPolicy.isBackfilled(today, TODAY));
        assertTrue(MealEntryPolicy.isBackfilled(past, TODAY));
    }

    @Test
    public void emptyDateDefaultsToToday() {
        assertEquals(TODAY, MealEntryPolicy.requireRecordDate("", TODAY));
    }

    @Test
    public void futureDateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MealEntryPolicy.requireRecordDate("2026-08-10", TODAY));
    }
}
