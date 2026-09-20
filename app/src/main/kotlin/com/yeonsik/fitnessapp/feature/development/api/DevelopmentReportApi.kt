package com.yeonsik.fitnessapp.feature.development.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.development.DevelopmentReport
import com.yeonsik.fitnessapp.development.PaperAdviceAssessment
import java.time.LocalDate

/** Public report read boundary; report assembly is not a storage repository concern. */
interface DevelopmentReportApi {
    fun buildReport(scope: AccountScope, referenceDate: LocalDate): DevelopmentReport
    fun buildPaperAdviceAssessment(
        scope: AccountScope,
        referenceDate: LocalDate
    ): PaperAdviceAssessment
}
