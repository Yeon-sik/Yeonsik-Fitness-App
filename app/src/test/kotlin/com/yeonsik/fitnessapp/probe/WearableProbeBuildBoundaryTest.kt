package com.yeonsik.fitnessapp.probe

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source contract for K-01: the wearable PR0 probe, its Health Connect dependency and its health
 * permissions exist only in the debug build. qa and release must stay free of them until a later
 * PR adds a reviewed production integration.
 */
class WearableProbeBuildBoundaryTest {
    private val moduleDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    private fun text(path: String): String = File(moduleDir, path).readText()

    @Test
    fun mainManifestHasNoHealthConnectDeclarations() {
        val manifest = text("src/main/AndroidManifest.xml")
        assertFalse(manifest.contains("android.permission.health."))
        assertFalse(manifest.contains("HEALTH_PERMISSIONS"))
        assertFalse(manifest.contains("ACTION_SHOW_PERMISSIONS_RATIONALE"))
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
    fun healthConnectIsADebugOnlyDependency() {
        val declarations = text("build.gradle").lines()
            .map { it.trim() }
            .filter { it.contains("androidx.health.connect") }
        assertEquals(1, declarations.size)
        assertTrue(declarations.single().startsWith("debugImplementation "))
    }

    @Test
    fun mainSourcesDoNotReferenceTheProbeOrHealthConnect() {
        val offenders = File(moduleDir, "src/main").walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { file ->
                val content = file.readText()
                content.contains("androidx.health.connect") || content.contains("probe.wearable")
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
