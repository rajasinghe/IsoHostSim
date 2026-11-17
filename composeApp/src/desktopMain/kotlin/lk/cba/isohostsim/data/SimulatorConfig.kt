package lk.cba.isohostsim.data

import lk.cba.isohostsim.ui.navigation.stateConfigs.SimulatorType

/**
 * Base interface for all simulator configurations
 */
interface SimulatorConfig {
    val id: String
    val name: String
    val description: String
    val serverAddress: String
    val serverPort: Int
    val simulatorType: SimulatorType
    val enabled: Boolean
    val createdDate: Long
    val modifiedDate: Long
    val version: String
}