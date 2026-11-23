package lk.cba.isohostsim.data

import lk.cba.isohostsim.compat.LegacyNames
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudioPathsTest {

    private val root: File = Files.createTempDirectory("studio-paths").toFile()
    private val home = File(root, "home/.pos-testing-platform")
    private val workDir = File(root, "work").apply { mkdirs() }

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `copies a legacy working-directory file into the app home once`() {
        File(workDir, "PosTestingPlatform_simulators.json").writeText("""{"legacy":true}""")

        val file = StudioPaths.simulatorsFile("PosTestingPlatform", home, workDir)

        assertEquals(File(home, "PosTestingPlatform_simulators.json"), file)
        assertEquals("""{"legacy":true}""", file.readText())
        assertTrue(File(workDir, "PosTestingPlatform_simulators.json").exists(), "the old file is kept")
    }

    @Test
    fun `never overwrites a file already in the app home`() {
        home.mkdirs()
        File(home, "PosTestingPlatform_simulators.json").writeText("current")
        File(workDir, "PosTestingPlatform_simulators.json").writeText("stale")

        val file = StudioPaths.simulatorsFile("PosTestingPlatform", home, workDir)

        assertEquals("current", file.readText())
    }

    @Test
    fun `returns the home path even when there is nothing to migrate`() {
        val file = StudioPaths.simulatorsFile("PosTestingPlatform", home, workDir)

        assertEquals(File(home, "PosTestingPlatform_simulators.json"), file)
        assertFalse(file.exists())
    }

    @Test
    fun `renames the saved file of a copy installed under the old name`() {
        home.mkdirs()
        val old = File(home, "${LegacyNames.SIMULATORS_NAME}_simulators.json").apply { writeText("gateways") }

        val file = StudioPaths.simulatorsFile(StudioPaths.SIMULATORS_NAME, home, workDir)

        assertEquals(File(home, "PosTestingPlatform_simulators.json"), file)
        assertEquals("gateways", file.readText())
        assertFalse(old.exists(), "the old file was renamed, not copied")
    }

    @Test
    fun `copies an old-name working-directory file when nothing newer exists`() {
        File(workDir, "${LegacyNames.SIMULATORS_NAME}_simulators.json").writeText("dev gateways")

        val file = StudioPaths.simulatorsFile(StudioPaths.SIMULATORS_NAME, home, workDir)

        assertEquals("dev gateways", file.readText())
    }

    @Test
    fun `adopts the settings folder of a copy installed under the old name`() {
        val legacy = File(root, "home/${LegacyNames.HOME_DIR}").apply { mkdirs() }
        File(legacy, "app_settings.json").writeText("settings")
        File(legacy, "hosts").mkdirs()
        File(legacy, "hosts/a.json").writeText("profile")

        assertTrue(StudioPaths.adoptLegacyHome(legacy, home))

        assertEquals("settings", File(home, "app_settings.json").readText())
        assertEquals("profile", File(home, "hosts/a.json").readText())
        assertFalse(legacy.exists(), "the old folder was renamed")
    }

    @Test
    fun `leaves an existing settings folder alone`() {
        home.mkdirs()
        File(home, "app_settings.json").writeText("current")
        val legacy = File(root, "home/${LegacyNames.HOME_DIR}").apply { mkdirs() }
        File(legacy, "app_settings.json").writeText("old")

        assertFalse(StudioPaths.adoptLegacyHome(legacy, home))

        assertEquals("current", File(home, "app_settings.json").readText())
        assertTrue(legacy.exists())
    }
}
