package com.yeonsik.fitnessapp.feature.development.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.development.DevelopmentReport
import java.time.LocalDate

/** Public report read boundary; report assembly is not a storage repository concern. */
interface DevelopmentReportApi {
    fun buildReport(scope: AccountScope, referenceDate: LocalDate): DevelopmentReport
}
