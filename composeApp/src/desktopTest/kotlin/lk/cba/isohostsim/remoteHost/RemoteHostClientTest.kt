package lk.cba.isohostsim.remoteHost

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import lk.cba.isohostsim.domain.service.remoteHost.OverrideRequest
import lk.cba.isohostsim.domain.service.remoteHost.RemoteConnection
import lk.cba.isohostsim.domain.service.remoteHost.RemoteHostClient
import lk.cba.isohostsim.domain.service.remoteHost.TerminalFilter
import lk.cba.isohostsim.domain.service.remoteHost.normalizeBaseUrl
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Talks to a running remote host (host_sim_backend_node). Skipped unless REMOTE_HOST_URL is set:
 *   REMOTE_HOST_URL=http://127.0.0.1:8590 REMOTE_HOST_ISO_PORT=8583 ./gradlew :composeApp:desktopTest --tests '*RemoteHostClientTest*'
 */
class RemoteHostClientTest {
    private val url = System.getenv("REMOTE_HOST_URL")
    private val isoPort = System.getenv("REMOTE_HOST_ISO_PORT")?.toInt() ?: 8583

    /** The HNB chip sale from app-specs/test-tools/requests.tsv; F41 is "80503875". */
    private val sale = "0090600096000002003020058020C002040000000000000100000000420071009600304216892700202304D2402221190382" +
        "383035303338373531333530363030312020202020202000639F260811223344556677889F2701809F3602000195050000000000" +
        "820200009A032611179C01009F02060000000100005F2A0201449F100706010A03A000000006303030303033"

    private fun saleFor(tid: String): ByteArray {
        require(tid.length == 8)
        val tidHex = tid.toByteArray().joinToString("") { "%02X".format(it) }
        val hex = sale.replace("3830353033383735", tidHex)
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    /** Sends one packet as a terminal and returns the reply's F39. */
    private fun sendAsTerminal(tid: String): String {
        Socket(java.net.URI(normalizeBaseUrl(url!!)).host, isoPort).use { s ->
            s.soTimeout = 5000
            s.getOutputStream().write(saleFor(tid))
            val input = s.getInputStream()
            val len = (input.read() shl 8) or input.read()
            val body = input.readNBytes(len)
            // The sale reply sets 3, 4, 11, 12, 13, 37, 38, 39: F39 is the last two bytes.
            return String(body.copyOfRange(body.size - 2, body.size))
        }
    }

    @Test
    fun watchesOnlyTheFilteredTerminalAndAppliesItsOverride() = runBlocking {
        if (url.isNullOrBlank()) return@runBlocking
        val mine = "RT" + (100000..999999).random()
        val other = "OT" + (100000..999999).random()
        val client = RemoteHostClient(url)
        try {
            client.start(TerminalFilter(tid = mine))
            withTimeout(10_000) { client.connection.first { it == RemoteConnection.Live } }
            assertEquals("all-apps", client.health.value?.profile?.name)
            assertTrue(client.profile.value!!.transactions.any { it.name == "SETTLEMENT" })

            val o = client.setOverride(OverrideRequest(tid = mine, responseCode = "05", setBy = "client-test", minutes = 5))
            assertEquals("05", sendAsTerminal(mine))
            assertEquals("00", sendAsTerminal(other))

            val seen = withTimeout(10_000) { client.transactions.first { it.isNotEmpty() } }
            assertTrue(seen.all { it.tid == mine }, "only the filtered terminal's traffic arrives: ${seen.map { it.tid }}")
            assertEquals("05", seen.first().responseCode)
            assertTrue(seen.first().request!!.hex.contains("421689FFFFFF2304"), "card data arrives masked")
            withTimeout(10_000) { client.logs.first { logs -> logs.any { it.tid == mine } } }

            client.deleteOverride(o.id)
            assertTrue(client.overrides.value.none { it.id == o.id })
            assertEquals("00", sendAsTerminal(mine))

            // A new filter reloads history from the host for that terminal.
            client.changeFilter(TerminalFilter(tid = other))
            val otherSeen = withTimeout(10_000) { client.transactions.first { list -> list.isNotEmpty() && list.all { it.tid == other } } }
            assertEquals("00", otherSeen.first().responseCode)
        } finally {
            client.close()
        }
    }
}
