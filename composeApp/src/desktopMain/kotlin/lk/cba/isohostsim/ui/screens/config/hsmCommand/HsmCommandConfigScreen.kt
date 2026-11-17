package lk.cba.isohostsim.ui.screens.config.hsmCommand

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import lk.cba.isohostsim.ui.navigation.Destination
import lk.cba.isohostsim.ui.navigation.NavigationController
import lk.cba.isohostsim.ui.navigation.stateConfigs.SimulatorType
import lk.cba.isohostsim.ui.navigation.stateConfigs.UnifiedSimulatorState
import lk.cba.isohostsim.ui.navigation.stateConfigs.hsmCommand.HsmCommandConfig
import lk.cba.isohostsim.ui.screens.components.AppBarWithBack
import lk.cba.isohostsim.ui.screens.components.DevelopmentStatus
import lk.cba.isohostsim.ui.screens.config.ConfigTab
import lk.cba.isohostsim.ui.screens.config.ContainerConfig
import lk.cba.isohostsim.ui.screens.config.SimulatorConfigLayout
import lk.cba.isohostsim.ui.session.SimulatorSessionManager

@Composable
fun HsmCommandConfigScreen(
    navigationController: NavigationController,
    appState: UnifiedSimulatorState,
) {
    val tabsList = listOf(
        ConfigTab(
            label = "Connection Settings",
            content = {
                ConnectionSettingsTab(
                    config = appState.currentConfig(SimulatorType.HSM_COMMAND)!! as HsmCommandConfig
                ) { updatedConfig ->
                    appState.updateConfig(updatedConfig)
                }
            }
        ),
        ConfigTab(
            label = "SSL/TLS Configuration",
            content = {
                SslConfigurationTab(
                    config = appState.currentConfig(SimulatorType.HSM_COMMAND)!! as HsmCommandConfig
                ) { updatedConfig ->
                    appState.updateConfig(updatedConfig)
                }
            }
        ),
        ConfigTab(
            label = "Load Test Settings",
            content = {
                LoadTestSettingsTab(
                    config = appState.currentConfig(SimulatorType.HSM_COMMAND)!! as HsmCommandConfig
                ) { updatedConfig ->
                    appState.updateConfig(updatedConfig)
                }
            }
        )
    )

    Scaffold(
        topBar = {
            AppBarWithBack(
                title = "HSM Host Console Configuration",
                onBackClick = { navigationController.goBack() }
            )
        },
        backgroundColor = MaterialTheme.colors.background
    ) {
        SimulatorConfigLayout(
            config = ContainerConfig(
                tabs = tabsList,
                icon = Icons.Default.Terminal,
                label = "HSM Host Console",
                simulatorConfigs = appState.hsmCommandConfigs.value,
                currentConfig = { appState.currentConfig(SimulatorType.HSM_COMMAND) },
                containerStatus = DevelopmentStatus.BETA
            ),
            onSelectConfig = { appState.selectConfig(it.id) },
            createNewConfig = {
                appState.addConfig(
                    HsmCommandConfig(
                        id = appState.generateConfigId(),
                        name = "HSM Host Console - ${appState.hsmCommandConfigs.value.size + 1}"
                    )
                )
            },
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
                    (appState.currentConfig(SimulatorType.HSM_COMMAND) as HsmCommandConfig).copy(
                        modifiedDate = System.currentTimeMillis()
                    )
                )
                appState.save()
            }
        )
    }
}
