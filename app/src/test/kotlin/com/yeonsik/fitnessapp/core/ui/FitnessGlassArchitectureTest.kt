package com.yeonsik.fitnessapp.core.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessGlassArchitectureTest {
    @Test fun vendorImportsStayInsideTheAdapter() {
        val source = listOf(File("src/main/kotlin"), File("app/src/main/kotlin")).first { it.isDirectory }
        val imports = source.walkTopDown().filter { it.extension == "kt" }
            .filter { Regex("(?m)^import com\\.kashif_e\\.backdrop\\.").containsMatchIn(it.readText()) }
            .map { it.name }.toList()
        assertEquals(listOf("FitnessGlass.kt"), imports)
    }

    @Test fun unsupportedApiAndHighContrastAlwaysUseOpaqueSurfaces() {
        assertFalse(fitnessGlassSupported(30, false))
        assertFalse(fitnessGlassSupported(31, true))
        assertFalse(fitnessGlassSupported(36, true))
        assertTrue(fitnessGlassSupported(31, false))
        assertTrue(fitnessGlassSupported(33, false))
    }

    @Test fun modalTransparencyRequiresItsOwnSupportedWindowAndSystemBlur() {
        assertFalse(fitnessDialogGlassSupported(30, true, true, true))
        assertFalse(fitnessDialogGlassSupported(31, false, true, true))
        assertFalse(fitnessDialogGlassSupported(31, true, false, true))
        assertFalse(fitnessDialogGlassSupported(31, true, true, false))
        assertTrue(fitnessDialogGlassSupported(31, true, true, true))
    }
}
