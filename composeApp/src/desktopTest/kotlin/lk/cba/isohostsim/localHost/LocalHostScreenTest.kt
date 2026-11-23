package lk.cba.isohostsim.localHost

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import lk.cba.isohostsim.domain.service.hostSimulatorService.HostSimulator
import lk.cba.isohostsim.domain.service.hostSimulatorService.ListenChecks
import lk.cba.isohostsim.domain.service.hostSimulatorService.LocalHostState
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.BuiltInHostProfile
import lk.cba.isohostsim.domain.service.remoteHost.FilterPreset
import lk.cba.isohostsim.domain.service.remoteHost.RemoteHostSettings
import lk.cba.isohostsim.domain.service.remoteHost.RemoteHostStore
import lk.cba.isohostsim.ui.AppTheme
import lk.cba.isohostsim.ui.screens.hostSimulator.LocalHostScreen
import java.io.DataInputStream
import java.io.File
import java.net.Socket
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the Local Host screen against real terminal traffic: starts the host, sends sample
 * packets, applies, pauses and resumes an override, rejects a settlement, and saves each view as a
 * PNG. The host answers from the built-in profile. Skipped unless LOCAL_HOST_REQUESTS and
 * SCREENSHOT_DIR are set:
 *   LOCAL_HOST_REQUESTS=../app-specs/test-tools/requests.tsv SCREENSHOT_DIR=/tmp/shots ./gradlew :composeApp:desktopTest --tests '*LocalHostScreenTest*'
 * It overwrites ~/.pos-testing-platform/remote_hosts.json, so back that file up first.
 */
@OptIn(ExperimentalTestApi::class)
class LocalHostScreenTest {
    private val port = 18600

    private fun packet(requests: File, name: String): ByteArray =
        requests.readLines().first { it.startsWith("$name\t") }.split('\t')[3].chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** Sends one framed packet and returns the reply body, or null when none came within 4 s. */
    private fun send(address: String, bytes: ByteArray): ByteArray? = Socket(address, port).use { s ->
        s.soTimeout = 4000
        s.getOutputStream().write(bytes)
        runCatching {
            val input = DataInputStream(s.getInputStream())
            val len = input.readUnsignedShort()
            ByteArray(len).also { input.readFully(it) }
        }.getOrNull()
    }

    @Test
    fun runsTheLocalHost() {
        val requests = File(System.getenv("LOCAL_HOST_REQUESTS") ?: return)
        val out = File(System.getenv("SCREENSHOT_DIR") ?: return).apply { mkdirs() }
        val config = BuiltInHostProfile.newConfig("local-host-test")
        RemoteHostStore.save(
            RemoteHostSettings(
                localPorts = mapOf(config.id to port),
                presets = listOf(FilterPreset("IM30 HNB", "80503875", "13506001")),
            ),
        )
        val state = LocalHostState.of(config.id)
        val address = ListenChecks.hotspot()?.address ?: "127.0.0.1"
        val sale = packet(requests, "hnb:SALE chip")
        val settle = packet(requests, "cbc_im30:SETTLE 920000")
        val gw = HostSimulator(config)

        runDesktopComposeUiTest(width = 1500, height = 1000) {
            setContent { AppTheme(darkTheme = false) { LocalHostScreen(gw, onBack = {}, onSaveClick = {}) } }
            fun shot(name: String) {
                waitForIdle()
                Thread.sleep(400)
                waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "local-$name.png"))
            }
            fun click(text: String, substring: Boolean = false) = onAllNodes(hasText(text, substring = substring))[0].performClick()
            fun waitFor(text: String) = waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
            fun lastRc(): String? = state.transactions.value.first().responseCode

            waitFor("Port $port is free")
            shot("1-stopped")

            click("Start host")
            waitFor("Stop host")
            send(address, sale)
            waitUntil(timeoutMillis = 5_000) { state.transactions.value.isNotEmpty() }
            assertEquals("00", lastRc())

            click("Overrides", substring = true)
            waitFor("Decline, no response or delay")
            click("Insufficient funds")
            waitFor("On · click to pause")
            send(address, sale)
            assertEquals("51", lastRc(), "the decline preset applies to every terminal")
            shot("3-override-on")

            click("Insufficient funds")
            waitFor("Paused · click to resume")
            send(address, sale)
            assertEquals("00", lastRc(), "a paused override is not applied")

            onAllNodes(isToggleable())[0].performClick()
            waitUntil(timeoutMillis = 5_000) { state.overrides.value.any { it.kind == "rejectSettlement" } }
            send(address, settle)
            assertEquals("95", lastRc(), "the settlement reject answers 95")
            shot("4-overrides")

            click("Transactions")
            waitFor("SETTLEMENT")
            onAllNodes(hasText("SETTLEMENT", substring = true))[0].performClick()
            shot("2-transactions")

            // The tab, not the "Host config" label in the status strip above it.
            onAllNodes(hasText("Host config")).fetchSemanticsNodes().size.let { n -> onAllNodes(hasText("Host config"))[n - 1].performClick() }
            waitFor("What the host answers")
            assertEquals(0, onAllNodes(hasText("Developer options")).fetchSemanticsNodes().size, "no profile file controls for testers")
            shot("5-host-config")

            click("Logs")
            shot("6-logs")

            click("Stop host")
            waitFor("Start host")
        }
        assertEquals(emptyList(), state.overrides.value.filter { it.expiresAt == null }, "overrides kept until stop end with the host")
    }
}
