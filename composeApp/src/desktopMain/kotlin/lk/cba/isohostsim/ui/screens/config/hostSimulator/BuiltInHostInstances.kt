package lk.cba.isohostsim.ui.screens.config.hostSimulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.BuiltInHostProfile
import lk.cba.isohostsim.ui.navigation.stateConfigs.UnifiedSimulatorState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import lk.cba.isohostsim.domain.service.remoteHost.RemoteHostStore
import lk.cba.isohostsim.ui.design.posLine
import lk.cba.isohostsim.ui.design.posMuted

/**
 * What the built-in profile ([BuiltInHostProfile]) sets up, read only. Every instance shares it;
 * developers change it in the source and rebuild. //gimhan added: was the per-host Profile tab.
 */
@Composable
fun BuiltInProfileSummary(config: GatewayConfig) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Summary("Type", "${config.gatewayType} · ${config.transmissionType}")
        Summary("Connection", "${config.serverConnectionType}, on the port each instance picks")
        Summary("Framing", "${config.messageLengthTypeSource} length · TPDU " + when {
            config.doNotUseHeaderSource -> "none"
            config.swapResponseTpdu -> "swapped from the request"
            else -> config.fixedResponseHeaderSource?.joinToString("") { "%02X".format(it) }?.ifEmpty { null } ?: "60 00 00 00 00"
        })
        Summary("Unmatched", config.unmatchedResponseCode?.let { "declined with $it" } ?: "connection closed")
        Text("Transactions", style = MaterialTheme.typography.subtitle1, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        Column {
            config.simulatedTransactionsToSource.forEach { t ->
                val fields = t.fields.orEmpty().indices.filter { t.fields!![it].isSet }.map { it + 1 }
                Box(Modifier.fillMaxWidth().height(1.dp).background(posLine()))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(t.description, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(140.dp))
                    Text(
                        "${t.mti.padStart(4, '0')} · ${t.proCode}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.width(150.dp),
                    )
                    Text("fields ${fields.joinToString(" ")}", color = posMuted(), fontSize = 13.sp)
                }
            }
            if (config.simulatedTransactionsToSource.isEmpty()) Text("None", color = posMuted(), fontSize = 13.sp)
        }
    }
}

@Composable
private fun Summary(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, color = posMuted(), fontSize = 13.sp, modifier = Modifier.width(130.dp))
        Text(value, fontSize = 14.sp)
    }
}

/**
 * Adds an instance of the built-in profile with its own name and the next port after the highest
 * one in use, so it can run beside the others straight away. //gimhan added
 */
fun addHostInstance(appState: UnifiedSimulatorState): GatewayConfig {
    val hosts = appState.hostConfigs.value
    val config = BuiltInHostProfile.newConfig(appState.generateConfigId())
    val ports = RemoteHostStore.load().localPorts
    val highest = hosts.maxOfOrNull { ports[it.id]?.takeIf { p -> p > 0 } ?: it.serverPort } ?: 0
    if (hosts.isNotEmpty()) config.name = nextInstanceName(hosts)
    if (highest in 1..65534) config.serverPort = highest + 1
    BuiltInHostProfile.applyTo(config)
    appState.addConfig(config)
    appState.selectConfig(config.id)
    appState.save()
    return config
}

/** "Host 2", "Host 3", ...: the first such name no instance has yet. //gimhan added */
fun nextInstanceName(hosts: List<GatewayConfig>): String {
    val taken = hosts.map { it.name.trim().lowercase() }.toSet()
    return generateSequence(2) { it + 1 }.map { "Host $it" }.first { it.lowercase() !in taken }
}
