package lk.cba.isohostsim.ui.screens.ecrSimulator

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import lk.cba.isohostsim.domain.service.ecr.EcrCommand
import lk.cba.isohostsim.domain.service.ecr.EcrOutcome
import lk.cba.isohostsim.domain.service.ecr.EcrResult
import lk.cba.isohostsim.domain.service.ecr.EcrRunState
import lk.cba.isohostsim.domain.service.ecr.EcrSaleFields
import lk.cba.isohostsim.domain.service.ecr.EcrSimulatorService
import lk.cba.isohostsim.domain.service.ecr.EcrVoidFields
import lk.cba.isohostsim.ui.ErrorRed
import lk.cba.isohostsim.ui.SuccessGreen
import lk.cba.isohostsim.ui.navigation.stateConfigs.ecr.EcrSimulatorConfig
import lk.cba.isohostsim.ui.navigation.stateConfigs.ecr.EcrTransport
import lk.cba.isohostsim.ui.screens.components.FixedOutlinedTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import lk.cba.isohostsim.ui.design.CodeBlock
import lk.cba.isohostsim.ui.design.PosButton
import lk.cba.isohostsim.ui.design.PosButtonKind
import lk.cba.isohostsim.ui.design.PosCard
import lk.cba.isohostsim.ui.design.StatusChip
import lk.cba.isohostsim.ui.design.Tone
import lk.cba.isohostsim.ui.design.posMuted
import androidx.compose.foundation.layout.BoxWithConstraints

private enum class EcrTab(val label: String) { CONSOLE("Console"), EXCHANGES("Exchange Log"), LIBRARY_LOG("Library Log") }

/**
 * Console buttons, in the ECR demo app's order. BIN + Sale is a chain, not a library command.
 * [comingSoon] entries are listed so users can see what is planned; they have no form and cannot be sent.
 */
private enum class EcrAction(val label: String, val command: EcrCommand?, val comingSoon: Boolean = false) {
    CHECK_STATUS("Check Status", EcrCommand.CHECK_STATUS),
    BIN_REQUEST("BIN Request", EcrCommand.BIN_REQUEST),
    SALE("Sale", EcrCommand.SALE),
    BIN_SALE("BIN + Sale", null),
    VOID("Void", EcrCommand.VOID),
    REQUEST_LAST("Request Last", EcrCommand.REQUEST_LAST),
    TRANSFER_KEY("Transfer Key", EcrCommand.TRANSFER_KEY),
    SETTLEMENT("Settlement", EcrCommand.SETTLEMENT),
    LAST_SETTLEMENT("Last Settlement", EcrCommand.LAST_SETTLEMENT),
    CUSTOM_FLOW("Custom command flow", null, comingSoon = true),
}

/**
 * The running ECR Simulator: plays the cash register against a terminal through the CBA ECR
 * library, with the same commands and fields as the ECR demo app.
 */
@Composable
fun EcrSimulatorScreen(
    service: EcrSimulatorService,
    onBack: () -> Unit,
    onSaveConfig: (EcrSimulatorConfig) -> Unit,
) {
    LaunchedEffect(service) {
        service.onConfigChanged = onSaveConfig
        service.startLogTail()
    }
    val config by service.config.collectAsState()
    val runState by service.state.collectAsState()
    val history by service.history.collectAsState()
    var tab by remember { mutableStateOf(EcrTab.CONSOLE) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 32.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(config.name, style = MaterialTheme.typography.h5)
                    RunStatus(runState)
                }
                Text(
                    when (config.transport) {
                        EcrTransport.TCP -> "TCP ${config.terminalIp}:${config.port}"
                        EcrTransport.USB -> "USB ${config.usbPortOverride.ifBlank { "(auto-detect PAX port)" }}"
                    } + " · timeout ${config.timeoutSec}s" + if (config.encrypted) " · encrypted" else "",
                    color = posMuted(),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            PosButton(
                "Transaction cancel",
                onClick = { scope.launch { service.cancel() } },
                kind = PosButtonKind.Danger,
                enabled = runState is EcrRunState.Running,
            )
        }
        TabRow(
            selectedTabIndex = tab.ordinal,
            backgroundColor = Color.Transparent,
            contentColor = MaterialTheme.colors.primary,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            EcrTab.entries.forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
                    text = { Text(t.label, fontWeight = FontWeight.SemiBold) },
                    selectedContentColor = MaterialTheme.colors.primary,
                    unselectedContentColor = posMuted(),
                )
            }
        }
        when (tab) {
            EcrTab.CONSOLE -> ConsoleTab(service, config, runState, history.firstOrNull())
            EcrTab.EXCHANGES -> ExchangeLogTab(history, onClear = service::clearHistory)
            EcrTab.LIBRARY_LOG -> LibraryLogTab(service)
        }
    }
}

