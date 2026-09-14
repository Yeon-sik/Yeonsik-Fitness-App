package com.yeonsik.fitnessapp.feature.workout.data

/** Shared interpretation for completed workout facts at the storage boundary. */
internal object WorkoutReadSemantics {
    fun isCompleted(sourceApp: String?, metadata: String?): Boolean {
        if (sourceApp == "os") return true
        return metadata.orEmpty().filterNot(Char::isWhitespace)
            .contains("\"status\":\"completed\"")
    }
}
