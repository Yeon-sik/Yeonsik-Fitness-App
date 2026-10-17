package com.yeonsik.fitnessapp.feature.development.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.development.DevelopmentReport
import com.yeonsik.fitnessapp.development.PaperAdviceAssessment
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentTrainingOverview
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentGuidance
import java.time.LocalDate

/** Public report read boundary; report assembly is not a storage repository concern. */
interface DevelopmentReportApi {
    fun buildReport(scope: AccountScope, referenceDate: LocalDate): DevelopmentReport
    fun buildTrainingOverview(scope: AccountScope, referenceDate: LocalDate): DevelopmentTrainingOverview =
        DevelopmentTrainingOverview.empty(referenceDate)
    fun buildGuidance(scope: AccountScope, referenceDate: LocalDate,
                      training: DevelopmentTrainingOverview): DevelopmentGuidance = DevelopmentGuidance(referenceDate)
    fun buildPaperAdviceAssessment(
        scope: AccountScope,
        referenceDate: LocalDate
    ): PaperAdviceAssessment
}
