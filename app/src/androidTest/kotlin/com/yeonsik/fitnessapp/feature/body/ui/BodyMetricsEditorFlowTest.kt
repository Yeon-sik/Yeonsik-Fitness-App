package com.yeonsik.fitnessapp.feature.body.ui

import androidx.activity.ComponentActivity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.application.BodyMetricsApplicationService
import com.yeonsik.fitness.shared.feature.body.ui.BodyMetricsEditorController
import com.yeonsik.fitness.shared.feature.body.ui.BodyMetricsEditorDialog
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.settings.DisplayPreferences
import com.yeonsik.fitness.shared.feature.settings.DisplaySettings
import com.yeonsik.fitnessapp.config.MassUnitPreferences
import com.yeonsik.fitnessapp.config.ThemeModePreferences
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.feature.body.data.BodyMetricsRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BodyMetricsEditorFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun existingRoomRecordKeepsItsIdentityAndCancelDoesNotWrite() {
        val room = Room.inMemoryDatabaseBuilder(
            compose.activity.applicationContext, FitnessRoomDatabase::class.java
        ).build()
        try {
            val account = AccountScope("body-editor-test-owner")
            val date = "2026-09-20"
            val repository = BodyMetricsRepository(room, account.ownerId)
            val id = repository.addBodyMetric(account, date, 70.5, "original memo")
            val service = BodyMetricsApplicationService(repository, account.ownerId)

            compose.setContent {
                val scope = rememberCoroutineScope()
                val editor = remember { BodyMetricsEditorController(service, scope) }
                FitnessComposeTheme(dark = false) {
                    TextButton(onClick = { editor.open(account, date, id) }) { Text("체중 기록") }
                    BodyMetricsEditorDialog(
                        controller = editor, account = account, unit = MassUnit.KG,
                        formatInput = MassFormatter::formatInput,
                        onRecordsChanged = {}, notify = {}
                    )
                }
            }

            openWeightEditor()
            compose.onNodeWithTag("weight-input").assertTextContains("70.5")
            compose.onNodeWithTag("weight-date").performClick()
            compose.onNodeWithTag("weight-date-confirm").assertExists()
            compose.onNodeWithTag("weight-date-cancel").performClick()
            compose.onNodeWithTag("weight-date-confirm").assertDoesNotExist()
            compose.onNodeWithTag("weight-input").assertExists()
            compose.onNodeWithTag("weight-date").performClick()
            pressBack()
            compose.onNodeWithTag("weight-date-confirm").assertDoesNotExist()
            compose.onNodeWithTag("weight-input").assertExists()
            compose.onNodeWithTag("weight-cancel").performClick()
            compose.onNodeWithTag("weight-input").assertDoesNotExist()
            assertEquals(70.5, repository.bodyMetricEntryById(account, id)?.weightKg ?: -1.0, 0.001)

            openWeightEditor()
            compose.onNodeWithTag("weight-input").performTextReplacement("72.5")
            compose.onNodeWithTag("weight-save").performClick()
            compose.waitUntil(5_000) { repository.bodyMetricEntryById(account, id)?.weightKg == 72.5 }
            val entries = repository.bodyMetricEntriesForDate(account, date)
            assertEquals(listOf(id), entries.map { it.id })
            assertEquals("original memo", entries.single().memo)

            openWeightEditor()
            compose.onNodeWithTag("weight-delete").performClick()
            compose.waitUntil(5_000) { repository.bodyMetricEntryById(account, id) == null }
        } finally {
            room.close()
        }
    }

    @Test
    fun storedPoundsAndThemeSurviveReentryAndRoundedFieldsKeepTheSameRecord() {
        val base = compose.activity.applicationContext
        val file = "parity-display-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int) = base.getSharedPreferences(file, mode)
        }
        val room = Room.inMemoryDatabaseBuilder(base, FitnessRoomDatabase::class.java).build()
        try {
            val account = AccountScope("display-owner")
            val date = "2026-09-20"
            val repository = BodyMetricsRepository(room, account.ownerId)
            val service = BodyMetricsApplicationService(repository, account.ownerId)
            val units = MassUnitPreferences(context)
            val themes = ThemeModePreferences(context)
            compose.setContent {
                var unit by remember { mutableStateOf(units.preferredMassUnit()) }
                var theme by remember { mutableStateOf(themes.themeMode()) }
                val scope = rememberCoroutineScope()
                val editor = remember { BodyMetricsEditorController(service, scope) }
                FitnessComposeTheme(DisplayPreferences.theme(theme).isDark(false)) {
                    Column {
                        DisplaySettings(unit, theme,
                            { units.setPreferredMassUnit(it); unit = units.preferredMassUnit() },
                            { themes.setThemeMode(it); theme = themes.themeMode() })
                        TextButton(onClick = { editor.open(account, date, null) }) { Text("체중 기록") }
                    }
                    BodyMetricsEditorDialog(editor, account, unit, MassFormatter::formatInput, {}, {})
                }
            }
            compose.onNodeWithText("파운드").performClick()
            compose.onNodeWithText("밝게").performClick()
            assertEquals(MassUnit.LB, MassUnitPreferences(context).preferredMassUnit())
            assertEquals("light", ThemeModePreferences(context).themeMode())
            openWeightEditor()
            compose.onNodeWithTag("weight-input").assertTextContains("체중 lb").performTextReplacement("225")
            compose.onNodeWithTag("weight-save").performClick()
            compose.waitUntil(5_000) { repository.bodyMetricForDate(account, date) != null }
            val original = checkNotNull(repository.bodyMetricForDate(account, date))
            assertEquals(102.05828325, original.weightKg, 1e-10)
            compose.onNodeWithText("킬로그램").performClick()
            compose.onNodeWithText("기기 설정").performClick()
            assertEquals("system", ThemeModePreferences(context).themeMode())
            openWeightEditor()
            compose.onNodeWithTag("weight-input").assertTextContains("102.06")
            compose.onNodeWithTag("weight-save").performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("weight-input")).fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf(original), repository.bodyMetricEntriesForDate(account, date))
        } finally {
            room.close()
            base.deleteSharedPreferences(file)
        }
    }

    private fun openWeightEditor() {
        compose.onNodeWithText("체중 기록").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("weight-input"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

}