@Composable
private fun RunStatus(state: EcrRunState) {
    when (state) {
        EcrRunState.Idle -> StatusChip("Ready", Tone.Success, showDot = true)
        is EcrRunState.Running -> {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(state) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(500)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${state.command.label}: waiting for the terminal (${(now - state.startedAtMs) / 1000}s)",
                    style = MaterialTheme.typography.body2,
                )
            }
        }
    }
}

@Composable
private fun ConsoleTab(
    service: EcrSimulatorService,
    config: EcrSimulatorConfig,
    runState: EcrRunState,
    latest: EcrResult?,
) {
    val scope = rememberCoroutineScope()
    var action by remember { mutableStateOf(EcrAction.SALE) }
    val lastBinRef by service.lastBinReference.collectAsState()
    val busy = runState is EcrRunState.Running

    // Sale form, seeded from the profile defaults like the demo's text fields.
    var amt by remember { mutableStateOf("100") }
    var tipAmt by remember { mutableStateOf("0") }
    var currency by remember(config.currency) { mutableStateOf(config.currency) }
    var detail by remember(config.detail) { mutableStateOf(config.detail) }
    var print by remember(config.print) { mutableStateOf(config.print) }
    var ecrRef by remember(config.nextEcrRef) { mutableStateOf(config.nextEcrRef.toString()) }
    var txnConsequentialNo by remember { mutableStateOf("") }
    var binRef by remember(lastBinRef) { mutableStateOf(lastBinRef) }
    var mobileNo by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    // Void / Request Last / Transfer Key
    var invoiceNo by remember { mutableStateOf("") }
    var pwd by remember(config.testPassword) { mutableStateOf(config.testPassword) }
    var key by remember { mutableStateOf("") }

    fun saleFields() = EcrSaleFields(
        amt = amt.trim(), tipAmt = tipAmt.trim(), currency = currency.trim(), detail = detail,
        ecrRef = ecrRef.trim(), print = print, txnConsequentialNo = txnConsequentialNo.trim(),
        binReference = binRef.trim(), mobileNo = mobileNo.trim(), email = email.trim(),
    )

    fun send() = scope.launch {
        when (action) {
            EcrAction.CHECK_STATUS -> service.checkStatus()
            EcrAction.BIN_REQUEST -> service.binRequest()
            EcrAction.SALE -> service.sale(saleFields())
            EcrAction.BIN_SALE -> service.binSale(saleFields())
            EcrAction.VOID -> service.void(EcrVoidFields(invoiceNo.trim(), pwd, detail, print, mobileNo.trim(), email.trim()))
            EcrAction.REQUEST_LAST -> service.requestLast(detail)
            EcrAction.TRANSFER_KEY -> service.transferKey(key.trim())
            EcrAction.SETTLEMENT -> service.settlement()
            EcrAction.LAST_SETTLEMENT -> service.lastSettlement()
            EcrAction.CUSTOM_FLOW -> return@launch
        }
        // The invoice of an approved sale prefills the void form.
        service.history.value.firstOrNull()?.takeIf { it.command == EcrCommand.SALE && it.outcome == EcrOutcome.APPROVED }
            ?.fields?.get("invoiceNo")?.let { invoiceNo = it }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // Command list, grouped by what the commands are for
        PosCard(Modifier.width(230.dp).fillMaxHeight(), padding = 10.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(
                    "Terminal" to listOf(EcrAction.CHECK_STATUS, EcrAction.BIN_REQUEST),
                    "Payments" to listOf(EcrAction.SALE, EcrAction.BIN_SALE, EcrAction.VOID),
                    "Reports" to listOf(EcrAction.REQUEST_LAST, EcrAction.SETTLEMENT, EcrAction.LAST_SETTLEMENT),
                    "Keys" to listOf(EcrAction.TRANSFER_KEY),
                    "Custom" to listOf(EcrAction.CUSTOM_FLOW),
                ).forEach { (group, actions) ->
                    Text(
                        group,
                        color = posMuted(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp),
                    )
                    actions.forEach { a ->
                        val selected = a == action
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) MaterialTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable(role = Role.Tab) { action = a }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            Text(
                                a.label,
                                color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (a.comingSoon) {
                                StatusChip("Coming soon", Tone.Accent, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
        }

        // Form and result, side by side when the window is wide enough for both
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val sideBySide = maxWidth >= 980.dp
            val form: @Composable () -> Unit = {
                PosCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(action.label, style = MaterialTheme.typography.h6)
                            if (action.comingSoon) StatusChip("Coming soon", Tone.Accent)
                        }
                        when (action) {
                            EcrAction.SALE, EcrAction.BIN_SALE -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Field("Amount (amt)", amt, Modifier.weight(1f)) { amt = it }
                                    Field("Tip (tipAmt)", tipAmt, Modifier.weight(1f)) { tipAmt = it }
                                    Field("Currency", currency, Modifier.weight(1f)) { currency = it }
                                }
                                Text(
                                    "amt is sent as typed, in minor units: 100 means ${currency.ifBlank { "LKR" }} 1.00.",
                                    style = MaterialTheme.typography.caption,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Field("ECR reference (ecrRef)", ecrRef, Modifier.weight(1f)) { ecrRef = it }
                                    Field("txnConsequentialNo", txnConsequentialNo, Modifier.weight(1f)) { txnConsequentialNo = it }
                                    if (action == EcrAction.SALE) {
                                        Field("BIN reference (BinRefferance)", binRef, Modifier.weight(1f)) { binRef = it }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Field("Mobile no. (digital receipt)", mobileNo, Modifier.weight(1f)) { mobileNo = it }
                                    Field("Email (digital receipt)", email, Modifier.weight(1f)) { email = it }
                                }
                                Row { YesNo("Detail", detail) { detail = it }; YesNo("Print", print) { print = it } }
                                if (action == EcrAction.BIN_SALE) {
                                    Text(
                                        "Runs Check Status, then BIN Request, then Sale with the new BIN reference.",
                                        style = MaterialTheme.typography.caption,
                                    )
                                }
                            }
                            EcrAction.VOID -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Field("Invoice no.", invoiceNo, Modifier.weight(1f)) { invoiceNo = it }
                                    Field("Password (pwd)", pwd, Modifier.weight(1f)) { pwd = it }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Field("Mobile no. (digital receipt)", mobileNo, Modifier.weight(1f)) { mobileNo = it }
                                    Field("Email (digital receipt)", email, Modifier.weight(1f)) { email = it }
                                }
                                Row { YesNo("Detail", detail) { detail = it }; YesNo("Print", print) { print = it } }
                            }
                            EcrAction.REQUEST_LAST -> Row { YesNo("Detail", detail) { detail = it } }
                            EcrAction.TRANSFER_KEY -> {
                                Field("Key (32 characters)", key, Modifier.fillMaxWidth()) { key = it }
                                Text("${key.trim().length} of 32 characters", style = MaterialTheme.typography.caption)
                            }
                            EcrAction.BIN_REQUEST -> Text(
                                "Asks the terminal to read a card and return its BIN reference for a BIN sale.",
                                style = MaterialTheme.typography.caption,
                            )
                            EcrAction.CHECK_STATUS -> Text(
                                "Sends 'active'. Also resets the terminal's repeat-command guard.",
                                style = MaterialTheme.typography.caption,
                            )
                            EcrAction.CUSTOM_FLOW -> Text(
                                "Build and run your own sequence of ECR commands. Coming soon.",
                                style = MaterialTheme.typography.caption,
                            )
                            EcrAction.SETTLEMENT, EcrAction.LAST_SETTLEMENT -> Text(
                                "Waits up to 180 seconds for the terminal, as the library does.",
                                style = MaterialTheme.typography.caption,
                            )
                        }
                        action.command?.takeIf { it in EcrSimulatorConfig.CHECK_STATUS_CAPABLE }?.let { cmd ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = cmd in config.checkStatusBefore,
                                    onCheckedChange = { service.setCheckStatusBefore(cmd, it) },
                                )
                                Text("Send Check Status first", style = MaterialTheme.typography.body2)
                            }
                        }
                        action.command?.knownTerminalGap?.let {
                            Text("Known gap: $it", color = ErrorRed, style = MaterialTheme.typography.caption)
                        }
                        if (!action.comingSoon) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PosButton("Send ${action.label}", onClick = { send() }, enabled = !busy)
                                if (busy) {
                                    Spacer(Modifier.width(12.dp))
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
            val result: @Composable () -> Unit = {
                if (latest != null) {
                    ResultCard(latest)
                } else {
                    PosCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                        Text("No result yet", style = MaterialTheme.typography.subtitle1)
                        Text(
                            "Send a command and its request and the terminal's reply show here.",
                            color = posMuted(),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            if (sideBySide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { form() }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { result() }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    form()
                    result()
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: EcrResult) {
    val good = result.succeeded
    PosCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Last result", style = MaterialTheme.typography.subtitle1)
                StatusChip(
                    outcomeLabel(result.outcome) + (result.code?.let { " ($it)" } ?: ""),
                    if (good) Tone.Success else Tone.Danger,
                    showDot = true,
                )
                Text(result.command.label, color = posMuted(), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("${result.durationMs} ms", color = posMuted(), fontSize = 12.sp)
            }
            Text(result.message, style = MaterialTheme.typography.body2)
            val keyFields = listOf(
                "invoiceNo" to "Invoice", "appCode" to "Approval code", "referenceNo" to "RRN",
                "pan" to "Card", "cardType" to "Card type", "amt" to "Amount", "currency" to "Currency",
                "terminalId" to "Terminal ID", "merchantId" to "Merchant ID", "batchNo" to "Batch",
                "traceNo" to "Trace", "ecrRef" to "ECR ref", "binReferenceNo" to "BIN reference",
            ).mapNotNull { (k, label) -> result.fields[k]?.takeIf { it.isNotBlank() }?.let { label to it } }
            if (keyFields.isNotEmpty()) {
                Divider()
                keyFields.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { (label, value) ->
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.caption)
                                SelectionContainer { Text(value, style = MaterialTheme.typography.body2) }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Divider()
            JsonBlock("Response", result.response)
        }
    }
}

@Composable
private fun ExchangeLogTab(history: List<EcrResult>, onClear: () -> Unit) {
    val time = remember { SimpleDateFormat("HH:mm:ss.SSS") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${history.size} exchanges, newest first", style = MaterialTheme.typography.body2)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClear, enabled = history.isNotEmpty()) { Text("Clear") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history) { r ->
                PosCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${time.format(Date(r.startedAtMs))}  ${r.command.label} (${r.command.wireName})  →  " +
                                "${outcomeLabel(r.outcome)}${r.code?.let { " $it" } ?: ""}  ·  ${r.durationMs} ms",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.body2,
                            color = if (r.succeeded) SuccessGreen else ErrorRed,
                        )
                        r.request?.let { JsonBlock("Request (before the library adds its checksum)", it) }
                        JsonBlock("Response", r.response)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryLogTab(service: EcrSimulatorService) {
    val log by service.libraryLog.collectAsState()
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            "Today's log from the CBA ECR library (~/.iso8583studio/ecr/PAX_EDC_ECR_logs). It shows the raw frames and timings.",
            style = MaterialTheme.typography.caption,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colors.surface, RoundedCornerShape(6.dp))
                .padding(8.dp)
                .verticalScroll(rememberScrollState(), reverseScrolling = true),
        ) {
            SelectionContainer {
                Text(log.ifBlank { "Nothing logged yet today." }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.caption)
            }
        }
    }
}

@Composable
private fun JsonBlock(title: String, text: String) {
    val clipboard = LocalClipboardManager.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold)
            IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(14.dp))
            }
        }
        // Pretty-printed and in its own scroll area, so a long terminal reply no longer shows as one
        // squeezed line. The copy button above still copies the raw text exactly as received.
        val pretty = remember(text) { prettyJson(text) }
        val scroll = rememberScrollState()
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().heightIn(max = 420.dp)) {
            Box(Modifier.verticalScroll(scroll)) {
                SelectionContainer { CodeBlock(pretty) }
            }
            Box(Modifier.matchParentSize()) {
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scroll),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp, horizontal = 2.dp),
                )
            }
        }
    }
}

private val prettyJsonFormat = Json { prettyPrint = true }

/** Indents a JSON object/array for display; anything that is not JSON is shown unchanged. */
private fun prettyJson(text: String): String {
    val trimmed = text.trim().trimEnd('\u0000')
    if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return text
    return runCatching {
        prettyJsonFormat.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(trimmed))
    }.getOrDefault(text)
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    FixedOutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
    )
}

@Composable
private fun YesNo(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
        Checkbox(checked = value == "Y", onCheckedChange = { onChange(if (it) "Y" else "N") })
        Text(label)
    }
}

private fun outcomeLabel(outcome: EcrOutcome): String = when (outcome) {
    EcrOutcome.APPROVED -> "Approved"
    EcrOutcome.REJECTED -> "Rejected"
    EcrOutcome.TERMINAL_NAK -> "Refused by terminal"
    EcrOutcome.LIBRARY_NAK -> "Library error"
    EcrOutcome.ACKNOWLEDGED -> "Acknowledged"
    EcrOutcome.UNKNOWN -> "Unrecognised reply"
}
