package lk.cba.isohostsim.ui.screens.config.hostSimulator

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.runtime.Composable
import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.ui.navigation.Destination
import lk.cba.isohostsim.ui.navigation.NavigationController
import lk.cba.isohostsim.ui.navigation.stateConfigs.SimulatorType
import lk.cba.isohostsim.ui.navigation.stateConfigs.UnifiedSimulatorState
import lk.cba.isohostsim.ui.screens.components.AppBarWithBack
import lk.cba.isohostsim.ui.screens.components.DevelopmentStatus
import lk.cba.isohostsim.ui.screens.config.ConfigTab
import lk.cba.isohostsim.ui.screens.config.ContainerConfig
import lk.cba.isohostsim.ui.screens.config.SimulatorConfigLayout
import lk.cba.isohostsim.ui.session.SimulatorSessionManager
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lk.cba.isohostsim.ui.design.PageHeader
import lk.cba.isohostsim.ui.design.PosButton

/**
 * Host Simulator configuration. Every server host answers from the profile built into the app
 * (see docs/host-profiles.md), which developers change in the source. The one tab here shows,
 * read only, what it sets up.
 */
@Composable
fun HostSimulatorConfigScreen(
    navigationController: NavigationController,
    appState: UnifiedSimulatorState,
) {
    val tabsList = listOf(
        ConfigTab(
            label = "Profile",
            content = {
                HostProfileTab(
                    config = appState.currentConfig(SimulatorType.HOST)!! as GatewayConfig,
                    appState = appState,
                )
            }
        ),
    )
    Scaffold(
        topBar = {
            PageHeader(
                title = "Host simulators",
                subtitle = "Each host answers from the profile built into the app. Pick the port and override replies on the Local Host screen.",
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 8.dp),
                actions = {
                    PosButton("Add host", onClick = { addBuiltInHost(appState) })
                },
            )
        },
        backgroundColor = MaterialTheme.colors.background
    ) {
        SimulatorConfigLayout(
            config = ContainerConfig(
                tabs = tabsList,
                icon = Icons.Default.Computer,
                label = "Host Simulator",
                simulatorConfigs = appState.hostConfigs.value,
                currentConfig = { appState.currentConfig(SimulatorType.HOST) },
                containerStatus = DevelopmentStatus.STABLE,
            ),
            onSelectConfig = { appState.selectConfig(it.id) },
            createNewConfig = { addBuiltInHost(appState) },
            onDeleteConfig = {
                appState.deleteConfig(it.id)
            },
            onLaunchSimulator = {
                appState.selectConfig(it.id)
                SimulatorSessionManager.launchSimulator(it)
                navigationController.navigateTo(Destination.Home)
            },
            onSaveAllConfigs = {
                appState.updateConfig(
                    (appState.currentConfig(SimulatorType.HOST) as GatewayConfig).copy(
                        modifiedDate = System.currentTimeMillis()
                    )
                )
                appState.save()
            }
        )
    }
}
