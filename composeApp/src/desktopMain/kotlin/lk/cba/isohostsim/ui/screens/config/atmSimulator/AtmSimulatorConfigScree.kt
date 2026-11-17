package lk.cba.isohostsim.ui.screens.config.atmSimulator

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import lk.cba.isohostsim.ui.navigation.NavigationController
import lk.cba.isohostsim.ui.navigation.stateConfigs.UnifiedSimulatorState
import lk.cba.isohostsim.ui.screens.components.AppBarWithBack

@Composable
fun AtmSimulatorConfigScreen(
    navigationController: NavigationController,
    appState: UnifiedSimulatorState,
) {
//    appState.selectedSimulatorType = SimulatorType.ATM
    Scaffold(
        topBar = {
            AppBarWithBack(
                title = "ATM Simulator Configuration",
                onBackClick = { navigationController.goBack() })
        },
        backgroundColor = MaterialTheme.colors.background
    ) {

    }
}