package lk.cba.isohostsim.ui.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.ui.Studio.appState
import lk.cba.isohostsim.ui.navigation.stateConfigs.apdu.APDUSimulatorConfig
import lk.cba.isohostsim.ui.navigation.stateConfigs.pos.POSSimulatorConfig
import lk.cba.isohostsim.ui.navigation.stateConfigs.SimulatorType
import lk.cba.isohostsim.ui.screens.Emv.AtrParserScreen
import lk.cba.isohostsim.ui.screens.Emv.CapTokenComputationScreen
import lk.cba.isohostsim.ui.screens.Emv.EmvDataParserScreen
import lk.cba.isohostsim.ui.screens.Emv.EmvTagDictionaryScreen
import lk.cba.isohostsim.ui.screens.Emv.applicationCryptogram.emvCrypto41.EmvCrypto4_1
import lk.cba.isohostsim.ui.screens.Emv.applicationCryptogram.EmvCrypto4_2
import lk.cba.isohostsim.ui.screens.Emv.applicationCryptogram.MastercardCryptoScreen
import lk.cba.isohostsim.ui.screens.Emv.applicationCryptogram.VsdcCryptoScreen
import lk.cba.isohostsim.ui.screens.Emv.dda.DdaScreen
import lk.cba.isohostsim.ui.screens.Emv.dsPartialKey.DsPartialKeyScreen
import lk.cba.isohostsim.ui.screens.Emv.hce.VisaHceCryptoScreen
import lk.cba.isohostsim.ui.screens.Emv.iccDynamicNumber.IccDynamicNumberScreen
import lk.cba.isohostsim.ui.screens.Emv.sda.SdaScreen
import lk.cba.isohostsim.ui.screens.Emv.secureMessaging.MastercardSecureMessagingScreen
import lk.cba.isohostsim.ui.screens.Emv.secureMessaging.VisaSecureMessagingScreen
import lk.cba.isohostsim.ui.screens.HomeScreenViewModel
import lk.cba.isohostsim.ui.screens.apduSimulator.APDUSimulatorScreen
import lk.cba.isohostsim.ui.screens.apduSimulator.v2.ApduSimulatorV2Screen
import lk.cba.isohostsim.ui.screens.Emv.ApduResponseQueryScreen
import lk.cba.isohostsim.ui.screens.cipher.AesCalculatorScreen
import lk.cba.isohostsim.ui.screens.cipher.DesCalculatorScreen
import lk.cba.isohostsim.ui.screens.cipher.EcdsaCalculatorScreen
import lk.cba.isohostsim.ui.screens.cipher.RsaCalculatorScreen
import lk.cba.isohostsim.ui.screens.cipher.ThalesRsaScreen
import lk.cba.isohostsim.ui.screens.config.acquirerGateway.AcquiringGatewayConfigScreen
import lk.cba.isohostsim.ui.screens.config.apduSimulator.ApduSimulatorConfigScreen
import lk.cba.isohostsim.ui.screens.config.atmSimulator.AtmSimulatorConfigScreen
import lk.cba.isohostsim.ui.screens.config.ecrSimulator.EcrSimulatorConfigScreen
import lk.cba.isohostsim.ui.screens.config.hostSimulator.HostSimulatorConfigScreen
import lk.cba.isohostsim.ui.screens.config.hsmCommand.HsmCommandConfigScreen
import lk.cba.isohostsim.ui.screens.config.hsmSimulator.HsmSimulatorConfigScreen
import lk.cba.isohostsim.ui.screens.config.issuerSystem.IssuerSystemConfigScreen
import lk.cba.isohostsim.ui.screens.config.paymentSwitch.PaymentSwitchConfigScreen
import lk.cba.isohostsim.ui.screens.config.posTerminal.PosTerminalConfigScreen
import lk.cba.isohostsim.ui.screens.fpe.FpeCalculatorScreen
import lk.cba.isohostsim.ui.screens.generic.AESPinBlockScreen
import lk.cba.isohostsim.ui.screens.generic.Base64EncoderDecoderScreen
import lk.cba.isohostsim.ui.screens.generic.Base94EncoderDecoderScreen
import lk.cba.isohostsim.ui.screens.generic.BcdConverterScreen
import lk.cba.isohostsim.ui.screens.generic.CharacterEncodingScreen
import lk.cba.isohostsim.ui.screens.generic.CheckDigitScreen
import lk.cba.isohostsim.ui.screens.payments.dukpt.DukptIso9797Screen
import lk.cba.isohostsim.ui.screens.generic.HashCalculatorScreen
import lk.cba.isohostsim.ui.screens.parser.IsoParserScreen
import lk.cba.isohostsim.ui.screens.generic.PinBlockGeneralScreen
import lk.cba.isohostsim.ui.screens.generic.Track2Screen
import lk.cba.isohostsim.ui.screens.hostSimulator.HostSimulatorScreen
import lk.cba.isohostsim.ui.screens.hsmCommand.HsmCommandScreen
import lk.cba.isohostsim.ui.screens.hsmSimulator.HsmSimulatorScreen
import lk.cba.isohostsim.ui.screens.keys.DeaKeysScreen
import lk.cba.isohostsim.ui.screens.keys.KeyshareGeneratorScreen
import lk.cba.isohostsim.ui.screens.keys.SslUtilityScreen
import lk.cba.isohostsim.ui.screens.keys.hsmKeys.AtallaKeysScreen
import lk.cba.isohostsim.ui.screens.keys.hsmKeys.FuturexKeysScreen
import lk.cba.isohostsim.ui.screens.keys.hsmKeys.SafenetKeysScreen
import lk.cba.isohostsim.ui.screens.keys.keyBlocks.ThalesKeyBlockScreen
import lk.cba.isohostsim.ui.screens.landing.HomeScreen
import lk.cba.isohostsim.ui.screens.monitor.MonitorScreen
import lk.cba.isohostsim.ui.screens.payments.As2805CalculatorScreen
import lk.cba.isohostsim.ui.screens.payments.BitmapScreen
import lk.cba.isohostsim.ui.screens.payments.MDCHashScreen
import lk.cba.isohostsim.ui.screens.payments.PinOffsetScreen
import lk.cba.isohostsim.ui.screens.payments.PinPvvScreen
import lk.cba.isohostsim.ui.screens.payments.ZKAScreen
import lk.cba.isohostsim.ui.screens.payments.cardValidation.AmexCvvScreen
import lk.cba.isohostsim.ui.screens.payments.cardValidation.CVC3Screen
import lk.cba.isohostsim.ui.screens.payments.cardValidation.CvvCalculatorScreen
import lk.cba.isohostsim.ui.screens.payments.dukpt.DUKPTAESScreen
import lk.cba.isohostsim.ui.screens.payments.macAlgorithms.ANSIMACScreen
import lk.cba.isohostsim.ui.screens.payments.macAlgorithms.AS2805MACScreen
import lk.cba.isohostsim.ui.screens.payments.macAlgorithms.CMACScreen
import lk.cba.isohostsim.ui.screens.payments.macAlgorithms.HMACScreen
import lk.cba.isohostsim.ui.screens.payments.macAlgorithms.ISO9797Screen
import lk.cba.isohostsim.ui.screens.payments.macAlgorithms.RetailMACScreen
import lk.cba.isohostsim.ui.screens.payments.macAlgorithms.TDESCBCMACScreen
import lk.cba.isohostsim.ui.screens.posTerminal.POSTerminalSimulatorScreen
import lk.cba.isohostsim.ui.screens.rsader.RsaDerKeyScreen
import lk.cba.isohostsim.ui.screens.settings.GlobalSettingsScreen
import lk.cba.isohostsim.ui.screens.thaleskeys.ThalesKeysScreen
import lk.cba.isohostsim.ui.screens.tr31keyblock.Tr31KeyBlockScreen

