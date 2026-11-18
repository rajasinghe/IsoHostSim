package lk.cba.isohostsim.remoteHost

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import lk.cba.isohostsim.domain.service.remoteHost.FilterPreset
import lk.cba.isohostsim.domain.service.remoteHost.RemoteHostSettings
import lk.cba.isohostsim.domain.service.remoteHost.RemoteHostStore
import lk.cba.isohostsim.domain.service.remoteHost.SavedRemoteHost
import lk.cba.isohostsim.ui.AppTheme
import lk.cba.isohostsim.ui.screens.remoteHost.RemoteHostScreen
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Renders the Remote Host screen against a running remote host and saves each tab as a PNG, for
 * checking the layout by eye. Skipped unless REMOTE_HOST_URL and SCREENSHOT_DIR are set:
 *   REMOTE_HOST_URL=http://127.0.0.1:8590 SCREENSHOT_DIR=/tmp/shots ./gradlew :composeApp:desktopTest --tests '*RemoteHostScreenPreviewTest*'
 */
@OptIn(ExperimentalTestApi::class)
class RemoteHostScreenPreviewTest {
    @Test
    fun rendersEveryTab() {
        val url = System.getenv("REMOTE_HOST_URL") ?: return
        val out = File(System.getenv("SCREENSHOT_DIR") ?: return).apply { mkdirs() }
        val tid = System.getenv("REMOTE_HOST_TID") ?: "80503875"
        for (dark in listOf(false, true)) {
            // Fresh settings each pass: switching presets in one pass saves its TID as the last filter.
            RemoteHostStore.save(
                RemoteHostSettings(
                    hosts = listOf(SavedRemoteHost("h1", "QA host", url)),
                    selectedId = "h1",
                    myName = "QA",
                    lastTid = tid,
                    presets = listOf(FilterPreset("IM30 HNB", "80503875", "13506001"), FilterPreset("Second terminal", "99999999")),
                ),
            )
            runDesktopComposeUiTest(width = 1500, height = 1000) {
                setContent { AppTheme(darkTheme = dark) { RemoteHostScreen() } }
                waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText("Live")).fetchSemanticsNodes().isNotEmpty() }
                waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText("Rule")).fetchSemanticsNodes().isNotEmpty() }
                val mode = if (dark) "dark" else "light"
                fun shot(name: String) {
                    waitForIdle()
                    Thread.sleep(300)
                    waitForIdle()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "remote-$name-$mode.png"))
                }
                // Expand the newest transaction to show its decoded fields.
                onAllNodes(hasText("SALE", substring = true))[0].performClick()
                shot("transactions")
                for (tab in listOf("Logs", "Overrides", "Host config")) {
                    onAllNodes(hasText(tab))[0].performClick()
                    shot(tab.lowercase().replace(' ', '-'))
                    if (tab == "Overrides") {
                        // One click applies a preset; a second click on it pauses it.
                        onAllNodes(hasText("Insufficient funds"))[0].performClick()
                        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("On · click to pause")).fetchSemanticsNodes().isNotEmpty() }
                        shot("override-on")
                        onAllNodes(hasText("Insufficient funds"))[0].performClick()
                        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Paused · click to resume")).fetchSemanticsNodes().isNotEmpty() }
                        onAllNodes(hasText("Custom…"))[0].performClick()
                        shot("override-paused")
                        onAllNodes(hasText("Remove"))[0].performClick()
                        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("No overrides are active", substring = true)).fetchSemanticsNodes().isNotEmpty() }
                    }
                }
                // One click on a preset switches the view to that terminal.
                onAllNodes(hasText("Second terminal"))[0].performClick()
                waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Showing terminal 99999999")).fetchSemanticsNodes().isNotEmpty() }
                onAllNodes(hasText("Transactions"))[0].performClick()
                shot("preset-switched")
            }
        }
    }
}
