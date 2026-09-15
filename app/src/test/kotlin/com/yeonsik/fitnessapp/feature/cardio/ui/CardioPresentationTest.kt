package com.yeonsik.fitnessapp.feature.cardio.ui

import com.yeonsik.fitnessapp.core.ui.FitnessSemanticStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioPresentationTest {
    @Test
    fun gpsStatusUsesSemanticStateAndExplicitUnknown() {
        assertEquals(FitnessSemanticStatus.SUCCESS, cardioGpsStatusPresentation("ready").status)
        assertEquals(FitnessSemanticStatus.WARNING, cardioGpsStatusPresentation("weak").status)
        assertEquals(FitnessSemanticStatus.UNKNOWN, cardioGpsStatusPresentation("future_status").status)
        assertEquals("GPS 상태 미상", cardioGpsStatusPresentation("").label)
    }

    @Test
    fun sessionStatusDoesNotTreatUnknownAsCompleted() {
        assertEquals(FitnessSemanticStatus.INFO, cardioSessionStatusPresentation("tracking").status)
        assertEquals(FitnessSemanticStatus.WARNING, cardioSessionStatusPresentation("paused").status)
        assertEquals(FitnessSemanticStatus.UNKNOWN, cardioSessionStatusPresentation("future_status").status)
    }

    @Test
    fun unavailableMetricsRemainExplicitWhenDistanceIsInsufficient() {
        assertTrue(cardioMeasurementExplanation(10, 5.0, 2).contains("20m 이상"))
        assertEquals("미측정", cardioPaceDisplay(10, 5.0))
        assertEquals("미측정", cardioSpeedDisplay(10, 5.0))
    }

    @Test
    fun metricsUseOnlyAvailableSessionFacts() {
        assertEquals("5:00 /km", cardioPaceDisplay(300, 1_000.0))
        assertEquals("12.0 km/h", cardioSpeedDisplay(300, 1_000.0))
        assertTrue(cardioMeasurementExplanation(300, 1_000.0, 8).contains("수락된 GPS 지점 8개"))
    }

    @Test
    fun noAcceptedPointsExplainWhyMetricsAreUnavailable() {
        assertTrue(cardioMeasurementExplanation(120, 500.0, 0).contains("계산할 수 없습니다"))
        assertEquals("미측정", cardioPaceDisplay(120, Double.NaN))
        assertEquals("미측정", cardioSpeedDisplay(120, Double.NaN))
    }
}
