package lk.cba.isohostsim.ui.screens.config.issuerSystem

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import lk.cba.isohostsim.ui.navigation.NavigationController
import lk.cba.isohostsim.ui.navigation.stateConfigs.UnifiedSimulatorState
import lk.cba.isohostsim.ui.screens.components.AppBarWithBack

@Composable
fun IssuerSystemConfigScreen(
    navigationController: NavigationController,
    appState: UnifiedSimulatorState,
) {
//    appState.selectedSimulatorType = SimulatorType.ISSUER
    Scaffold(
        topBar = {
            AppBarWithBack(
                title = "Issuer System Configuration",
                onBackClick = { navigationController.goBack() })
        },
        backgroundColor = MaterialTheme.colors.background
    ) {

    }
}