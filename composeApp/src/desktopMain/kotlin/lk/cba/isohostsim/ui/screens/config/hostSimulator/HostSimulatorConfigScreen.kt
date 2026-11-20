package lk.cba.isohostsim.ui.screens.config.hostSimulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.data.model.GatewayType
import lk.cba.isohostsim.domain.service.remoteHost.RemoteHostStore
import lk.cba.isohostsim.ui.design.PageHeader
import lk.cba.isohostsim.ui.design.PosButton
import lk.cba.isohostsim.ui.design.PosButtonKind
import lk.cba.isohostsim.ui.design.PosCard
import lk.cba.isohostsim.ui.design.SectionTitle
import lk.cba.isohostsim.ui.design.StatusChip
import lk.cba.isohostsim.ui.design.Tone
import lk.cba.isohostsim.ui.design.posLine
import lk.cba.isohostsim.ui.design.posMuted
import lk.cba.isohostsim.ui.navigation.Destination
import lk.cba.isohostsim.ui.navigation.NavigationController
import lk.cba.isohostsim.ui.navigation.stateConfigs.UnifiedSimulatorState
import lk.cba.isohostsim.ui.screens.components.FixedOutlinedTextField
import lk.cba.isohostsim.ui.session.SimulatorSessionManager

/**
 * Host Simulator: one built-in profile, run as many instances as needed, each on its own port.
 * //gimhan added: replaces the per-host profile list, since every host now shares one profile.
 */
@Composable
fun HostSimulatorConfigScreen(
    navigationController: NavigationController,
    appState: UnifiedSimulatorState,
) {
    HostInstancesScreen(
        appState = appState,
        onOpen = { cfg ->
            appState.selectConfig(cfg.id)
            SimulatorSessionManager.launchSimulator(cfg)
            navigationController.navigateTo(Destination.Home)
        },
    )
}

/** The instances list, separate from navigation so it can be previewed. //gimhan added */
@Composable
fun HostInstancesScreen(appState: UnifiedSimulatorState, onOpen: (GatewayConfig) -> Unit) {
    val hosts = appState.hostConfigs.value
    val openIds = SimulatorSessionManager.sessions.map { it.config.id }.toSet()
    // Ports a tester picked on the Local Host screen win over the saved config's port.
    var localPorts by remember { mutableStateOf(RemoteHostStore.load().localPorts) }
    fun portOf(cfg: GatewayConfig) = localPorts[cfg.id]?.takeIf { it > 0 } ?: cfg.serverPort

    // Hosts created before instances had their own names all share the profile's name, and two
    // hosts with one name open as the same tab. Give the later ones their own name once.
    LaunchedEffect(Unit) {
        val seen = mutableSetOf<String>()
        var renamed = false
        appState.hostConfigs.value.forEach { cfg ->
            if (!seen.add(cfg.name.trim().lowercase())) {
                val name = nextInstanceName(appState.hostConfigs.value)
                appState.updateConfig(cfg.copy(name = name, modifiedDate = System.currentTimeMillis()))
                seen.add(name.lowercase())
                renamed = true
            }
        }
        if (renamed) appState.save()
    }

    fun commit(cfg: GatewayConfig, name: String, port: Int) {
        if (name == cfg.name && port == portOf(cfg)) return
        appState.updateConfig(cfg.copy(name = name, serverPort = port, modifiedDate = System.currentTimeMillis()))
        val settings = RemoteHostStore.load()
        RemoteHostStore.save(settings.copy(localPorts = settings.localPorts + (cfg.id to port)))
        localPorts = settings.localPorts + (cfg.id to port)
        appState.save()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        PageHeader(
            title = "Host simulator",
            subtitle = "Every instance answers from the profile built into the app. Run several at once, each on its own port.",
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 16.dp),
            actions = {
                PosButton("Add instance", onClick = { addHostInstance(appState) }, icon = Icons.Default.Add)
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 32.dp, end = 32.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PosCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle(
                        "Instances",
                        "Open an instance to start it, watch its transactions and override replies.",
                        modifier = Modifier.weight(1f),
                    )
                    val open = hosts.count { it.id in openIds }
                    Text(
                        "${hosts.size} instance${if (hosts.size == 1) "" else "s"}" + if (open > 0) " · $open open" else "",
                        color = posMuted(),
                        fontSize = 13.sp,
                    )
                }
                if (hosts.isEmpty()) {
                    Divider()
                    Text(
                        "No instances yet. Add one to start a host.",
                        color = posMuted(),
                        modifier = Modifier.padding(20.dp),
                    )
                }
                hosts.forEach { cfg ->
                    Divider()
                    InstanceRow(
                        cfg = cfg,
                        port = portOf(cfg),
                        isOpen = cfg.id in openIds,
                        canDelete = hosts.size > 1,
                        nameTaken = { n -> hosts.any { it.id != cfg.id && it.name.trim().equals(n.trim(), ignoreCase = true) } },
                        portUsedBy = { p -> hosts.firstOrNull { it.id != cfg.id && portOf(it) == p }?.name },
                        onCommit = { n, p -> commit(cfg, n, p) },
                        onOpen = { onOpen(cfg) },
                        onDelete = {
                            appState.deleteConfig(cfg.id)
                            appState.save()
                        },
                    )
                }
            }

            hosts.firstOrNull { it.gatewayType == GatewayType.SERVER }?.let { ProfileCard(it) }
        }
    }
}

