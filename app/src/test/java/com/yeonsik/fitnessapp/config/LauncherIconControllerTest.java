package com.yeonsik.fitnessapp.config;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class LauncherIconControllerTest {
    @Test public void darkDefaultDoesNotWriteComponentOverrides() {
        FakeComponents state = new FakeComponents(false);
        LauncherIconController.synchronize(state, true);
        assertTrue(state.dark);
        assertFalse(state.light);
        assertTrue(state.operations.isEmpty());
    }

    @Test public void legacySwitchesBothWaysWithoutRemovingAllLaunchers() {
        FakeComponents state = new FakeComponents(false);
        LauncherIconController.synchronize(state, false);
        assertEquals(List.of("light:true", "dark:false"), state.operations);
        assertFalse(state.dark);
        assertTrue(state.light);
        state.operations.clear();
        LauncherIconController.synchronize(state, true);
        assertEquals(List.of("dark:true", "light:false"), state.operations);
        assertTrue(state.dark);
        assertFalse(state.light);
        LauncherIconController.synchronize(state, true);
        assertEquals(2, state.operations.size());
    }

    @Test public void modernSwitchesAreAtomicAndIdempotent() {
        FakeComponents state = new FakeComponents(true);
        LauncherIconController.synchronize(state, false);
        LauncherIconController.synchronize(state, false);
        LauncherIconController.synchronize(state, true);
        assertEquals(List.of("atomic:light", "atomic:dark"), state.operations);
        assertTrue(state.dark);
        assertFalse(state.light);
    }

    @Test public void startupRestoresWhiteAndRepairsDuplicateIcons() {
        FakeComponents state = new FakeComponents(false);
        state.light = true;
        LauncherIconController.synchronize(state, false);
        assertEquals(List.of("dark:false"), state.operations);
        assertFalse(state.dark);
        assertTrue(state.light);
    }

    @Test public void recoversIfBothAliasesWereDisabled() {
        for (boolean atomic : new boolean[]{false, true}) {
            FakeComponents state = new FakeComponents(atomic);
            state.dark = false;
            LauncherIconController.synchronize(state, true);
            assertTrue(state.dark);
            assertFalse(state.light);
        }
    }

    @Test public void failedEnableKeepsTheOriginalLauncher() {
        FakeComponents state = new FakeComponents(false);
        state.fail = "light:true";
        assertThrows(SecurityException.class, () -> LauncherIconController.synchronize(state, false));
        assertTrue(state.dark);
        assertFalse(state.light);
        assertEquals(List.of("light:true"), state.operations);
    }

    @Test public void failedDisableKeepsTheDestinationLaunchableAndCanRetry() {
        FakeComponents state = new FakeComponents(false);
        state.fail = "dark:false";
        assertThrows(SecurityException.class, () -> LauncherIconController.synchronize(state, false));
        assertTrue(state.dark);
        assertTrue(state.light);
        state.fail = null;
        LauncherIconController.synchronize(state, false);
        assertFalse(state.dark);
        assertTrue(state.light);
    }

    @Test public void failedAtomicUpdateKeepsTheOriginalLauncher() {
        FakeComponents state = new FakeComponents(true);
        state.fail = "atomic:light";
        assertThrows(SecurityException.class, () -> LauncherIconController.synchronize(state, false));
        assertTrue(state.dark);
        assertFalse(state.light);
    }

    private static final class FakeComponents implements LauncherIconController.Components {
        boolean dark = true;
        boolean light;
        final boolean atomic;
        String fail;
        final List<String> operations = new ArrayList<>();

        FakeComponents(boolean atomic) { this.atomic = atomic; }
        public boolean isEnabled(boolean darkIcon) { return darkIcon ? dark : light; }
        public boolean supportsAtomicSwitch() { return atomic; }

        private void operation(String value) {
            operations.add(value);
            if (value.equals(fail)) throw new SecurityException("component change rejected");
        }

        public void switchAtomically(boolean darkIcon) {
            operation("atomic:" + (darkIcon ? "dark" : "light"));
            dark = darkIcon;
            light = !darkIcon;
        }

        public void setEnabled(boolean darkIcon, boolean enabled) {
            operation((darkIcon ? "dark" : "light") + ":" + enabled);
            if (darkIcon) dark = enabled; else light = enabled;
            assertTrue("At least one launcher must remain enabled", dark || light);
        }
    }
}
