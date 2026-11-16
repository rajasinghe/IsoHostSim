package `in`.aicortex.iso8583studio.domain.service.ecr

/**
 * The commands the CBA ECR library can send, one per button in the ECR demo app.
 *
 * [wireName] is the `dataType` the library puts on the wire. Settlement, Last Settlement and
 * Transaction Cancel use the library's own names, which the HNB 1.07 terminal app does not know
 * (it expects `settle` and has no last-settle or cancel command), so [knownTerminalGap] explains
 * the failure QA will see against that terminal.
 */
enum class EcrCommand(
    val label: String,
    val wireName: String,
    val knownTerminalGap: String? = null,
) {
    CHECK_STATUS("Check Status", "active"),
    BIN_REQUEST("BIN Request", "binRequest"),
    SALE("Sale", "sale"),
    VOID("Void", "void"),
    REQUEST_LAST("Request Last", "RequestLast"),
    TRANSFER_KEY("Transfer Key", "KeyTransfer",
        "The HNB terminal app does not register KeyTransfer, so it always answers NAK 2."),
    SETTLEMENT("Settlement", "settleRequest",
        "The library sends 'settleRequest'; the HNB 1.07 terminal app only accepts 'settle'."),
    LAST_SETTLEMENT("Last Settlement", "lastSettleRequest",
        "The HNB 1.07 terminal app has no last-settlement command."),
    TRANSACTION_CANCEL("Transaction Cancel", "tranCancel",
        "The HNB 1.07 terminal app has no cancel command. Over TCP the library opens a second " +
            "socket, so the running sale's result may be lost."),
}
