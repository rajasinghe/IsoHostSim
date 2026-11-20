package lk.cba.isohostsim.ui.screens.parser

import androidx.compose.runtime.mutableStateOf
import lk.cba.isohostsim.data.model.StudioTool
import lk.cba.isohostsim.ui.session.SimulatorSessionManager

/** Two packets to open side by side in the ISO8583 Parser, with what to call each one. //gimhan added */
data class ParserComparison(
    val firstHex: String,
    val secondHex: String,
    val firstLabel: String,
    val secondLabel: String,
)

/**
 * Lets another screen open the ISO8583 Parser already in compare mode with two packets filled in.
 * The parser picks up [pending] when it shows (or straight away if its tab is already open) and
 * clears it. //gimhan added
 */
object ParserHandoff {
    val pending = mutableStateOf<ParserComparison?>(null)

    fun compare(comparison: ParserComparison) {
        pending.value = comparison
        SimulatorSessionManager.openTool(StudioTool.MESSAGE_PARSER)
    }
}
