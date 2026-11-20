package lk.cba.isohostsim.ui.screens.remoteHost

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.Switch
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import lk.cba.isohostsim.domain.service.parser.IsoPacketDecoder
import lk.cba.isohostsim.ui.screens.parser.ParserComparison
import lk.cba.isohostsim.ui.screens.parser.ParserHandoff
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.size
import lk.cba.isohostsim.domain.service.remoteHost.FilterPreset
import lk.cba.isohostsim.domain.service.remoteHost.DEFAULT_OVERRIDE_PRESETS
import lk.cba.isohostsim.domain.service.remoteHost.OverridePreset
import lk.cba.isohostsim.domain.service.remoteHost.OverrideRequest
import lk.cba.isohostsim.domain.service.remoteHost.RemoteConnection
import lk.cba.isohostsim.domain.service.remoteHost.RemoteHealth
import lk.cba.isohostsim.domain.service.remoteHost.RemoteHostClient
import lk.cba.isohostsim.domain.service.remoteHost.HostFeed
import lk.cba.isohostsim.domain.service.remoteHost.RemoteHostSettings
import lk.cba.isohostsim.domain.service.remoteHost.RemoteHostStore
import lk.cba.isohostsim.domain.service.remoteHost.RemoteLog
import lk.cba.isohostsim.domain.service.remoteHost.RemoteMessage
import lk.cba.isohostsim.domain.service.remoteHost.RemoteOverride
import lk.cba.isohostsim.domain.service.remoteHost.RemoteProfile
import lk.cba.isohostsim.domain.service.remoteHost.RemoteTxn
import lk.cba.isohostsim.domain.service.remoteHost.SavedRemoteHost
import lk.cba.isohostsim.domain.service.remoteHost.TerminalFilter
import lk.cba.isohostsim.domain.service.remoteHost.normalizeBaseUrl
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private enum class RemoteTab(val label: String) {
    TRANSACTIONS("Transactions"), LOGS("Logs"), OVERRIDES("Overrides"), CONFIG("Host config")
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault())

private fun time(iso: String): String = runCatching { TIME.format(Instant.parse(iso)) }.getOrDefault(iso)
private fun dateTime(iso: String): String = runCatching { DATE_TIME.format(Instant.parse(iso)) }.getOrDefault(iso)

/** "until 18 Nov 19:02", or "until the host stops" for a local override with no expiry. */
private fun until(expiresAt: String?): String = expiresAt?.let { "until ${dateTime(it)}" } ?: "until the host stops"

private fun rcTone(rc: String?): Tone = when (rc) {
    null -> Tone.Warning
    "00" -> Tone.Success
    else -> Tone.Danger
}

/**
 * Remote Host: watches a shared remote host (host_sim_backend_node) that many terminals use.
 * The tester enters their TID, MID or both; the host then streams only that traffic. Overrides
 * set here apply to one TID only, so testers sharing the host don't affect each other.
 */
