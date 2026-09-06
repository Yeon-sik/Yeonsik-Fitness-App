package com.yeonsik.fitnessapp.core.account

/**
 * Captures the owner boundary at the beginning of a user action.  Long-running
 * UI work must use this value again before publishing its result.
 */
data class AccountScope(val ownerId: String) {
    init {
        require(ownerId.isNotBlank()) { "An account scope requires an owner id." }
    }
}
