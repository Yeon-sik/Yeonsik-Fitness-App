package com.yeonsik.fitnessapp.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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

    /**
     * Replaces the current destination without re-adding a target already below it, e.g. an
     * in-progress session with its completion page.
     */
    public void replace(FitnessScreen screen) {
        requireScreen(screen);
        if (screen.equals(current())) {
            return;
        }
        if (!entries.isEmpty()) {
            entries.removeLast();
        }
        if (!screen.equals(current())) {
            entries.addLast(screen);
        }
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

    public ArrayList<String> savedScreenNames() {
        ArrayList<String> names = new ArrayList<>(entries.size());
        for (FitnessScreen entry : entries) {
            names.add(entry.name());
        }
        return names;
    }

    /** Restores a validated HOME-rooted stack saved by the Activity state bundle. */
    public void restoreScreenNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("navigation history is required");
        }
        ArrayDeque<FitnessScreen> restored = new ArrayDeque<>();
        for (String name : names) {
            FitnessScreen screen = FitnessScreen.valueOf(name);
            if (restored.isEmpty() && screen != FitnessScreen.HOME) {
                throw new IllegalArgumentException("navigation history must start at HOME");
            }
            if (!screen.equals(restored.peekLast())) {
                restored.addLast(screen);
            }
        }
        if (restored.isEmpty()) {
            throw new IllegalArgumentException("navigation history is empty");
        }
        entries.clear();
        entries.addAll(restored);
    }

    /** Restores a safe HOME-rooted stack after process recreation. */
    public void restoreCurrent(FitnessScreen screen) {
        requireScreen(screen);
        entries.clear();
        entries.addLast(FitnessScreen.HOME);
        if (screen != FitnessScreen.HOME) entries.addLast(screen);
    }

    private static void requireScreen(FitnessScreen screen) {
        if (screen == null) {
            throw new IllegalArgumentException("screen is required");
        }
    }
}
