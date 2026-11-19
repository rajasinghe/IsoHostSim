package lk.cba.isohostsim.ui.screens.landing

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import lk.cba.isohostsim.ui.screens.components.FixedOutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lk.cba.isohostsim.data.model.StudioTool
import lk.cba.isohostsim.edition.EnabledTools
import lk.cba.isohostsim.ui.screens.HomeScreenViewModel
import lk.cba.isohostsim.ui.screens.OverviewContent
import lk.cba.isohostsim.ui.screens.components.DevelopmentStatus
import lk.cba.isohostsim.ui.screens.components.UnderDevelopmentChip
import lk.cba.isohostsim.ui.navigation.stateConfigs.SimulatorType
import lk.cba.isohostsim.ui.session.SimulatorSessionManager
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.material.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import lk.cba.isohostsim.ui.Studio
import lk.cba.isohostsim.ui.design.Pos
import lk.cba.isohostsim.ui.design.PosButton
import lk.cba.isohostsim.ui.design.PosButtonKind
import lk.cba.isohostsim.ui.design.PosCard
import lk.cba.isohostsim.ui.design.SectionTitle
import lk.cba.isohostsim.ui.design.StatusChip
import lk.cba.isohostsim.ui.design.Tone
import lk.cba.isohostsim.ui.design.posLine
import lk.cba.isohostsim.ui.design.posMuted

/**
 * Comprehensive tool suite following the existing theme
 */
