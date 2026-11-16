package `in`.aicortex.iso8583studio.edition

import `in`.aicortex.iso8583studio.data.model.StudioTool
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnabledToolsTest {

    @Test
    fun `this release shows only the host and ecr simulators`() {
        assertEquals(
            listOf(StudioTool.HOST_SIMULATOR, StudioTool.ECR_SIMULATOR),
            EnabledTools.visibleTools(),
        )
        assertFalse(EnabledTools.isEnabled(StudioTool.HSM_SIMULATOR))
        assertFalse(EnabledTools.analyticsEnabled)
    }

    @Test
    fun `anyEnabled is true when one listed tool is enabled`() {
        assertTrue(EnabledTools.anyEnabled(StudioTool.POS_TERMINAL, StudioTool.ECR_SIMULATOR))
        assertFalse(EnabledTools.anyEnabled(StudioTool.POS_TERMINAL, StudioTool.APDU_SIMULATOR))
    }

    @Test
    fun `every tool has a line in the list file so it can be switched on`() {
        val source = File("src/desktopMain/kotlin/in/aicortex/iso8583studio/edition/EnabledTools.kt").readText()
        val missing = StudioTool.entries.filter { "StudioTool.${it.name}," !in source }
        assertTrue(missing.isEmpty(), "Add these to EnabledTools.ENABLED (commented out if off): $missing")
    }
}
