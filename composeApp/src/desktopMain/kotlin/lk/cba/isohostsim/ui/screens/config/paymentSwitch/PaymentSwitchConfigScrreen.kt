package lk.cba.isohostsim.ui.screens.config.paymentSwitch

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import lk.cba.isohostsim.ui.navigation.NavigationController
import lk.cba.isohostsim.ui.navigation.stateConfigs.UnifiedSimulatorState
import lk.cba.isohostsim.ui.screens.components.AppBarWithBack

@Composable
fun PaymentSwitchConfigScreen(
    navigationController: NavigationController,
    appState: UnifiedSimulatorState,
) {
//    appState.selectedSimulatorType = SimulatorType.SWITCH
    Scaffold(
        topBar = {
            AppBarWithBack(
                title = "Payment Switch Configuration",
                onBackClick = { navigationController.goBack() })
        },
        backgroundColor = MaterialTheme.colors.background
    ) {

    }
}