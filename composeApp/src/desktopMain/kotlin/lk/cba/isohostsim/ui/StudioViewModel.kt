package lk.cba.isohostsim.ui

import lk.cba.isohostsim.ui.navigation.stateConfigs.UnifiedSimulatorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object Studio {
    private val _uiState = MutableStateFlow(UnifiedSimulatorState())
    val appState: StateFlow<UnifiedSimulatorState> = _uiState.asStateFlow()
}