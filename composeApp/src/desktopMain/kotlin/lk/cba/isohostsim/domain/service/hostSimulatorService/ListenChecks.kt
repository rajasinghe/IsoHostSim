package lk.cba.isohostsim.domain.service.hostSimulatorService

import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/**
 * The checks the Local Host screen runs before it starts: is this PC's Wi-Fi hotspot on, is the
 * port free, and does the firewall let terminals on the hotspot reach it.
 */
object ListenChecks {
    /** This PC's hotspot: the interface and the address terminals on it connect to. */
    data class Hotspot(val iface: String, val address: String)

    sealed class Firewall {
        data object Open : Firewall()

        /** The port is closed in [zone]; [command] opens it for good. */
        data class Blocked(val zone: String, val command: String) : Firewall()

        /** No firewalld (another OS, or it isn't running), so nothing to check. */
        data object Unknown : Firewall()
    }

    /**
     * The hotspot interface, found by its address: NetworkManager shares a hotspot on 10.42.x.1,
     * Windows on 192.168.137.1. Null when no hotspot is on.
     */
    fun hotspot(): Hotspot? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .firstNotNullOfOrNull { nic ->
                nic.inetAddresses.toList().filterIsInstance<Inet4Address>()
                    .map { it.hostAddress }
                    .firstOrNull { it.startsWith("10.42.") || it == "192.168.137.1" }
                    ?.let { Hotspot(nic.name, it) }
            }
    }.getOrNull()

    /** True when nothing else listens on [port] at [address]. */
    fun portFree(address: String, port: Int): Boolean = runCatching {
        ServerSocket().use { s ->
            s.reuseAddress = true
            s.bind(InetSocketAddress(InetAddress.getByName(address), port))
        }
        true
    }.getOrDefault(false)

    /** Asks firewalld whether [port] is open in the zone of [iface]. */
    fun firewall(iface: String, port: Int): Firewall {
        val zone = run("firewall-cmd", "--get-zone-of-interface=$iface")?.takeIf { it.first == 0 }?.second?.trim()
            ?: run("firewall-cmd", "--get-default-zone")?.takeIf { it.first == 0 }?.second?.trim()
            ?: return Firewall.Unknown
        val query = run("firewall-cmd", "--zone=$zone", "--query-port=$port/tcp") ?: return Firewall.Unknown
        return when (query.second.trim()) {
            "yes" -> Firewall.Open
            "no" -> Firewall.Blocked(
                zone,
                "sudo firewall-cmd --zone=$zone --add-port=$port/tcp --permanent && sudo firewall-cmd --reload",
            )
            else -> Firewall.Unknown
        }
    }

    private fun run(vararg cmd: String): Pair<Int, String>? = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        if (!p.waitFor(3, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return null
        }
        p.exitValue() to p.inputStream.bufferedReader().readText()
    }.getOrNull()
}
