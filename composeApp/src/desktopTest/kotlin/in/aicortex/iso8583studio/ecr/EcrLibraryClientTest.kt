package `in`.aicortex.iso8583studio.ecr

import `in`.aicortex.iso8583studio.domain.service.ecr.EcrCodes
import `in`.aicortex.iso8583studio.domain.service.ecr.EcrCommand
import `in`.aicortex.iso8583studio.domain.service.ecr.EcrLibraryClient
import `in`.aicortex.iso8583studio.domain.service.ecr.EcrOutcome
import `in`.aicortex.iso8583studio.domain.service.ecr.EcrRequests
import `in`.aicortex.iso8583studio.domain.service.ecr.EcrSaleFields
import `in`.aicortex.iso8583studio.domain.service.ecr.EcrSimulatorService
import `in`.aicortex.iso8583studio.domain.service.ecr.EcrTarget
import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.ecr.EcrSimulatorConfig
import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.ecr.EcrTransport
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Drives the real CBA ECR library against [FakeEcrTerminal] over TCP. */
class EcrLibraryClientTest {

    @BeforeTest
    fun keepLibraryLogsOutOfTheHomeFolder() {
        System.setProperty(
            EcrLibraryClient.LIBRARY_DIR_PROPERTY,
            Files.createTempDirectory("ecr-lib").toFile().absolutePath,
        )
    }

    private fun target(port: Int) = EcrTarget(EcrTransport.TCP, "127.0.0.1", port)

    private fun approvedSale() = """{"checksum":"abc","data":{"status":"A","rspCode":"00","rspText":"APPROVED",""" +
        """"invoiceNo":"000012","appCode":"123456","referenceNo":"000000000777","pan":"5399****7798",""" +
        """"amt":"10000"},"dataType":"sale"}"""

    @Test
    fun `sale goes out as the library builds it and comes back approved`() = runBlocking {
        FakeEcrTerminal { listOf(FakeEcrTerminal.ACK, approvedSale()) }.use { terminal ->
            val request = EcrRequests.sale(EcrSaleFields(amt = "10000", ecrRef = "42"))

            val result = EcrLibraryClient().execute(target(terminal.port), EcrCommand.SALE, request, timeoutSec = 10)

            assertEquals(EcrOutcome.APPROVED, result.outcome, result.response)
            assertEquals("00", result.code)
            assertEquals("000012", result.fields["invoiceNo"])
            val sent = JSONObject(terminal.received.single())
            assertEquals("sale", sent.getString("dataType"))
            assertEquals("10000", sent.getJSONObject("data").getString("amt"))
            assertEquals("LKR", sent.getJSONObject("data").getString("Currency"))
            assertTrue(sent.getString("checksum").isNotBlank(), "the library adds a checksum")
        }
    }

    @Test
    fun `settlement keeps the library's settleRequest name`() = runBlocking {
        FakeEcrTerminal {
            listOf(FakeEcrTerminal.ACK, """{"data":{"status":"A","rspCode":"00"},"dataType":"settleRequest"}""")
        }.use { terminal ->
            val result = EcrLibraryClient().execute(target(terminal.port), EcrCommand.SETTLEMENT)

            assertEquals("settleRequest", JSONObject(terminal.received.single()).getString("dataType"))
            assertEquals(EcrOutcome.APPROVED, result.outcome, result.response)
        }
    }

    @Test
    fun `a terminal NAK is reported as the terminal's`() = runBlocking {
        FakeEcrTerminal { listOf("""{"dataType":"NAK","error":"10"}""") }.use { terminal ->
            val request = EcrRequests.sale(EcrSaleFields(amt = "100"))

            val result = EcrLibraryClient().execute(target(terminal.port), EcrCommand.SALE, request, timeoutSec = 5)

            assertEquals(EcrOutcome.TERMINAL_NAK, result.outcome, result.response)
            assertEquals("10", result.code)
            assertEquals(EcrCodes.terminalNak.getValue("10"), result.message)
        }
    }

    @Test
    fun `an unreachable terminal is a library NAK`() = runBlocking {
        val freePort = java.net.ServerSocket(0).use { it.localPort }

        val result = EcrLibraryClient().execute(target(freePort), EcrCommand.CHECK_STATUS)

        assertEquals(EcrOutcome.LIBRARY_NAK, result.outcome, result.response)
    }

    @Test
    fun `an approved sale advances the next ECR reference`() = runBlocking {
        FakeEcrTerminal { listOf(FakeEcrTerminal.ACK, approvedSale()) }.use { terminal ->
            val config = EcrSimulatorConfig(id = "t", name = "fake", terminalIp = "127.0.0.1", port = terminal.port, timeoutSec = 10)
            val service = EcrSimulatorService(config)
            var saved: EcrSimulatorConfig? = null
            service.onConfigChanged = { saved = it }

            service.sale(EcrSaleFields(amt = "10000", ecrRef = "41"))

            assertEquals(42, service.config.value.nextEcrRef)
            assertEquals(42, saved?.nextEcrRef)
            assertEquals(1, service.history.value.size)
            service.close()
        }
    }

    @Test
    fun `every library NAK code has text`() {
        (11..28).forEach { assertTrue(EcrCodes.isLibraryNak(it.toString()), "code $it") }
    }
}
