package com.yeonsik.fitnessapp.ui

import android.os.Build
import androidx.test.core.app.ActivityScenario
import com.yeonsik.fitnessapp.MainActivity
import com.yeonsik.fitnessapp.state.FitnessScreen
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Only navigation/ViewModel state is changed; never create or modify a workout. */
class RecordingNavigationRecreationTest {
    @Before fun emulatorOnly() {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"))
    }

    @Test fun recordingEntryScreensRecreateWithoutAnActiveWorkout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            listOf(FitnessScreen.STRENGTH, FitnessScreen.MEALS, FitnessScreen.CARDIO).forEach { screen ->
                scenario.onActivity {
                    assertNull(it.workoutSessionViewModel().activeRecordId())
                    it.navigate(screen)
                }
                scenario.recreate()
                scenario.onActivity {
                    assertEquals(screen, it.currentScreen())
                    assertNull(it.workoutSessionViewModel().activeRecordId())
                }
            }
        }
    }

    @Test fun existingSelectedRecordIsStillRestored() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.workoutSessionViewModel().rememberActiveRecord("recreation-fixture") }
            scenario.recreate()
            scenario.onActivity {
                assertEquals("recreation-fixture", it.workoutSessionViewModel().activeRecordId())
                it.workoutSessionViewModel().clearActiveRecordIfMatches("recreation-fixture")
            }
        }
    }
}