@Composable
fun RemoteHostScreen() {
    var settings by remember { mutableStateOf(RemoteHostStore.load()) }
    fun persist(s: RemoteHostSettings) {
        settings = s
        RemoteHostStore.save(s)
    }

    val selected = settings.hosts.find { it.id == settings.selectedId } ?: settings.hosts.firstOrNull()
    val client = remember(selected?.url) { selected?.let { RemoteHostClient(it.url) } }
    DisposableEffect(client) { onDispose { client?.close() } }

    var tidText by remember { mutableStateOf(settings.lastTid) }
    var midText by remember { mutableStateOf(settings.lastMid) }
    var applied by remember { mutableStateOf(TerminalFilter(settings.lastTid, settings.lastMid)) }
    LaunchedEffect(client) { client?.start(applied) }

    var tab by remember { mutableStateOf(RemoteTab.TRANSACTIONS) }
    var adding by remember { mutableStateOf(settings.hosts.isEmpty()) }

    fun applyFilter() {
        val f = TerminalFilter(tidText.trim(), midText.trim())
        applied = f
        client?.changeFilter(f)
        persist(settings.copy(lastTid = f.tid, lastMid = f.mid))
    }

    fun usePreset(p: FilterPreset) {
        tidText = p.tid
        midText = p.mid
        applyFilter()
    }

    val idleConnection = remember { MutableStateFlow<RemoteConnection>(RemoteConnection.Idle) }
    val noHealth = remember { MutableStateFlow<RemoteHealth?>(null) }
    val connection by (client?.connection ?: idleConnection).collectAsState()
    val health by (client?.health ?: noHealth).collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colors.background),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader(
                title = "Remote Host",
                subtitle = "Watch a shared host's traffic for your terminal, and set overrides for it",
                leading = { if (client != null) ConnectionChip(connection) },
                actions = {
                    if (settings.hosts.isNotEmpty()) {
                        HostPicker(settings.hosts, selected) { persist(settings.copy(selectedId = it.id)) }
                    }
                    PosButton("Add host", { adding = !adding }, kind = PosButtonKind.Secondary, icon = Icons.Default.Add)
                },
            )
        }

        if (adding) {
            item {
                AddHostCard(
                    onAdd = { host ->
                        persist(settings.copy(hosts = settings.hosts + host, selectedId = host.id))
                        adding = false
                    },
                    onCancel = if (settings.hosts.isNotEmpty()) ({ adding = false }) else null,
                )
            }
        }

        if (client == null || selected == null) {
            if (!adding) item { NoteBox("Add a remote host to start.") }
            return@LazyColumn
        }

        item {
            PosCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField("Terminal ID (F41)", tidText, { tidText = it }, Modifier.width(200.dp), onDone = ::applyFilter)
                    LabeledField("Merchant ID (F42)", midText, { midText = it }, Modifier.width(220.dp), onDone = ::applyFilter)
                    PosButton("Show", ::applyFilter, icon = Icons.Default.FilterList)
                    if (!applied.isEmpty) {
                        PosButton("Show all", {
                            tidText = ""; midText = ""; applyFilter()
                        }, kind = PosButtonKind.Secondary)
                    }
                    Spacer(Modifier.weight(1f))
                    HostSummary(selected, health)
                }
                Spacer(Modifier.height(12.dp))
                PresetBar(
                    presets = settings.presets,
                    applied = applied,
                    current = TerminalFilter(tidText.trim(), midText.trim()),
                    onPick = ::usePreset,
                    onSave = { preset ->
                        // A preset with the same name is replaced, so saving again updates it.
                        persist(settings.copy(presets = settings.presets.filterNot { it.name.equals(preset.name, true) } + preset))
                        usePreset(preset)
                    },
                    onDelete = { preset -> persist(settings.copy(presets = settings.presets - preset)) },
                )
                Spacer(Modifier.height(10.dp))
                Text(filterDescription(applied), fontSize = 13.sp, color = posMuted())
                if (applied.isEmpty) {
                    Spacer(Modifier.height(8.dp))
                    NoteBox(
                        "You are watching every terminal on this host. Enter your TID, MID or both to see only your own traffic.",
                        Tone.Warning,
                    )
                }
            }
        }

        item { SegmentedChoice(RemoteTab.entries.toList(), tab, { it.label }, { tab = it }) }

        when (tab) {
            RemoteTab.TRANSACTIONS -> transactionsTab(client)
            RemoteTab.LOGS -> logsTab(client)
            RemoteTab.OVERRIDES -> item {
                OverridesTab(
                    client,
                    applied,
                    settings.myName,
                    settings.overridePresets,
                    onMyName = { persist(settings.copy(myName = it)) },
                    onPresets = { persist(settings.copy(overridePresets = it)) },
                )
            }
            RemoteTab.CONFIG -> item { HostConfigTab(client) }
        }
    }
}

/**
 * Saved TID/MID pairs as chips: one click shows that terminal. "Save as preset" names the TID and
 * MID typed above; saving under an existing name updates it.
 */
@Composable
internal fun PresetBar(
    presets: List<FilterPreset>,
    applied: TerminalFilter,
    current: TerminalFilter,
    onPick: (FilterPreset) -> Unit,
    onSave: (FilterPreset) -> Unit,
    onDelete: (FilterPreset) -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Presets", fontSize = 13.sp, color = posMuted(), modifier = Modifier.padding(end = 4.dp))
        if (presets.isEmpty() && !naming) {
            Text("None yet. Type a TID or MID above and save it.", fontSize = 13.sp, color = posMuted())
        }
        Row(
            Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { p -> PresetChip(p, selected = p.toFilter() == applied, onPick = { onPick(p) }, onDelete = { onDelete(p) }) }
        }
        if (naming) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                singleLine = true,
                placeholder = { Text("Name, e.g. IM30 HNB", color = posMuted()) },
                modifier = Modifier.width(220.dp).onEnter {
                    if (name.isNotBlank()) {
                        onSave(FilterPreset(name.trim(), current.tid, current.mid))
                        naming = false
                    }
                },
            )
            PosButton("Save", {
                onSave(FilterPreset(name.trim(), current.tid, current.mid))
                naming = false
            }, enabled = name.isNotBlank())
            PosButton("Cancel", { naming = false }, kind = PosButtonKind.Secondary)
        } else {
            PosButton("Save as preset", {
                name = current.tid.ifBlank { current.mid }
                naming = true
            }, kind = PosButtonKind.Secondary, enabled = !current.isEmpty, icon = Icons.Default.BookmarkAdd)
        }
    }
}

@Composable
private fun PresetChip(p: FilterPreset, selected: Boolean, onPick: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.clip(shape)
            .background(if (selected) MaterialTheme.colors.primary.copy(alpha = 0.14f) else posSunken())
            .border(1.dp, if (selected) MaterialTheme.colors.primary else posLine(), shape)
            .clickable(onClick = onPick)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                p.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
            )
            Text(
                listOfNotNull(p.tid.ifBlank { null }?.let { "TID $it" }, p.mid.ifBlank { null }?.let { "MID $it" }).joinToString(" · "),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = posMuted(),
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onDelete).padding(4.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove preset ${p.name}", tint = posMuted(), modifier = Modifier.width(16.dp).height(16.dp))
        }
    }
}

private fun filterDescription(f: TerminalFilter): String = when {
    f.tid.isNotBlank() && f.mid.isNotBlank() -> "Showing terminal ${f.tid} at merchant ${f.mid}"
    f.tid.isNotBlank() -> "Showing terminal ${f.tid}"
    f.mid.isNotBlank() -> "Showing every terminal at merchant ${f.mid}"
    else -> "Showing every terminal"
}

