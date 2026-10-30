package com.yeonsik.fitnessapp.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The alpha backdrop library stays behind core/ui/FitnessGlass.kt so an upgrade touches one file. */
class BackdropImportIsolationTest {
    @Test
    fun onlyFitnessGlassImportsTheBackdropLibrary() {
        // Gradle runs unit tests from the module directory; IDE runs may start at the repository root.
        val sources = listOf(File("src/main"), File("app/src/main")).firstOrNull { it.isDirectory }
        assertTrue("app/src/main must be reachable from ${File("").absolutePath}", sources != null)
        val importers = sources!!.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { file -> file.readLines().any { it.trimStart().startsWith("import com.kashif_e.backdrop") } }
            .map { it.relativeTo(sources).invariantSeparatorsPath }
            .toList()
        assertEquals(listOf("kotlin/com/yeonsik/fitnessapp/core/ui/FitnessGlass.kt"), importers)
    }
}
