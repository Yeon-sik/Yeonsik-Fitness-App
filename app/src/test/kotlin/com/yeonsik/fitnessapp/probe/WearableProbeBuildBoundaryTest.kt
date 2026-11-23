package com.yeonsik.fitnessapp.probe

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keep probe entry points and step reads in debug while the daily workout feature uses the
 * production Health Connect read permissions.
 */
class WearableProbeBuildBoundaryTest {
    private val moduleDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    private fun text(path: String): String = File(moduleDir, path).readText()

    @Test
    fun mainManifestDeclaresReadOnlyWorkoutPermissionsAndRationale() {
        val manifest = text("src/main/AndroidManifest.xml")
        val permissions = Regex("android\\.permission\\.health\\.([A-Z_]+)").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf("READ_EXERCISE", "READ_HEART_RATE", "READ_DISTANCE", "READ_ACTIVE_CALORIES_BURNED",
            "READ_HEALTH_DATA_HISTORY").sorted(), permissions.sorted())
        assertTrue(manifest.contains("HEALTH_PERMISSIONS"))
        assertEquals(1, Regex("ACTION_SHOW_PERMISSIONS_RATIONALE").findAll(manifest).count())
        assertEquals(1, Regex("android\\.intent\\.action\\.VIEW_PERMISSION_USAGE").findAll(manifest).count())
        assertTrue(manifest.contains(".integration.wearable.WearableImportActivity"))
        assertTrue(manifest.contains("android.permission.START_VIEW_PERMISSION_USAGE"))
        assertFalse(manifest.contains("probe.wearable"))
    }

    @Test
    fun qaAndReleaseSourceSetsDoNotDeclareHealthPermissions() {
        for (sourceSet in listOf("qa", "release")) {
            val manifest = File(moduleDir, "src/$sourceSet/AndroidManifest.xml")
            if (manifest.isFile) {
                assertFalse("$sourceSet manifest", manifest.readText().contains("android.permission.health."))
            }
        }
    }

    @Test
    fun healthConnectDependencyIsSharedByDailyWorkoutAndProbe() {
        val declarations = text("build.gradle").lines()
            .map { it.trim() }
            .filter { it.contains("androidx.health.connect") }
        assertEquals(1, declarations.size)
        assertTrue(declarations.single().startsWith("implementation "))
    }

    @Test
    fun mainSourcesDoNotReferenceTheProbe() {
        val offenders = File(moduleDir, "src/main").walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { file ->
                val content = file.readText()
                content.contains("probe.wearable")
            }
            .map { it.relativeTo(moduleDir).path }
            .toList()
        assertTrue("main sources must not use the debug probe: $offenders", offenders.isEmpty())
    }

    @Test
    fun debugManifestAddsOnlyTheProbeAndStepRead() {
        val manifest = text("src/debug/AndroidManifest.xml")
        assertTrue(manifest.contains(".probe.wearable.WearableProbeActivity"))
        assertTrue(manifest.contains("android.permission.health.READ_STEPS"))
        assertFalse(manifest.contains("ACTION_SHOW_PERMISSIONS_RATIONALE"))
        assertFalse(manifest.contains("WRITE_"))
        assertFalse(manifest.contains("READ_HEALTH_DATA_IN_BACKGROUND"))
    }
}
