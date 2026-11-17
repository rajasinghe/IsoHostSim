package lk.cba.isohostsim.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudioPathsTest {

    private val root: File = Files.createTempDirectory("studio-paths").toFile()
    private val home = File(root, "home/.iso8583studio")
    private val workDir = File(root, "work").apply { mkdirs() }

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `copies a legacy working-directory file into the app home once`() {
        File(workDir, "Iso8583Studio_simulators.json").writeText("""{"legacy":true}""")

        val file = StudioPaths.simulatorsFile("Iso8583Studio", home, workDir)

        assertEquals(File(home, "Iso8583Studio_simulators.json"), file)
        assertEquals("""{"legacy":true}""", file.readText())
        assertTrue(File(workDir, "Iso8583Studio_simulators.json").exists(), "the old file is kept")
    }

    @Test
    fun `never overwrites a file already in the app home`() {
        home.mkdirs()
        File(home, "Iso8583Studio_simulators.json").writeText("current")
        File(workDir, "Iso8583Studio_simulators.json").writeText("stale")

        val file = StudioPaths.simulatorsFile("Iso8583Studio", home, workDir)

        assertEquals("current", file.readText())
    }

    @Test
    fun `returns the home path even when there is nothing to migrate`() {
        val file = StudioPaths.simulatorsFile("Iso8583Studio", home, workDir)

        assertEquals(File(home, "Iso8583Studio_simulators.json"), file)
        assertFalse(file.exists())
    }
}
