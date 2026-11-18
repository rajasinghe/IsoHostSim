package lk.cba.isohostsim.remoteHost

import lk.cba.isohostsim.domain.service.hostSimulatorService.SimulatedResponse
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.HostProfileLoader
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.ProfileResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Writes the desktop host engine's replies to the sample requests, in the format
 * check_replies.py reads, so they can be compared with the remote (Node) host's replies.
 * Skipped unless PARITY_PROFILE, PARITY_REQUESTS and PARITY_OUT are set.
 */
class RemoteHostParityTest {
    @Test
    fun writeDesktopEngineReplies() {
        val profile = System.getenv("PARITY_PROFILE") ?: return
        val requests = System.getenv("PARITY_REQUESTS") ?: return
        val out = System.getenv("PARITY_OUT") ?: return
        val p = assertIs<ProfileResult.Ok>(HostProfileLoader.read(File(profile))).profile
        val cfg = HostProfileLoader.toGatewayConfig(p, "parity", null)
        val lines = File(requests).readLines().filter { it.isNotBlank() }.map { line ->
            val (id, _, _, hex) = line.split('\t')
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val reply = try {
                val req = lk.cba.isohostsim.data.Iso8583Data(cfg, true).apply { unpack(bytes) }
                SimulatedResponse(req, cfg, true).pack()?.joinToString("") { "%02X".format(it) } ?: "NOMATCH"
            } catch (e: Exception) {
                "ERROR ${e.message}"
            }
            "$id\t$reply"
        }
        File(out).writeText(lines.joinToString("\n") + "\n")
    }
}
