package com.yeonsik.fitnessapp.integration.sync;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import java.util.function.Consumer;

/** The settings screen schedules one manual sync without owning its network dependencies. */
@FunctionalInterface
public interface ManualSyncRunner {
    SyncApplicationService.Result run(
            SupabaseConfig shared, SupabaseConfig nutrition, Consumer<String> progress
    ) throws Exception;
}
