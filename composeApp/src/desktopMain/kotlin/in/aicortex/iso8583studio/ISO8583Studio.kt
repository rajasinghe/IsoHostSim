package `in`.aicortex.iso8583studio

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import `in`.aicortex.iso8583studio.domain.service.posSimulatorService.avd.ProcessRegistry
import `in`.aicortex.iso8583studio.analytics.Analytics
import `in`.aicortex.iso8583studio.edition.EnabledTools
import `in`.aicortex.iso8583studio.analytics.DesktopAnalyticsSink
import `in`.aicortex.iso8583studio.analytics.StudioAnalytics
import `in`.aicortex.iso8583studio.data.ExceptionHandler
import `in`.aicortex.iso8583studio.data.model.AnalyticsConsent
import `in`.aicortex.iso8583studio.data.model.AppSettings
import `in`.aicortex.iso8583studio.ui.screens.about.AnalyticsConsentDialog
import `in`.aicortex.iso8583studio.data.ResultDialogInterface
import `in`.aicortex.iso8583studio.data.model.GatewayConfig
import `in`.aicortex.iso8583studio.domain.FileImporter
import `in`.aicortex.iso8583studio.domain.ImportResult
import `in`.aicortex.iso8583studio.domain.utils.ExportResult
import `in`.aicortex.iso8583studio.domain.utils.FileExporter
import `in`.aicortex.iso8583studio.ui.AppTheme
import `in`.aicortex.iso8583studio.ui.screens.components.LocalBackNavigationAvailable
import `in`.aicortex.iso8583studio.ui.ErrorRed
import `in`.aicortex.iso8583studio.ui.Studio.appState
import `in`.aicortex.iso8583studio.ui.SuccessGreen
import `in`.aicortex.iso8583studio.ui.navigation.Destination
import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.SimulatorType
import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.apdu.APDUSimulatorConfig
import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.hsm.HSMSimulatorConfig
import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.pos.POSSimulatorConfig
import `in`.aicortex.iso8583studio.ui.screens.components.StatusBadge
import `in`.aicortex.iso8583studio.ui.screens.about.AboutDialog
import `in`.aicortex.iso8583studio.ui.screens.apduSimulator.v2.ApduSimulatorV2Screen
import `in`.aicortex.iso8583studio.ui.screens.hostSimulator.HostSimulatorScreen
import `in`.aicortex.iso8583studio.ui.screens.hsmSimulator.HsmSimulatorScreen
import `in`.aicortex.iso8583studio.ui.screens.posTerminal.POSTerminalSimulatorScreen
import `in`.aicortex.iso8583studio.ui.navigation.rememberNavigationController
import `in`.aicortex.iso8583studio.ui.session.GlobalSimulatorTabBar
import `in`.aicortex.iso8583studio.ui.session.SimulatorSessionManager
import `in`.aicortex.iso8583studio.ui.session.ToolUsageTracker
import `in`.aicortex.iso8583studio.data.model.StudioTool
import iso8583studio.composeapp.generated.resources.Res
import iso8583studio.composeapp.generated.resources.app
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import `in`.aicortex.iso8583studio.ui.GlassBackdrop
import `in`.aicortex.iso8583studio.ui.GlassWindowEffect
import `in`.aicortex.iso8583studio.ui.GlossyMenuBar
import `in`.aicortex.iso8583studio.ui.MenuBarItem
import `in`.aicortex.iso8583studio.ui.MenuBarMenu
import `in`.aicortex.iso8583studio.ui.MenuBarSeparator
import `in`.aicortex.iso8583studio.ui.MenuBarSubMenu
import `in`.aicortex.iso8583studio.ui.detectWindowsDarkMode
import java.awt.Desktop
import java.awt.desktop.AboutHandler
import androidx.compose.foundation.isSystemInDarkTheme

enum class DialogType {
    SUCCESS, ERROR, NONE
}

