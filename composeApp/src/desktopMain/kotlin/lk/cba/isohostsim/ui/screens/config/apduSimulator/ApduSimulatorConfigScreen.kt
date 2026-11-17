package lk.cba.isohostsim.ui.screens.config.apduSimulator

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import lk.cba.isohostsim.ui.navigation.Destination
import lk.cba.isohostsim.ui.navigation.NavigationController
import lk.cba.isohostsim.ui.navigation.stateConfigs.SimulatorType
import lk.cba.isohostsim.ui.navigation.stateConfigs.UnifiedSimulatorState
import lk.cba.isohostsim.ui.navigation.stateConfigs.apdu.APDUSimulatorConfig
import lk.cba.isohostsim.ui.navigation.stateConfigs.apdu.CardType
import lk.cba.isohostsim.ui.navigation.stateConfigs.apdu.ConnectionInterface
import lk.cba.isohostsim.ui.screens.components.AppBarWithBack
import lk.cba.isohostsim.ui.screens.config.ConfigTab
import lk.cba.isohostsim.ui.screens.config.ContainerConfig
import lk.cba.isohostsim.ui.screens.config.SimulatorConfigLayout
import lk.cba.isohostsim.ui.screens.config.apduSimulator.v2.CardProfileTab
import lk.cba.isohostsim.ui.screens.config.apduSimulator.v2.ModeTransportTab
import lk.cba.isohostsim.ui.screens.config.apduSimulator.v2.PlansTab
import lk.cba.isohostsim.ui.screens.config.apduSimulator.v2.RiskBehaviorTab
import lk.cba.isohostsim.ui.screens.config.apduSimulator.v2.TerminalProfileTab
import lk.cba.isohostsim.ui.session.SimulatorSessionManager

/**
 * APDU simulator configuration screen — host-simulator-style 5-tab layout:
 *   1. Mode & Transport
 *   2. Card Profile         (issuer-side identity, applications, keys)
 *   3. Terminal Profile     (acquirer-side: terminal capabilities, TACs, CAPK, kernel)
 *   4. Risk & Behavior      (PIN policy, lifecycle, fault-injection rules)
 *   5. Test Plans           (built-in + custom directory, auto-run)
 */
@Composable
fun ApduSimulatorConfigScreen(
    navigationController: NavigationController,
    appState: UnifiedSimulatorState,
) {
    fun current(): APDUSimulatorConfig? = appState.currentConfig(SimulatorType.APDU) as APDUSimulatorConfig?

    val tabs = listOf(
        ConfigTab(label = "Mode & Transport") {
            current()?.let { c -> ModeTransportTab(c) { appState.updateConfig(it) } }
        },
        ConfigTab(label = "Card Profile") {
            current()?.let { c -> CardProfileTab(c) { appState.updateConfig(it) } }
        },
        ConfigTab(label = "Terminal Profile") {
            current()?.let { c -> TerminalProfileTab(c) { appState.updateConfig(it) } }
        },
        ConfigTab(label = "Risk & Behavior") {
            current()?.let { c -> RiskBehaviorTab(c) { appState.updateConfig(it) } }
        },
        ConfigTab(label = "Test Plans") {
            current()?.let { c -> PlansTab(c) { appState.updateConfig(it) } }
        },
    )

    Scaffold(
        topBar = {
            AppBarWithBack(
                title = "APDU Simulator Configuration",
                onBackClick = { navigationController.goBack() },
            )
        },
        backgroundColor = MaterialTheme.colors.background,
    ) {
        SimulatorConfigLayout(
            config = ContainerConfig(
                tabs = tabs,
                label = "APDU Simulator",
                currentConfig = { current() },
                simulatorConfigs = appState.apduConfigs.value,
                icon = Icons.Default.Security,
            ),
            onSelectConfig = { appState.selectConfig(it.id) },
            createNewConfig = {
                appState.addConfig(
                    APDUSimulatorConfig(
                        name = "Card-${appState.apduConfigs.value.size + 1}",
                        cardType = CardType.CUSTOM,
                        id = appState.generateConfigId(),
                        connectionInterface = ConnectionInterface.PC_SC,
                    ),
                )
            },
            onDeleteConfig = {
                current()?.id?.let { appState.deleteConfig(it) }
            },
            onSaveAllConfigs = {
                current()?.let {
                    appState.updateConfig(it.copy(modifiedDate = System.currentTimeMillis()))
                }
                appState.save()
            },
            onLaunchSimulator = {
                SimulatorSessionManager.launchSimulator(it)
                navigationController.navigateTo(Destination.Home)
            },
        )
    }
}