@Composable
private fun ConnectionChip(c: RemoteConnection) = when (c) {
    RemoteConnection.Live -> StatusChip("Live", Tone.Success, showDot = true)
    RemoteConnection.Connecting -> StatusChip("Connecting", Tone.Neutral, showDot = true)
    RemoteConnection.Idle -> StatusChip("Not connected", Tone.Neutral)
    is RemoteConnection.Retrying -> StatusChip("Reconnecting: ${c.message}", Tone.Danger, showDot = true)
}

@Composable
private fun HostSummary(host: SavedRemoteHost, health: RemoteHealth?) {
    Column(horizontalAlignment = Alignment.End) {
        Text(host.url, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = posMuted())
        if (health != null) {
            Text(
                "Terminals connect to port ${health.iso.port} · profile ${health.profile.name} · " +
                    "${health.iso.connections} open now · history ${health.retentionHours} h" +
                    if (health.store == "memory") " (memory only)" else "",
                fontSize = 13.sp,
                color = posMuted(),
            )
        }
    }
}

@Composable
private fun HostPicker(hosts: List<SavedRemoteHost>, selected: SavedRemoteHost?, onSelect: (SavedRemoteHost) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PosButton(selected?.name ?: "Choose a host", { open = true }, kind = PosButtonKind.Secondary, icon = Icons.Default.ArrowDropDown)
        DropdownMenu(open, { open = false }) {
            hosts.forEach { h ->
                DropdownMenuItem(onClick = { onSelect(h); open = false }) {
                    Column {
                        Text(h.name, fontWeight = FontWeight.Medium)
                        Text(h.url, fontSize = 12.sp, color = posMuted())
                    }
                }
            }
        }
    }
}

@Composable
internal fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    onDone: (() -> Unit)? = null,
) {
    Column(modifier) {
        Text(label, fontSize = 13.sp, color = posMuted(), modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = posMuted()) },
            modifier = Modifier.fillMaxWidth().then(
                if (onDone != null) Modifier.onEnter(onDone) else Modifier,
            ),
        )
    }
}

/** Runs [action] when Enter is pressed in the field. */
private fun Modifier.onEnter(action: () -> Unit): Modifier = this.onPreviewKeyEvent {
    if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
        action()
        true
    } else {
        false
    }
}

@Composable
private fun AddHostCard(onAdd: (SavedRemoteHost) -> Unit, onCancel: (() -> Unit)?) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Tone>?>(null) }
    val scope = rememberCoroutineScope()
    PosCard(Modifier.fillMaxWidth(), padding = 16.dp) {
        SectionTitle("Add a remote host", "The address of the host's API, for example 10.0.0.5:8590")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledField("Name", name, { name = it }, Modifier.width(220.dp), placeholder = "QA host")
            LabeledField("Address", url, { url = it }, Modifier.width(320.dp), placeholder = "10.0.0.5:8590")
            PosButton(if (checking) "Checking..." else "Check and add", {
                val base = normalizeBaseUrl(url)
                checking = true
                message = null
                scope.launch {
                    try {
                        val h = RemoteHostClient.probe(base)
                        onAdd(SavedRemoteHost(UUID.randomUUID().toString(), name.ifBlank { h.profile.name }, base))
                    } catch (e: Exception) {
                        message = "Could not reach a remote host at $base: ${e.message ?: e.javaClass.simpleName}" to Tone.Danger
                    } finally {
                        checking = false
                    }
                }
            }, enabled = url.isNotBlank() && !checking)
            if (onCancel != null) PosButton("Cancel", onCancel, kind = PosButtonKind.Secondary)
        }
        message?.let { (text, tone) ->
            Spacer(Modifier.height(10.dp))
            NoteBox(text, tone)
        }
    }
}

// ---------- transactions ----------

private fun LazyListScope.transactionsTab(client: RemoteHostClient) {
    item { TransactionsTable(client) }
}

