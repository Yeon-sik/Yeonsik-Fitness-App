package com.yeonsik.fitnessapp.feature.development.api

import com.yeonsik.fitnessapp.development.DevelopmentReport
import java.time.LocalDate

/** Read port for the development report projection. */
interface DevelopmentRepositoryApi {
    fun buildReport(referenceDate: LocalDate): DevelopmentReport
}
