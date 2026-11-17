package lk.cba.isohostsim.ui.screens.config.acquirerGateway

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import lk.cba.isohostsim.ui.navigation.NavigationController
import lk.cba.isohostsim.ui.navigation.stateConfigs.UnifiedSimulatorState
import lk.cba.isohostsim.ui.screens.components.AppBarWithBack

@Composable
fun AcquiringGatewayConfigScreen(
    navigationController: NavigationController,
    appState: UnifiedSimulatorState,
) {
    // SimulatorType.ACQUIRER
    Scaffold(
        topBar = {
            AppBarWithBack(
                title = "Acquiring Gateway Configuration",
                onBackClick = { navigationController.goBack() })
        },
        backgroundColor = MaterialTheme.colors.background
    ) {

    }
}