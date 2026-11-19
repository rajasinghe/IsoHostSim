package lk.cba.isohostsim.hostprofile

import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.data.model.GatewayType
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.BuiltInHostProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuiltInHostProfileTest {
    @Test
    fun theBuiltInProfileIsValid() {
        val p = BuiltInHostProfile.profile
        assertEquals("all-apps", p.name)
        assertTrue(p.transactions.isNotEmpty())
    }

    @Test
    fun serverHostsGetTheBuiltInProfileAndKeepTheirPort() {
        val linked = GatewayConfig(id = "a", name = "old", gatewayType = GatewayType.SERVER).apply {
            profilePath = "/somewhere/old.json"
            serverPort = 18123
        }
        val hosts = BuiltInHostProfile.install(listOf(linked)) { "new" }
        assertEquals(1, hosts.size)
        assertEquals("all-apps", linked.name)
        assertNull(linked.profilePath)
        assertEquals(18123, linked.serverPort)
        assertTrue(linked.simulatedTransactionsToSource.isNotEmpty())
    }

    @Test
    fun aHostIsAddedWhenThereIsNone() {
        val hosts = BuiltInHostProfile.install(emptyList()) { "new" }
        assertEquals(listOf("new"), hosts.map { it.id })
        assertEquals(GatewayType.SERVER, hosts.single().gatewayType)
    }
}