enum class ToolSuite(
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    /** Every tool in the suite, whatever the edition. */
    val allTools: List<StudioTool>
) {

    CORE_SIMULATORS(
        displayName = "Payment Simulators",
        description = "Core payment system simulators for transaction testing",
        icon = Icons.Default.Router,
        allTools = listOf(
            StudioTool.HOST_SIMULATOR,
            StudioTool.HSM_SIMULATOR,
            StudioTool.HSM_COMMAND,
            StudioTool.POS_TERMINAL,
            StudioTool.ATM_SIMULATOR,
            StudioTool.PAYMENT_SWITCH,
            StudioTool.ACQUIRER_GATEWAY,
            StudioTool.APDU_SIMULATOR,
            StudioTool.ISSUER_SYSTEM,
            StudioTool.ECR_SIMULATOR,
            // Beside the Host and ECR simulators so QA can switch between all three tabs.
            StudioTool.LOGCAT_VIEWER,
            StudioTool.MESSAGE_PARSER,
            StudioTool.REMOTE_HOST,
        )
    ),

    EMV_CARD_TOOLS(
        displayName = "EMV & Card Tools",
        description = "Smart card, EMV, and contactless payment tools",
        icon = Icons.Default.CreditCard,
        allTools = listOf(
            StudioTool.EMV_41_CRYPTO,
            StudioTool.EMV_42_CRYPTO,
            StudioTool.MASTERCARD_CRYPTO,
            StudioTool.VSDC_CRYPTO,
            StudioTool.SDA_VERIFICATION,
            StudioTool.DDA_VERIFICATION,
            StudioTool.CAP_TOKEN,
            StudioTool.HCE_VISA,
            StudioTool.SECURE_MESSAGING,
            StudioTool.ATR_PARSER,
            StudioTool.EMV_DATA_PARSER,
            StudioTool.EMV_TAG_DICTIONARY
        )
    ),

    CRYPTOGRAPHY(
        displayName = "Cryptographic Tools",
        description = "Encryption, decryption, and security utilities",
        icon = Icons.Default.VpnKey,
        allTools = listOf(
            StudioTool.AES_CALCULATOR,
            StudioTool.DES_CALCULATOR,
            StudioTool.RSA_CALCULATOR,
            StudioTool.ECDSA_CALCULATOR,
            StudioTool.HASH_CALCULATOR,
            StudioTool.THALES_RSA,
            StudioTool.FPE_CALCULATOR
        )
    ),

    KEY_MANAGEMENT(
        displayName = "Key Management",
        description = "HSM keys, key blocks, and derivation tools",
        icon = Icons.Default.VpnKey,
        allTools = listOf(
            StudioTool.DEA_KEYS,
            StudioTool.KEYSHARE_GENERATOR,
            StudioTool.THALES_KEYS,
            StudioTool.FUTUREX_KEYS,
            StudioTool.ATALLA_KEYS,
            StudioTool.SAFENET_KEYS,
            StudioTool.THALES_KEY_BLOCKS,
            StudioTool.TR31_KEY_BLOCKS,
            StudioTool.SSL_CERTIFICATE,
            StudioTool.RSA_DER_KEYS
        )
    ),

    PAYMENT_UTILITIES(
        displayName = "Payment Utilities",
        description = "Payment processing and validation utilities",
        icon = Icons.Default.Payment,
        allTools = listOf(
            StudioTool.CVV_CALCULATOR,
            StudioTool.AMEX_CSC,
            StudioTool.AS2805_CALCULATOR,
            StudioTool.BITMAP_CALCULATOR,
            StudioTool.MASTERCARD_CSC,
            StudioTool.DUKPT_ISO_9797,
            StudioTool.DUKPT_ISO_AES,
            StudioTool.ISO_IES_9797_1_MAC,
            StudioTool.ANSI_MAC,
            StudioTool.AS2805_MAC,
            StudioTool.TDES_CBC_MAC,
            StudioTool.HMAC_MAC,
            StudioTool.CMAC_MAC,
            StudioTool.RETAIL_MAC,
            StudioTool.MDC_HASH,
            StudioTool.PIN_BLOCK_GENERAL,
            StudioTool.PIN_BLOCK_AES,
            StudioTool.PIN_OFFSET_IBM,
            StudioTool.PIN_PVV,
            StudioTool.ZKA,

        )
    ),

    DATA_CONVERTERS(
        displayName = "Data Converters",
        description = "Format conversion and encoding utilities",
        icon = Icons.Default.Transform,
        allTools = listOf(
            StudioTool.BASE64_ENCODER,
            StudioTool.BASE94_ENCODER,
            StudioTool.BCD_CONVERTER,
            StudioTool.CHARACTER_ENCODER,
            StudioTool.CHECK_DIGIT,
            StudioTool.TRACK2_CODEC
        )
    );

    /** The suite's tools this build exposes (see [EnabledTools]). */
    val tools: List<StudioTool> get() = allTools.filter { EnabledTools.isEnabled(it) }

    companion object {
        /** Suites with at least one tool in this build, in display order. */
        fun visible(): List<ToolSuite> = entries.filter { it.tools.isNotEmpty() }
    }
}


/**
 * Landing page: a welcome line with tool search, the host simulators and the tools.
 */
