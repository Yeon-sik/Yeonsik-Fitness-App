package com.yeonsik.fitnessapp.config;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.util.Arrays;

/** Keeps the launcher icon aligned with the theme already resolved by the UI. */
public final class LauncherIconController {
    private LauncherIconController() { }

    public static void synchronize(Context context, boolean dark) {
        try {
            synchronize(new AndroidComponents(context), dark);
        } catch (RuntimeException error) {
            // An OEM launcher failure must not interrupt an active workout or settings change.
            Log.w("LauncherIcon", "Could not update launcher icon", error);
        }
    }

    static void synchronize(Components components, boolean dark) {
        boolean selectedEnabled = components.isEnabled(dark);
        boolean otherEnabled = components.isEnabled(!dark);
        if (selectedEnabled && !otherEnabled) return;

        if (components.supportsAtomicSwitch()) {
            components.switchAtomically(dark);
        } else {
            // Older Android versions must enable the destination before disabling the old icon.
            if (!selectedEnabled) components.setEnabled(dark, true);
            if (otherEnabled) components.setEnabled(!dark, false);
        }
    }

    interface Components {
        boolean isEnabled(boolean dark);
        boolean supportsAtomicSwitch();
        void switchAtomically(boolean dark);
        void setEnabled(boolean dark, boolean enabled);
    }

    private static final class AndroidComponents implements Components {
        private final PackageManager manager;
        private final ComponentName darkLauncher;
        private final ComponentName lightLauncher;

        AndroidComponents(Context context) {
            manager = context.getPackageManager();
            // Alias class names use the namespace, which also works with applicationId suffixes.
            darkLauncher = new ComponentName(context.getPackageName(), "com.yeonsik.fitnessapp.DarkLauncher");
            lightLauncher = new ComponentName(context.getPackageName(), "com.yeonsik.fitnessapp.LightLauncher");
        }

        private ComponentName component(boolean dark) {
            return dark ? darkLauncher : lightLauncher;
        }

        public boolean isEnabled(boolean dark) {
            int state = manager.getComponentEnabledSetting(component(dark));
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && dark);
        }

        public boolean supportsAtomicSwitch() {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
        }

        @android.annotation.TargetApi(33)
        public void switchAtomically(boolean dark) {
            manager.setComponentEnabledSettings(Arrays.asList(
                    new PackageManager.ComponentEnabledSetting(component(dark),
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP),
                    new PackageManager.ComponentEnabledSetting(component(!dark),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            ));
        }

        public void setEnabled(boolean dark, boolean enabled) {
            manager.setComponentEnabledSetting(component(dark), enabled
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }
}
