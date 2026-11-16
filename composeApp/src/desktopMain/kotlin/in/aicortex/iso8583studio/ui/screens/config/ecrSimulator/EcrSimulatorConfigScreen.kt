package `in`.aicortex.iso8583studio.ui.screens.config.ecrSimulator

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.runtime.Composable
import `in`.aicortex.iso8583studio.ui.navigation.Destination
import `in`.aicortex.iso8583studio.ui.navigation.NavigationController
import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.SimulatorType
import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.UnifiedSimulatorState
import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.ecr.EcrSimulatorConfig
import `in`.aicortex.iso8583studio.ui.screens.components.AppBarWithBack
import `in`.aicortex.iso8583studio.ui.screens.components.DevelopmentStatus
import `in`.aicortex.iso8583studio.ui.screens.config.ConfigTab
import `in`.aicortex.iso8583studio.ui.screens.config.ContainerConfig
import `in`.aicortex.iso8583studio.ui.screens.config.SimulatorConfigLayout
import `in`.aicortex.iso8583studio.ui.session.SimulatorSessionManager

/** Saved ECR Simulator profiles: which terminal to drive and the defaults for the console forms. */
@Composable
fun EcrSimulatorConfigScreen(
    navigationController: NavigationController,
    appState: UnifiedSimulatorState,
) {
    fun current() = appState.currentConfig(SimulatorType.ECR) as EcrSimulatorConfig

    val tabsList = listOf(
        ConfigTab(
            label = "Connection",
            content = { EcrConnectionTab(config = current()) { appState.updateConfig(it) } },
        ),
        ConfigTab(
            label = "Transaction Defaults",
            content = { EcrDefaultsTab(config = current()) { appState.updateConfig(it) } },
        ),
    )

    Scaffold(
        topBar = {
            AppBarWithBack(
                title = "ECR Simulator Configuration",
                onBackClick = { navigationController.goBack() },
            )
        },
        backgroundColor = MaterialTheme.colors.background,
    ) {
        SimulatorConfigLayout(
            config = ContainerConfig(
                tabs = tabsList,
                icon = Icons.Default.PointOfSale,
                label = "ECR Simulator",
                simulatorConfigs = appState.ecrConfigs.value,
                currentConfig = { appState.currentConfig(SimulatorType.ECR) },
                containerStatus = DevelopmentStatus.STABLE,
            ),
            onSelectConfig = { appState.selectConfig(it.id) },
            createNewConfig = {
                appState.addConfig(
                    EcrSimulatorConfig(
                        id = appState.generateConfigId(),
                        name = "ECR - ${appState.ecrConfigs.value.size + 1}",
                    )
                )
            },
            onDeleteConfig = { appState.deleteConfig(it.id) },
            onLaunchSimulator = {
                appState.selectConfig(it.id)
                SimulatorSessionManager.launchSimulator(it)
                navigationController.navigateTo(Destination.Home)
            },
            onSaveAllConfigs = {
                appState.updateConfig(current().copy(modifiedDate = System.currentTimeMillis()))
                appState.save()
            },
        )
    }
}