@Composable
internal fun TransactionsTable(client: HostFeed, emptyText: String = "No transactions yet for this filter. Run one on the terminal and it appears here.") {
    val txns by client.transactions.collectAsState()
    val older by client.olderTransactionsCursor.collectAsState()
    var expanded by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (txns.isEmpty()) {
        NoteBox(emptyText)
        return
    }
    // Tick two received packets to open them side by side in the ISO8583 Parser. //gimhan added
    var picked by remember { mutableStateOf(listOf<String>()) }
    val pickedTxns = txns.filter { it.id in picked && !it.request?.hex.isNullOrBlank() }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            when (pickedTxns.size) {
                0 -> "Tick two received packets to compare them in the ISO8583 Parser."
                1 -> "1 packet ticked. Tick one more to compare."
                else -> "2 packets ticked."
            },
            color = posMuted(),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        if (pickedTxns.isNotEmpty()) {
            PosButton("Clear", { picked = emptyList() }, kind = PosButtonKind.Secondary)
        }
        PosButton(
            "Compare selected",
            {
                val (earlier, later) = pickedTxns.sortedBy { it.createdAt }
                ParserHandoff.compare(
                    ParserComparison(
                        firstHex = earlier.request!!.hex,
                        secondHex = later.request!!.hex,
                        firstLabel = "${earlier.mti ?: "?"} · ${time(earlier.createdAt)}" + (earlier.tid?.let { " · $it" } ?: ""),
                        secondLabel = "${later.mti ?: "?"} · ${time(later.createdAt)}" + (later.tid?.let { " · $it" } ?: ""),
                    )
                )
            },
            icon = Icons.Default.CompareArrows,
            enabled = pickedTxns.size == 2,
        )
    }
    PosCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        TxnRow(
            header = true,
            cells = listOf("Time", "Invoice", "Batch", "TID", "MID", "MTI", "Proc. code", "STAN", "RC", "ms", "Rule"),
            leading = {},
        )
        txns.forEach { t ->
            Divider()
            Box(Modifier.fillMaxWidth().clickable { expanded = if (expanded == t.id) null else t.id }) {
                TxnRow(
                    header = false,
                    cells = listOf(
                        time(t.createdAt), invoiceOf(t), batchOf(t), t.tid ?: "-", t.mid ?: "-", t.mti ?: "-", t.processingCode ?: "-",
                        t.stan ?: "-", "", t.durationMs.toString(), ruleLabel(t),
                    ),
                    rc = t.responseCode,
                    highlighted = expanded == t.id || t.id in picked,
                    leading = {
                        val canPick = !t.request?.hex.isNullOrBlank()
                        val ticked = t.id in picked
                        // Only two at a time: untick one to pick another.
                        val enabled = canPick && (ticked || pickedTxns.size < 2)
                        Icon(
                            if (ticked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = if (ticked) "Untick for comparing" else "Tick for comparing",
                            tint = when {
                                ticked -> MaterialTheme.colors.primary
                                enabled -> posMuted()
                                else -> posMuted().copy(alpha = 0.3f)
                            },
                            modifier = Modifier.size(20.dp).clickable(enabled = enabled, role = Role.Checkbox) {
                                picked = if (ticked) picked - t.id else picked + t.id
                            },
                        )
                    },
                )
            }
            if (expanded == t.id) TxnDetail(t)
        }
    }
    if (older != null) {
        Spacer(Modifier.height(12.dp))
        PosButton(if (loading) "Loading..." else "Load older", {
            loading = true
            scope.launch {
                runCatching { client.loadOlderTransactions() }
                loading = false
            }
        }, kind = PosButtonKind.Secondary, enabled = !loading)
    }
}

private fun ruleLabel(t: RemoteTxn): String = buildString {
    append(
        when (t.disposition) {
            "matched" -> t.rule ?: "matched"
            "unmatched" -> "unmatched"
            "no-reply" -> "no reply"
            "override-no-response" -> "no response (override)"
            "parse-error" -> "unreadable"
            else -> t.disposition
        },
    )
    when {
        t.overrideKind == "rejectSettlement" -> append(" · settlement rejected (95)")
        t.overrideId != null && t.disposition != "override-no-response" -> append(" · override")
    }
}

/** The invoice number (F62), from the host's record or, for an older host, from the decoded request. */
private fun invoiceOf(t: RemoteTxn): String =
    t.invoice ?: t.request?.fields?.find { it.field == 62 }?.value?.trim()?.ifBlank { null } ?: "-"

/** The batch number (F60), from the host's record or, for an older host, from the decoded request. */
private fun batchOf(t: RemoteTxn): String =
    t.batch ?: t.request?.fields?.find { it.field == 60 }?.value?.trim()?.let { v ->
        // In a 0320 batch upload F60 is the original MTI and STAN, not a batch number.
        when {
            v.length == 6 && v.all(Char::isDigit) -> v
            v.length == 8 && v.all(Char::isDigit) -> v.drop(2)
            else -> null
        }
    } ?: "-"

private val COLUMN_WIDTHS: List<Dp> = listOf(110.dp, 80.dp, 70.dp, 100.dp, 140.dp, 60.dp, 90.dp, 70.dp, 60.dp, 60.dp)

/** The column that shows the response code as a chip. */
private const val RC_COLUMN = 8

