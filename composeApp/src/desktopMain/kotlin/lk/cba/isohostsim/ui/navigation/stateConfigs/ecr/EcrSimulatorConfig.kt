package lk.cba.isohostsim.ui.navigation.stateConfigs.ecr

import lk.cba.isohostsim.data.SimulatorConfig
import lk.cba.isohostsim.domain.service.ecr.EcrCommand
import lk.cba.isohostsim.ui.navigation.stateConfigs.SimulatorType
import kotlinx.serialization.Serializable

/** How the ECR reaches the terminal. These are the two transports the CBA ECR library offers. */
@Serializable
enum class EcrTransport(val label: String) {
    /** The terminal's ECR TCP server (HNB app: port 5555, Wi-Fi). */
    TCP("TCP / Wi-Fi"),

    /** The terminal's USB virtual serial port ("USB Virtual Serial Port (PAX)" on Windows). */
    USB("USB"),
}

/**
 * A saved ECR Simulator profile: which terminal to drive and the defaults the console forms start
 * with. The defaults mirror the CBA ECR demo app.
 */
@Serializable
data class EcrSimulatorConfig(
    override val id: String,
    override val name: String,
    override val description: String = "",
    override val simulatorType: SimulatorType = SimulatorType.ECR,
    override val enabled: Boolean = true,
    override val createdDate: Long = System.currentTimeMillis(),
    override val modifiedDate: Long = System.currentTimeMillis(),
    override val version: String = "1.0",

    val transport: EcrTransport = EcrTransport.TCP,
    /** Terminal address on the QA network (the IM30 on the laptop's hotspot by default). */
    val terminalIp: String = "10.42.0.246",
    val port: Int = 5555,
    /** Optional USB port to force, e.g. `/dev/ttyACM0` or `COM5`; blank means auto-detect. */
    val usbPortOverride: String = "",
    /** Seconds the library waits for the final result after the ACK (demo default: 70). */
    val timeoutSec: Int = 70,
    /** Ask the library for encrypted replies (needs a key sent with Transfer Key first). */
    val encrypted: Boolean = false,

    val currency: String = "LKR",
    val detail: String = "Y",
    val print: String = "Y",
    /** ECR reference for the next sale; goes up by one after every approved sale. */
    val nextEcrRef: Long = 1,
    /**
     * Terminal password used to prefill Void and Settlement. This is a test credential for a QA
     * terminal and is stored in plain text with the rest of the profile.
     */
    val testPassword: String = "",
    /**
     * Commands that send Check Status (`active`) first. The terminal ignores the same command twice
     * in a row and Check Status resets that, so QA does not have to send it by hand each time.
     */
    val checkStatusBefore: Set<EcrCommand> = DEFAULT_CHECK_STATUS_BEFORE,
) : SimulatorConfig {
    override val serverAddress: String get() = terminalIp
    override val serverPort: Int get() = port

    companion object {
        /** Every command that can have it: not Check Status itself, and not Cancel, which runs alongside. */
        val CHECK_STATUS_CAPABLE: Set<EcrCommand> =
            EcrCommand.entries.toSet() - EcrCommand.CHECK_STATUS - EcrCommand.TRANSACTION_CANCEL

        val DEFAULT_CHECK_STATUS_BEFORE: Set<EcrCommand> = CHECK_STATUS_CAPABLE
    }
}
