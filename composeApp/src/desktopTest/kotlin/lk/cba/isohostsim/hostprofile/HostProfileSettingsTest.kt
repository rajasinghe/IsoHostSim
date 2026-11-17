package lk.cba.isohostsim.hostprofile

import lk.cba.isohostsim.data.model.ConnectionType
import lk.cba.isohostsim.data.model.GatewayType
import lk.cba.isohostsim.data.model.LoggingOption
import lk.cba.isohostsim.data.model.MessageEncoding
import lk.cba.isohostsim.data.model.TransmissionType
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.HostProfileLoader
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.HostProfileStarter
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.ProfileResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The settings that used to live in the Gateway Type, Transmission and Log tabs. */
class HostProfileSettingsTest {

    private fun errors(json: String) = assertIs<ProfileResult.Invalid>(HostProfileLoader.parse(json)).errors

    @Test
    fun `the starter profile loads and runs as a server on 8000`() {
        val profile = assertIs<ProfileResult.Ok>(HostProfileLoader.parse(HostProfileStarter.text("QA \"Desk\" 1"))).profile
        assertEquals(listOf("SALE", "VOID", "REVERSAL", "SETTLEMENT"), profile.transactions.map { it.name })
        val cfg = HostProfileLoader.toGatewayConfig(profile, "id", null)
        assertEquals(GatewayType.SERVER, cfg.gatewayType)
        assertEquals(8000, cfg.serverPort)
        assertTrue(cfg.swapResponseTpdu)
        assertEquals("96", cfg.unmatchedResponseCode)
    }

    @Test
    fun `gateway transmission and logging settings reach the gateway`() {
        val cfg = HostProfileLoader.toGatewayConfig(
            assertIs<ProfileResult.Ok>(HostProfileLoader.parse("""
                {
                  "name": "Desk",
                  "gateway": { "type": "PROXY", "transmission": "ASYNCHRONOUS", "maxConnections": 7, "timeoutSeconds": 45, "terminateOnError": true },
                  "listen": { "connection": "RS232", "serialPort": "/dev/ttyUSB0", "baudRate": 115200 },
                  "destination": { "address": "172.19.25.141", "port": 51837, "lengthHeader": "BCD" },
                  "logging": { "file": "/tmp/desk.log", "maxSizeMB": 3, "rawData": true, "encoding": "UTF8" }
                }
            """.trimIndent())).profile, "id", null,
        )
        assertEquals(GatewayType.PROXY, cfg.gatewayType)
        assertEquals(TransmissionType.ASYNCHRONOUS, cfg.transmissionType)
        assertEquals(7, cfg.maxConcurrentConnection)
        assertEquals(45, cfg.advanceOptions.timeOutFromSource, "the timeout drives the sockets")
        assertTrue(cfg.terminateWhenError)
        assertEquals(ConnectionType.COM, cfg.serverConnectionType)
        assertEquals("/dev/ttyUSB0", cfg.serialPort)
        assertEquals("115200", cfg.baudRate)
        assertEquals("172.19.25.141", cfg.destinationServer)
        assertEquals(51837, cfg.destinationPort)
        assertEquals("/tmp/desk.log", cfg.logFileName)
        assertEquals(3, cfg.maxLogSizeInMB)
        assertEquals(MessageEncoding.UTF8, cfg.textEncoding)
        assertTrue(cfg.logOptions and LoggingOption.PARSED_DATA.value != 0, "parsing always on")
        assertTrue(cfg.logOptions and LoggingOption.RAW_DATA.value != 0)
    }

    @Test
    fun `an empty log file gets one per simulator under the app folder`() {
        val cfg = HostProfileLoader.toGatewayConfig(
            assertIs<ProfileResult.Ok>(HostProfileLoader.parse("""{ "name": "HNB IM30" }""")).profile, "id", null,
        )
        assertTrue(cfg.logFileName.endsWith("logs/HNB-IM30.log"), cfg.logFileName)
    }

    @Test
    fun `settings problems are reported`() {
        assertTrue(errors("""{ "name": "x", "gateway": { "type": "CLIENT" } }""").any { "CLIENT is not supported" in it })
        assertTrue(errors("""{ "name": "x", "gateway": { "type": "PROXY" } }""").any { it.startsWith("destination:") })
        assertTrue(errors("""{ "name": "x", "listen": { "connection": "RS232" } }""").any { it.startsWith("listen.serialPort") })
        assertTrue(errors("""{ "name": "x", "logging": { "encoding": "EBCDIC" } }""").any { it.startsWith("logging.encoding") })
        assertTrue(errors("""{ "name": "x", "gateway": { "timeoutSeconds": 0 } }""").any { it.startsWith("gateway.timeoutSeconds") })
    }
}
