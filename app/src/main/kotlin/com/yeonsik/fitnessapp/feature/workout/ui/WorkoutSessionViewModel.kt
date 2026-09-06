package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/** Restores only the active session identifier; session data remains in SQLite. */
class WorkoutSessionViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    var recordId: String?
        get() = savedStateHandle[KEY_RECORD_ID]
        private set(value) { savedStateHandle[KEY_RECORD_ID] = value }

    fun enter(recordId: String?) {
        this.recordId = recordId
    }

    private companion object {
        const val KEY_RECORD_ID = "workout_session.record_id"
    }
}