@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel,
    onToolSelected: (StudioTool) -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(200)
        viewModel.isLoaded.value = true
    }

    var query by viewModel.searchQuery
    val allTools = viewModel.tools.filter { EnabledTools.isEnabled(it) }
    val shownTools = allTools.filter {
        query.isBlank() ||
            it.label.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
    }
    val hostConfigs = Studio.appState.value.hostConfigs.value
    val sessions = SimulatorSessionManager.sessions

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // ── Welcome and search ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Welcome back", style = MaterialTheme.typography.h5)
                Text(
                    "Professional payment testing platform. Pick up where you left off, or start something new.",
                    color = posMuted(),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search tools") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.width(280.dp),
            )
        }

        if (query.isBlank()) {
            // ── Host simulators ──
            if (EnabledTools.isEnabled(StudioTool.HOST_SIMULATOR)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Host simulators")
                    PosCard(padding = 0.dp, modifier = Modifier.fillMaxWidth()) {
                        if (hostConfigs.isEmpty()) {
                            Text(
                                "No host simulators yet. Open the Host Simulator tool to create one.",
                                color = posMuted(),
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                        hostConfigs.forEachIndexed { index, cfg ->
                            val running = sessions.find { it.config.id == cfg.id }
                            HostRow(
                                name = cfg.name,
                                address = "${cfg.serverAddress.ifBlank { "0.0.0.0" }}:${cfg.serverPort}",
                                note = if (cfg.gatewayType == lk.cba.isohostsim.data.model.GatewayType.SERVER) {
                                    "Answers from the built-in profile"
                                } else {
                                    "${cfg.gatewayType.name.lowercase().replaceFirstChar(Char::uppercase)} gateway"
                                },
                                running = running != null,
                                actionLabel = if (running != null) "Open monitor" else "Open",
                                onAction = {
                                    if (running != null) SimulatorSessionManager.activateSession(running.id)
                                    else onToolSelected(StudioTool.HOST_SIMULATOR)
                                },
                                divider = index < hostConfigs.lastIndex,
                            )
                        }
                    }
                }
            }
        }

        // ── Tools ──
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Tools")
            if (shownTools.isEmpty()) {
                Text("No tools match \"$query\".", color = posMuted())
            }
            // As many equal tiles per row as fit, so a wide window is filled edge to edge.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = ((maxWidth + 16.dp) / 244.dp).toInt().coerceIn(1, maxOf(1, shownTools.size))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    shownTools.chunked(columns).forEach { rowTools ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                            rowTools.forEach { tool ->
                                ToolTile(tool = tool, onClick = { onToolSelected(tool) }, modifier = Modifier.weight(1f))
                            }
                            repeat(columns - rowTools.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostRow(
    name: String,
    address: String,
    note: String,
    running: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    divider: Boolean,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1.2f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    StatusChip(if (running) "Running" else "Stopped", if (running) Tone.Success else Tone.Neutral, showDot = true)
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Server ·", color = posMuted(), fontSize = 13.sp)
                    Text(address, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
            Text(note, color = posMuted(), fontSize = 13.sp, modifier = Modifier.weight(1f))
            PosButton(actionLabel, onClick = onAction, kind = PosButtonKind.Secondary)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(posLine()))
    }
}

@Composable
private fun ToolTile(tool: StudioTool, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = tool == StudioTool.HOST_SIMULATOR || tool == StudioTool.MESSAGE_PARSER
    val teal = if (MaterialTheme.colors.isLight) Pos.Teal else Color(0xFF4FD1B5)
    val markBg = if (accent) MaterialTheme.colors.primary.copy(alpha = 0.12f) else teal.copy(alpha = 0.14f)
    val markFg = if (accent) MaterialTheme.colors.primary else teal
    val words = tool.label.split(' ', '-').filter { it.isNotBlank() }
    val mark = if (words.size >= 2) "${words[0].first()}${words[1].first()}".uppercase() else tool.label.take(2).uppercase()
    PosCard(
        modifier = modifier
            .height(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        padding = 18.dp,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(markBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(mark, color = markFg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Text(tool.label, fontWeight = FontWeight.SemiBold)
        Text(
            tool.description,
            color = posMuted(),
            fontSize = 13.sp,
            maxLines = 3,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Small icon button shown on every tool card.
 * - When the tool is NOT in a global tab: shows an "open in new tab" icon.
 * - When the tool IS already open: shows a highlighted "layers" icon as a "switch to tab" affordance.
 * Clicking either state calls the appropriate lambda.
 */
@Composable
internal fun OpenInGlobalTabButton(
    isInGlobalTab: Boolean,
    onOpen: () -> Unit,
    onSwitch: () -> Unit = onOpen,
) {
    val tint   = if (isInGlobalTab) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.35f)
    val bgColor = if (isInGlobalTab) MaterialTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent
    val tip    = if (isInGlobalTab) "Already open — switch to tab" else "Open in global tab"
    val icon   = if (isInGlobalTab) Icons.Default.Layers else Icons.Default.OpenInNew

    Box(
        modifier = Modifier
            .size(24.dp)
            .background(bgColor, RoundedCornerShape(4.dp))
            .clickable(onClick = if (isInGlobalTab) onSwitch else onOpen),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tip,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
    }
}
