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
import lk.cba.isohostsim.ui.design.PosCard
import lk.cba.isohostsim.ui.design.SectionTitle
import lk.cba.isohostsim.ui.design.StatusChip
import lk.cba.isohostsim.ui.design.posLine
import lk.cba.isohostsim.ui.design.posMuted

/**
 * The only configuration view for a Host Simulator. A server host answers from the profile built
 * into the app ([BuiltInHostProfile]); that profile is for developers, who change it in the source
 * and rebuild. This shows, read only, what it sets up.
 */
@Composable
fun HostProfileTab(config: GatewayConfig, appState: UnifiedSimulatorState) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PosCard(Modifier.fillMaxWidth(), padding = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(
                    "Built-in profile · ${config.name}",
                    "What this host answers is built into the app. Override replies from the Local Host screen while it runs.",
                    modifier = Modifier.weight(1f),
                )
                StatusChip("Read only")
            }
        }

        PosCard(Modifier.fillMaxWidth(), padding = 20.dp) {
            SectionTitle("What it runs")
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Summary("Type", "${config.gatewayType} · ${config.transmissionType}")
                Summary("Listens on", "${config.serverConnectionType} ${config.serverAddress.ifBlank { "0.0.0.0" }}:${config.serverPort}")
                Summary("Framing", "${config.messageLengthTypeSource} length · TPDU " + when {
                    config.doNotUseHeaderSource -> "none"
                    config.swapResponseTpdu -> "swapped from the request"
                    else -> config.fixedResponseHeaderSource?.joinToString("") { "%02X".format(it) }?.ifEmpty { null } ?: "60 00 00 00 00"
                })
                Summary("Unmatched", config.unmatchedResponseCode?.let { "declined with $it" } ?: "connection closed")
                Summary("Log file", config.logFileName)
            }
            Text("Transactions", style = MaterialTheme.typography.subtitle1, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
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

/** Adds another server host that answers from the built-in profile, for example on another port. */
fun addBuiltInHost(appState: UnifiedSimulatorState) {
    val config = BuiltInHostProfile.newConfig(appState.generateConfigId())
    appState.addConfig(config)
    appState.selectConfig(config.id)
    appState.save()
}
