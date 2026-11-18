package lk.cba.isohostsim.logcat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import lk.cba.isohostsim.logging.LogEntry
import lk.cba.isohostsim.logging.LogStamp
import lk.cba.isohostsim.logging.LogType
import lk.cba.isohostsim.ui.AppTheme
import lk.cba.isohostsim.ui.screens.logcat.LogcatState
import lk.cba.isohostsim.ui.screens.logcat.LogcatTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What QA does with the viewer: drag across part of a line and copy just that, or click a line and copy all of it. */
@OptIn(ExperimentalTestApi::class)
class LogcatSelectionTest {
    private val message = "ALPHA BRAVO CHARLIE DELTA"

    init {
        System.setProperty("iso8583studio.logcat.history", java.io.File(System.getProperty("java.io.tmpdir"), "logcat-test-history.txt").absolutePath)
    }

    private fun entries() = mutableStateListOf(
        LogEntry(
            timestamp = "12:00:00.000", type = LogType.INFO, message = message, details = null,
            metadata = mapOf(LogStamp.TAG to "GatewayClient", LogStamp.TS to System.currentTimeMillis(), LogStamp.PID to 1L, LogStamp.TID to 1L),
        ),
    )

    /** The test JVM is headless, so there is no system clipboard to read back; catch what is copied instead. */
    private class FakeClipboard {
        var text: String = ""

        /** What the viewer's own copy of whole lines writes to. */
        val manager = object : ClipboardManager {
            override fun setText(annotatedString: AnnotatedString) { text = annotatedString.text }
            override fun getText(): AnnotatedString? = AnnotatedString(text).takeIf { text.isNotEmpty() }
        }

        /** What the text selection's Ctrl+C writes to; Compose 1.8 uses this one for it. */
        val clip = object : Clipboard {
            override suspend fun getClipEntry(): ClipEntry? = null
            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                text = (clipEntry?.nativeClipEntry as? java.awt.datatransfer.Transferable)?.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String ?: ""
            }
            override val nativeClipboard: java.awt.datatransfer.Clipboard = java.awt.datatransfer.Clipboard("test")
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.copyShortcut() {
        onNode(isRoot()).performKeyInput { keyDown(Key.CtrlLeft); keyDown(Key.C); keyUp(Key.C); keyUp(Key.CtrlLeft) }
        waitForIdle()
    }

    @Test
    fun `dragging across a line copies only the dragged text`() = runDesktopComposeUiTest(width = 1280, height = 400) {
        val clipboard = FakeClipboard()
        setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard.manager, LocalClipboard provides clipboard.clip) {
                AppTheme(darkTheme = true) { LogcatTab(LogcatState(), entries(), "host-sim", 5000, {}) }
            }
        }
        waitForIdle()
        onNodeWithText(message, substring = true).performMouseInput {
            moveTo(Offset(2f, center.y)); press(); moveTo(Offset(60f, center.y)); release()
        }
        waitForIdle()
        copyShortcut()
        val copied = clipboard.text
        assertTrue(copied.startsWith("ALPHA"), "copied: '$copied'")
        assertFalse(copied.contains("DELTA"), "copied: '$copied'")
        assertFalse(copied.contains("GatewayClient"), "copied: '$copied'")
    }

    @Test
    fun `clicking a line then copying gives the whole line`() = runDesktopComposeUiTest(width = 1280, height = 400) {
        val clipboard = FakeClipboard()
        setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard.manager, LocalClipboard provides clipboard.clip) {
                AppTheme(darkTheme = true) { LogcatTab(LogcatState(), entries(), "host-sim", 5000, {}) }
            }
        }
        waitForIdle()
        onNodeWithText(message, substring = true).performMouseInput { click(Offset(2f, center.y)) }
        waitForIdle()
        copyShortcut()
        val copied = clipboard.text
        assertTrue(copied.contains(message) && copied.contains("GatewayClient"), "copied: '$copied'")
    }

    @Test
    fun `the viewer starts dark whatever the app theme`() = runDesktopComposeUiTest(width = 1280, height = 400) {
        assertEquals(true, LogcatState().darkTheme)
    }
}
