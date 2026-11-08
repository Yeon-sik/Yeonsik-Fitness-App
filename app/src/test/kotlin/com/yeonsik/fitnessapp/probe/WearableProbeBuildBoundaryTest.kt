package com.yeonsik.fitnessapp.probe

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Production import reads exercise/heart rate; the diagnostic probe stays debug-only. */
class WearableProbeBuildBoundaryTest {
    private val moduleDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    private fun text(path: String): String = File(moduleDir, path).readText()

    @Test
    fun mainManifestHasReadOnlyHealthConnectDeclarations() {
        val manifest = text("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android.permission.health.READ_EXERCISE"))
        assertTrue(manifest.contains("android.permission.health.READ_HEART_RATE"))
        assertFalse(manifest.contains("android.permission.health.WRITE_"))
        assertTrue(manifest.contains("HEALTH_PERMISSIONS"))
        assertTrue(manifest.contains("ACTION_SHOW_PERMISSIONS_RATIONALE"))
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
    fun healthConnectIsAnAppDependency() {
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
    fun debugManifestDeclaresTheHealthConnectRationaleEntryPoints() {
        val manifest = text("src/debug/AndroidManifest.xml")
        assertTrue(manifest.contains("android.permission.health.READ_HEART_RATE"))
        assertTrue(manifest.contains("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"))
        assertTrue(manifest.contains("android.intent.action.VIEW_PERMISSION_USAGE"))
        assertTrue(manifest.contains("android.intent.category.HEALTH_PERMISSIONS"))
        assertTrue(manifest.contains("android.permission.START_VIEW_PERMISSION_USAGE"))
        assertTrue(manifest.contains("com.google.android.apps.healthdata"))
        assertFalse(manifest.contains("WRITE_"))
        assertFalse(manifest.contains("READ_HEALTH_DATA_IN_BACKGROUND"))
    }
}
