package lk.cba.isohostsim.edition

import lk.cba.isohostsim.data.model.StudioTool
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnabledToolsTest {

    @Test
    fun `this release shows the host and ecr simulators and logcat`() {
        assertEquals(
            listOf(StudioTool.HOST_SIMULATOR, StudioTool.ECR_SIMULATOR, StudioTool.LOGCAT_VIEWER, StudioTool.MESSAGE_PARSER, StudioTool.REMOTE_HOST),
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
        val source = File("src/desktopMain/kotlin/lk/cba/isohostsim/edition/EnabledTools.kt").readText()
        val missing = StudioTool.entries.filter { "StudioTool.${it.name}," !in source }
        assertTrue(missing.isEmpty(), "Add these to EnabledTools.ENABLED (commented out if off): $missing")
    }
}
