package lk.cba.isohostsim.ui.screens.hostSimulator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lk.cba.isohostsim.data.getValue
import lk.cba.isohostsim.data.model.AppSettings
import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.domain.service.hostSimulatorService.HostSimulator
import lk.cba.isohostsim.domain.service.hostSimulatorService.ListenChecks
import lk.cba.isohostsim.domain.service.hostSimulatorService.LocalHostFeed
import lk.cba.isohostsim.domain.service.hostSimulatorService.LocalHostState
import lk.cba.isohostsim.domain.service.hostSimulatorService.profile.BuiltInHostProfile
import lk.cba.isohostsim.domain.service.remoteHost.FilterPreset
import lk.cba.isohostsim.domain.service.remoteHost.RemoteHostSettings
import lk.cba.isohostsim.domain.service.remoteHost.RemoteHostStore
import lk.cba.isohostsim.domain.service.remoteHost.TerminalFilter
import lk.cba.isohostsim.logging.LogHistory
import lk.cba.isohostsim.logging.addBounded
import lk.cba.isohostsim.ui.design.NoteBox
import lk.cba.isohostsim.ui.design.PageHeader
import lk.cba.isohostsim.ui.design.PosButton
import lk.cba.isohostsim.ui.design.PosButtonKind
import lk.cba.isohostsim.ui.design.PosCard
import lk.cba.isohostsim.ui.design.SectionTitle
import lk.cba.isohostsim.ui.design.SegmentedChoice
import lk.cba.isohostsim.ui.design.StatusChip
import lk.cba.isohostsim.ui.design.Tone
import lk.cba.isohostsim.ui.design.posLine
import lk.cba.isohostsim.ui.design.posMuted
import lk.cba.isohostsim.ui.design.posSunken
import lk.cba.isohostsim.ui.screens.remoteHost.LabeledField
import lk.cba.isohostsim.ui.screens.remoteHost.OverridesTab
import lk.cba.isohostsim.ui.screens.remoteHost.PresetBar
import lk.cba.isohostsim.ui.screens.remoteHost.TransactionsTable
import lk.cba.isohostsim.logging.LogEntry
import androidx.compose.ui.graphics.Color

private enum class LocalTab(val label: String) {
    TRANSACTIONS("Transactions"), LOGS("Logs"), OVERRIDES("Overrides"), CONFIG("Host config")
}

/**
 * The Local Host: a host simulator on this PC that POS terminals reach over its Wi-Fi hotspot.
 *
 * Testers pick a port and start it, then watch transactions, filter them by terminal, and change
 * replies with overrides, the same way as on the Remote Host. What the host answers is the profile
 * built into the app ([BuiltInHostProfile]); developers change it in the source and rebuild, and the
 * Host config tab shows it read only.
 */
@OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
@Composable
fun LocalHostScreen(gw: HostSimulator, onBack: () -> Unit, onSaveClick: () -> Unit) {
    val config = gw.configuration
    val state = remember(config.id) { LocalHostState.of(config.id) }
    val feed = remember(config.id) { LocalHostFeed(state) }
    DisposableEffect(feed) { onDispose { feed.close() } }

    var settings by remember { mutableStateOf(RemoteHostStore.load()) }
    fun persist(s: RemoteHostSettings) {
        settings = s
        RemoteHostStore.save(s)
    }

    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(gw.started.load()) }
    var busy by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    var portText by remember(config.id) { mutableStateOf((settings.localPorts[config.id] ?: config.serverPort).takeIf { it > 0 }?.toString() ?: "8000") }
    val port = portText.toIntOrNull()?.takeIf { it in 1..65535 }

    // Checks before starting, rerun when the port changes or on "Check again".
    var hotspot by remember { mutableStateOf<ListenChecks.Hotspot?>(null) }
    var portFree by remember { mutableStateOf<Boolean?>(null) }
    var firewall by remember { mutableStateOf<ListenChecks.Firewall?>(null) }
    var checkRun by remember { mutableStateOf(0) }
    LaunchedEffect(running, port, checkRun) {
        if (running) return@LaunchedEffect
        delay(300)
        withContext(Dispatchers.IO) {
            val h = ListenChecks.hotspot()
            hotspot = h
            portFree = port?.let { ListenChecks.portFree(h?.address ?: "0.0.0.0", it) }
            firewall = if (h != null && port != null) ListenChecks.firewall(h.iface, port) else null
        }
    }
    // While running: the hotspot as started, and expired overrides dropped. There is no count of
    // connected terminals: most terminals open a connection per transaction and close it straight
    // after, so an open-socket count says nothing about which devices are using the host.
    LaunchedEffect(running) {
        if (running) hotspot = withContext(Dispatchers.IO) { ListenChecks.hotspot() }
        while (running) {
            state.prune()
            delay(1000)
        }
    }

    val bindAddress = hotspot?.address ?: "0.0.0.0"
    val connectAddress = hotspot?.address ?: "this PC's address"

    fun start() {
        val p = port ?: return
        busy = true
        startError = null
        scope.launch {
            try {
                config.serverAddress = bindAddress
                config.serverPort = p
                persist(settings.copy(localPorts = settings.localPorts + (config.id to p)))
                onSaveClick()
                gw.start()
                running = true
            } catch (e: Exception) {
                startError = "The host didn't start: ${e.message ?: e::class.simpleName}. " +
                    "Another program may be using port $p; pick another port."
                checkRun++
            } finally {
                busy = false
            }
        }
    }

    fun stop(then: (() -> Unit)? = null) {
        busy = true
        scope.launch {
            try {
                gw.stop()
            } finally {
                running = false
                state.onHostStopped()
                busy = false
                then?.invoke()
            }
        }
    }

    // Log lines for the Logs tab, and the file history behind them.
    val logText = remember(config.id) { mutableStateListOf<LogEntry>() }
    gw.beforeWriteLog { if (AppSettings.enableGlobalLogging) logText.addBounded(it) }
    val logHistory = remember(config.logFileName, config.maxLogSizeInMB) { LogHistory.of(config.logFileName, config.maxLogSizeInMB) }

    var tab by remember { mutableStateOf(LocalTab.TRANSACTIONS) }
    var tidText by remember { mutableStateOf(settings.lastTid) }
    var midText by remember { mutableStateOf(settings.lastMid) }
    var applied by remember { mutableStateOf(TerminalFilter(settings.lastTid, settings.lastMid)) }
    LaunchedEffect(applied) { feed.changeFilter(applied) }
    fun applyFilter() {
        val f = TerminalFilter(tidText.trim(), midText.trim())
        applied = f
        persist(settings.copy(lastTid = f.tid, lastMid = f.mid))
    }
    fun usePreset(p: FilterPreset) {
        tidText = p.tid
        midText = p.mid
        applyFilter()
    }

    // The whole page scrolls as one, like the Remote Host, so the table isn't squeezed under the
    // header and filter. //gimhan added
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        val viewportHeight = maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 32.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.width(4.dp))
                PageHeader(
                    title = "Local Host",
                    subtitle = if (running) {
                        "${config.name} · answering your POS terminals. Watch the traffic and change the replies while it runs."
                    } else {
                        "${config.name} · answers your POS terminals from this PC over its Wi-Fi hotspot. Set the port, then start."
                    },
                    leading = {
                        if (running) StatusChip("Running", Tone.Success, showDot = true) else StatusChip("Stopped", Tone.Neutral, showDot = true)
                    },
                    actions = {
                        if (running) {
                            PosButton("Restart", { stop { start() } }, kind = PosButtonKind.Secondary, enabled = !busy)
                            PosButton("Stop host", { stop() }, kind = PosButtonKind.Danger, enabled = !busy, icon = Icons.Default.Stop)
                        } else {
                            PosButton("Start host", ::start, enabled = !busy && port != null && portFree != false, icon = Icons.Default.PlayArrow)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            if (!running) {
                Column(Modifier.fillMaxWidth().padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ListenCard(
                        hotspot = hotspot,
                        portText = portText,
                        onPort = { portText = it.filter(Char::isDigit).take(5) },
                        port = port,
                        portFree = portFree,
                        firewall = firewall,
                        onCheckAgain = { checkRun++ },
                    )
                    startError?.let { NoteBox(it, Tone.Danger) }
                }
                return@Column
            }

            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RunningStrip(connectAddress, port ?: config.serverPort, hotspot, config.name)
                PosCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabeledField("Terminal ID (F41)", tidText, { tidText = it }, Modifier.width(200.dp), onDone = ::applyFilter)
                        LabeledField("Merchant ID (F42)", midText, { midText = it }, Modifier.width(220.dp), onDone = ::applyFilter)
                        PosButton("Show", ::applyFilter, icon = Icons.Default.FilterList)
                        if (!applied.isEmpty) PosButton("Show all", { tidText = ""; midText = ""; applyFilter() }, kind = PosButtonKind.Secondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    PresetBar(
                        presets = settings.presets,
                        applied = applied,
                        current = TerminalFilter(tidText.trim(), midText.trim()),
                        onPick = ::usePreset,
                        onSave = { preset ->
                            persist(settings.copy(presets = settings.presets.filterNot { it.name.equals(preset.name, true) } + preset))
                            usePreset(preset)
                        },
                        onDelete = { preset -> persist(settings.copy(presets = settings.presets - preset)) },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            applied.tid.isNotBlank() && applied.mid.isNotBlank() -> "Showing terminal ${applied.tid} at merchant ${applied.mid}"
                            applied.tid.isNotBlank() -> "Showing terminal ${applied.tid}"
                            applied.mid.isNotBlank() -> "Showing merchant ${applied.mid}"
                            else -> "Showing every terminal connected to this PC"
                        },
                        fontSize = 13.sp,
                        color = posMuted(),
                    )
                }
                val overrides by state.overrides.collectAsState()
                val on = overrides.count { !it.paused }
                SegmentedChoice(LocalTab.entries.toList(), tab, { if (it == LocalTab.OVERRIDES && on > 0) "Overrides · $on on" else it.label }, { tab = it })
            }

            Column(Modifier.fillMaxWidth().padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (tab) {
                    LocalTab.TRANSACTIONS -> TransactionsTable(feed, "No transactions yet. Run one on the terminal and it appears here.")
                    // The log viewer scrolls its own list, so it gets about a screen's height. //gimhan added
                    LocalTab.LOGS -> Box(Modifier.fillMaxWidth().height((viewportHeight - 48.dp).coerceAtLeast(360.dp))) {
                        LogTab(
                            logEntries = logText,
                            history = logHistory,
                            liveEntryCap = AppSettings.maxLiveLogEntries,
                            onClearClick = { logText.clear() },
                            connectionCount = gw.connectionCount.get(),
                            bytesIncoming = gw.bytesIncoming.get().toLong(),
                            bytesOutgoing = gw.bytesOutgoing.get().toLong(),
                        )
                    }
                    LocalTab.OVERRIDES -> OverridesTab(
                        client = feed,
                        filter = applied,
                        myName = "",
                        presets = settings.overridePresets,
                        onMyName = {},
                        onPresets = { persist(settings.copy(overridePresets = it)) },
                        local = true,
                    )
                    LocalTab.CONFIG -> HostConfigView(config, state)
                }
            }
        }
    }
}

