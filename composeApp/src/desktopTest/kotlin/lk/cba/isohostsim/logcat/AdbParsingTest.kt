package lk.cba.isohostsim.logcat

import lk.cba.isohostsim.domain.service.logcat.AdbParsing
import lk.cba.isohostsim.logging.LogType
import lk.cba.isohostsim.ui.screens.logcat.LogcatLevel
import lk.cba.isohostsim.ui.screens.logcat.toLogcatRecord
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdbParsingTest {

    private val utc = ZoneOffset.UTC

    @Test
    fun `parses adb devices -l`() {
        val devices = AdbParsing.parseDevices(
            listOf(
                "List of devices attached",
                "1640244226             device usb:3-3 product:IM30 model:IM30 device:IM30 transport_id:8",
                "10.42.0.246:5555       offline",
                "",
            )
        )
        assertEquals(2, devices.size)
        assertEquals("1640244226", devices[0].serial)
        assertEquals("IM30", devices[0].model)
        assertEquals(true, devices[0].isOnline)
        assertEquals("offline", devices[1].state)
        assertEquals(false, devices[1].isOnline)
    }

    @Test
    fun `parses a threadtime line from a PAX terminal`() {
        val line = AdbParsing.parseThreadtime(
            "11-04 13:12:35.302 11599 11612 W SEND PACKET_TCPISO8583: 00CF600096000002007024",
            year = 2026, zone = utc,
        )!!
        assertEquals(11599, line.pid)
        assertEquals(11612, line.tid)
        assertEquals('W', line.level)
        assertEquals("SEND PACKET_TCPISO8583", line.tag)
        assertEquals("00CF600096000002007024", line.message)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 4, 13, 12, 35, 302_000_000).toInstant(utc).toEpochMilli(), line.epochMs)
    }

    @Test
    fun `keeps tags padded with spaces and messages with colons`() {
        val line = AdbParsing.parseThreadtime(
            "11-04 14:16:55.770  11599  11599 I Contactless     : result code: 0, trans result: RESULT_ONLINE_APPROVED",
            year = 2026, zone = utc,
        )!!
        assertEquals("Contactless", line.tag)
        assertEquals("result code: 0, trans result: RESULT_ONLINE_APPROVED", line.message)
    }

    @Test
    fun `fatal is shown as assert and banners are skipped`() {
        assertEquals('A', AdbParsing.parseThreadtime("11-04 14:11:40.781 11599 11599 F libc    : Fatal signal 6", 2026, utc)!!.level)
        assertNull(AdbParsing.parseThreadtime("--------- beginning of main", 2026, utc))
    }

    @Test
    fun `parses ps output into pid to package`() {
        val map = AdbParsing.parseProcesses(
            listOf("  PID NAME", "    1 init", "11599 com.pax.edc.ui1", "  2158 system_server")
        )
        assertEquals("com.pax.edc.ui1", map[11599])
        assertEquals("system_server", map[2158])
        assertNull(map[0])
    }

    @Test
    fun `a device line becomes a viewer row with its own level and package`() {
        val line = AdbParsing.parseThreadtime(
            "11-04 14:11:40.781 11599 11612 E TcpIso8583Online: PacketException: response tpdu error",
            2026, utc,
        )!!
        val entry = AdbParsing.toLogEntry(line, "com.pax.edc.ui1", utc)
        assertEquals(LogType.ERROR, entry.type)

        val record = entry.toLogcatRecord(packageName = "")
        assertEquals("TcpIso8583Online", record.tag)
        assertEquals(11599, record.pid)
        assertEquals(11612, record.tid)
        assertEquals(LogcatLevel.ERROR, record.level)
        assertEquals("com.pax.edc.ui1", record.packageName)
        assertEquals(line.epochMs, record.epochMs)
    }
}
