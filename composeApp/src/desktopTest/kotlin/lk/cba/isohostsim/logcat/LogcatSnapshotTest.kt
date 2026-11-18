package lk.cba.isohostsim.logcat

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import lk.cba.isohostsim.logging.LogEntry
import lk.cba.isohostsim.logging.LogStamp
import lk.cba.isohostsim.logging.LogType
import lk.cba.isohostsim.ui.AppTheme
import lk.cba.isohostsim.ui.screens.logcat.LogcatFormat
import lk.cba.isohostsim.ui.screens.logcat.LogcatState
import lk.cba.isohostsim.ui.screens.logcat.LogcatTab
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Renders the Logcat tab with a realistic run of simulator logs and drives it the way a user would.
 * Set LOGCAT_SNAPSHOT_DIR to also write the frames as PNGs for a look at the result.
 */
@OptIn(ExperimentalTestApi::class)
class LogcatSnapshotTest {
    private val outDir: File? = System.getenv("LOGCAT_SNAPSHOT_DIR")?.takeIf { it.isNotBlank() }?.let(::File)

    init {
        System.setProperty("iso8583studio.logcat.history", File(System.getProperty("java.io.tmpdir"), "logcat-test-history.txt").absolutePath)
    }

    private fun entry(
        offsetMs: Long, tag: String, tid: Long, type: LogType, message: String, details: String? = null,
    ): LogEntry {
        val ts = System.currentTimeMillis() - offsetMs
        val clock = java.time.LocalTime.ofNanoOfDay((ts % 86_400_000L) * 1_000_000L).toString().take(12)
        return LogEntry(
            timestamp = clock, type = type, message = message, details = details,
            metadata = mapOf(LogStamp.TAG to tag, LogStamp.TS to ts, LogStamp.PID to 123456L, LogStamp.TID to tid, LogStamp.THREAD to if (tid == 1L) "main" else "conn-$tid"),
        )
    }

