package lk.cba.isohostsim.domain.service.hostSimulatorService

/**
 * What the Host Simulator does with the response it has just built. QA switches this on the
 * running gateway to test the terminal's timeout and reversal handling.
 */
enum class ResponseMode(val label: String) {
    /** Send the response straight away. */
    NORMAL("Respond normally"),

    /** Build the response but never send it. The connection stays open, so the terminal times out. */
    NO_RESPONSE("Don't respond"),

    /** Wait [ResponseControl.delaySeconds] before sending. */
    DELAY("Delay response"),
}

/**
 * Read for every response, so a change applies to the next transaction. One per gateway, kept in
 * a registry by gateway id: the running screen recreates its HostSimulator object when it redraws,
 * and the setting must stay with the gateway, not with that object.
 */
class ResponseControl {
    @Volatile var mode: ResponseMode = ResponseMode.NORMAL
    @Volatile var delaySeconds: Int = 5

    companion object {
        private val byGateway = java.util.concurrent.ConcurrentHashMap<String, ResponseControl>()

        fun of(gatewayId: String): ResponseControl = byGateway.getOrPut(gatewayId) { ResponseControl() }
    }
}