// ---------- before starting ----------

@Composable
private fun ListenCard(
    hotspot: ListenChecks.Hotspot?,
    portText: String,
    onPort: (String) -> Unit,
    port: Int?,
    portFree: Boolean?,
    firewall: ListenChecks.Firewall?,
    onCheckAgain: () -> Unit,
) {
    PosCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        SectionTitle(
            "Where terminals connect",
            "The host listens on this PC's Wi-Fi hotspot. Join your POS device to the hotspot, then set the port.",
        )
        Spacer(Modifier.height(16.dp))
        if (hotspot != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(posSunken()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colors.primary)
                Column {
                    Text("Wi-Fi hotspot", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${hotspot.address} · ${hotspot.iface}", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            NoteBox(
                "The Wi-Fi hotspot is off. Turn it on (Settings › Wi-Fi › Turn on Wi-Fi hotspot), then check again. " +
                    "Started without it, the host listens on every network this PC is on.",
                Tone.Warning,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledField("Port", portText, onPort, Modifier.width(140.dp))
            if (hotspot != null && port != null) {
                Column {
                    Text("Set the terminal's host IP and port to", fontSize = 13.sp, color = posMuted(), modifier = Modifier.padding(bottom = 4.dp))
                    AddressBox("${hotspot.address}:$port", large = true)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Checks", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                port == null -> CheckLine(false, "Enter a port from 1 to 65535")
                portFree == true -> CheckLine(true, "Port $port is free on this PC")
                portFree == false -> CheckLine(false, "Port $port is in use by another program. Pick another port, or stop that program.")
            }
            if (hotspot != null) CheckLine(true, "Hotspot ${hotspot.iface} is up")
            when (firewall) {
                ListenChecks.Firewall.Open -> CheckLine(true, "The hotspot firewall lets terminals reach port $port")
                is ListenChecks.Firewall.Blocked -> FirewallWarning(port ?: 0, firewall, onCheckAgain)
                else -> {}
            }
            if (firewall !is ListenChecks.Firewall.Blocked) {
                Text(
                    "Check again",
                    color = MaterialTheme.colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onCheckAgain).padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CheckLine(ok: Boolean, text: String) {
    val color = if (ok) Color(0xFF0B6B43) else MaterialTheme.colors.error
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (ok) Icons.Default.Check else Icons.Default.Warning, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 13.sp, color = color)
    }
}

@Composable
private fun FirewallWarning(port: Int, fw: ListenChecks.Firewall.Blocked, onCheckAgain: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF3DC)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF8A5200), modifier = Modifier.size(16.dp))
            Text(
                "The hotspot firewall (zone ${fw.zone}) blocks port $port, so terminals would get \"connect error\"",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8A5200),
            )
        }
        Text("Run this once in a terminal, then check again:", fontSize = 13.sp, color = Color(0xFF8A5200))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer(Modifier.weight(1f, fill = false)) {
                Text(
                    fw.command,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFE6E9F2),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF0F1420)).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            PosButton(if (copied) "Copied" else "Copy command", { clipboard.setText(AnnotatedString(fw.command)); copied = true }, kind = PosButtonKind.Secondary)
            PosButton("Check again", onCheckAgain, kind = PosButtonKind.Secondary)
        }
    }
}

