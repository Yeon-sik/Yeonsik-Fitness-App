package com.yeonsik.fitnessapp.state;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Central screen history for the Activity-owned FitnessScreen navigation.
 *
 * <p>The history stores screen identity only. Dialog state remains owned by FitnessUi and is
 * dismissed before this stack is consulted.</p>
 */
public final class FitnessNavigationHistory {
    private final Deque<FitnessScreen> entries = new ArrayDeque<>();

    public FitnessNavigationHistory(FitnessScreen initialScreen) {
        if (initialScreen == null) {
            throw new IllegalArgumentException("initialScreen is required");
        }
        entries.addLast(initialScreen);
    }

    public FitnessScreen current() {
        return entries.peekLast();
    }

    public boolean canBack() {
        return entries.size() > 1;
    }

    public void push(FitnessScreen screen) {
        requireScreen(screen);
        if (screen.equals(current())) {
            return;
        }
        entries.addLast(screen);
    }

    /** Replaces the current destination, e.g. an in-progress session with its completion page. */
    public void replace(FitnessScreen screen) {
        requireScreen(screen);
        if (!entries.isEmpty()) {
            entries.removeLast();
        }
        entries.addLast(screen);
    }

    /** Returns the previous screen, or null when the initial HOME entry is current. */
    public FitnessScreen back() {
        if (!canBack()) {
            return null;
        }
        entries.removeLast();
        return entries.peekLast();
    }

    public int size() {
        return entries.size();
    }

    private static void requireScreen(FitnessScreen screen) {
        if (screen == null) {
            throw new IllegalArgumentException("screen is required");
        }
    }
}