/**
 * Navigation destinations for the app, converted to the Voyager pattern.
 * Each screen object is self-contained and defines its own content.
 */
sealed class Destination : Screen {

    object Home : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            HomeScreen(
                viewModel = rememberScreenModel { HomeScreenViewModel() },
                onToolSelected = { tool ->
                    if (lk.cba.isohostsim.edition.EnabledTools.isEnabled(tool)) {
                        if (tool.opensAsTab) {
                            // A tab in the session bar, beside the running Host and ECR simulators.
                            lk.cba.isohostsim.ui.session.SimulatorSessionManager.openTool(tool)
                        } else {
                            lk.cba.isohostsim.ui.session.ToolUsageTracker.recordUsage(tool.label)
                            navigationController.navigateTo(tool.screen)
                        }
                    }
                },
                onGetStarted = { navigationController.navigateTo(HostSimulatorConfig) }
            )
        }
    }

    object GlobalSettings : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            GlobalSettingsScreen(onBack = { navigationController.goBack() })
        }
    }

    object Monitor : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            MonitorScreen(appState = appState.value, onBack = { navigationController.goBack() })
        }
    }

    object HostSimulatorConfig : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            HostSimulatorConfigScreen(
                navigationController = navigationController,
                appState = appState.value
            )
        }
    }

    object HSMSimulatorConfig : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            HsmSimulatorConfigScreen(
                navigationController = navigationController,
                appState = appState.value
            )
        }
    }

    object HsmCommandConfig : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            HsmCommandConfigScreen(
                navigationController = navigationController,
                appState = appState.value
            )
        }
    }

    object HsmCommandTool : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            HsmCommandScreen(
                config = appState.value.currentConfig(SimulatorType.HSM_COMMAND) as lk.cba.isohostsim.ui.navigation.stateConfigs.hsmCommand.HsmCommandConfig,
                onBack = { navigationController.goBack() },
                onSaveConfig = { updatedConfig ->
                    appState.value.updateConfig(updatedConfig)
                    appState.value.save()
                }
            )
        }
    }

    object HostSimulator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            appState.value.window?.let {
                HostSimulatorScreen(
                    window = it,
                    config = appState.value.currentConfig(SimulatorType.HOST) as GatewayConfig,
                    navigationController = navigationController,
                    onBack = { navigationController.goBack() },
                    onError = appState.value.resultDialogInterface!!,
                    onSaveClick = { appState.value.save() }
                )
            }
        }
    }

    object EMV4_1 : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            EmvCrypto4_1(onBack = { navigationController.goBack() })
        }
    }

    object EMV4_2 : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            EmvCrypto4_2(onBack = { navigationController.goBack() })
        }
    }

    object EMVMasterCardCrypto : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            MastercardCryptoScreen(onBack = { navigationController.goBack() })
        }
    }

    object EMVVsdcCrypto : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            VsdcCryptoScreen(onBack = { navigationController.goBack() })
        }
    }

    object SDA : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            SdaScreen(onBack = { navigationController.goBack() })
        }
    }

    object CapTokenComputation : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            CapTokenComputationScreen { navigationController.goBack() }
        }
    }

    object DDA : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            DdaScreen(onBack = { navigationController.goBack() })
        }
    }

    object DataStoragePartialKeyMaterCard : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            DsPartialKeyScreen(onBack = { navigationController.goBack() })
        }
    }

    object HceVisa : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            VisaHceCryptoScreen() { navigationController.goBack() }
        }
    }

    object ICCDynamicNumberMasterCard : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            IccDynamicNumberScreen(onBack = { navigationController.goBack() })
        }
    }

    object SecureMessagingMasterCard : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            MastercardSecureMessagingScreen() { navigationController.goBack() }
        }
    }

    object SecureMessagingVisa : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            VisaSecureMessagingScreen() { navigationController.goBack() }
        }
    }

    object AtrParser : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            AtrParserScreen() { navigationController.goBack() }
        }
    }

    object EmvDataParser : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            EmvDataParserScreen() { navigationController.goBack() }
        }
    }

    object EmvTagDictionary : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            EmvTagDictionaryScreen() { navigationController.goBack() }
        }
    }

    object ApduResponseQuery : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            ApduResponseQueryScreen() { navigationController.goBack() }
        }
    }

    object HashCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            HashCalculatorScreen() { navigationController.goBack() }
        }
    }

    object CharacterEncoder : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            CharacterEncodingScreen() { navigationController.goBack() }
        }
    }

    object BcdCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            BcdConverterScreen() { navigationController.goBack() }
        }
    }

    object CheckDigit : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            CheckDigitScreen() { navigationController.goBack() }
        }
    }

    object Base64Calculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            Base64EncoderDecoderScreen() { navigationController.goBack() }
        }
    }

    object LogcatViewer : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            lk.cba.isohostsim.ui.screens.logcat.LogcatViewerScreen(
                onBack = { navigationController.goBack() }
            )
        }
    }

    object MessageParser : Screen {
        @Composable
        override fun Content() {
            IsoParserScreen(hostConfigs = appState.value.hostConfigs.value)
        }
    }

    object Track2Calculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            Track2Screen { navigationController.goBack() }
        }
    }

    object Base94Calculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            Base94EncoderDecoderScreen() { navigationController.goBack() }
        }
    }

    object RsaDerPubKeyCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            RsaDerKeyScreen() { navigationController.goBack() }
        }
    }

    object AesCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            AesCalculatorScreen() { navigationController.goBack() }
        }
    }

    object DesCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            DesCalculatorScreen() { navigationController.goBack() }
        }
    }

    object RsaCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            RsaCalculatorScreen() { navigationController.goBack() }
        }
    }

    object ThalesRsaCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            ThalesRsaScreen() { navigationController.goBack() }
        }
    }

    object EcdsaCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            EcdsaCalculatorScreen() { navigationController.goBack() }
        }
    }

    object FpeCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            FpeCalculatorScreen() { navigationController.goBack() }
        }
    }

    object DeaKeyCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            DeaKeysScreen() { navigationController.goBack() }
        }
    }

    object KeyshareGenerator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            KeyshareGeneratorScreen() { navigationController.goBack() }
        }
    }

    object FuturexKeyCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            FuturexKeysScreen() { navigationController.goBack() }
        }
    }

    object AtallaKeyCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            AtallaKeysScreen() { navigationController.goBack() }
        }
    }

    object SafeNetKeyCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            SafenetKeysScreen() { navigationController.goBack() }
        }
    }

    object ThalesKeyCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            ThalesKeysScreen() { navigationController.goBack() }
        }
    }

    object ThalesKeyBlockCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            ThalesKeyBlockScreen() { navigationController.goBack() }
        }
    }

    object TR31KeyBlockCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            Tr31KeyBlockScreen() { navigationController.goBack() }
        }
    }

    object SslCertificate : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            SslUtilityScreen() { navigationController.goBack() }
        }
    }

    object As2805Calculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            As2805CalculatorScreen() { navigationController.goBack() }
        }
    }

    object BitmapCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            BitmapScreen() { navigationController.goBack() }
        }
    }

    object CvvCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            CvvCalculatorScreen() { navigationController.goBack() }
        }
    }

    object DukptIso9797 : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            DukptIso9797Screen { navigationController.goBack() }
        }
    }

    object DukptIsoAES : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            DUKPTAESScreen { navigationController.goBack() }
        }
    }

    object Isoies97971mac : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            ISO9797Screen { navigationController.goBack() }
        }
    }

    object AnsiMac : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            ANSIMACScreen { navigationController.goBack() }
        }
    }

    object AS2805MacScreen : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            AS2805MACScreen { navigationController.goBack() }
        }
    }

    object TDESCBCMACScreen : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            TDESCBCMACScreen { navigationController.goBack() }
        }
    }

    object HMACScreen : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            HMACScreen { navigationController.goBack() }
        }
    }

    object CMACScreen : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            CMACScreen { navigationController.goBack() }
        }
    }

    object RetailMACScreen : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            RetailMACScreen { navigationController.goBack() }
        }
    }

    object MdcHashCalculatorScreen : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            MDCHashScreen { navigationController.goBack() }
        }
    }

    object PinBlockGeneralScreen : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            PinBlockGeneralScreen { navigationController.goBack() }
        }
    }

    object AESPinBlockScreen : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            AESPinBlockScreen { navigationController.goBack() }
        }
    }

    object PinOffsetScreen : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            PinOffsetScreen { navigationController.goBack() }
        }
    }

    object PinPvvScreen : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            PinPvvScreen { navigationController.goBack() }
        }
    }

    object ZKAScreen : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            ZKAScreen { navigationController.goBack() }
        }
    }

    object AmexCscCalculator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            AmexCvvScreen() { navigationController.goBack() }
        }
    }


    object Cvc3MasterCardScreen : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            CVC3Screen {
                navigationController.goBack()
            }
        }
    }

    object HSMSimulator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            HsmSimulatorScreen(
                config = appState.value.currentConfig(SimulatorType.HSM) as lk.cba.isohostsim.ui.navigation.stateConfigs.hsm.HSMSimulatorConfig,
                onBack = { navigationController.goBack() },
                onSaveConfig = { updated -> appState.value.updateConfig(updated) }
            )
        }
    }

    object POSTerminal : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            appState.value.window?.let {
                POSTerminalSimulatorScreen(
                    window = it,
                    config = appState.value.currentConfig(SimulatorType.POS) as POSSimulatorConfig,
                    onBack = { navigationController.goBack() },
                    onSaveClick = { appState.value.save() }
                )
            }
        }
    }

    object POSTerminalConfig : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            PosTerminalConfigScreen(
                navigationController = navigationController,
                appState = appState.value
            )
        }
    }

    object EcrSimulatorConfigScreen : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            EcrSimulatorConfigScreen(
                navigationController = navigationController,
                appState = appState.value
            )
        }
    }

    object ATMSimulatorConfig : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            AtmSimulatorConfigScreen(
                navigationController = navigationController,
                appState = appState.value
            )
        }
    }

    object PaymentSwitchConfig : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            PaymentSwitchConfigScreen(
                navigationController = navigationController,
                appState = appState.value
            )
        }
    }

    object AcquirerGatewayConfig : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            AcquiringGatewayConfigScreen(
                navigationController = navigationController,
                appState.value
            )
        }
    }

    object IssuerSystemConfig : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            IssuerSystemConfigScreen(
                navigationController = navigationController,
                appState = appState.value
            )
        }
    }

    object ApduSimulatorConfig : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            ApduSimulatorConfigScreen(
                navigationController = navigationController,
                appState = appState.value
            )
        }
    }

    object ATMSimulator : Screen {
        @Composable
        override fun Content() {
            // TODO: Implement ATMSimulator Screen
        }
    }

    object PaymentSwitch : Screen {
        @Composable
        override fun Content() {
            // TODO: Implement PaymentSwitch Screen
        }
    }

    object AcquirerGateway : Screen {
        @Composable
        override fun Content() {
            // TODO: Implement AcquirerGateway Screen
        }
    }

    object IssuerSystem : Screen {
        @Composable
        override fun Content() {
            // TODO: Implement IssuerSystem Screen
        }
    }

    object ApduSimulator : Screen {
        @Composable
        override fun Content() {
            val navigationController = rememberNavigationController(LocalNavigator.currentOrThrow)
            val cfg = appState.value.currentConfig(SimulatorType.APDU) as? APDUSimulatorConfig
            if (cfg != null) {
                ApduSimulatorV2Screen(
                    config = cfg,
                    onBack = { navigationController.goBack() },
                    onSaveClick = { appState.value.save() },
                )
            }
        }
    }
}