@Composable
private fun TxnRow(
    header: Boolean,
    cells: List<String>,
    rc: String? = null,
    highlighted: Boolean = false,
    leading: (@Composable () -> Unit)? = null, //gimhan added: the compare tick
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (highlighted) posSunken() else MaterialTheme.colors.surface)
            .padding(horizontal = 16.dp, vertical = if (header) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) Box(Modifier.width(32.dp)) { leading() }
        cells.forEachIndexed { i, text ->
            val mod = if (i < COLUMN_WIDTHS.size) Modifier.width(COLUMN_WIDTHS[i]) else Modifier.weight(1f)
            Box(mod) {
                if (!header && i == RC_COLUMN) {
                    StatusChip(rc ?: "none", rcTone(rc))
                } else {
                    Text(
                        text,
                        fontSize = 13.sp,
                        fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
                        color = if (header) posMuted() else MaterialTheme.colors.onSurface,
                        fontFamily = if (header || i == COLUMN_WIDTHS.size) FontFamily.Default else FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TxnDetail(t: RemoteTxn) {
    Column(Modifier.fillMaxWidth().background(posSunken()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "From ${t.remote} · received ${time(t.createdAt)}" + (t.respondedAt?.let { " · answered ${time(it)}" } ?: " · not answered"),
            fontSize = 13.sp,
            color = posMuted(),
        )
        t.error?.let { NoteBox(it, Tone.Warning) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MessagePanel("Request", t.request, Modifier.weight(1f))
            MessagePanel("Response", t.response, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MessagePanel(title: String, msg: RemoteMessage?, modifier: Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (msg == null) title else "$title · MTI ${msg.mti}" + (msg.tpdu?.let { " · TPDU $it" } ?: ""),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            if (msg != null) CopyPacketButton(msg.hex)
        }
        Spacer(Modifier.height(6.dp))
        if (msg == null) {
            Text("None sent", fontSize = 13.sp, color = posMuted())
            return@Column
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colors.surface)) {
            msg.fields.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text("F${f.field}", Modifier.width(44.dp), fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colors.primary)
                    Text(IsoPacketDecoder.fieldName(f.field), Modifier.width(190.dp), fontSize = 13.sp, color = posMuted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(f.value, Modifier.weight(1f), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                if (f.field == 55) EmvBreakdown(f.value)
            }
        }
        Spacer(Modifier.height(6.dp))
        SelectionContainer {
            Text(msg.hex, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = posMuted())
        }
    }
}

/**
 * Copies the whole packet as hex, length header included, so it can be pasted into the ISO8583
 * Parser or a bug report. Card data in it is masked unless the host stores raw card data.
 */
@Composable
private fun CopyPacketButton(hex: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(hex) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    PosButton(
        if (copied) "Copied" else "Copy packet",
        {
            clipboard.setText(AnnotatedString(hex))
            copied = true
        },
        kind = PosButtonKind.Secondary,
        icon = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
    )
}

@Composable
private fun EmvBreakdown(hex: String) {
    val tags = remember(hex) { runCatching { IsoPacketDecoder.tlv(hex) }.getOrDefault(emptyList()) }
    if (tags.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(start = 56.dp, end = 12.dp, bottom = 6.dp)) {
        tags.forEach { tag ->
            Row {
                Text(tag.tag, Modifier.width(56.dp), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colors.primary)
                Text(tag.name, Modifier.width(220.dp), fontSize = 12.sp, color = posMuted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tag.valueHex, Modifier.weight(1f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(posLine()))

// ---------- logs ----------

private fun LazyListScope.logsTab(client: RemoteHostClient) {
    item { LogsList(client) }
}

@Composable
private fun LogsList(client: RemoteHostClient) {
    val logs by client.logs.collectAsState()
    var problemsOnly by remember { mutableStateOf(false) }
    val shown = if (problemsOnly) logs.filter { it.level == "warn" || it.level == "error" } else logs
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(problemsOnly, { problemsOnly = it })
        Text("Only warnings and errors", fontSize = 14.sp)
        Spacer(Modifier.width(16.dp))
        Text("${shown.size} lines", fontSize = 13.sp, color = posMuted())
    }
    Spacer(Modifier.height(8.dp))
    if (shown.isEmpty()) {
        NoteBox("No log lines for this filter yet.")
        return
    }
    PosCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        shown.take(1000).forEachIndexed { i, l ->
            if (i > 0) Divider()
            LogRow(l)
        }
    }
}

@Composable
private fun LogRow(l: RemoteLog) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time(l.createdAt), Modifier.width(110.dp), fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = posMuted())
        Box(Modifier.width(80.dp)) {
            StatusChip(
                l.level,
                when (l.level) {
                    "error" -> Tone.Danger
                    "warn" -> Tone.Warning
                    else -> Tone.Neutral
                },
            )
        }
        Text(l.category, Modifier.width(100.dp), fontSize = 13.sp, color = posMuted())
        Text(l.tid ?: "", Modifier.width(100.dp), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Text(l.message, Modifier.weight(1f), fontSize = 13.sp)
    }
}

// ---------- overrides ----------

/** The TID the host uses for a settlement reject that covers every terminal. */
private const val ALL_TERMINALS = "*"

private enum class OverrideKind(val label: String) { DECLINE("Decline"), NO_RESPONSE("No response"), DELAY("Delay only") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverridesTab(
    client: HostFeed,
    filter: TerminalFilter,
    myName: String,
    presets: List<OverridePreset>,
    onMyName: (String) -> Unit,
    onPresets: (List<OverridePreset>) -> Unit,
    /**
     * The host on this PC: one tester, so an override with no Terminal ID applies to every
     * terminal, and one with no minutes lasts until the host stops. There is no "Your name".
     */
    local: Boolean = false,
) {
    val overrides by client.overrides.collectAsState()
    val scope = rememberCoroutineScope()
    var tid by remember(filter.tid) { mutableStateOf(filter.tid) }
    var minutes by remember { mutableStateOf(if (local) "" else "120") }
    var name by remember(myName) { mutableStateOf(myName) }
    var message by remember { mutableStateOf<Pair<String, Tone>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var settleMessage by remember { mutableStateOf<Pair<String, Tone>?>(null) }
    val terminal = tid.trim()
    // With no Terminal ID the settlement reject applies to every terminal on the host ("*").
    val settleTarget = terminal.ifBlank { ALL_TERMINALS }
    val settleReject = overrides.find { it.kind == "rejectSettlement" && it.tid == settleTarget }
    val settleRejectAll = overrides.find { it.kind == "rejectSettlement" && it.tid == ALL_TERMINALS && !it.paused }
    // Declines and delays need a Terminal ID on a shared host; on this PC an empty one means every terminal.
    val generalTarget = if (local) terminal.ifBlank { ALL_TERMINALS } else terminal
    val canApply = generalTarget.isNotEmpty()
    val current = if (!canApply) null else overrides.find { it.kind == "general" && it.tid == generalTarget }
    val setBy = if (local) null else name.trim().ifBlank { null }

    /** Runs one call to the host; a failure shows under the card it came from. */
    fun run(onError: (String) -> Unit, action: suspend () -> Unit) {
        busy = true
        message = null
        settleMessage = null
        if (!local && name.trim() != myName) onMyName(name.trim())
        scope.launch {
            try {
                action()
            } catch (e: Exception) {
                onError("The host didn't accept that: ${e.message}")
            } finally {
                busy = false
            }
        }
    }
    val showError: (String) -> Unit = { message = it to Tone.Danger }
    fun apply(p: OverridePreset) = run(showError) { client.setOverride(p.toRequest(generalTarget, setBy, minutes.toIntOrNull())) }
    fun togglePause(o: RemoteOverride, onError: (String) -> Unit = showError) = run(onError) { client.pauseOverride(o.id, !o.paused, setBy) }
    fun remove(o: RemoteOverride) = run(showError) { client.deleteOverride(o.id) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PosCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("Terminal ID", tid, { tid = it }, Modifier.width(180.dp))
                if (!local) LabeledField("Your name", name, { name = it }, Modifier.width(180.dp))
                LabeledField("Lasts (minutes)", minutes, { minutes = it.filter(Char::isDigit).take(4) }, Modifier.width(130.dp))
                Text(
                    if (local) {
                        "Leave the Terminal ID empty to apply to every terminal, and Lasts empty to keep it until you stop the host."
                    } else {
                        "Leave the Terminal ID empty to reject settlements from every terminal. Declines, no response and delays need one."
                    },
                    fontSize = 13.sp,
                    color = posMuted(),
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }
        }

        PosCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            SectionTitle(
                "Decline, no response or delay",
                if (local) {
                    "Click one to apply it to every reply, replacing the current one. " +
                        "Click it again to pause it: terminals get normal replies until you resume."
                } else {
                    "Click one to apply it to every reply this terminal gets, replacing its current one. " +
                        "Click it again to pause it: the terminal gets normal replies until you resume."
                },
            )
            Spacer(Modifier.height(12.dp))
            if (!canApply) {
                NoteBox("Enter a Terminal ID above to use these.", Tone.Warning)
            } else {
                CurrentOverride(
                    if (generalTarget == ALL_TERMINALS) "Every terminal" else "Terminal $generalTarget",
                    current,
                    busy,
                    onPause = { togglePause(it) },
                    onRemove = { remove(it) },
                )
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p ->
                    val on = current != null && p.matches(current)
                    OverridePresetChip(
                        p,
                        state = when {
                            !on -> PresetState.Off
                            current!!.paused -> PresetState.Paused
                            else -> PresetState.On
                        },
                        enabled = canApply && !busy,
                        onClick = { if (on) togglePause(current!!) else apply(p) },
                        onDelete = { onPresets(presets - p) },
                    )
                }
                PosButton(
                    if (custom) "Hide custom" else "Custom…",
                    { custom = !custom },
                    kind = PosButtonKind.Secondary,
                    icon = if (custom) Icons.Default.Close else Icons.Default.Tune,
                )
            }
            if (custom) {
                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(16.dp))
                CustomOverrideForm(
                    canSet = canApply && !busy,
                    missingDefaults = DEFAULT_OVERRIDE_PRESETS.filterNot { d -> presets.any { it.name == d.name } },
                    onSet = ::apply,
                    onSave = { p -> onPresets(presets.filterNot { it.name.equals(p.name, true) } + p) },
                    onRestore = { onPresets(presets + it) },
                )
            }
            message?.let { (text, tone) ->
                Spacer(Modifier.height(10.dp))
                NoteBox(text, tone)
            }
        }

        PosCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle(
                        "Reject settlement with 95",
                        "The settlement (0500, processing code 920000) gets F39 95, so the terminal sends its batch upload. " +
                            "The 0320 uploads and the closing settlement (960000) are still approved, so the batch closes.",
                    )
                    settleReject?.let {
                        Text(
                            listOfNotNull(
                                if (it.paused) "Paused" else "On",
                                it.setBy?.let { by -> "set by $by" },
                                until(it.expiresAt),
                            ).joinToString(" · "),
                            fontSize = 13.sp,
                            color = posMuted(),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = settleReject != null && !settleReject.paused,
                    enabled = !busy,
                    onCheckedChange = { on ->
                        val onError: (String) -> Unit = { settleMessage = it to Tone.Danger }
                        when {
                            on && settleReject?.paused == true -> togglePause(settleReject, onError)
                            on -> run(onError) {
                                client.setOverride(
                                    OverrideRequest(tid = settleTarget, kind = "rejectSettlement", setBy = setBy, minutes = minutes.toIntOrNull()),
                                )
                            }
                            else -> settleReject?.let { o -> run(onError) { client.deleteOverride(o.id) } }
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            when {
                settleTarget == ALL_TERMINALS && local -> Text(
                    "Applies to every terminal connected to this PC. Enter a Terminal ID above to limit it to one terminal.",
                    fontSize = 13.sp,
                    color = posMuted(),
                )
                settleTarget == ALL_TERMINALS -> NoteBox(
                    "Applies to every terminal on this host, including other testers' terminals. " +
                        "Enter a Terminal ID above to limit it to one terminal.",
                    Tone.Warning,
                )
                settleRejectAll != null -> NoteBox(
                    "It is also on for every terminal" + (settleRejectAll.setBy?.let { " (set by $it)" } ?: "") +
                        ", so this terminal's settlement gets 95 even with this switch off.",
                    Tone.Warning,
                )
                else -> Text("Applies to terminal $settleTarget only.", fontSize = 13.sp, color = posMuted())
            }
            settleMessage?.let { (text, tone) ->
                Spacer(Modifier.height(10.dp))
                NoteBox(text, tone)
            }
        }

        SectionTitle(
            "Active overrides",
            if (local) "Pause or remove them when you're done." else "Every tester on this host sees these. Pause or remove yours when you're done.",
        )
        if (overrides.isEmpty()) {
            NoteBox("No overrides are active. Every terminal gets the host config's normal replies.")
        } else {
            PosCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                overrides.forEachIndexed { i, o ->
                    if (i > 0) Divider()
                    OverrideRow(
                        o,
                        mine = o.setBy != null && o.setBy == myName,
                        highlighted = o.tid == filter.tid,
                        busy = busy,
                        onPause = { togglePause(o) },
                        onRemove = { remove(o) },
                    )
                }
            }
        }
    }
}

/** What the terminal on the Overrides tab gets now, with Pause/Resume and Remove. */
@Composable
private fun CurrentOverride(who: String, o: RemoteOverride?, busy: Boolean, onPause: (RemoteOverride) -> Unit, onRemove: (RemoteOverride) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(posSunken()).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (o == null) {
            StatusChip("Normal", Tone.Neutral)
            Text("$who gets the host config's normal replies.", fontSize = 14.sp, modifier = Modifier.weight(1f))
            return@Row
        }
        if (o.paused) StatusChip("Paused", Tone.Warning) else StatusChip("On", Tone.Danger, showDot = true)
        Column(Modifier.weight(1f)) {
            Text(
                (o.note?.let { "$it: " } ?: "") + describe(o) + if (o.paused) " (paused, normal replies for now)" else "",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(overrideMeta(o), fontSize = 13.sp, color = posMuted())
        }
        PauseButton(o, busy, onPause)
        PosButton("Remove", { onRemove(o) }, kind = PosButtonKind.Danger, enabled = !busy)
    }
}

@Composable
private fun PauseButton(o: RemoteOverride, busy: Boolean, onPause: (RemoteOverride) -> Unit) {
    PosButton(
        if (o.paused) "Resume" else "Pause",
        { onPause(o) },
        kind = PosButtonKind.Secondary,
        enabled = !busy,
        icon = if (o.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
    )
}

private enum class PresetState { Off, On, Paused }

@Composable
private fun OverridePresetChip(p: OverridePreset, state: PresetState, enabled: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val accent = when (state) {
        PresetState.On -> MaterialTheme.colors.primary
        PresetState.Paused -> MaterialTheme.colors.onSurface.copy(alpha = 0.55f)
        PresetState.Off -> null
    }
    Row(
        Modifier.clip(shape)
            .background(if (state == PresetState.On) MaterialTheme.colors.primary.copy(alpha = 0.14f) else posSunken())
            .border(1.dp, accent ?: posLine(), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state != PresetState.Off) {
            Icon(
                if (state == PresetState.On) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = accent!!,
                modifier = Modifier.width(16.dp).height(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Column {
            Text(
                p.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = when {
                    !enabled -> posMuted()
                    state == PresetState.On -> MaterialTheme.colors.primary
                    else -> MaterialTheme.colors.onSurface
                },
            )
            Text(
                when (state) {
                    PresetState.On -> "On · click to pause"
                    PresetState.Paused -> "Paused · click to resume"
                    PresetState.Off -> p.detail
                },
                fontSize = 11.sp,
                fontFamily = if (state == PresetState.Off) FontFamily.Monospace else FontFamily.Default,
                color = posMuted(),
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onDelete).padding(4.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove preset ${p.name}", tint = posMuted(), modifier = Modifier.width(16.dp).height(16.dp))
        }
    }
}

/** Any code, no response or delay. "Save as preset" adds it as a chip under the name given. */
@Composable
private fun CustomOverrideForm(
    canSet: Boolean,
    missingDefaults: List<OverridePreset>,
    onSet: (OverridePreset) -> Unit,
    onSave: (OverridePreset) -> Unit,
    onRestore: (List<OverridePreset>) -> Unit,
) {
    var kind by remember { mutableStateOf(OverrideKind.DECLINE) }
    var code by remember { mutableStateOf("05") }
    var delay by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val delaySeconds = delay.toIntOrNull()?.takeIf { it > 0 }
    val valid = (kind != OverrideKind.DECLINE || code.trim().length == 2) && (kind != OverrideKind.DELAY || delaySeconds != null)
    fun preset(fallbackName: String) = OverridePreset(
        name = name.trim().ifBlank { fallbackName },
        responseCode = code.trim().takeIf { kind == OverrideKind.DECLINE },
        delaySeconds = delaySeconds.takeIf { kind != OverrideKind.NO_RESPONSE },
        noResponse = kind == OverrideKind.NO_RESPONSE,
    )

    SegmentedChoice(OverrideKind.entries.toList(), kind, { it.label }, { kind = it })
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (kind == OverrideKind.DECLINE) LabeledField("Response code (F39)", code, { code = it.take(2) }, Modifier.width(160.dp))
        if (kind != OverrideKind.NO_RESPONSE) {
            LabeledField(
                if (kind == OverrideKind.DELAY) "Delay (seconds)" else "Delay (optional)",
                delay,
                { delay = it.filter(Char::isDigit).take(3) },
                Modifier.width(140.dp),
            )
        }
        LabeledField("Name", name, { name = it.take(30) }, Modifier.width(220.dp))
        PosButton("Set override", { onSet(preset("")) }, enabled = canSet && valid)
        PosButton(
            "Save as preset",
            {
                onSave(preset(""))
                name = ""
            },
            kind = PosButtonKind.Secondary,
            enabled = valid && name.isNotBlank(),
            icon = Icons.Default.BookmarkAdd,
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "The name shows on the override so other testers know what it is for, and labels the preset.",
            fontSize = 13.sp,
            color = posMuted(),
            modifier = Modifier.weight(1f),
        )
        if (missingDefaults.isNotEmpty()) {
            PosButton("Restore default presets", { onRestore(missingDefaults) }, kind = PosButtonKind.Secondary)
        }
    }
}

private fun describe(o: RemoteOverride): String = when {
    o.kind == "rejectSettlement" -> "Settlement rejected with 95 (batch upload)"
    o.noResponse == true -> "No response"
    o.responseCode != null -> "Decline with F39 ${o.responseCode}" + (o.delaySeconds?.let { " after ${it}s" } ?: "")
    o.delaySeconds != null -> "Normal reply after ${o.delaySeconds}s"
    else -> "Normal reply"
}

private fun overrideMeta(o: RemoteOverride): String = listOfNotNull(
    o.setBy?.let { "Set by $it" },
    o.pausedChangedAt?.let { at ->
        (if (o.paused) "paused" else "resumed") + (o.pausedChangedBy?.let { " by $it" } ?: "") + " at ${dateTime(at)}"
    },
    until(o.expiresAt),
).joinToString(" · ")

@Composable
private fun OverrideRow(
    o: RemoteOverride,
    mine: Boolean,
    highlighted: Boolean,
    busy: Boolean,
    onPause: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(if (highlighted) posSunken() else MaterialTheme.colors.surface).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (o.tid == ALL_TERMINALS) "All terminals" else o.tid,
            Modifier.width(110.dp),
            fontFamily = if (o.tid == ALL_TERMINALS) FontFamily.Default else FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Column(Modifier.weight(1f)) {
            Text((o.note?.takeIf { o.kind == "general" }?.let { "$it: " } ?: "") + describe(o), fontSize = 14.sp)
            Text(overrideMeta(o), fontSize = 13.sp, color = posMuted())
        }
        if (o.paused) StatusChip("Paused", Tone.Warning)
        if (mine) StatusChip("Yours", Tone.Accent)
        PauseButton(o, busy) { onPause() }
        PosButton("Remove", onRemove, kind = PosButtonKind.Danger, enabled = !busy)
    }
}

// ---------- host config ----------

@Composable
private fun HostConfigTab(client: RemoteHostClient) {
    val profile by client.profile.collectAsState()
    val p: RemoteProfile = profile ?: run {
        NoteBox("Loading the host config...")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NoteBox(
            "Read only. Every terminal on this host gets these replies, apart from per-terminal overrides. " +
                "To change them, a developer edits config/host-profile.json on the host and restarts it.",
        )
        PosCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            SectionTitle(p.name, p.description.ifBlank { null })
            Spacer(Modifier.height(8.dp))
            Text("Profile version ${p.hash.take(12)}", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = posMuted())
            Text(
                "Unmatched requests: " + (p.unmatched.responseCode?.let { "declined with F39 $it" } ?: "no reply"),
                fontSize = 13.sp,
                color = posMuted(),
            )
        }
        PosCard(Modifier.fillMaxWidth(), padding = 0.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                listOf("Rule" to 260.dp, "MTI" to 70.dp, "Proc. code" to 100.dp, "Echoes" to 200.dp).forEach { (h, w) ->
                    Text(h, Modifier.width(w), fontSize = 13.sp, color = posMuted(), fontWeight = FontWeight.Medium)
                }
                Text("Sets", Modifier.weight(1f), fontSize = 13.sp, color = posMuted(), fontWeight = FontWeight.Medium)
            }
            p.transactions.forEach { r ->
                Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(r.name, Modifier.width(260.dp), fontSize = 13.sp)
                    Text(r.match.mti, Modifier.width(70.dp), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Text(r.match.processingCode, Modifier.width(100.dp), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Text(r.respond.echo.joinToString(", ") { "F$it" }, Modifier.width(200.dp), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        r.respond.set.entries.joinToString(", ") { "F${it.key}=${it.value}" },
                        Modifier.weight(1f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