/** The address terminals connect to, with a Copy button. */
@Composable
private fun AddressBox(address: String, large: Boolean) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(address) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colors.primary.copy(alpha = 0.10f)).padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(address, fontSize = if (large) 18.sp else 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colors.primary)
        IconButton(onClick = { clipboard.setText(AnnotatedString(address)); copied = true }) {
            Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = "Copy address", tint = MaterialTheme.colors.primary, modifier = Modifier.size(18.dp))
        }
    }
}

// ---------- running ----------

@Composable
private fun RunningStrip(address: String, port: Int, hotspot: ListenChecks.Hotspot?, profileName: String) {
    PosCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column {
                Text("Terminals connect to", fontSize = 12.sp, color = posMuted())
                AddressBox(if (hotspot != null) "$address:$port" else "port $port", large = false)
            }
            Fact("Network", hotspot?.let { "Wi-Fi hotspot · ${it.iface}" } ?: "Every network (hotspot off)")
            Fact("Host config", profileName)
            Spacer(Modifier.weight(1f))
            Text("Stop the host to change the port", fontSize = 13.sp, color = posMuted())
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = posMuted())
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- host config ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostConfigView(config: GatewayConfig, state: LocalHostState) {
    val txns by state.transactions.collectAsState()
    val rules = config.simulatedTransactionsToSource
    PosCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(
                "What the host answers · ${config.name}",
                listOfNotNull(
                    config.description.takeIf { it.isNotBlank() },
                    "Built into the app and read only: overrides change replies without touching it.",
                ).joinToString(" "),
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("Length header ${config.messageLengthTypeSource}")
                StatusChip(
                    "TPDU " + when {
                        config.doNotUseHeaderSource -> "none"
                        config.swapResponseTpdu -> "swapped"
                        else -> config.fixedResponseHeaderSource?.joinToString("") { "%02X".format(it) }?.ifEmpty { null } ?: "fixed"
                    },
                )
                StatusChip(config.unmatchedResponseCode?.let { "Unmatched → $it" } ?: "Unmatched: no reply")
            }
        }
        RuleRow(listOf("Rule", "MTI", "Proc. code", "Reply sets", "Seen"), header = true)
        rules.forEach { t ->
            val set = t.fields.orEmpty().indices.filter { t.fields!![it].isSet }.map { it + 1 }
            val rc = t.fields?.getOrNull(38)?.takeIf { it.isSet }?.let { f -> runCatching { f.getValue() }.getOrNull() }
            Box(Modifier.fillMaxWidth().height(1.dp).background(posLine()))
            RuleRow(
                listOf(
                    t.description,
                    t.mti.padStart(4, '0'),
                    if (t.proCode == "*") "any" else t.proCode,
                    "fields ${set.joinToString(" ")}" + (rc?.let { " · F39 $it" } ?: ""),
                    txns.count { it.rule == t.description }.toString(),
                ),
                header = false,
            )
        }
        if (rules.isEmpty()) Text("No rules", color = posMuted(), fontSize = 13.sp, modifier = Modifier.padding(20.dp))
    }
}

private val RULE_WIDTHS = listOf(260.dp, 80.dp, 110.dp)

@Composable
private fun RuleRow(cells: List<String>, header: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(if (header) posSunken() else MaterialTheme.colors.surface).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { i, text ->
            val mod = when {
                i < RULE_WIDTHS.size -> Modifier.width(RULE_WIDTHS[i])
                i == cells.lastIndex -> Modifier.width(80.dp)
                else -> Modifier.weight(1f)
            }
            Text(
                text,
                mod,
                fontSize = 13.sp,
                fontWeight = if (header) FontWeight.Medium else if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (header || i >= 3) posMuted() else MaterialTheme.colors.onSurface,
                fontFamily = if (!header && i in 1..3) FontFamily.Monospace else FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