    private fun sample(): List<LogEntry> = buildList {
        var t = 60_000L
        fun add(tag: String, tid: Long, type: LogType, msg: String, details: String? = null) {
            t -= 220; add(entry(t, tag, tid, type, msg, details))
        }
        add("HostSimulator", 1, LogType.INFO, "===============GATEWAY STARTED===============", "Name = host-sim\nGateway type = SERVER\nLocal Address = 10.42.0.1  Port = 8000")
        for (n in 0 until 3) {
            val tid = 31L + n
            add("GatewayClient", tid, LogType.CONNECTION, "==========A REMOTE CLIENT CONNECTED FROM IP = 10.42.0.246 : ${44692 + n}==========")
            add("GatewayClient", tid, LogType.VERBOSE, "RECEIVING MESSAGE.......")
            add("GatewayClient", tid, LogType.DEBUG, "RECEIVED MESSAGE FROM SOURCE ")
            add("Iso8583Data", tid, LogType.MESSAGE, "Message Type = 200", "TPDU Header = 6000960000\nField 3 = \"000000\"\nField 4 = \"000000001000\"\nField 11 = \"000005\"\nField 41 = \"80503875\"\nField 42 = \"13506001       \"")
            add("Iso8583Data", tid, LogType.BITMAP, "Bitmap: 7024058020C00204")
            add("SimulatedResponse", tid, LogType.TRANSACTION, "Matched simulated transaction SALE (mti 200, proCode 000000)")
            add("GatewayClient", tid, LogType.INFO, "SEND ENCRYPTED MESSAGE TO CLIENT INSTANCE: 88 BYTES ")
            add("GatewayClient", tid, LogType.CONNECTION, "===============CONNECTION TERMINATED===============")
        }
        add("GatewayClient", 34, LogType.CONNECTION, "==========A REMOTE CLIENT CONNECTED FROM IP = 10.42.0.246 : 44710==========")
        add("Iso8583Data", 34, LogType.MESSAGE, "Message Type = 500", "TPDU Header = 6000960000\nField 3 = \"920000\"\nField 11 = \"000009\"")
        add("HostSimulator", 34, LogType.WARNING, "Connection timeout detected", "Client: 10.42.0.246, Timeout: 30s")
        add("GatewayClient", 34, LogType.ERROR, "lk.cba.isohostsim.data.model.VerificationException: INPUT DATA IS NULL OR EMPTY, CHECK IF CONFIGURATION IS AVAILABLE",
            "\tat lk.cba.isohostsim.data.GatewayClient.send(GatewayClient.kt:954)\n\tat lk.cba.isohostsim.data.GatewayClient.processSynchronous(GatewayClient.kt:656)")
        add("GatewayClient", 34, LogType.INFO, "SEND ENCRYPTED MESSAGE TO CLIENT INSTANCE: 19 BYTES ")
        add("GatewayClient", 34, LogType.CONNECTION, "===============CONNECTION TERMINATED===============")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.snap(name: String) {
        val dir = outDir ?: return
        dir.mkdirs()
        // A popup (the suggestion list) is a second root, so capture each layer on its own.
        val layers = onAllNodes(isRoot()).fetchSemanticsNodes().size
        for (i in 0 until layers) {
            val file = File(dir, if (i == 0) "$name.png" else "${name}_layer$i.png")
            ImageIO.write(onAllNodes(isRoot())[i].captureToImage().toAwtImage(), "png", file)
        }
    }

    @Test
    fun `dark and light frames render and the query filters rows`() = runDesktopComposeUiTest(width = 1280, height = 640) {
        val entries = mutableStateListOf<LogEntry>().apply { addAll(sample()) }
        val state = LogcatState()
        var dark = true
        setContent {
            AppTheme(darkTheme = dark) { LogcatTab(state, entries, "host-sim", 5000, { entries.clear() }) }
        }
        waitForIdle()
        snap("01_dark_standard")
        onNodeWithText("Connection timeout detected", substring = true).assertExists()

        // Typing a query narrows the view live.
        onNode(hasSetTextAction()).performTextInput("level:ERROR")
        waitForIdle()
        onNodeWithText("Connection timeout detected", substring = true).assertDoesNotExist()
        snap("02_dark_level_error")

        state.setQuery("tag:GatewayClient -message:CONNECTION | tag:HostSimulator")
        waitForIdle()
        snap("03_dark_tag_or")

        state.setQuery("message:\"RECEIVED MESSAGE\" tag~:\"Gateway|Iso\"")
        waitForIdle()
        snap("04_dark_regex_highlight")

        state.setQuery("")
        state.anchor = 24; state.focus = 27
        state.format = LogcatFormat.COMPACT
        waitForIdle()
        snap("05_dark_compact_selection")

        state.format = LogcatFormat.STANDARD
        state.softWrap = true
        waitForIdle()
        snap("06_dark_softwrap")
    }

    @Test
    fun `light theme renders`() = runDesktopComposeUiTest(width = 1280, height = 520) {
        val entries = mutableStateListOf<LogEntry>().apply { addAll(sample()) }
        val state = LogcatState().apply { darkTheme = false }
        setContent { AppTheme(darkTheme = false) { LogcatTab(state, entries, "host-sim", 5000, { entries.clear() }) } }
        waitForIdle()
        state.anchor = 27; state.focus = 27
        waitForIdle()
        snap("07_light_standard")
    }

    @Test
    fun `paused view ignores new lines and resume catches up`() = runDesktopComposeUiTest(width = 1280, height = 520) {
        val entries = mutableStateListOf<LogEntry>().apply { addAll(sample()) }
        val state = LogcatState()
        setContent { AppTheme(darkTheme = true) { LogcatTab(state, entries, "host-sim", 5000, { entries.clear() }) } }
        waitForIdle()
        state.togglePause(entries)
        entries.add(entry(0, "LateTag", 99, LogType.ERROR, "arrived while paused"))
        waitForIdle()
        onNodeWithText("arrived while paused", substring = true).assertDoesNotExist()
        state.togglePause(entries)
        waitForIdle()
        onNodeWithText("arrived while paused", substring = true).assertExists()
    }

    @Test
    fun `suggestions appear while typing and enter accepts the first`() = runDesktopComposeUiTest(width = 1280, height = 520) {
        val entries = mutableStateListOf<LogEntry>().apply { addAll(sample()) }
        val state = LogcatState()
        setContent { AppTheme(darkTheme = true) { LogcatTab(state, entries, "host-sim", 5000, { entries.clear() }) } }
        waitForIdle()
        onNode(hasSetTextAction()).performTextInput("tag:Gat")
        waitForIdle()
        snap("08_dark_suggestions")
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        kotlin.test.assertEquals("tag:GatewayClient ", state.query.text)
    }
}
