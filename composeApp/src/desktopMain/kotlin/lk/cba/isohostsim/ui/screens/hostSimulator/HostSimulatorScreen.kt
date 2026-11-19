package lk.cba.isohostsim.ui.screens.hostSimulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Tab
import androidx.compose.material.TabPosition
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import lk.cba.isohostsim.data.BitAttribute
import lk.cba.isohostsim.data.Iso8583Data
import lk.cba.isohostsim.data.convertToDest
import lk.cba.isohostsim.data.ResultDialogInterface
import lk.cba.isohostsim.data.model.AppSettings
import lk.cba.isohostsim.logging.LogHistory
import lk.cba.isohostsim.logging.addBounded
import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.data.model.GatewayType
import lk.cba.isohostsim.data.rememberIsoCoroutineScope
import lk.cba.isohostsim.domain.service.hostSimulatorService.HostSimulator
import lk.cba.isocore.IsoUtil
import lk.cba.isohostsim.logging.LogEntry
import lk.cba.isohostsim.ui.navigation.NavigationController
import lk.cba.isohostsim.ui.screens.components.AppBarWithBack
import lk.cba.isohostsim.ui.screens.components.SimulatorHandlerTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import lk.cba.isohostsim.ui.design.PosButton
import lk.cba.isohostsim.ui.design.PosButtonKind
import lk.cba.isohostsim.ui.design.posMuted

enum class HostSimulatorTabs(val title: String, val icon: ImageVector) {
    HOST_HANDLER("ISO8583 Transaction", Icons.Default.CompareArrows),
    LOGS("Logs", Icons.Default.Article),
    SETTINGS("Settings", Icons.Default.Settings),
    TEMPLATE("ISO8583 Template", Icons.Default.Code),
    SEND_MESSAGE("Send Message", Icons.Default.Send),
    UNPACK_MESSAGE("Unsolicited Message", Icons.Default.MarkEmailRead),
    LOAD_TEST("Load Test", Icons.Default.Speed),
}

/**
 * Modern Host Simulator screen
 */
@Composable
fun HostSimulatorScreen(
    window: ComposeWindow,
    navigationController: NavigationController,
    config: GatewayConfig?,
    onBack: () -> Unit,
    onSaveClick: () -> Unit,
    onError: ResultDialogInterface? = null,
) {
    // A server host answers terminals itself from the profile built into the app: testers get the
    // Local Host screen, and the profile stays in the source for developers. Proxy and client
    // gateways keep the full screen below.
    if (config != null && config.gatewayType == GatewayType.SERVER) {
        val local = remember(config.id) {
            lk.cba.isohostsim.domain.service.hostSimulatorService.profile.BuiltInHostProfile.applyTo(config)
            HostSimulator(config).apply {
                composeWindow = window
                if (onError != null) setShowErrorListener(onError)
            }
        }
        DisposableEffect(local) {
            onDispose {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { local.stop() }
            }
        }
        LocalHostScreen(local, onBack = onBack, onSaveClick = onSaveClick)
        return
    }
    var gw: HostSimulator? = null
    val coroutineScope = rememberCoroutineScope()
    DisposableEffect(LocalLifecycleOwner.current) {
        onDispose {
            coroutineScope.launch {
                gw?.stop()
            }
        }
    }
    Scaffold(
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colors.background)
                    .padding(start = 12.dp, end = 32.dp, top = 12.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(config?.name ?: "Unknown", style = MaterialTheme.typography.h5)
                    if (config != null) {
                        val kind = when (config.gatewayType) {
                            GatewayType.SERVER -> "Server gateway"
                            GatewayType.CLIENT -> "Client gateway"
                            else -> "Proxy gateway"
                        }
                        Text(
                            "$kind · ${config.serverAddress.ifBlank { "0.0.0.0" }}:${config.serverPort}",
                            color = posMuted(),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                PosButton(
                    "Save",
                    onClick = { onSaveClick() },
                    kind = PosButtonKind.Secondary,
                    icon = Icons.Default.Save,
                )
            }
        },
        backgroundColor = MaterialTheme.colors.background
    ) { paddingValues ->
        if (config != null) {
            gw = HostSimulator(config)
            gw!!.composeWindow = window
            if (onError != null) {
                gw!!.setShowErrorListener(onError)
            }
            HostSimulator(
                gw = gw!!,
                onSaveClick = onSaveClick,
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No configuration selected")
            }
        }
    }
}

/**
 * Modern Host Simulator implementation
 */