class ISO8583Studio {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // Set system look-and-feel before creating any windows
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName())
            } catch (_: Throwable) { }

            // Reap long-lived child processes on exit. `onCloseRequest = ::exitApplication` does no
            // cleanup, and an Android emulator started from a POS session outlives its parent by
            // default — leaving a CPU core and ~2 GB of RAM held after the app is gone.
            ProcessRegistry.installShutdownHook()

            AppSettings.recordLaunch()
            // Starts reporting only if consent was previously granted; a no-op otherwise.
            StudioAnalytics.init()
            Analytics.sink = DesktopAnalyticsSink

            application {

            Thread.currentThread().uncaughtExceptionHandler = ExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler())

            val windowState = remember {
                WindowState(
                    size = DpSize(width = 1800.dp, height = 800.dp),
                )
            }
            var showAboutDialog by remember { mutableStateOf(false) }
            val isoCoroutine = rememberCoroutineScope()

            // Override macOS application menu "About" to show our custom About dialog
            LaunchedEffect(Unit) {
                if (System.getProperty("os.name").lowercase().contains("mac")) {
                    try {
                        Desktop.getDesktop().setAboutHandler(AboutHandler { showAboutDialog = true })
                    } catch (_: Exception) {
                        // setAboutHandler not supported on this platform
                    }
                }
            }

            // Detect OS dark mode (registry fallback for Windows)
            val osDarkMode = remember {
                detectWindowsDarkMode() ?: false
            }

            AppTheme {
                val isDark = isSystemInDarkTheme() || osDarkMode
                Window(
                    onCloseRequest = ::exitApplication,
                    title = "ISO8583Studio",
                    state = windowState,
                    icon = painterResource(Res.drawable.app),
                    transparent = false,
                    undecorated = false,
                ) {
                    GlassWindowEffect(
                        window = window,
                        backdrop = GlassBackdrop.MICA,
                        darkMode = isDark,
                    )
                    Navigator(screen = Destination.Home) { navigator ->
                        val navigationController = rememberNavigationController(navigator)

                        // ─── About Dialog ───────────────────────────────
                        if (showAboutDialog) {
                            AboutDialog(onCloseRequest = { showAboutDialog = false })
                        }

                        // ─── Analytics Consent (first run only) ─────────
                        // Asked once. Nothing has been transmitted at this point: StudioAnalytics.init()
                        // above is inert while consent is UNSET.
                        // Never asked in the QA edition, which ships with analytics off.
                        if (EnabledTools.analyticsEnabled && AppSettings.analyticsConsent == AnalyticsConsent.UNSET) {
                            AnalyticsConsentDialog(
                                onAccept = {
                                    AppSettings.updateAnalyticsConsent(AnalyticsConsent.GRANTED)
                                    StudioAnalytics.init()
                                },
                                onDecline = {
                                    AppSettings.updateAnalyticsConsent(AnalyticsConsent.DENIED)
                                }
                            )
                        }

                        LaunchedEffect(AppSettings.analyticsConsent) {
                            if (AppSettings.analyticsConsent.isGranted) StudioAnalytics.appOpen()
                        }

                        // ─── Error/Success Dialog ───────────────────────
                        var showErrorDialog by remember {
                            mutableStateOf<Pair<DialogType, @Composable (() -> Unit)>?>(null)
                        }

                        appState.value.resultDialogInterface = object : ResultDialogInterface {
                            override fun onError(item: @Composable (() -> Unit)) {
                                showErrorDialog = Pair(DialogType.ERROR, item)
                            }
                            override fun onSuccess(item: @Composable (() -> Unit)) {
                                showErrorDialog = Pair(DialogType.SUCCESS, item)
                            }
                        }

                        if (showErrorDialog?.first == DialogType.ERROR) {
                            AlertDialog(
                                onDismissRequest = { showErrorDialog = null },
                                confirmButton = {
                                    Button(
                                        onClick = { showErrorDialog = null },
                                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
                                    ) { Text("OK") }
                                },
                                title = {
                                    StatusBadge(text = "ERROR", color = ErrorRed, modifier = Modifier.padding(bottom = 8.dp))
                                },
                                text = showErrorDialog!!.second,
                                shape = MaterialTheme.shapes.medium,
                                backgroundColor = MaterialTheme.colors.surface
                            )
                        }

                        if (showErrorDialog?.first == DialogType.SUCCESS) {
                            AlertDialog(
                                onDismissRequest = { showErrorDialog = null },
                                confirmButton = {
                                    Button(
                                        onClick = { showErrorDialog = null },
                                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
                                    ) { Text("OK") }
                                },
                                title = {
                                    StatusBadge(text = "SUCCESS", color = SuccessGreen, modifier = Modifier.padding(bottom = 8.dp))
                                },
                                text = showErrorDialog!!.second,
                                shape = MaterialTheme.shapes.medium,
                                backgroundColor = MaterialTheme.colors.surface
                            )
                        }

                        appState.value.setComposableWindow(window)

                        // ═══════════════════════════════════════════════════
                        //  CORE CHANGE: Global Tab Bar + Session-Aware Content
                        // ═══════════════════════════════════════════════════
                        //
                        // Architecture:
                        //   ┌──────────────────────────────────────────┐
                        //   │  Global Tab Bar (session tabs)           │
                        //   ├──────────────────────────────────────────┤
                        //   │                                          │
                        //   │  Content Area:                           │
                        //   │    - Main Navigation (Voyager) OR        │
                        //   │    - Active Simulator Session            │
                        //   │                                          │
                        //   └──────────────────────────────────────────┘
                        //
                        // Running simulators persist in memory via
                        // SimulatorSessionManager even when the user
                        // navigates to tools/calculators/config screens.

                        val activeSessionId by SimulatorSessionManager.activeSessionId
                        val poppedOutIds = SimulatorSessionManager.poppedOutSessionIds

                        Column(modifier = Modifier.fillMaxSize()) {
                            // ── Custom Menu Bar (themed, cross-platform) ──
                            GlossyMenuBar {
                                // Quick Access
                                MenuBarMenu("Quick Access", 0) {
                                    val topLabels = ToolUsageTracker.topLabels(8)
                                    val allTools = EnabledTools.visibleTools()
                                    if (topLabels.isNotEmpty()) {
                                        for (label in topLabels) {
                                            val tool = allTools.firstOrNull { it.label == label }
                                            if (tool != null) {
                                                MenuBarItem(tool.label) { SimulatorSessionManager.openTool(tool) }
                                            }
                                        }
                                        MenuBarSeparator()
                                    }
                                    MenuBarItem("Home") {
                                        SimulatorSessionManager.activateMainContent()
                                        navigationController.navigateTo(Destination.Home)
                                    }
                                }
                                // Simulators
                                if (EnabledTools.anyEnabled(StudioTool.HOST_SIMULATOR, StudioTool.POS_TERMINAL, StudioTool.ATM_SIMULATOR, StudioTool.ECR_SIMULATOR, StudioTool.HSM_SIMULATOR, StudioTool.HSM_COMMAND, StudioTool.APDU_SIMULATOR, StudioTool.PAYMENT_SWITCH, StudioTool.ACQUIRER_GATEWAY, StudioTool.ISSUER_SYSTEM)) MenuBarMenu("Simulators", 1) {
                                    if (EnabledTools.anyEnabled(StudioTool.HOST_SIMULATOR, StudioTool.POS_TERMINAL, StudioTool.ATM_SIMULATOR, StudioTool.ECR_SIMULATOR)) MenuBarSubMenu("Transaction Processing") {
                                        ToolMenuItem("Host Simulator", StudioTool.HOST_SIMULATOR)
                                        ToolMenuItem("POS Terminal", StudioTool.POS_TERMINAL)
                                        ToolMenuItem("ATM Simulator", StudioTool.ATM_SIMULATOR)
                                        ToolMenuItem("ECR Simulator", StudioTool.ECR_SIMULATOR)
                                    }
                                    if (EnabledTools.anyEnabled(StudioTool.HSM_SIMULATOR, StudioTool.HSM_COMMAND, StudioTool.APDU_SIMULATOR)) MenuBarSubMenu("HSM & Security") {
                                        ToolMenuItem("HSM Simulator", StudioTool.HSM_SIMULATOR)
                                        ToolMenuItem("HSM Host Console", StudioTool.HSM_COMMAND)
                                        ToolMenuItem("APDU Simulator", StudioTool.APDU_SIMULATOR)
                                    }
                                    if (EnabledTools.anyEnabled(StudioTool.PAYMENT_SWITCH, StudioTool.ACQUIRER_GATEWAY, StudioTool.ISSUER_SYSTEM)) MenuBarSubMenu("Network & Switching") {
                                        ToolMenuItem("Payment Switch", StudioTool.PAYMENT_SWITCH)
                                        ToolMenuItem("Acquirer Gateway", StudioTool.ACQUIRER_GATEWAY)
                                        ToolMenuItem("Issuer System", StudioTool.ISSUER_SYSTEM)
                                    }
                                }
                                // Tools
                                if (EnabledTools.anyEnabled(StudioTool.EMV_41_CRYPTO, StudioTool.EMV_42_CRYPTO, StudioTool.MASTERCARD_CRYPTO, StudioTool.VSDC_CRYPTO, StudioTool.SDA_VERIFICATION, StudioTool.DDA_VERIFICATION, StudioTool.CAP_TOKEN, StudioTool.HCE_VISA, StudioTool.SECURE_MESSAGING, StudioTool.ATR_PARSER, StudioTool.EMV_DATA_PARSER, StudioTool.EMV_TAG_DICTIONARY, StudioTool.AES_CALCULATOR, StudioTool.DES_CALCULATOR, StudioTool.RSA_CALCULATOR, StudioTool.ECDSA_CALCULATOR, StudioTool.FPE_CALCULATOR, StudioTool.HASH_CALCULATOR, StudioTool.THALES_RSA, StudioTool.DEA_KEYS, StudioTool.KEYSHARE_GENERATOR, StudioTool.THALES_KEYS, StudioTool.FUTUREX_KEYS, StudioTool.ATALLA_KEYS, StudioTool.SAFENET_KEYS, StudioTool.THALES_KEY_BLOCKS, StudioTool.TR31_KEY_BLOCKS, StudioTool.RSA_DER_KEYS, StudioTool.SSL_CERTIFICATE, StudioTool.CVV_CALCULATOR, StudioTool.AMEX_CSC, StudioTool.MASTERCARD_CSC, StudioTool.DUKPT_ISO_9797, StudioTool.DUKPT_ISO_AES, StudioTool.ISO_IES_9797_1_MAC, StudioTool.ANSI_MAC, StudioTool.AS2805_MAC, StudioTool.TDES_CBC_MAC, StudioTool.HMAC_MAC, StudioTool.CMAC_MAC, StudioTool.RETAIL_MAC, StudioTool.MDC_HASH, StudioTool.PIN_BLOCK_GENERAL, StudioTool.PIN_BLOCK_AES, StudioTool.PIN_OFFSET_IBM, StudioTool.PIN_PVV, StudioTool.ZKA, StudioTool.BASE64_ENCODER, StudioTool.BASE94_ENCODER, StudioTool.BCD_CONVERTER, StudioTool.CHARACTER_ENCODER, StudioTool.CHECK_DIGIT, StudioTool.TRACK2_CODEC, StudioTool.MESSAGE_PARSER, StudioTool.AS2805_CALCULATOR, StudioTool.BITMAP_CALCULATOR, StudioTool.APDU_RESPONSE_QUERY)) MenuBarMenu("Tools", 2) {
                                    if (EnabledTools.anyEnabled(StudioTool.EMV_41_CRYPTO, StudioTool.EMV_42_CRYPTO, StudioTool.MASTERCARD_CRYPTO, StudioTool.VSDC_CRYPTO, StudioTool.SDA_VERIFICATION, StudioTool.DDA_VERIFICATION, StudioTool.CAP_TOKEN, StudioTool.HCE_VISA, StudioTool.SECURE_MESSAGING, StudioTool.ATR_PARSER, StudioTool.EMV_DATA_PARSER, StudioTool.EMV_TAG_DICTIONARY)) MenuBarSubMenu("EMV & Card Tools") {
                                        ToolMenuItem("EMV 4.1 Crypto", StudioTool.EMV_41_CRYPTO)
                                        ToolMenuItem("EMV 4.2 Crypto", StudioTool.EMV_42_CRYPTO)
                                        ToolMenuItem("MasterCard Crypto", StudioTool.MASTERCARD_CRYPTO)
                                        ToolMenuItem("VSDC Crypto", StudioTool.VSDC_CRYPTO)
                                        ToolMenuItem("SDA Verification", StudioTool.SDA_VERIFICATION)
                                        ToolMenuItem("DDA Verification", StudioTool.DDA_VERIFICATION)
                                        MenuBarSeparator()
                                        ToolMenuItem("CAP Token", StudioTool.CAP_TOKEN)
                                        ToolMenuItem("HCE Visa", StudioTool.HCE_VISA)
                                        ToolMenuItem("Secure Messaging", StudioTool.SECURE_MESSAGING)
                                        MenuBarSeparator()
                                        ToolMenuItem("ATR Parser", StudioTool.ATR_PARSER)
                                        ToolMenuItem("EMV Data Parser", StudioTool.EMV_DATA_PARSER)
                                        ToolMenuItem("EMV Tag Dictionary", StudioTool.EMV_TAG_DICTIONARY)
                                    }
                                    if (EnabledTools.anyEnabled(StudioTool.AES_CALCULATOR, StudioTool.DES_CALCULATOR, StudioTool.RSA_CALCULATOR, StudioTool.ECDSA_CALCULATOR, StudioTool.FPE_CALCULATOR, StudioTool.HASH_CALCULATOR, StudioTool.THALES_RSA)) MenuBarSubMenu("Cryptography") {
                                        ToolMenuItem("AES Calculator", StudioTool.AES_CALCULATOR)
                                        ToolMenuItem("DES/3DES Calculator", StudioTool.DES_CALCULATOR)
                                        ToolMenuItem("RSA Calculator", StudioTool.RSA_CALCULATOR)
                                        ToolMenuItem("ECDSA Calculator", StudioTool.ECDSA_CALCULATOR)
                                        ToolMenuItem("FPE Calculator", StudioTool.FPE_CALCULATOR)
                                        MenuBarSeparator()
                                        ToolMenuItem("Hash Calculator", StudioTool.HASH_CALCULATOR)
                                        ToolMenuItem("Thales RSA", StudioTool.THALES_RSA)
                                    }
                                    if (EnabledTools.anyEnabled(StudioTool.DEA_KEYS, StudioTool.KEYSHARE_GENERATOR, StudioTool.THALES_KEYS, StudioTool.FUTUREX_KEYS, StudioTool.ATALLA_KEYS, StudioTool.SAFENET_KEYS, StudioTool.THALES_KEY_BLOCKS, StudioTool.TR31_KEY_BLOCKS, StudioTool.RSA_DER_KEYS, StudioTool.SSL_CERTIFICATE)) MenuBarSubMenu("Key Management") {
                                        ToolMenuItem("DEA Keys", StudioTool.DEA_KEYS)
                                        ToolMenuItem("Keyshare Generator", StudioTool.KEYSHARE_GENERATOR)
                                        MenuBarSeparator()
                                        ToolMenuItem("Thales Keys", StudioTool.THALES_KEYS)
                                        ToolMenuItem("Futurex Keys", StudioTool.FUTUREX_KEYS)
                                        ToolMenuItem("Atalla Keys", StudioTool.ATALLA_KEYS)
                                        ToolMenuItem("SafeNet Keys", StudioTool.SAFENET_KEYS)
                                        MenuBarSeparator()
                                        ToolMenuItem("Thales Key Blocks", StudioTool.THALES_KEY_BLOCKS)
                                        ToolMenuItem("TR-31 Key Blocks", StudioTool.TR31_KEY_BLOCKS)
                                        ToolMenuItem("RSA DER Keys", StudioTool.RSA_DER_KEYS)
                                        ToolMenuItem("SSL Certificate", StudioTool.SSL_CERTIFICATE)
                                    }
                                    if (EnabledTools.anyEnabled(StudioTool.CVV_CALCULATOR, StudioTool.AMEX_CSC, StudioTool.MASTERCARD_CSC, StudioTool.DUKPT_ISO_9797, StudioTool.DUKPT_ISO_AES, StudioTool.ISO_IES_9797_1_MAC, StudioTool.ANSI_MAC, StudioTool.AS2805_MAC, StudioTool.TDES_CBC_MAC, StudioTool.HMAC_MAC, StudioTool.CMAC_MAC, StudioTool.RETAIL_MAC, StudioTool.MDC_HASH, StudioTool.PIN_BLOCK_GENERAL, StudioTool.PIN_BLOCK_AES, StudioTool.PIN_OFFSET_IBM, StudioTool.PIN_PVV, StudioTool.ZKA)) MenuBarSubMenu("Payment & MAC") {
                                        ToolMenuItem("CVV Calculator", StudioTool.CVV_CALCULATOR)
                                        ToolMenuItem("Amex CSC", StudioTool.AMEX_CSC)
                                        ToolMenuItem("MasterCard CVC3", StudioTool.MASTERCARD_CSC)
                                        MenuBarSeparator()
                                        ToolMenuItem("DUKPT ISO 9797", StudioTool.DUKPT_ISO_9797)
                                        ToolMenuItem("DUKPT AES", StudioTool.DUKPT_ISO_AES)
                                        ToolMenuItem("ISO/IES 9797-1 MAC", StudioTool.ISO_IES_9797_1_MAC)
                                        ToolMenuItem("ANSI MAC", StudioTool.ANSI_MAC)
                                        ToolMenuItem("AS2805 MAC", StudioTool.AS2805_MAC)
                                        ToolMenuItem("3DES CBC MAC", StudioTool.TDES_CBC_MAC)
                                        ToolMenuItem("HMAC", StudioTool.HMAC_MAC)
                                        ToolMenuItem("CMAC", StudioTool.CMAC_MAC)
                                        ToolMenuItem("Retail MAC", StudioTool.RETAIL_MAC)
                                        ToolMenuItem("MDC Hash", StudioTool.MDC_HASH)
                                        MenuBarSeparator()
                                        ToolMenuItem("PIN Block General", StudioTool.PIN_BLOCK_GENERAL)
                                        ToolMenuItem("AES PIN Block", StudioTool.PIN_BLOCK_AES)
                                        ToolMenuItem("PIN Offset (IBM)", StudioTool.PIN_OFFSET_IBM)
                                        ToolMenuItem("PIN PVV", StudioTool.PIN_PVV)
                                        ToolMenuItem("ZKA", StudioTool.ZKA)
                                    }
                                    if (EnabledTools.anyEnabled(StudioTool.BASE64_ENCODER, StudioTool.BASE94_ENCODER, StudioTool.BCD_CONVERTER, StudioTool.CHARACTER_ENCODER, StudioTool.CHECK_DIGIT, StudioTool.TRACK2_CODEC, StudioTool.MESSAGE_PARSER, StudioTool.AS2805_CALCULATOR, StudioTool.BITMAP_CALCULATOR, StudioTool.APDU_RESPONSE_QUERY)) MenuBarSubMenu("Data Converters") {
                                        ToolMenuItem("Base64 Encoder", StudioTool.BASE64_ENCODER)
                                        ToolMenuItem("Base94 Encoder", StudioTool.BASE94_ENCODER)
                                        ToolMenuItem("BCD Converter", StudioTool.BCD_CONVERTER)
                                        ToolMenuItem("Character Encoder", StudioTool.CHARACTER_ENCODER)
                                        ToolMenuItem("Check Digit", StudioTool.CHECK_DIGIT)
                                        ToolMenuItem("Track 2 Codec", StudioTool.TRACK2_CODEC)
                                        MenuBarSeparator()
                                        ToolMenuItem("Message Parser", StudioTool.MESSAGE_PARSER)
                                        ToolMenuItem("AS2805 Calculator", StudioTool.AS2805_CALCULATOR)
                                        ToolMenuItem("Bitmap Calculator", StudioTool.BITMAP_CALCULATOR)
                                        ToolMenuItem("APDU Response Query", StudioTool.APDU_RESPONSE_QUERY)
                                    }
                                }
                                // Configuration
                                MenuBarMenu("Configuration", 3) {
                                    MenuBarItem("Export Configuration") {
                                        isoCoroutine.launch {
                                            val file = FileExporter().exportFile(
                                                window = window,
                                                fileName = "ISO8583Studio",
                                                fileExtension = "json",
                                                fileContent = appState.value.export().toByteArray(),
                                                fileDescription = "Configuration File"
                                            )
                                            when (file) {
                                                is ExportResult.Success -> {
                                                    appState.value.resultDialogInterface?.onSuccess {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center
                                                        ) { Text("Configuration exported successfully!") }
                                                    }
                                                }
                                                is ExportResult.Cancelled -> println("Export cancelled")
                                                is ExportResult.Error -> {
                                                    appState.value.resultDialogInterface?.onError {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center,
                                                        ) { Text((file as ExportResult.Error).message) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    MenuBarItem("Import Configuration") {
                                        isoCoroutine.launch {
                                            val file = FileImporter().importFile(
                                                window = window,
                                                fileExtensions = listOf("json"),
                                                importLogic = { f -> appState.value.import(f) }
                                            )
                                            when (file) {
                                                is ImportResult.Success -> {
                                                    appState.value.resultDialogInterface?.onSuccess {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center
                                                        ) { Text("Configuration imported successfully!") }
                                                    }
                                                }
                                                is ImportResult.Cancelled -> println("Import cancelled")
                                                is ImportResult.Error -> {
                                                    appState.value.resultDialogInterface?.onError {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center
                                                        ) { Text(file.message) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    MenuBarSeparator()
                                    MenuBarItem("Settings") { navigationController.navigateTo(Destination.GlobalSettings) }
                                }
                                // Help
                                MenuBarMenu("Help", 4) {
                                    MenuBarItem("About ISO8583 Studio") { showAboutDialog = true }
                                    MenuBarSeparator()
                                    MenuBarItem("Exit") { exitApplication() }
                                }
                            }

                            // ── Global Tab Bar ──
                            GlobalSimulatorTabBar()

                            // ── Content Area ──
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Layer 1: Main Voyager navigation (always composed, visibility toggled)
                                CurrentScreen()

                                // Layer 2: Inline session content (skip popped-out sessions)
                                SimulatorSessionManager.sessions.forEach { session ->
                                    if (session.id !in poppedOutIds) {
                                        key(session.id) {
                                            val isVisible = session.id == activeSessionId
                                            Box(
                                                modifier = if (isVisible) Modifier.fillMaxSize()
                                                           else Modifier.requiredSize(0.dp)
                                            ) {
                                                SessionContent(
                                                    session = session,
                                                    window = window,
                                                    onBack = {
                                                        val configDest = when (session.simulatorType) {
                                                            SimulatorType.HOST        -> Destination.HostSimulatorConfig
                                                            SimulatorType.HSM         -> Destination.HSMSimulatorConfig
                                                            SimulatorType.POS         -> Destination.POSTerminalConfig
                                                            SimulatorType.APDU        -> Destination.ApduSimulatorConfig
                                                            SimulatorType.ECR         -> Destination.EcrSimulatorConfigScreen
                                                            SimulatorType.ATM         -> Destination.ATMSimulatorConfig
                                                            SimulatorType.HSM_COMMAND -> Destination.HsmCommandConfig
                                                            SimulatorType.SWITCH      -> Destination.PaymentSwitchConfig
                                                            SimulatorType.ACQUIRER    -> Destination.AcquirerGatewayConfig
                                                            SimulatorType.ISSUER      -> Destination.IssuerSystemConfig
                                                            else -> Destination.Home
                                                        }
                                                        navigationController.navigateTo(configDest)
                                                        SimulatorSessionManager.activateMainContent()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── Pop-out Windows ──
                        // Each pop-out Window has its own composition, so we
                        // wrap content in a Navigator to provide LocalNavigator
                        // (required by HOST sessions for NavigationController).
                        SimulatorSessionManager.sessions
                            .filter { it.id in poppedOutIds }
                            .forEach { session ->
                                key("popout-${session.id}") {
                                    val popoutWindowState = rememberWindowState(
                                        size = DpSize(width = 1400.dp, height = 750.dp)
                                    )
                                    Window(
                                        onCloseRequest = {
                                            SimulatorSessionManager.dockSession(session.id)
                                        },
                                        title = session.displayName,
                                        state = popoutWindowState,
                                        icon = painterResource(Res.drawable.app)
                                    ) {
                                        GlassWindowEffect(
                                            window = window,
                                            backdrop = GlassBackdrop.MICA,
                                            darkMode = isDark,
                                        )
                                        AppTheme {
                                            Navigator(screen = Destination.Home) {
                                                Column(modifier = Modifier.fillMaxSize()) {
                                                    PopOutWindowTopBar(
                                                        session = session,
                                                        onDock = { SimulatorSessionManager.dockSession(session.id) },
                                                        onClose = { SimulatorSessionManager.closeSession(session.id) }
                                                    )
                                                    Box(modifier = Modifier.fillMaxSize()) {
                                                        SessionContent(
                                                            session = session,
                                                            window = window,
                                                            onBack = {
                                                                SimulatorSessionManager.dockSession(session.id)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

/**
 * Renders the appropriate simulator screen for a given session.
 * Each session type gets its own composable with the service instance
 * from the session, ensuring state persistence across tab switches.
 */
@Composable
private fun SessionContent(
    session: `in`.aicortex.iso8583studio.ui.session.SimulatorSession,
    window: java.awt.Window,
    onBack: () -> Unit
) {
    when (session.simulatorType) {
        SimulatorType.HSM -> {
            val config = session.config as HSMSimulatorConfig
            HsmSimulatorScreen(
                config = config,
                onBack = onBack,
                service = session.hsmService,  // reuse session-owned service; closeSession() stops it
                onSaveConfig = { updated -> appState.value.updateConfig(updated) }
            )
        }

        SimulatorType.HOST -> {
            val config = session.config as GatewayConfig
            val navigationController = `in`.aicortex.iso8583studio.ui.navigation.rememberNavigationController(
                cafe.adriel.voyager.navigator.LocalNavigator.currentOrThrow
            )
            HostSimulatorScreen(
                window = window as androidx.compose.ui.awt.ComposeWindow,
                config = config,
                navigationController = navigationController,
                onBack = onBack,
                onError = appState.value.resultDialogInterface!!,
                onSaveClick = { appState.value.save() }
            )
        }

        SimulatorType.POS -> {
            val config = session.config as POSSimulatorConfig
            POSTerminalSimulatorScreen(
                window = window as androidx.compose.ui.awt.ComposeWindow,
                config = config,
                onBack = onBack,
                onSaveClick = { appState.value.save() },
                service = session.posService,
            )
        }

        SimulatorType.APDU -> {
            val config = session.config as APDUSimulatorConfig
            ApduSimulatorV2Screen(
                config = config,
                onBack = onBack,
                onSaveClick = { appState.value.save() },
            )
        }

        SimulatorType.HSM_COMMAND -> {
            val config = session.config as `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.hsmCommand.HsmCommandConfig
            `in`.aicortex.iso8583studio.ui.screens.hsmCommand.HsmCommandScreen(
                config = config,
                onBack = onBack,
                service = session.hsmCommandService,
                onSaveConfig = { updatedConfig ->
                    appState.value.updateConfig(updatedConfig)
                    appState.value.save()
                }
            )
        }

        SimulatorType.ECR -> {
            val service = session.ecrService
            if (service != null) {
                `in`.aicortex.iso8583studio.ui.screens.ecrSimulator.EcrSimulatorScreen(
                    service = service,
                    onBack = onBack,
                    onSaveConfig = { updatedConfig ->
                        appState.value.updateConfig(updatedConfig)
                        appState.value.save()
                    },
                )
            }
        }

        SimulatorType.TOOL -> {
            val screen = session.toolScreen
            if (screen != null) {
                Navigator(screen = screen) { nav ->
                    // Voyager 1.1.0-beta02 uses `providesDefault` for LocalNavigatorStateHolder,
                    // so all nested Navigators share the root's SaveableStateHolder. CurrentScreen()
                    // always registers key "${screen.key}:currentScreen" in that shared holder.
                    // If this nested navigator and the root both show the same screen (e.g.
                    // Destination.Home), the duplicate key crashes the app.
                    //
                    // Fix: call saveableState with a session-unique suffix instead of the fixed
                    // "currentScreen" used by CurrentScreen(). This keeps all lifecycle/state
                    // management intact while guaranteeing key uniqueness in the shared holder.
                    // A tool tab starts at the bottom of its own stack: until the tool pushes a
                    // screen of its own, the back arrow has nowhere to go, so it is not shown.
                    CompositionLocalProvider(LocalBackNavigationAvailable provides nav.canPop) {
                        nav.saveableState("session_${session.id}") {
                            nav.lastItem.Content()
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tool unavailable")
                }
            }
        }

        else -> {
            // Placeholder for future simulator types
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("${session.simulatorType.displayName} session is running")
            }
        }
    }
}

/**
 * Compact top bar for pop-out windows with session info, dock-back, and close actions.
 */
@Composable
private fun PopOutWindowTopBar(
    session: `in`.aicortex.iso8583studio.ui.session.SimulatorSession,
    onDock: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colors.surface,
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = session.displayName,
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.onSurface
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onDock, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "Dock back to main window",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close session",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/** A menu entry that opens [tool], shown only when the tool is in [EnabledTools.ENABLED]. */
@Composable
private fun ToolMenuItem(label: String, tool: StudioTool) {
    if (EnabledTools.isEnabled(tool)) {
        MenuBarItem(label) { SimulatorSessionManager.openTool(tool) }
    }
}
