package com.yeonsik.fitnessapp.feature.settings.application

import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.feature.settings.ui.SettingsConnection
import com.yeonsik.fitnessapp.integration.sync.SyncApplicationService

/** Applies settings-owned session changes to the activity-scoped application graph. */
interface SettingsSessionCoordinator {
    fun apply(connection: SettingsConnection, config: SupabaseConfig, authenticated: Boolean)

    fun applySync(result: SyncApplicationService.Result)
}
