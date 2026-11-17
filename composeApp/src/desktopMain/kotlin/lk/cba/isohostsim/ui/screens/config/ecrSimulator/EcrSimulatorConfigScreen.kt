package lk.cba.isohostsim.ui.screens.config.ecrSimulator

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.runtime.Composable
import lk.cba.isohostsim.ui.navigation.Destination
import lk.cba.isohostsim.ui.navigation.NavigationController
import lk.cba.isohostsim.ui.navigation.stateConfigs.SimulatorType
import lk.cba.isohostsim.ui.navigation.stateConfigs.UnifiedSimulatorState
import lk.cba.isohostsim.ui.navigation.stateConfigs.ecr.EcrSimulatorConfig
import lk.cba.isohostsim.ui.screens.components.AppBarWithBack
import lk.cba.isohostsim.ui.screens.components.DevelopmentStatus
import lk.cba.isohostsim.ui.screens.config.ConfigTab
import lk.cba.isohostsim.ui.screens.config.ContainerConfig
import lk.cba.isohostsim.ui.screens.config.SimulatorConfigLayout
import lk.cba.isohostsim.ui.session.SimulatorSessionManager

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
