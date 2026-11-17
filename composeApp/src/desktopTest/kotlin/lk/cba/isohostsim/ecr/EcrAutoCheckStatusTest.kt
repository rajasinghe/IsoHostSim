package lk.cba.isohostsim.ecr

import lk.cba.isohostsim.domain.service.ecr.EcrCommand
import lk.cba.isohostsim.domain.service.ecr.EcrLibraryClient
import lk.cba.isohostsim.domain.service.ecr.EcrSaleFields
import lk.cba.isohostsim.domain.service.ecr.EcrSimulatorService
import lk.cba.isohostsim.ui.navigation.stateConfigs.ecr.EcrSimulatorConfig
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "Send Check Status first": the terminal sees `active` before the command when it is ticked. */
class EcrAutoCheckStatusTest {

    @BeforeTest
    fun keepLibraryLogsOutOfTheHomeFolder() {
        System.setProperty(
            EcrLibraryClient.LIBRARY_DIR_PROPERTY,
            Files.createTempDirectory("ecr-lib").toFile().absolutePath,
        )
    }

    private val approvedSale = """{"data":{"status":"A","rspCode":"00","invoiceNo":"000001"},"dataType":"sale"}"""

    private fun terminal() = FakeEcrTerminal { request ->
        if (JSONObject(request).getString("dataType") == "active") listOf(FakeEcrTerminal.ACK)
        else listOf(FakeEcrTerminal.ACK, approvedSale)
    }

    private fun service(port: Int, before: Set<EcrCommand>) = EcrSimulatorService(
        EcrSimulatorConfig(id = "t", name = "fake", terminalIp = "127.0.0.1", port = port, timeoutSec = 10, checkStatusBefore = before)
    )

    private fun FakeEcrTerminal.sentTypes() = received.map { JSONObject(it).getString("dataType") }

    @Test
    fun `ticked sale sends check status first`() = runBlocking {
        terminal().use { t ->
            val svc = service(t.port, setOf(EcrCommand.SALE))
            svc.sale(EcrSaleFields(amt = "100", ecrRef = "1"))
            svc.sale(EcrSaleFields(amt = "100", ecrRef = "2"))

            assertEquals(listOf("active", "sale", "active", "sale"), t.sentTypes())
            assertEquals(4, svc.history.value.size)
            svc.close()
        }
    }

    @Test
    fun `unticked sale goes straight out`() = runBlocking {
        terminal().use { t ->
            val svc = service(t.port, emptySet())
            svc.sale(EcrSaleFields(amt = "100"))
            assertEquals(listOf("sale"), t.sentTypes())
            svc.close()
        }
    }

    @Test
    fun `no second check status right after one`() = runBlocking {
        terminal().use { t ->
            val svc = service(t.port, setOf(EcrCommand.SALE))
            svc.checkStatus()
            svc.sale(EcrSaleFields(amt = "100"))
            assertEquals(listOf("active", "sale"), t.sentTypes())
            svc.close()
        }
    }

    @Test
    fun `the tick is saved in the profile`() {
        val svc = service(1, EcrSimulatorConfig.DEFAULT_CHECK_STATUS_BEFORE)
        var saved: EcrSimulatorConfig? = null
        svc.onConfigChanged = { saved = it }

        svc.setCheckStatusBefore(EcrCommand.SALE, false)

        assertFalse(EcrCommand.SALE in svc.config.value.checkStatusBefore)
        assertFalse(EcrCommand.SALE in saved!!.checkStatusBefore)
        assertTrue(EcrCommand.VOID in saved!!.checkStatusBefore)
        assertFalse(EcrCommand.CHECK_STATUS in EcrSimulatorConfig.CHECK_STATUS_CAPABLE)
        svc.close()
    }
}