@OptIn(ExperimentalAtomicApi::class)
@Composable
fun HostSimulator(
    gw: HostSimulator,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isStarted by remember { mutableStateOf(gw.started.load()) }
    var transactionCount by remember { mutableStateOf("0") }
    var bytesOutgoing by remember { mutableStateOf(gw.bytesOutgoing) }
    var bytesIncoming by remember { mutableStateOf(gw.bytesIncoming) }
    var connectionCount by remember { mutableStateOf(gw.connectionCount) }
    var concurrentConnections by remember { mutableStateOf(gw.activeClients.size) }
    var isHoldMessage by remember { mutableStateOf(false) }
    var holdMessageTime by remember { mutableStateOf("60") }

    var rawRequest by remember { mutableStateOf("") }
    var request by remember { mutableStateOf("") }
    var rawResponse by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    val logText = remember { mutableStateListOf<LogEntry>() }
    val lastResponseIso = remember { mutableStateOf<Iso8583Data?>(null) }
    val lastResponseRawHex = remember { mutableStateOf("") }
    val sendMsgHistory = remember { mutableStateListOf<TxHistoryEntry>() }

    var waitingRemain by remember { mutableStateOf("0") }
    var sendHoldMessage by remember { mutableStateOf(true) }
    val coroutineScope = rememberIsoCoroutineScope(gw)

    var selectedField = remember { mutableStateOf<BitAttribute?>(null) }
    var selectedFieldIndex = remember { mutableStateOf<Int?>(null) }
    var showBitmapAnalysis = remember { mutableStateOf(false) }
    var showMessageParser = remember { mutableStateOf(true) }
    var isFirst = remember {
        mutableStateOf(
            if (gw.configuration.gatewayType == GatewayType.CLIENT) {
                false
            } else if (gw.configuration.gatewayType == GatewayType.SERVER) {
                true
            }else{
                true
            }
        )
    }
    var animationTrigger = remember { mutableStateOf(0) }
    var rawMessage = remember { mutableStateOf("") }
    var parseError = remember { mutableStateOf<String?>(null) }
    var currentFields = remember { mutableStateOf<Array<BitAttribute>?>(null) }
    var currentBitmap = remember { mutableStateOf<ByteArray?>(null) }
    var searchQuery = remember { mutableStateOf("") }


    // Set up event handlers
    gw.onReceiveFromSource { iso ->
        rawRequest = IsoUtil.bytesToHexString(iso?.rawMessage ?: byteArrayOf())
        request = iso?.logFormat() ?: ""
        response = ""
        rawResponse = ""
        if (isHoldMessage) {
            waitingRemain = "0"
            sendHoldMessage = false
        }
    }

    gw.onReceiveFromDest { iso ->
        response = iso?.logFormat() ?: ""
        val hex = IsoUtil.bytesToHexString(iso?.rawMessage ?: byteArrayOf())
        rawResponse = hex
        lastResponseIso.value = iso as? Iso8583Data
        lastResponseRawHex.value = hex
    }

    gw.onSentToSource { iso ->
        response = iso?.logFormat() ?: ""
        val hex = IsoUtil.bytesToHexString(iso?.rawMessage ?: byteArrayOf())
        rawResponse = hex
        lastResponseIso.value = iso as? Iso8583Data
        lastResponseRawHex.value = hex
    }

    gw.onSentToDest { iso ->
        rawRequest = IsoUtil.bytesToHexString(iso?.rawMessage ?: byteArrayOf())
        request = iso?.logFormat() ?: ""
        response = ""
        rawResponse = ""
        if (isHoldMessage) {
            waitingRemain = "0"
            sendHoldMessage = false
        }
    }

    gw.beforeReceive {
    }

    // The on-disk history behind this gateway's log view. The log panel starts and refreshes it.
    val logHistory = remember(gw.configuration.logFileName, gw.configuration.maxLogSizeInMB) {
        LogHistory.of(gw.configuration.logFileName, gw.configuration.maxLogSizeInMB)
    }

    gw.beforeWriteLog {
        if (AppSettings.enableGlobalLogging) {
            // Bounded window only; the full history is on disk and stays scrollable via LogHistory.
            logText.addBounded(it)
        }
    }

    // Handle hold message countdown
    LaunchedEffect(key1 = isHoldMessage, key2 = sendHoldMessage) {
        if (isHoldMessage && !sendHoldMessage) {
            val holdTimeValue = holdMessageTime.toIntOrNull() ?: 0
            if (holdTimeValue > 0) {
                for (i in 1..holdTimeValue) {
                    waitingRemain = "$i/$holdTimeValue"
                    delay(1000)
                    if (sendHoldMessage) break
                }
                waitingRemain = "0"
                if (!sendHoldMessage) {
                    sendHoldMessage = true
                    gw.sendHoldMessage?.invoke()
                }
            }
        }
    }

    // Update the bytesOutgoing, bytesIncoming, connectionCount values periodically
    LaunchedEffect(key1 = isStarted) {
        if (isStarted) {
            while (isStarted) {
                delay(1000)
                bytesOutgoing = gw.bytesOutgoing
                bytesIncoming = gw.bytesIncoming
                connectionCount = gw.connectionCount
                concurrentConnections = gw.activeClients.size
                transactionCount = (transactionCount.toIntOrNull() ?: 0).let {
                    if (it < gw.connectionCount.get()) gw.connectionCount.toString() else it.toString()
                }
            }
        }
    }

    // Auto-clear logs at configured interval (global setting)
    val autoClearEnabled = AppSettings.autoClearLogsEnabled
    val autoClearInterval = AppSettings.autoClearLogsIntervalMinutes
    LaunchedEffect(autoClearEnabled, autoClearInterval) {
        if (autoClearEnabled) {
            val intervalMs = autoClearInterval * 60 * 1000L
            while (true) {
                delay(intervalMs)
                if (AppSettings.autoClearLogsEnabled) {
                    logText.clear()
                    // Only purge the files when the user asked for that; otherwise the history
                    // stays and the panel keeps showing it, which is the point of keeping it.
                    if (AppSettings.deleteLogFileOnClear) {
                        logHistory?.clear()
                    }
                }
            }
        }
    }

    // Hoisted state for SendMessageTab — survives tab switches
    val sendMsgLiveMessage = remember(gw) {
        mutableStateOf(createDefaultIso8583EditorMessage(gw, isFirst = false))
    }
    val sendMsgBitAttributes = remember(gw) {
        mutableStateOf(sendMsgLiveMessage.value.bitAttributes.clone())
    }
    val sendMsgRawMessageString = remember(gw) {
        mutableStateOf(IsoUtil.bcdToString(sendMsgLiveMessage.value.pack()))
    }
    val sendMsgLastPackedHexNorm = remember(gw) {
        mutableStateOf(normalizeSendTabHex(sendMsgRawMessageString.value))
    }
    val sendMsgSelectedMessage = remember { mutableStateOf<Transaction?>(null) }
    // Persistent-connection state for ASYNC CLIENT mode — survives tab switches
    val sendMsgIsConnected = remember(gw) { mutableStateOf(false) }

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabList =
        if (gw.configuration.gatewayType == GatewayType.PROXY) {
            listOf(
                HostSimulatorTabs.HOST_HANDLER,
                HostSimulatorTabs.LOGS,
                HostSimulatorTabs.SETTINGS,
                HostSimulatorTabs.TEMPLATE,
                HostSimulatorTabs.SEND_MESSAGE
            )
        } else if (gw.configuration.gatewayType == GatewayType.CLIENT){
            listOf(
                HostSimulatorTabs.SEND_MESSAGE,
                HostSimulatorTabs.LOAD_TEST,
                HostSimulatorTabs.LOGS,
                HostSimulatorTabs.TEMPLATE
            )
        } else {
            listOf(
                HostSimulatorTabs.HOST_HANDLER,
                HostSimulatorTabs.LOGS,
                HostSimulatorTabs.SETTINGS,
                HostSimulatorTabs.TEMPLATE
            )
        }.let { tabs ->
            // A profile-linked gateway is configured in its JSON file; the in-app editors would
            // change settings that the next file save overwrites.
            if (gw.configuration.profilePath != null) tabs - HostSimulatorTabs.SETTINGS - HostSimulatorTabs.TEMPLATE else tabs
        }
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        if (gw.configuration.gatewayType == GatewayType.SERVER || gw.configuration.profilePath != null) {
            HostQaBar(config = gw.configuration, onSaveClick = onSaveClick)
        }
        // Tab row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            backgroundColor = Color.Transparent,
            contentColor = MaterialTheme.colors.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 2.dp),
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.customTabIndicatorOffset(tabPositions[selectedTabIndex]),
                    height = 2.dp,
                    color = MaterialTheme.colors.primary
                )
            }
        ) {

            tabList.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(tab.title, fontWeight = FontWeight.SemiBold) },
                    selectedContentColor = MaterialTheme.colors.primary,
                    unselectedContentColor = posMuted()
                )
            }
        }

        // Tab content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 8.dp)
        ) {
            when (tabList[selectedTabIndex]) {
                HostSimulatorTabs.HOST_HANDLER -> SimulatorHandlerTab(
                    simulator = gw,
                    isStarted = isStarted,
                    onStartStopClick = {
                        coroutineScope.launchSafely {
                            if (!isStarted) {
                                gw.start()
                                isStarted = true
                            } else {
                                gw.stop()
                                isStarted = false
                            }
                        }
                    },
                    onClearClick = {
                        rawRequest = ""
                        request = ""
                        rawResponse = ""
                        response = ""
                    },
                    transactionCount = transactionCount,
                    isHoldMessage = isHoldMessage,
                    onHoldMessageChange = {
                        isHoldMessage = it
                        gw.holdMessage = it
                    },
                    holdMessageTime = holdMessageTime,
                    onHoldMessageTimeChange = { holdMessageTime = it },
                    waitingRemain = waitingRemain,
                    onSendClick = {
                        sendHoldMessage = true
                        waitingRemain = "0"
                        coroutineScope.launchSafely {
                            gw.sendHoldMessage?.invoke()
                        }
                    },
                    request = request,
                    rawRequest = rawRequest,
                    response = response,
                    rawResponse = rawResponse
                )

                HostSimulatorTabs.LOGS -> LogTab(
                    logEntries = logText,
                    history = logHistory,
                    liveEntryCap = AppSettings.maxLiveLogEntries,
                    onClearClick = { logText.clear() },
                    connectionCount = connectionCount.get(),
                    bytesIncoming = bytesIncoming.get().toLong(),
                    bytesOutgoing = bytesOutgoing.get().toLong(),
                    concurrentConnections = concurrentConnections
                )

                HostSimulatorTabs.SETTINGS -> {
                    ISO8583SettingsScreen(
                        gw = gw,
                        onSaveClick = onSaveClick
                    )
                }

                HostSimulatorTabs.TEMPLATE -> {
                    Iso8583TemplateScreen(
                        config = gw.configuration,
                        onSaveClick = {
                            onSaveClick()
                            // After saving, re-sync the Send Message tab's liveMessage so that
                            // hasHeader / TPDU / bit-template changes take effect immediately.
                            // convertToDest creates a fresh Iso8583Data with the updated config
                            // (new doNotUseHeaderDest, fixedResponseHeaderDest, bitTemplateDest)
                            // while preserving every field value the user already filled in.
                            val updated = convertToDest(sendMsgLiveMessage.value) ?: return@Iso8583TemplateScreen
                            sendMsgLiveMessage.value = updated
                            sendMsgBitAttributes.value = updated.bitAttributes.clone()
                            val newHex = IsoUtil.bcdToString(updated.pack())
                            sendMsgRawMessageString.value = newHex
                            sendMsgLastPackedHexNorm.value = normalizeSendTabHex(newHex)
                        }
                    )
                }

                HostSimulatorTabs.SEND_MESSAGE -> SendMessageTab(
                    gw = gw,
                    logText = logText,
                    onClearClick = { logText.clear() },
                    liveMessageState = sendMsgLiveMessage,
                    bitAttributesState = sendMsgBitAttributes,
                    rawMessageStringState = sendMsgRawMessageString,
                    lastPackedHexNormState = sendMsgLastPackedHexNorm,
                    selectedMessageState = sendMsgSelectedMessage,
                    isConnectedState = sendMsgIsConnected,
                    lastResponseIsoState = lastResponseIso,
                    lastResponseRawHexState = lastResponseRawHex,
                    transactionHistory = sendMsgHistory,
                )


                HostSimulatorTabs.LOAD_TEST -> LoadTestTab(
                    gw = gw,
                    rawMessageStringState = sendMsgRawMessageString
                )

                // The offline parser is its own studio tool now (ISO8583 Parser).
                HostSimulatorTabs.UNPACK_MESSAGE -> {}
            }
        }
    }
}


// Custom tab indicator offset extension
fun Modifier.customTabIndicatorOffset(
    currentTabPosition: TabPosition
): Modifier = composed {
    val indicatorWidth = 32.dp
    val currentTabWidth = currentTabPosition.width
    val indicatorOffset = currentTabPosition.left + (currentTabWidth - indicatorWidth) / 2

    fillMaxWidth()
        .wrapContentSize(Alignment.BottomStart)
        .offset(x = indicatorOffset)
        .width(indicatorWidth)
}