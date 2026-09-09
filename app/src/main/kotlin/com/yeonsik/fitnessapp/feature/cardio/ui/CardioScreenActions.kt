package com.yeonsik.fitnessapp.feature.cardio.ui

import com.yeonsik.fitnessapp.cardio.CardioActivityType

/** Platform and route actions supplied to cardio composables by app navigation. */
internal interface CardioScreenActions {
    fun start(activityType: CardioActivityType)
    fun back()
    fun refresh()
    fun pause()
    fun resume()
    fun editAverageHeartRate()
    fun finish()
    fun cancel()
    fun loadRoute(recordId: String)
}