@Composable
private fun InstanceRow(
    cfg: GatewayConfig,
    port: Int,
    isOpen: Boolean,
    canDelete: Boolean,
    nameTaken: (String) -> Boolean,
    portUsedBy: (Int) -> String?,
    onCommit: (String, Int) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var nameText by remember(cfg.id, cfg.name) { mutableStateOf(cfg.name) }
    var portText by remember(cfg.id, port) { mutableStateOf(port.takeIf { it > 0 }?.toString().orEmpty()) }
    var confirmDelete by remember(cfg.id) { mutableStateOf(false) }

    val portValue = portText.toIntOrNull()?.takeIf { it in 1..65535 }
    val nameError = when {
        nameText.isBlank() -> "Give the instance a name."
        nameTaken(nameText) -> "Another instance already has this name."
        else -> null
    }
    val portError = if (portValue == null) "Enter a port from 1 to 65535." else null
    val sharedWith = portValue?.let(portUsedBy)

    // Save a valid edit shortly after typing stops.
    LaunchedEffect(nameText, portText) {
        if (nameError != null || portValue == null) return@LaunchedEffect
        delay(500)
        onCommit(nameText.trim(), portValue)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(92.dp)) {
                if (isOpen) StatusChip("Open", Tone.Success, showDot = true) else StatusChip("Closed", Tone.Neutral, showDot = true)
            }
            FixedOutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it.take(40) },
                label = { Text("Name") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !isOpen,
                isError = nameError != null,
            )
            FixedOutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                label = { Text("Port") },
                modifier = Modifier.width(120.dp),
                singleLine = true,
                enabled = !isOpen,
                isError = portError != null,
            )
            if (confirmDelete) {
                PosButton("Delete", onClick = onDelete, kind = PosButtonKind.Danger)
                PosButton("Cancel", onClick = { confirmDelete = false }, kind = PosButtonKind.Secondary)
            } else {
                PosButton(if (isOpen) "Go to tab" else "Open", onClick = onOpen, modifier = Modifier.width(120.dp))
                IconButton(onClick = { confirmDelete = true }, enabled = canDelete && !isOpen) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${cfg.name}")
                }
            }
        }
        val note = when {
            confirmDelete -> "Delete this instance? Its name and port are removed; this can't be undone." to Tone.Danger
            nameError != null -> nameError to Tone.Danger
            portError != null -> portError to Tone.Danger
            sharedWith != null -> "$sharedWith also uses port $portValue. Only one of them can run at a time." to Tone.Warning
            isOpen -> "Close its tab to rename it or change its port." to Tone.Neutral
            cfg.gatewayType != GatewayType.SERVER ->
                "${cfg.gatewayType.name.lowercase().replaceFirstChar(Char::uppercase)} gateway, not the built-in profile." to Tone.Neutral
            else -> null
        }
        if (note != null) {
            Text(
                note.first,
                color = when (note.second) {
                    Tone.Danger -> MaterialTheme.colors.error
                    Tone.Warning -> androidx.compose.ui.graphics.Color(0xFFD98A00)
                    else -> posMuted()
                },
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 104.dp),
            )
        }
    }
}

/** What every instance answers, read only and folded away by default. */
@Composable
private fun ProfileCard(sample: GatewayConfig) {
    var expanded by remember { mutableStateOf(false) }
    PosCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(
                "Built-in profile",
                "Shared by every instance. Developers change it in the source; override replies on an open instance.",
                modifier = Modifier.weight(1f),
            )
            StatusChip("Read only")
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide profile" else "Show profile",
                )
            }
        }
        if (expanded) {
            Box(Modifier.padding(top = 12.dp)) { BuiltInProfileSummary(sample) }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(posLine()))
}
